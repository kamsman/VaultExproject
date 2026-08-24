package com.vaultex.domain.usecase

import android.util.Base64
import com.vaultex.core.crypto.Base58
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.core.tx.BtcTransactionService
import com.vaultex.core.tx.EvmTransactionService
import com.vaultex.core.tx.SolanaTransactionService
import com.vaultex.core.tx.TronTransactionService
import com.vaultex.core.tx.Utxo
import com.vaultex.core.validation.AddressValidator
import com.vaultex.data.remote.api.BitcoinApi
import com.vaultex.data.remote.api.EvmRpcApi
import com.vaultex.data.remote.api.SolanaRpcApi
import com.vaultex.data.remote.api.TronApi
import com.vaultex.data.remote.dto.JsonRpcRequest
import com.vaultex.data.remote.dto.TronBroadcastDto
import com.vaultex.data.remote.dto.TronCreateTxBody
import com.vaultex.data.remote.dto.TronTriggerSmartContractBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Named

class SendCryptoUseCase @Inject constructor(
    private val secureStorage: SecureStorage,
    private val evmTx: EvmTransactionService,
    private val btcTx: BtcTransactionService,
    private val solTx: SolanaTransactionService,
    private val tronTx: TronTransactionService,
    @Named("eth") private val ethRpc: EvmRpcApi,
    @Named("bnb") private val bnbRpc: EvmRpcApi,
    private val bitcoinApi: BitcoinApi,
    private val solanaRpc: SolanaRpcApi,
    private val tronApi: TronApi
) {
    sealed class Result {
        data class Success(val txHash: String) : Result()
        data class Error(val message: String) : Result()
    }

    companion object {
        const val USDT_TRC20_CONTRACT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        const val USDT_ERC20_CONTRACT = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        const val USDT_BEP20_CONTRACT = "0x55d398326f99059fF775485246999027B3197955"

        /*
        ─── PLAFONDS DE FRAIS RÉSEAU ──────────────────────────────────────
        Le prix du gas (EVM) et le feerate (Bitcoin) viennent de nœuds publics.
        Un nœud compromis — ou un attaquant capable d'intercepter le trafic —
        peut renvoyer une valeur délirante : l'application signerait alors une
        transaction qui BRÛLE TOUT LE SOLDE en frais de mineur, opération
        strictement irréversible.

        Nuance EIP-1559 : le maxFeePerGas n'est qu'un plafond (on ne paie que
        baseFee + pourboire), mais le POURBOIRE, lui, est payé intégralement au
        mineur. C'est donc maxPriorityFeePerGas le champ réellement dangereux —
        et sur BNB Chain, en type-0, c'est le gasPrice entier qui est payé.

        Ces plafonds sont volontairement très au-dessus des pires congestions
        jamais observées : ils ne bloquent aucun envoi légitime. On REFUSE
        l'envoi au lieu de rogner la valeur — une transaction sous-payée
        resterait bloquée dans le mempool, ce qui est un autre mauvais
        résultat, et masquerait l'anomalie.
        ───────────────────────────────────────────────────────────────────
         */
        private const val GWEI = 1_000_000_000L

        /** Pourboire mineur ETH. Usuel : 0,1 à 3 gwei ; pics de « mint war » : ~30. */
        private const val ETH_MAX_PRIORITY_GWEI = 50L

        /** Plafond total ETH. Le record de base fee jamais observé avoisine 1 500 gwei. */
        private const val ETH_MAX_FEE_GWEI = 2_000L

        /** BNB Chain : usuel 0,1 à 5 gwei, jamais vu au-delà de ~20. */
        private const val BSC_MAX_GAS_GWEI = 100L

        /** Bitcoin : les pires pics (halving 2024) ont frôlé 1 500 sat/vB. */
        private const val BTC_MAX_SAT_PER_VBYTE = 2_000L

        /*
        ─── LIMITES DE GAS : LES MÊMES À L'AFFICHAGE ET À LA SIGNATURE ─────
        L'estimation montrée à l'utilisateur et la réserve réellement exigée
        par le nœud DOIVENT reposer sur la même limite de gas.

        Ce n'était pas le cas : l'écran calculait avec 21 000 (transfert ETH
        « théorique ») alors que la signature demande eth_estimateGas × 1,2,
        soit 25 200. L'écran annonçait donc des frais 20 % TROP BAS, et
        Ethereum exige `solde >= montant + maxFeePerGas × gasLimit`. Un envoi
        calculé au plus juste sur le total affiché était rejeté « insufficient
        funds » alors que l'application venait d'afficher que le compte
        suffisait — incompréhensible côté utilisateur.

        Même raison pour les tokens : la signature applique un plancher de
        100 000 de gas, l'écran comptait 65 000.

        NUANCE POUR LES JETONS, depuis que la signature utilise l'estimation
        réelle du nœud plutôt que le plancher : l'écran peut désormais annoncer
        un peu PLUS que ce que la signature réserve. C'est le sens sûr.

        Le sens dangereux — écran en dessous de la signature — ne peut faire
        qu'une victime : un envoi calculé au plus juste sur le total affiché,
        rejeté ensuite par le nœud. Or ce calcul n'existe pas pour un jeton :
        les frais sont en ETH/BNB, le montant envoyé est en jeton, et le
        bouton « Max » ne soustrait donc jamais l'un de l'autre. Seuls les
        envois NATIFS sont concernés, et eux gardent une limite fixe des deux
        côtés.
        ───────────────────────────────────────────────────────────────────
         */
        /** Transfert natif ETH/BNB : 21 000 × marge 1,2 (identique à la signature). */
        const val GAS_LIMIT_NATIVE = 25_200L

        /**
         * Transfert ERC-20/BEP-20 : repli quand `eth_estimateGas` échoue, et
         * base de l'estimation affichée à l'écran. La signature, elle, part de
         * l'estimation réelle du nœud dès qu'elle est disponible.
         */
        const val GAS_LIMIT_TOKEN = 100_000L
    }

    /** Frais renvoyés par le réseau jugés aberrants → envoi refusé. */
    private class FeeTooHighException(message: String) : Exception(message)

    /*
    ─── DESTINATIONS INTERDITES ───────────────────────────────────────────
    Plutôt qu'une liste noire d'adresses d'arnaqueurs — qui suppose une source
    de données à maintenir et sera toujours en retard sur les attaquants — on
    bloque les destinations qui provoquent une perte DÉFINITIVE et certaine,
    et qui n'ont aucun usage légitime :

    · l'adresse nulle et les adresses de destruction (« burn ») ;
    · les contrats des jetons eux-mêmes. Envoyer de l'USDT au contrat USDT est
      l'erreur la plus fréquente et la plus coûteuse du secteur : le contrat
      accepte le transfert et les fonds sont perdus pour toujours, personne ne
      pouvant les en sortir. L'adresse est parfaitement valide, donc aucun
      contrôle de format ne l'attrape.

    Ces adresses sont connues à l'avance : le contrôle est instantané, sans
    appel réseau et sans faux positif.
    ───────────────────────────────────────────────────────────────────────
     */
    private fun forbiddenDestination(address: String): Boolean {
        val a = address.trim().lowercase()
        if (a.isEmpty()) return false
        val burn = setOf(
            "0x0000000000000000000000000000000000000000",
            "0x000000000000000000000000000000000000dead",
            "0xdead000000000000000042069420694206942069"
        )
        val tokenContracts = setOf(
            USDT_ERC20_CONTRACT.lowercase(),
            USDT_BEP20_CONTRACT.lowercase(),
            USDT_TRC20_CONTRACT.lowercase()
        )
        return a in burn || a in tokenContracts
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    UN ENVOI N'EST « RÉUSSI » QUE SI LE RÉSEAU A RENDU UN VRAI IDENTIFIANT
    ═══════════════════════════════════════════════════════════════════════

    Les chemins d'envoi concluaient ainsi :

        Result.Success(reponse.result as? String ?: signed)      // EVM
        Result.Success(reponse.result as? String ?: "unknown")   // Solana

    Autrement dit : si le nœud répondait SANS erreur mais SANS identifiant —
    ce qui arrive avec les nœuds publics, dont les réponses sont parfois
    tronquées ou malformées — l'application prenait la transaction SIGNÉE
    entière, ou le mot « unknown », et le traitait comme un identifiant de
    transaction.

    La suite est mécanique, et c'est là que c'est grave :

    · l'écran annonce « Envoi effectué » ;
    · une ligne est écrite dans l'historique avec cet identifiant ;
    · le suivi de confirmation le cherche sur la chaîne — et ne le trouvera
      JAMAIS, puisqu'il n'existe pas ;
    · la transaction reste « en attente » indéfiniment, badge compris.

    L'utilisateur est donc informé d'un succès, puis regarde une attente qui
    ne se résoudra pas. Relancer donne exactement le même résultat. C'est
    précisément le comportement rapporté en test : « si c'est parti c'est
    parti, si c'est pas parti c'est pas parti ».

    POURQUOI UNE ERREUR PLUTÔT QU'UN FAUX SUCCÈS. Dans ce cas, l'état réel de
    la transaction est INCONNU — elle est peut-être partie. Le message le dit
    donc franchement et demande de vérifier avant de réessayer, au lieu
    d'affirmer une chose ou son contraire. Et rien n'est écrit dans
    l'historique : mieux vaut aucune trace qu'une trace fausse.

    Réessayer reste d'ailleurs peu risqué sur ces chaînes : le nonce EVM et
    les mêmes UTXO Bitcoin reproduisent une transaction IDENTIQUE, donc le
    même identifiant. Un second envoi ne peut pas doubler le premier.
    ═══════════════════════════════════════════════════════════════════════
     */
    private fun identifiantValide(hash: String?, chaine: String): Boolean {
        val h = hash?.trim().orEmpty()
        if (h.isEmpty()) return false
        return when (chaine) {
            // Ethereum, BNB Chain, Bitcoin, Tron : 32 octets en hexadécimal.
            // Le préfixe 0x est présent côté EVM, absent côté Bitcoin et Tron.
            "EVM", "BTC", "TRX" -> h.removePrefix("0x").matches(Regex("^[0-9a-fA-F]{64}$"))
            // Solana : signature en base58, 64 octets — 86 à 88 caractères.
            "SOL" -> h.matches(Regex("^[1-9A-HJ-NP-Za-km-z]{80,90}$"))
            else -> false
        }
    }

    /** Réponse sans identifiant exploitable : on ne tranche pas à la place du réseau. */
    private fun envoiIndetermine() = Result.Error(
        "Le réseau a répondu sans identifiant de transaction. Impossible de savoir " +
            "si l'envoi est parti ou non.\n\n" +
            "Vérifie ton solde et ton historique AVANT de réessayer."
    )

    /** Message affiché quand la destination est une adresse à perte certaine. */
    private fun forbiddenDestinationError() = Result.Error(
        "Cette adresse est celle d'un contrat de jeton ou une adresse de destruction : " +
            "les fonds envoyés là seraient DÉFINITIVEMENT perdus, personne ne pourrait les " +
            "récupérer. Vérifiez l'adresse du destinataire."
    )

    /**
     * Refuse un prix de gas au-dessus de [maxGwei]. [label] nomme le champ
     * fautif pour que le message reste compréhensible en cas de blocage.
     */
    private fun requireSaneGas(value: BigInteger, maxGwei: Long, label: String): BigInteger {
        val max = BigInteger.valueOf(maxGwei).multiply(BigInteger.valueOf(GWEI))
        if (value > max) {
            val gwei = value.divide(BigInteger.valueOf(GWEI))
            throw FeeTooHighException(
                "Frais réseau anormalement élevés ($label : $gwei gwei, plafond de sécurité " +
                    "$maxGwei gwei). Envoi bloqué pour protéger vos fonds — réessayez plus tard."
            )
        }
        return value
    }

    /**
     * Point d'entrée UNIQUE d'un envoi : convertit le montant saisi (unité
     * humaine) selon la chaîne puis appelle le bon envoi. Utilisé à la fois
     * par l'UI (envoi direct en ligne) et par la file hors-ligne
     * (PendingSendWorker), afin que les deux chemins soient identiques.
     */
    suspend fun sendByChain(
        chain: String, toAddress: String, amount: String,
        serviceFeeCrypto: Double = 0.0   // frais de service VaultEx (BTC/SOL), en unité crypto
    ): Result {
        // Contrôle des destinations à perte certaine. Placé ICI parce que
        // sendByChain est le point d'entrée UNIQUE des envois (UI en ligne et
        // file hors-ligne) : aucun chemin ne peut le contourner.
        if (forbiddenDestination(toAddress)) return forbiddenDestinationError()

        // Token personnalisé (ERC-20/BEP-20 ajouté par contrat). Encodé sous la
        // forme "ERC20:<ETH|BNB>:<contract>:<decimals>" pour que CE chemin unique
        // serve l'envoi direct ET la file hors-ligne (PendingSendWorker).
        if (chain.startsWith("ERC20:")) {
            val parts = chain.split(":")
            if (parts.size != 4) return Result.Error("Token personnalisé invalide")
            val evm = parts[1]
            val contract = parts[2]
            val decimals = parts[3].toIntOrNull() ?: return Result.Error("Décimales invalides")
            val chainId = if (evm == "BNB") 56L else 1L
            val amountWei = try {
                BigDecimal(amount).multiply(BigDecimal.TEN.pow(decimals)).toBigInteger()
            } catch (_: Exception) { return Result.Error("Montant invalide") }
            return sendErc20(toAddress = toAddress, amountWei = amountWei,
                contractAddress = contract, chainId = chainId)
        }
        return when (chain) {
            "ETH", "BNB" -> {
                val chainId = if (chain == "ETH") 1L else 56L
                val amountWei = try {
                    BigDecimal(amount).multiply(BigDecimal("1000000000000000000")).toBigInteger()
                } catch (_: Exception) { return Result.Error("Montant invalide") }
                sendEvm(toAddress = toAddress, amountWei = amountWei, chainId = chainId)
            }
            "BTC" -> {
                val amountSatoshi = try {
                    BigDecimal(amount).multiply(BigDecimal("100000000")).toLong()
                } catch (_: Exception) { return Result.Error("Montant invalide") }
                val svcSat = (serviceFeeCrypto * 100_000_000.0).toLong().coerceAtLeast(0L)
                sendBtc(toAddress = toAddress, amountSatoshi = amountSatoshi, serviceFeeSatoshi = svcSat)
            }
            "TRX" -> {
                val amountSun = try {
                    BigDecimal(amount).multiply(BigDecimal("1000000")).toLong()
                } catch (_: Exception) { return Result.Error("Montant invalide") }
                sendTrx(toAddress = toAddress, amountSun = amountSun)
            }
            "SOL" -> {
                val lamports = try {
                    BigDecimal(amount).multiply(BigDecimal("1000000000")).toLong()
                } catch (_: Exception) { return Result.Error("Montant invalide") }
                sendSol(toAddress = toAddress, lamports = lamports)
            }
            "USDT" -> sendUsdtTrc20(toAddress = toAddress, amountUsdt = amount)
            "USDT-ETH" -> {
                val amountWei = try {
                    BigDecimal(amount).multiply(BigDecimal("1000000")).toBigInteger() // 6 décimales
                } catch (_: Exception) { return Result.Error("Montant invalide") }
                sendErc20(toAddress = toAddress, amountWei = amountWei,
                    contractAddress = USDT_ERC20_CONTRACT, chainId = 1L)
            }
            "USDT-BNB" -> {
                val amountWei = try {
                    BigDecimal(amount).multiply(BigDecimal("1000000000000000000")).toBigInteger() // 18 décimales
                } catch (_: Exception) { return Result.Error("Montant invalide") }
                sendErc20(toAddress = toAddress, amountWei = amountWei,
                    contractAddress = USDT_BEP20_CONTRACT, chainId = 56L)
            }
            else -> Result.Error("Chain non supportée")
        }
    }

    // ─── EVM (ETH / BNB) ─────────────────────────────────────────────

    suspend fun sendEvm(
        toAddress: String,
        amountWei: BigInteger,
        chainId: Long,
        coinType: Int = 60
    ): Result {
        if (!AddressValidator.isValidEvm(toAddress)) return Result.Error("Adresse ETH/BNB invalide (0x + 40 hex requis)")
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        val passphrase = secureStorage.getPassphrase()
        return try {
            val rpc = if (chainId == 1L) ethRpc else bnbRpc
            val fromAddress = WalletManager.deriveAddresses(mnemonic, passphrase).eth

            val nonceRes = rpc.rpcCall(JsonRpcRequest("eth_getTransactionCount",
                mutableListOf(fromAddress as Any, "pending" as Any)))
            val nonce = BigInteger((nonceRes.result as? String ?: "0x0")
                .removePrefix("0x").ifEmpty { "0" }, 16)

            val estimateReq = JsonRpcRequest("eth_estimateGas", mutableListOf(
                mapOf("from" to fromAddress, "to" to toAddress,
                    "value" to "0x${amountWei.toString(16)}") as Any))
            val gasLimit = try {
                BigInteger((rpc.rpcCall(estimateReq).result as? String ?: "0x5208")
                    .removePrefix("0x"), 16)
                    .multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100))
            } catch (_: Exception) { BigInteger.valueOf(GAS_LIMIT_NATIVE) }

            val signed = if (chainId == 1L) {
                // Ethereum mainnet: EIP-1559 (type-2) — accurate base fee + tip
                val (maxPriority, maxFee) = fetchEip1559Fees(rpc)
                evmTx.signTransactionEip1559(mnemonic, passphrase, toAddress, amountWei,
                    maxPriority, maxFee, gasLimit, nonce, chainId, coinType)
            } else {
                // BSC and others: legacy (type-0)
                val gasPrice = fetchLegacyGasPrice(rpc, default = 5_000_000_000L, maxGwei = BSC_MAX_GAS_GWEI)
                evmTx.signTransaction(mnemonic, passphrase, toAddress, amountWei,
                    gasPrice, gasLimit, nonce, chainId, coinType)
            }

            val broadcastRes = rpc.rpcCall(JsonRpcRequest("eth_sendRawTransaction",
                mutableListOf(signed as Any)))
            if (broadcastRes.error != null) Result.Error(broadcastRes.error.message)
            else {
                val hash = broadcastRes.result as? String
                if (identifiantValide(hash, "EVM")) Result.Success(hash!!.trim())
                else envoiIndetermine()
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur transaction EVM")
        }
    }

    // ─── ERC-20 (USDT on ETH / BNB) ──────────────────────────────────

    suspend fun sendErc20(
        toAddress: String,
        amountWei: BigInteger,
        contractAddress: String,
        chainId: Long
    ): Result {
        if (!AddressValidator.isValidEvm(toAddress)) return Result.Error("Adresse ETH/BNB invalide (0x + 40 hex requis)")
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        val passphrase = secureStorage.getPassphrase()
        return try {
            val rpc = if (chainId == 1L) ethRpc else bnbRpc
            val fromAddress = WalletManager.deriveAddresses(mnemonic, passphrase).eth

            val nonceRes = rpc.rpcCall(JsonRpcRequest("eth_getTransactionCount",
                mutableListOf(fromAddress as Any, "pending" as Any)))
            val nonce = BigInteger((nonceRes.result as? String ?: "0x0")
                .removePrefix("0x").ifEmpty { "0" }, 16)

            val paddedTo  = toAddress.removePrefix("0x").padStart(64, '0')
            val paddedAmt = amountWei.toString(16).padStart(64, '0')
            val callData  = "0xa9059cbb$paddedTo$paddedAmt"
            val estimateReq = JsonRpcRequest("eth_estimateGas", mutableListOf(
                mapOf("from" to fromAddress, "to" to contractAddress, "data" to callData) as Any))
            /*
            ─── LIMITE DE GAS : L'ESTIMATION D'ABORD, LE PLANCHER ENSUITE ───

            Le nœud est INTERROGÉ juste au-dessus (eth_estimateGas), qui
            simule le transfert sur l'état réel de la chaîne. Le résultat
            était ensuite écrasé par `.max(100 000)` — l'estimation était donc
            demandée, payée en temps de réponse, puis jetée.

            Ce n'est pas gratuit. Ethereum exige
            `solde >= maxFeePerGas × gasLimit` pour ACCEPTER la transaction,
            et cette limite est une RÉSERVE : le gas non consommé est rendu.
            Réserver 100 000 quand le transfert en consomme 50 000 double donc
            le solde d'ETH exigé, sans que l'utilisateur dépense un centime de
            plus au final.

            Effet observé : un portefeuille contenant 1,80 $ d'ETH se voyait
            refuser un envoi de SHIB dont les frais réels tournaient autour de
            0,70 $. Le refus était exact au regard de la règle du protocole —
            et absurde au regard du solde réellement nécessaire.

            La marge de 25 % couvre l'écart entre la simulation et l'exécution
            (un premier transfert vers une adresse qui ne détient pas encore
            le jeton écrit un emplacement de stockage neuf, plus cher).

            Le plancher reste le repli quand l'estimation ÉCHOUE : sans donnée,
            la prudence est le seul choix défendable — un « out of gas » coûte
            les frais sans transférer le jeton.
            ────────────────────────────────────────────────────────────────
             */
            val estimation: BigInteger? = try {
                val hex = (rpc.rpcCall(estimateReq).result as? String)?.removePrefix("0x")
                if (hex.isNullOrEmpty()) null
                else BigInteger(hex, 16).takeIf { it.signum() > 0 }
            } catch (_: Exception) { null }
            val gasLimit = estimation
                ?.multiply(BigInteger.valueOf(125))?.divide(BigInteger.valueOf(100))
                ?: BigInteger.valueOf(GAS_LIMIT_TOKEN)

            val signed: String
            val feePerGas: BigInteger
            if (chainId == 1L) {
                val (maxPriority, maxFee) = fetchEip1559Fees(rpc)
                feePerGas = maxFee
                signed = evmTx.signErc20TransferEip1559(mnemonic, passphrase, contractAddress, toAddress,
                    amountWei, maxPriority, maxFee, gasLimit, nonce, chainId)
            } else {
                val gasPrice = fetchLegacyGasPrice(rpc, default = 5_000_000_000L, maxGwei = BSC_MAX_GAS_GWEI)
                feePerGas = gasPrice
                signed = evmTx.signErc20Transfer(mnemonic, passphrase, contractAddress, toAddress,
                    amountWei, gasPrice, gasLimit, nonce, chainId)
            }

            // ── Garde-fou gas : un token se paie en NATIF (ETH/BNB). Sans assez
            // de natif, le nœud rejetterait avec un message opaque — on bloque
            // AVANT avec le montant manquant, en clair.
            try {
                val balHex = rpc.rpcCall(JsonRpcRequest("eth_getBalance",
                    mutableListOf(fromAddress as Any, "latest" as Any))).result as? String
                if (balHex != null) {
                    val nativeBal = BigInteger(balHex.removePrefix("0x").ifEmpty { "0" }, 16)
                    val gasCost = gasLimit.multiply(feePerGas)
                    if (nativeBal < gasCost) {
                        val unit = if (chainId == 1L) "ETH" else "BNB"
                        val need = java.math.BigDecimal(gasCost).movePointLeft(18)
                            .setScale(6, java.math.RoundingMode.UP).stripTrailingZeros().toPlainString()
                        val dispo = java.math.BigDecimal(nativeBal).movePointLeft(18)
                            .setScale(6, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString()
                        /*
                        Le message annonçait un montant SANS dire ce qu'il est.

                        Ce chiffre est une RÉSERVE, pas une dépense : le réseau
                        exige de la bloquer pour accepter la transaction, puis
                        rend tout le gas non consommé. La dépense réelle est
                        nettement plus basse. Sans cette précision, quelqu'un
                        qui détient de quoi payer les frais lit « il te faut
                        plus d'ETH » et conclut que l'application se trompe.

                        On affiche aussi le solde DISPONIBLE : sans les deux
                        nombres côte à côte, impossible de savoir combien il
                        manque.
                         */
                        return Result.Error(
                            "Les frais réseau d'un envoi de jeton se paient en $unit, " +
                                "jamais dans le jeton envoyé.\n\n" +
                                "Réserve exigée par le réseau : ~$need $unit\n" +
                                "Disponible sur ce wallet : $dispo $unit\n\n" +
                                "Cette réserve est un plafond : le $unit non consommé " +
                                "t'est rendu, la dépense réelle sera plus faible. " +
                                "Le réseau exige quand même de pouvoir la bloquer."
                        )
                    }
                }
            } catch (_: Exception) { /* lecture du solde impossible : on laisse le réseau trancher */ }

            val broadcastRes = rpc.rpcCall(JsonRpcRequest("eth_sendRawTransaction",
                mutableListOf(signed as Any)))
            if (broadcastRes.error != null) Result.Error(broadcastRes.error.message)
            else {
                val hash = broadcastRes.result as? String
                if (identifiantValide(hash, "EVM")) Result.Success(hash!!.trim())
                else envoiIndetermine()
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur transaction ERC-20")
        }
    }

    // ─── BITCOIN ─────────────────────────────────────────────────────

    suspend fun sendBtc(toAddress: String, amountSatoshi: Long, serviceFeeSatoshi: Long = 0L): Result {
        if (!AddressValidator.isValidBtc(toAddress)) return Result.Error("Adresse BTC invalide")
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        val passphrase = secureStorage.getPassphrase()
        return try {
            val btcAddress = WalletManager.deriveAddresses(mnemonic, passphrase).btc
            val utxosDto = bitcoinApi.getUtxos(btcAddress)
            val feeEstimates = bitcoinApi.getFeeEstimates()
            // Plancher à 2 sat/vB : une estimation à ~1 sat/vB donne un taux réel
            // limite (< min relay) → rejet 400 du nœud. On arrondit vers le haut.
            val satPerByte = kotlin.math.ceil(feeEstimates["6"] ?: feeEstimates["3"] ?: feeEstimates["1"] ?: 10.0)
                .toLong().coerceAtLeast(2L)
            // Même risque que le gas EVM : le feerate vient d'un service externe
            // et est payé INTÉGRALEMENT aux mineurs. Une valeur aberrante viderait
            // le portefeuille en frais, sans retour possible.
            if (satPerByte > BTC_MAX_SAT_PER_VBYTE) {
                return Result.Error(
                    "Frais réseau Bitcoin anormalement élevés ($satPerByte sat/vB, plafond de " +
                        "sécurité $BTC_MAX_SAT_PER_VBYTE). Envoi bloqué pour protéger vos fonds — " +
                        "réessayez plus tard."
                )
            }

            val confirmedUtxos = utxosDto.filter { it.status.confirmed }.sortedByDescending { it.value }
                .map { Utxo(txHash = it.txid, outputIndex = it.vout, valueSatoshi = it.value) }
            if (confirmedUtxos.isEmpty()) {
                // On ne dépense QUE des entrées confirmées : dépenser du BTC à 0
                // confirmation est risqué (la transaction entrante peut encore être
                // remplacée/rejetée, ce qui invaliderait l'envoi). On distingue le
                // BTC « en cours de confirmation » d'un solde réellement vide.
                val hasUnconfirmed = utxosDto.any { !it.status.confirmed && it.value > 0 }
                return if (hasUnconfirmed)
                    Result.Error("Ton BTC vient d'arriver et attend sa confirmation sur le réseau Bitcoin (souvent 10 à 30 min). Réessaie une fois qu'il est confirmé.")
                else
                    Result.Error("Aucun BTC disponible à envoyer.")
            }

            // Frais = f(nombre d'entrées RÉELLEMENT dépensées). Le signataire choisit
            // les UTXO au plus juste (tri décroissant, arrêt dès que montant+frais est
            // couvert) ; on simule EXACTEMENT la même sélection ici pour que les frais
            // correspondent aux entrées signées.
            // Ancien bug : frais calculés pour min(nUTXO, 10) entrées → surpaiement
            // massif (petit envoi depuis un gros portefeuille) OU sous-paiement quand
            // > 10 entrées étaient nécessaires (aucun plafond côté signature) → tx
            // rejetée « min relay fee not met ». La sortie de frais de service n'était
            // pas non plus comptée dans la taille estimée.
            val svcFee = if (serviceFeeSatoshi > 546) serviceFeeSatoshi else 0L
            val outCount = 2 + (if (svcFee > 0L) 1 else 0)   // destinataire + change (+ frais service)
            var inputTotal = 0L
            var usedInputs = 0
            var feeSatoshi = 0L
            for (u in confirmedUtxos) {
                usedInputs++
                inputTotal += u.valueSatoshi
                // vsize P2WPKH : ~11 vB d'entête + ~68 vB/entrée + ~31 vB/sortie.
                feeSatoshi = (11L + 68L * usedInputs + 31L * outCount) * satPerByte
                if (inputTotal >= amountSatoshi + feeSatoshi + svcFee) break
            }
            if (inputTotal < amountSatoshi + feeSatoshi + svcFee)
                return Result.Error("Solde insuffisant pour couvrir le montant et les frais réseau")

            val signed = btcTx.signTransaction(
                mnemonic, passphrase, toAddress, amountSatoshi, feeSatoshi, confirmedUtxos,
                serviceFeeSatoshi = serviceFeeSatoshi,
                serviceFeeAddress = com.vaultex.BuildConfig.VAULTEX_FEE_RECIPIENT_BTC
            )
            val signedHex = signed.joinToString("") { "%02x".format(it) }
            val txHash = try {
                // Réponse en TEXTE BRUT (le txid) — on lit le corps tel quel.
                bitcoinApi.broadcastTx(signedHex.toRequestBody("text/plain".toMediaType()))
                    .string().trim()
            } catch (e: retrofit2.HttpException) {
                // Blockstream renvoie la VRAIE raison du rejet dans le corps de la
                // réponse (ex. « min relay fee not met », « bad-txns-… »), pas dans
                // le statut HTTP. On la fait remonter telle quelle.
                val body = try { e.response()?.errorBody()?.string()?.trim()?.take(280) } catch (_: Exception) { null }
                return Result.Error(if (!body.isNullOrBlank()) body else "Diffusion refusée (HTTP ${e.code()})")
            }
            // Blockstream renvoie le txid en texte brut. Un corps vide ou un
            // texte inattendu ne doit pas devenir un identifiant.
            if (identifiantValide(txHash, "BTC")) Result.Success(txHash.trim())
            else envoiIndetermine()
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur transaction BTC")
        }
    }

    // ─── TRON (TRX natif) — flux : Créer → Signer → Broadcast → Hash ──

    suspend fun sendTrx(toAddress: String, amountSun: Long): Result {
        if (!AddressValidator.isValidTron(toAddress)) return Result.Error("Adresse TRX invalide (T + 34 caractères + checksum)")
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        val passphrase = secureStorage.getPassphrase()
        return try {
            // Étape 1 — Dériver l'adresse owner en hex (format attendu par TronGrid)
            val ownerHex = tronAddrToHex(tronTx.deriveAddress(mnemonic, passphrase))
            val toHex    = tronAddrToHex(toAddress)

            // Étape 2 — Créer la transaction non signée via TronGrid (objet complet)
            val rawTx = tronApi.createTransaction(
                TronCreateTxBody(owner_address = ownerHex, to_address = toHex, amount = amountSun)
            )
            val rawDataHex = rawTx.get("raw_data_hex")?.takeIf { !it.isJsonNull }?.asString
                ?: return Result.Error(tronCreateError(rawTx))

            // Étape 3 — Signer le SHA-256 du raw_data
            val signature = tronTx.signRawTransaction(mnemonic, passphrase, rawDataHex)

            // Étape 4 — Rediffuser la transaction COMPLÈTE (avec sa signature)
            rawTx.add("signature", com.google.gson.JsonArray().apply { add(signature) })
            val broadcast = tronApi.broadcast(rawTx)
            val txId = rawTx.get("txID")?.takeIf { !it.isJsonNull }?.asString ?: ""
            if (broadcast.result == true) Result.Success(broadcast.txid ?: txId)
            else Result.Error(broadcast.message ?: "Broadcast TRX échoué")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur transaction TRX")
        }
    }

    // ─── USDT TRC20 — flux : Trigger → Signer → Broadcast → Hash ────

    suspend fun sendUsdtTrc20(toAddress: String, amountUsdt: String): Result {
        if (!AddressValidator.isValidTron(toAddress)) return Result.Error("Adresse TRX invalide (T + 34 caractères + checksum)")

        // Montant parsé ICI, avant tout accès au stockage sécurisé et tout appel
        // réseau. Les autres chaînes le font déjà dans sendByChain ; ce chemin
        // déléguait tout à cette fonction et parsait le montant tout en bas, APRÈS
        // avoir déchiffré le mnémonique et interrogé TronGrid deux fois. Un montant
        // mal écrit brûlait donc du quota API et renvoyait un message hors sujet
        // (« adresse jamais activée ») au lieu de « Montant invalide ».
        // Règle : ce qui est vérifiable localement et gratuitement passe en premier.
        val amountMicro = try {
            BigDecimal(amountUsdt.replace(",", ".")).multiply(BigDecimal("1000000")).toLong()
        } catch (_: Exception) { return Result.Error("Montant invalide") }
        if (amountMicro <= 0L) return Result.Error("Montant invalide")

        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        val passphrase = secureStorage.getPassphrase()

        // ── Garde-fous Tron (pièges réels, AVANT de brûler des frais) ──
        // 1) Adresse destinataire JAMAIS activée : un transfert TRC-20 vers un
        //    compte inexistant échoue en consommant quand même l'énergie. Il
        //    faut d'abord activer l'adresse avec un peu de TRX.
        // 2) Frais : sans énergie stakée, un transfert USDT brûle ~13-30 TRX.
        //    Avec un solde TRX trop bas, l'envoi échouerait en brûlant les
        //    frais → on bloque avec un message clair.
        try {
            val toAccount = tronApi.getAccount(toAddress)
            if (toAccount.data.isEmpty())
                return Result.Error(
                    "Cette adresse Tron est toute neuve (jamais activée). " +
                        "Envoie-lui d'abord un peu de TRX (ex. 2 TRX) pour l'activer, puis renvoie l'USDT."
                )
            val myAccount = tronApi.getAccount(tronTx.deriveAddress(mnemonic, passphrase))
            val myTrx = (myAccount.data.firstOrNull()?.balance ?: 0L) / 1_000_000.0
            if (myTrx < 15.0)
                return Result.Error(
                    "Un envoi USDT (TRC20) consomme environ 15 TRX de frais réseau — " +
                        "tu as ${"%.2f".format(java.util.Locale.US, myTrx)} TRX. Recharge d'abord ton TRX."
                )
        } catch (_: Exception) {
            // API TronGrid indisponible : on n'empêche pas l'envoi, le réseau tranchera.
        }

        return try {
            // Étape 1 — Préparer les paramètres hex + ABI-encode transfer(address,uint256)
            val ownerHex    = tronAddrToHex(tronTx.deriveAddress(mnemonic, passphrase))
            val contractHex = tronAddrToHex(USDT_TRC20_CONTRACT)
            // `amountMicro` est calculé en tête de fonction (BigDecimal, EXACT :
            // l'ancien passage par Double perdait des sous-unités — 20,02 donnait
            // 20019999 au lieu de 20020000).
            val parameter   = buildTrc20Param(toAddress, amountMicro)

            // Étape 2 — Déclencher le smart contract (génère la tx non signée)
            val triggerRes = tronApi.triggerSmartContract(
                TronTriggerSmartContractBody(
                    owner_address     = ownerHex,
                    contract_address  = contractHex,
                    function_selector = "transfer(address,uint256)",
                    parameter         = parameter,
                    // 100 TRX : un transfert TRC-20 sans énergie stakée brûle
                    // ~13-30 TRX. L'ancien plafond de 10 TRX faisait échouer le
                    // transfert (OUT_OF_ENERGY) EN BRÛLANT quand même les 10 TRX.
                    fee_limit         = 100_000_000
                )
            )
            val triggerResult = triggerRes.getAsJsonObject("result")
            if (triggerResult == null || triggerResult.get("result")?.asBoolean != true)
                return Result.Error("Création TRC20 échouée")

            // Étape 3 — Signer le SHA-256 du raw_data de la transaction
            val txObj      = triggerRes.getAsJsonObject("transaction")
                ?: return Result.Error("Transaction TRC20 vide")
            val rawDataHex = txObj.get("raw_data_hex")?.takeIf { !it.isJsonNull }?.asString
                ?: return Result.Error("raw_data_hex absent")
            val signature  = tronTx.signRawTransaction(mnemonic, passphrase, rawDataHex)

            // Étape 4 — Rediffuser la transaction COMPLÈTE (avec sa signature)
            txObj.add("signature", com.google.gson.JsonArray().apply { add(signature) })
            val broadcast = tronApi.broadcast(txObj)
            val txId = txObj.get("txID")?.takeIf { !it.isJsonNull }?.asString ?: ""
            if (broadcast.result == true) Result.Success(broadcast.txid ?: txId)
            else Result.Error(broadcast.message ?: "Broadcast USDT échoué")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur USDT TRC20")
        }
    }

    // ─── SOLANA ──────────────────────────────────────────────────────

    suspend fun sendSol(toAddress: String, lamports: Long): Result {
        if (!AddressValidator.isValidSolana(toAddress)) return Result.Error("Adresse SOL invalide")
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        val passphrase = secureStorage.getPassphrase()
        return try {
            val fromAddress = WalletManager.deriveAddresses(mnemonic, passphrase).sol
            val fromPubKey = Base58.decode(fromAddress)
            val toPubKey = Base58.decode(toAddress)

            // ── Règle Solana « rent-exempt » : un compte système ne peut PAS
            // rester avec un résidu entre 1 lamport et ~0.00089 SOL (890 880
            // lamports). Il doit être vidé à 0 EXACTEMENT, ou garder au moins ce
            // minimum. Sans ces garde-fous, le nœud rejette en simulation avec
            // le message opaque « Transaction simulation failed ».
            val rentMin = 890_880L
            val feeLamports = 5_000L
            @Suppress("UNCHECKED_CAST")
            val balanceLamports = (
                (solanaRpc.rpcCall(JsonRpcRequest("getBalance", mutableListOf(fromAddress as Any)))
                    .result as? Map<String, Any>)?.get("value") as? Number
            )?.toLong()
            if (balanceLamports != null) {
                val remainder = balanceLamports - lamports - feeLamports
                if (remainder < 0L)
                    return Result.Error("Solde insuffisant pour couvrir le montant et les frais réseau")
                if (remainder in 1L until rentMin) {
                    val maxAll = java.math.BigDecimal.valueOf(balanceLamports - feeLamports)
                        .movePointLeft(9).stripTrailingZeros().toPlainString()
                    return Result.Error(
                        "Solana n'autorise pas à laisser moins de 0.0009 SOL sur ton compte. " +
                            "Envoie tout ($maxAll SOL) ou réduis le montant pour garder au moins 0.0009 SOL."
                    )
                }
            }
            // Destinataire NEUF (compte inexistant) : le premier dépôt doit être
            // ≥ au minimum rent-exempt, sinon le réseau refuse la création.
            if (lamports < rentMin) {
                @Suppress("UNCHECKED_CAST")
                val toInfo = (solanaRpc.rpcCall(
                    JsonRpcRequest("getAccountInfo", mutableListOf(toAddress as Any))
                ).result as? Map<String, Any>)
                if (toInfo != null && toInfo["value"] == null)
                    return Result.Error(
                        "Cette adresse est un compte Solana tout neuf : le premier envoi doit être d'au moins 0.0009 SOL."
                    )
            }

            val bhRes = solanaRpc.rpcCall(
                JsonRpcRequest("getLatestBlockhash", mutableListOf(mapOf("commitment" to "finalized") as Any))
            )
            @Suppress("UNCHECKED_CAST")
            val bhValue = (bhRes.result as? Map<String, Any>)?.get("value") as? Map<String, Any>
            val blockhashB58 = bhValue?.get("blockhash") as? String
                ?: return Result.Error("Blockhash Solana introuvable")
            val recentBlockhash = Base58.decode(blockhashB58)

            val message = buildSolTransferMessage(fromPubKey, toPubKey, recentBlockhash, lamports)
            val sig = solTx.signTransaction(mnemonic, passphrase, message)

            val txBytes = ByteArray(1 + 64 + message.size)
            txBytes[0] = 1
            System.arraycopy(sig, 0, txBytes, 1, 64)
            System.arraycopy(message, 0, txBytes, 65, message.size)

            val txBase64 = Base64.encodeToString(txBytes, Base64.NO_WRAP)
            val sendRes = solanaRpc.rpcCall(
                JsonRpcRequest("sendTransaction", mutableListOf(txBase64 as Any, mapOf("encoding" to "base64") as Any))
            )
            if (sendRes.error != null) Result.Error(sendRes.error.message)
            else {
                val sig = sendRes.result as? String
                if (identifiantValide(sig, "SOL")) Result.Success(sig!!.trim())
                else envoiIndetermine()
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur transaction SOL")
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    /**
     * Frais réseau RÉELS estimés pour [chain], en unité native (ETH/BNB/BTC…).
     * Interroge le gas/feerate réel du réseau (pas une valeur fixe). null si
     * indisponible — l'envoi recalcule de toute façon ses propres frais en signant.
     */
    suspend fun estimateFeeNative(chain: String): Double? = try {
        // Token personnalisé : gas d'un transfert ERC-20/BEP-20 (~65 000 gas),
        // payé en natif (ETH ou BNB).
        if (chain.startsWith("ERC20:")) {
            val evm = chain.split(":").getOrNull(1)
            if (evm == "BNB") {
                fetchLegacyGasPrice(bnbRpc, 3_000_000_000L, BSC_MAX_GAS_GWEI).toDouble() * GAS_LIMIT_TOKEN / 1e18
            } else {
                val (_, maxFee) = fetchEip1559Fees(ethRpc); maxFee.toDouble() * GAS_LIMIT_TOKEN / 1e18
            }
        } else when (chain) {
            "ETH"      -> { val (_, maxFee) = fetchEip1559Fees(ethRpc); maxFee.toDouble() * GAS_LIMIT_NATIVE / 1e18 }
            "USDT-ETH" -> { val (_, maxFee) = fetchEip1559Fees(ethRpc); maxFee.toDouble() * GAS_LIMIT_TOKEN / 1e18 }
            "BNB"      -> fetchLegacyGasPrice(bnbRpc, 3_000_000_000L, BSC_MAX_GAS_GWEI).toDouble() * GAS_LIMIT_NATIVE / 1e18
            "USDT-BNB" -> fetchLegacyGasPrice(bnbRpc, 3_000_000_000L, BSC_MAX_GAS_GWEI).toDouble() * GAS_LIMIT_TOKEN / 1e18
            "BTC"      -> {
                val fees = bitcoinApi.getFeeEstimates()
                val satPerByte = fees["6"] ?: fees["3"] ?: fees["1"] ?: 10.0
                // Au-delà du plafond, l'envoi sera de toute façon refusé : mieux
                // vaut n'afficher AUCUNE estimation qu'un chiffre aberrant que
                // l'utilisateur pourrait valider sans le lire. (Pas de `return`
                // ici : la fonction a un corps-expression.)
                if (satPerByte > BTC_MAX_SAT_PER_VBYTE) null
                else satPerByte * 150.0 / 1e8   // ~150 vbytes pour une tx P2WPKH typique
            }
            "SOL"  -> 0.000005   // 5000 lamports (frais fixe Solana)
            "TRX"  -> 0.3        // bande passante standard
            "USDT" -> 27.0       // TRC20 : énergie (~27 TRX si non détenue)
            else   -> null
        }
    } catch (_: Exception) { null }

    /**
     * EIP-1559 fees: returns (maxPriorityFeePerGas, maxFeePerGas).
     * maxFeePerGas = 2 × baseFee + maxPriorityFeePerGas (canonical formula).
     */
    private suspend fun fetchEip1559Fees(rpc: EvmRpcApi): Pair<BigInteger, BigInteger> {
        val priorityRes = rpc.rpcCall(JsonRpcRequest("eth_maxPriorityFeePerGas", mutableListOf()))
        val rawPriority = try {
            BigInteger((priorityRes.result as? String ?: "0x3B9ACA00").removePrefix("0x"), 16)
        } catch (_: Exception) { BigInteger.valueOf(1_000_000_000L) }  // 1 gwei fallback
        // Le pourboire est payé INTÉGRALEMENT au mineur : c'est le champ que
        // détournerait un nœud hostile pour vider un portefeuille.
        val maxPriority = requireSaneGas(rawPriority, ETH_MAX_PRIORITY_GWEI, "pourboire mineur")

        val blockRes = rpc.rpcCall(JsonRpcRequest("eth_getBlockByNumber",
            mutableListOf("latest" as Any, false as Any)))
        @Suppress("UNCHECKED_CAST")
        val baseFeeHex = (blockRes.result as? Map<String, Any>)
            ?.get("baseFeePerGas") as? String ?: "0x0"
        val baseFee = BigInteger(baseFeeHex.removePrefix("0x").ifEmpty { "0" }, 16)
        // Repli : si le nœud ne renvoie pas baseFeePerGas (baseFee = 0), maxFee
        // tomberait à ~1 gwei (sous le prix du marché) → tx ETH rejetée « max fee
        // per gas less than block base fee ». On se rabat alors sur eth_gasPrice.
        val effectiveBase = if (baseFee.signum() > 0) baseFee
            else fetchLegacyGasPrice(rpc, 20_000_000_000L, ETH_MAX_FEE_GWEI)   // 20 gwei par défaut
        val maxFee = effectiveBase.multiply(BigInteger.TWO).add(maxPriority)
        // Second garde-fou : un baseFee mensonger gonflerait le plafond total,
        // et c'est ce plafond que le garde-fou de solde met en réserve.
        return Pair(maxPriority, requireSaneGas(maxFee, ETH_MAX_FEE_GWEI, "plafond de gas"))
    }

    /**
     * Prix du gas en type-0 (BNB Chain, et repli ETH). Payé intégralement au
     * mineur : plafonné à [maxGwei] selon la chaîne appelante.
     */
    private suspend fun fetchLegacyGasPrice(rpc: EvmRpcApi, default: Long, maxGwei: Long): BigInteger {
        val raw = try {
            BigInteger((rpc.rpcCall(JsonRpcRequest("eth_gasPrice", mutableListOf()))
                .result as? String ?: "0x0").removePrefix("0x"), 16)
        } catch (_: Exception) { BigInteger.valueOf(default) }
        return requireSaneGas(raw, maxGwei, "prix du gas")
    }

    /**
     * Traduit l'échec d'un `createtransaction` TronGrid (qui renvoie un champ
     * "Error" au lieu de `raw_data_hex`) en message clair plutôt qu'un opaque
     * « Création transaction TRX échouée ».
     */
    private fun tronCreateError(resp: com.google.gson.JsonObject): String {
        val raw = resp.get("Error")?.takeIf { !it.isJsonNull }?.asString
            ?: resp.toString().take(180)
        val low = raw.lowercase()
        return when {
            "balance is not sufficient" in low || "insufficient" in low ->
                "Solde TRX insuffisant : gardez au moins ~1,1 TRX pour les frais réseau (et l'activation du destinataire)."
            "validate" in low && "contract" in low ->
                "Transaction TRX refusée par le réseau : $raw"
            else -> "Création TRX échouée : $raw"
        }
    }

    /** Convertit une adresse Tron Base58Check en hex sans 0x (ex: "41XXXX...") */
    private fun tronAddrToHex(address: String): String =
        Base58.decode(address).copyOfRange(0, 21).joinToString("") { "%02x".format(it) }

    /** ABI encode pour transfer(address,uint256) dans TronGrid */
    private fun buildTrc20Param(toAddress: String, amountMicro: Long): String {
        val decoded = Base58.decode(toAddress)
        val addrHex = decoded.copyOfRange(1, 21).joinToString("") { "%02x".format(it) }
        return addrHex.padStart(64, '0') + amountMicro.toString(16).padStart(64, '0')
    }

    /**
     * Sérialise un message Solana « legacy » pour un transfert natif SOL.
     *
     * IMPORTANT (m-04) : sérialiseur écrit à la main, volontairement limité
     * au seul cas SystemProgram::Transfer. Les compteurs sont encodés sur un
     * octet, ce qui est valide tant que toutes les valeurs restent < 128
     * (compact-u16 / ShortVec) — ce qui est garanti ici (3 comptes, 1
     * instruction, 12 octets de data). Les gardes ci-dessous échouent vite
     * plutôt que de produire un message mal formé mais signé.
     *
     * Disposition exacte des octets :
     *   [header]      numRequiredSignatures=1, numReadonlySigned=0, numReadonlyUnsigned=1
     *   [comptes]     count=3, then from(signer,writable) | to(writable) | systemProgram(readonly)
     *   [blockhash]   32 octets
     *   [instructions] count=1
     *     programIdIndex=2 (systemProgram)
     *     accountCount=2, indices=[0(from), 1(to)]
     *     dataLen=12
     *     data = instruction(=2, Transfer, u32 LE) + lamports(u64 LE)
     */
    private fun buildSolTransferMessage(
        from: ByteArray, to: ByteArray, recentBlockhash: ByteArray, lamports: Long
    ): ByteArray {
        require(from.size == 32) { "Clé publique source Solana invalide (${from.size} octets)" }
        require(to.size == 32) { "Clé publique destination Solana invalide (${to.size} octets)" }
        require(recentBlockhash.size == 32) { "Blockhash Solana invalide (${recentBlockhash.size} octets)" }
        require(lamports >= 0) { "Montant lamports négatif" }

        val out = ByteArrayOutputStream()
        val systemProgram = ByteArray(32)
        out.write(1); out.write(0); out.write(1)
        out.write(3)
        out.write(from); out.write(to); out.write(systemProgram)
        out.write(recentBlockhash)
        out.write(1)
        out.write(2); out.write(2); out.write(0); out.write(1)
        out.write(12)
        out.write(2); out.write(0); out.write(0); out.write(0)
        for (i in 0 until 8) out.write((lamports ushr (i * 8)).toInt() and 0xFF)
        return out.toByteArray()
    }
}
