package com.vaultex.domain.swap

import com.vaultex.data.remote.api.EvmRpcApi
import com.vaultex.data.remote.dto.JsonRpcRequest
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Named

/*
═══════════════════════════════════════════════════════════════════════════
ÉCHANGE SUR PLACE — ÉTAPES 2b ET 3 : CE QUI SIGNE
═══════════════════════════════════════════════════════════════════════════

C'est le code le plus dangereux du dépôt après la signature Bitcoin, et pour
une raison précise : un dépôt mal parti chez un courtier se rembourse, un
appel de contrat raté ne se rembourse pas. Le gaz est brûlé, et un montant
minimum de sortie mal borné laisse partir la différence.

Tout ce qui suit existe pour que cela n'arrive pas.

─── LES QUATRE GARDE-FOUS ───────────────────────────────────────────────

1. UN PLAFOND DE MONTANT. Tant que cette fonction n'a pas tourné des dizaines
   de fois en production, une erreur doit coûter le prix d'un pain, pas un
   solde. Le plafond vit dans la configuration de compilation : on le lève
   quand on a des raisons de le lever, pas quand on est pressé.

2. UN MONTANT MINIMUM DE SORTIE, JAMAIS NUL. C'est 1inch qui l'inscrit dans
   la transaction, à partir du glissement qu'on lui donne. Sans borne,
   n'importe qui peut s'intercaler et se servir au passage.

3. LE CHIFFRE SIGNÉ EST CELUI QU'ON A MONTRÉ. Entre le devis affiché et la
   transaction construite, le prix a pu bouger. Au-delà d'un écart défini,
   on refuse et on redemande plutôt que de signer autre chose que ce que
   l'utilisateur a lu.

4. LE PRIX DU GAZ EST PLAFONNÉ. Il vient d'un service distant. Un nœud
   compromis ou une réponse aberrante ne doit pas pouvoir brûler le solde en
   frais de mineur — c'est la règle que requireSaneGas applique déjà aux
   envois, appliquée ici aussi.

─── L'AUTORISATION EST AU MONTANT EXACT ─────────────────────────────────

Jamais illimitée. L'illimitée est le défaut de l'industrie, et c'est une
erreur : elle laisse un contrat ponctionner le solde entier, pour toujours,
longtemps après l'échange. Tant qu'il se comporte bien, cela ne se voit pas.

─── DEUX TRANSACTIONS LA PREMIÈRE FOIS ──────────────────────────────────

Autoriser, puis échanger. La seconde attend que la première soit inscrite :
diffuser l'échange avant que l'autorisation ne soit prise le ferait échouer,
et brûler son gaz pour rien.
═══════════════════════════════════════════════════════════════════════════
*/

sealed class ResultatEchangeSurPlace {
    data class Reussi(val hash: String, val recu: Double) : ResultatEchangeSurPlace()
    data class Echec(val raison: String) : ResultatEchangeSurPlace()
}

class EchangeSurPlaceUseCase @Inject constructor(
    private val api: com.vaultex.data.remote.api.OneInchApi,
    private val autorisation: AutorisationSurPlace,
    private val evmTx: com.vaultex.core.tx.EvmTransactionService,
    private val secureStorage: com.vaultex.core.security.SecureStorage,
    @Named("bnb") private val rpc: EvmRpcApi
) {

    /**
     * Échange [montant] de [de] contre [vers], sur place.
     *
     * @param attendu montant de sortie affiché à l'utilisateur, pour vérifier
     *   qu'on ne signe pas autre chose que ce qu'il a lu.
     */
    suspend fun echanger(
        de: String, vers: String, montant: Double, attendu: Double
    ): ResultatEchangeSurPlace {
        if (com.vaultex.core.config.ApiKeys.ONEINCH.isBlank())
            return ResultatEchangeSurPlace.Echec("1inch non configure")

        /*
        ═══════════════════════════════════════════════════════════════════
        GARDE-FOU 1 : LE PLAFOND — ET IL COMPARAIT DES CHOUX À DES CAROTTES
        ═══════════════════════════════════════════════════════════════════

        La constante s'appelle ECHANGE_SUR_PLACE_MAX_USD et vaut 2. Elle
        était comparée à `montant`, qui est un NOMBRE DE JETONS.

        Pour l'USDT, les deux coïncident : deux USDT valent deux dollars, et
        le plafond faisait ce qu'on croyait. Pour le BNB, non — deux BNB
        valent plus de mille dollars. Le garde-fou censé limiter une
        première erreur au prix d'un pain était six cents fois trop large,
        exactement sur le chemin le plus récent et le moins éprouvé.

        LE MONTANT EN DOLLARS SE LIT SANS DEMANDER DE COURS À PERSONNE.
        Le registre ne contient que deux monnaies, BNB et USDT-BNB : toute
        paire valide a donc exactement un côté en dollars. Quand c'est le
        côté DÉPART, son montant est la valeur, exactement, et le contrôle
        se fait ici. Quand c'est le côté ARRIVÉE, la valeur n'est connue
        qu'après le devis — le contrôle est alors refait plus bas, AVANT
        toute diffusion.

        On ne se sert PAS de `attendu` pour cela. Il vient de l'écran, et un
        devis affiché trop bas laisserait passer un montant trop grand :
        la borne deviendrait fonction de ce qu'on affiche, ce qui n'est pas
        une borne.
        ═══════════════════════════════════════════════════════════════════
        */
        val plafond = com.vaultex.BuildConfig.ECHANGE_SUR_PLACE_MAX_USD
        if (ActifsBnbChain.estDollar(de) && montant > plafond)
            return ResultatEchangeSurPlace.Echec("plafond d'essai : $plafond $ maximum")

        val source = ActifsBnbChain.de(de)
            ?: return ResultatEchangeSurPlace.Echec("monnaie hors perimetre")
        val cible = ActifsBnbChain.de(vers)
            ?: return ResultatEchangeSurPlace.Echec("monnaie hors perimetre")

        val mnemonic = secureStorage.getMnemonic()
            ?: return ResultatEchangeSurPlace.Echec("portefeuille non charge")
        val passphrase = secureStorage.getPassphrase()
        val moi = runCatching {
            com.vaultex.core.crypto.WalletManager.deriveAddresses(mnemonic, passphrase).bnb
        }.getOrNull() ?: return ResultatEchangeSurPlace.Echec("adresse introuvable")

        val brut = java.math.BigDecimal.valueOf(montant)
            .multiply(java.math.BigDecimal.TEN.pow(source.decimales))
            .toBigInteger().toString()

        val cle = "Bearer " + com.vaultex.core.config.ApiKeys.ONEINCH

        // ── Étape 2b : autoriser, si nécessaire ───────────────────────
        val etat = autorisation.etat(de, moi, montant)
        if (etat?.insuffisante == true && etat.routeur != null) {
            val txA = try {
                api.transactionAutorisation(
                    chainId = ActifsBnbChain.CHAIN_ID,
                    tokenAddress = source.adresse,
                    amount = brut,            // le montant EXACT, jamais illimite
                    authorization = cle
                )
            } catch (e: Exception) {
                com.vaultex.core.monitoring.reportUnlessCancelled("autorisation 1inch", e)
                return ResultatEchangeSurPlace.Echec("autorisation refusee par le service")
            }
            val hashA = diffuser(txA.to, txA.data, txA.value, txA.gas, mnemonic, passphrase, moi)
                ?: return ResultatEchangeSurPlace.Echec("autorisation non diffusee")
            /*
            On ATTEND que l'autorisation soit inscrite. Diffuser l'echange
            avant qu'elle ne soit prise le ferait echouer et bruler son gaz
            pour rien — l'utilisateur paierait deux fois pour une operation
            qui n'a pas eu lieu.
            */
            if (!attendreRecu(hashA))
                return ResultatEchangeSurPlace.Echec("autorisation non confirmee, reessaie")
        }

        // ── Étape 3 : l'échange ───────────────────────────────────────
        val rep = try {
            api.echange(
                chainId = ActifsBnbChain.CHAIN_ID,
                src = source.adresse,
                dst = cible.adresse,
                amount = brut,
                from = moi,
                origin = moi,
                slippage = GLISSEMENT_POURCENT,   // garde-fou 2
                authorization = cle
            )
        } catch (e: Exception) {
            com.vaultex.core.monitoring.reportUnlessCancelled("echange 1inch", e)
            return ResultatEchangeSurPlace.Echec("echange refuse par le service")
        }

        val sortieBrute = rep.dstAmount ?: rep.toTokenAmount
            ?: return ResultatEchangeSurPlace.Echec("montant de sortie absent")
        val recu = java.math.BigDecimal(sortieBrute)
            .divide(java.math.BigDecimal.TEN.pow(cible.decimales)).toDouble()

        /*
        ── GARDE-FOU 1, SECONDE MOITIÉ : LE PLAFOND QUAND LE DOLLAR EST À
           L'ARRIVÉE ──────────────────────────────────────────────────────

        Quand la monnaie de départ n'est pas un dollar stable — un échange
        qui part du BNB — sa valeur n'était pas connue au début de cette
        fonction. Elle l'est maintenant : c'est `recu`, le montant d'USDT
        que 1inch annonce, et il vient du service, pas de l'écran.

        LE CONTRÔLE ARRIVE AVANT TOUTE DIFFUSION, et ce n'est pas un hasard.
        Une monnaie native n'a aucune autorisation à donner — voir
        ActifsBnbChain.estNatif — donc rien n'a encore été dépensé à ce
        point. Refuser ici ne coûte rien du tout.
        */
        if (!ActifsBnbChain.estDollar(de) && recu > plafond)
            return ResultatEchangeSurPlace.Echec(
                "plafond d'essai : $plafond $ maximum (cet echange en vaut ${"%.2f".format(recu)})"
            )

        // ── Garde-fou 3 : ce qu'on signe est ce qu'on a montré ────────
        if (attendu > 0.0 && recu < attendu * (1.0 - DERIVE_TOLEREE))
            return ResultatEchangeSurPlace.Echec("le prix a change, redemande un devis")

        val tx = rep.tx ?: return ResultatEchangeSurPlace.Echec("transaction absente")
        val hash = diffuser(tx.to, tx.data, tx.value, tx.gas, mnemonic, passphrase, moi)
            ?: return ResultatEchangeSurPlace.Echec("echange non diffuse")

        return ResultatEchangeSurPlace.Reussi(hash, recu)
    }

    /** Signe et diffuse une transaction fournie par le service. */
    private suspend fun diffuser(
        to: String?, data: String?, value: String?, gas: Long?,
        mnemonic: String, passphrase: String, moi: String
    ): String? {
        if (to.isNullOrBlank() || data.isNullOrBlank()) return null
        return try {
            val nonce = lireNonce(moi) ?: return null
            /*
            GARDE-FOU 4 : le prix du gaz vient d'un service distant.

            On l'interroge nous-memes et on le plafonne, exactement comme
            SendCryptoUseCase le fait pour les envois. Une valeur aberrante
            — noeud compromis, reponse corrompue — brulerait le solde en
            frais de mineur, operation strictement irreversible.
            */
            val prixGaz = lirePrixGaz() ?: return null
            if (prixGaz > PLAFOND_GAZ_WEI) return null

            val limite = BigInteger.valueOf(gas ?: GAZ_DEFAUT).let {
                // Marge : l'estimation du service peut etre juste trop courte.
                it.multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN)
            }
            val signee = evmTx.signContractCall(
                mnemonic = mnemonic,
                passphrase = passphrase,
                toAddress = to,
                data = data,
                valueWei = BigInteger(value?.ifBlank { "0" } ?: "0"),
                gasPrice = prixGaz,
                gasLimit = limite,
                nonce = nonce,
                chainId = ActifsBnbChain.CHAIN_ID
            )
            val res = rpc.rpcCall(
                JsonRpcRequest("eth_sendRawTransaction", mutableListOf(signee as Any))
            )
            if (res.error != null) null else res.result as? String
        } catch (e: Exception) {
            com.vaultex.core.monitoring.reportUnlessCancelled("diffusion sur place", e)
            null
        }
    }

    private suspend fun lireNonce(adresse: String): BigInteger? = try {
        val res = rpc.rpcCall(
            JsonRpcRequest("eth_getTransactionCount", mutableListOf(adresse as Any, "pending" as Any))
        )
        (res.result as? String)?.let { BigInteger(it.removePrefix("0x").ifEmpty { "0" }, 16) }
    } catch (_: Exception) { null }

    private suspend fun lirePrixGaz(): BigInteger? = try {
        val res = rpc.rpcCall(JsonRpcRequest("eth_gasPrice", mutableListOf()))
        (res.result as? String)?.let { BigInteger(it.removePrefix("0x").ifEmpty { "0" }, 16) }
    } catch (_: Exception) { null }

    /** Attend qu'une transaction soit inscrite. Faux si elle ne vient pas. */
    private suspend fun attendreRecu(hash: String): Boolean {
        repeat(ESSAIS_RECU) {
            kotlinx.coroutines.delay(3000)
            val res = try {
                rpc.rpcCall(JsonRpcRequest("eth_getTransactionReceipt", mutableListOf(hash as Any)))
            } catch (_: Exception) { null }
            @Suppress("UNCHECKED_CAST")
            val recu = res?.result as? Map<String, Any> ?: return@repeat
            // status 0x1 = reussie ; 0x0 = rejetee par la chaine.
            return (recu["status"] as? String) == "0x1"
        }
        return false
    }

    private companion object {
        /** 1 % : au-dela, on prefere refuser que subir. */
        const val GLISSEMENT_POURCENT = 1.0

        /** Ecart tolere entre le devis affiche et celui qu'on signe. */
        const val DERIVE_TOLEREE = 0.02

        /** Plafond de prix du gaz : 5 gwei, tres au-dessus de BNB Chain. */
        val PLAFOND_GAZ_WEI: BigInteger = BigInteger.valueOf(5_000_000_000L)

        /** Si le service ne dit rien : large, l'inutilise est rendu. */
        const val GAZ_DEFAUT = 300_000L

        /** 3 s x 20 = une minute d'attente pour l'autorisation. */
        const val ESSAIS_RECU = 20
    }
}
