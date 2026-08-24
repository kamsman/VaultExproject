package com.vaultex.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.R
import com.vaultex.core.security.SecureStorage
import com.vaultex.core.session.NetworkMonitor
import com.vaultex.core.validation.AddressValidator
import com.vaultex.data.local.dao.PendingSendDao
import com.vaultex.data.local.entity.PendingSendEntity
import com.vaultex.domain.usecase.SendCryptoUseCase
import com.vaultex.service.PendingSendWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Token personnalisé (ERC-20/BEP-20) sélectionnable dans l'écran Envoyer. */
data class CustomTokenLite(
    val symbol: String,
    val contractAddress: String,
    val decimals: Int,
    val blockchain: String   // "ETH" ou "BNB"
)

data class SendState(
    val selectedChain: String = "USDT",
    val toAddress: String = "",
    val amount: String = "",
    val estimatedFee: String = "",
    val isAddressValid: Boolean = false,
    val isLoading: Boolean = false,
    val txHash: String? = null,
    val error: String? = null,
    val queued: Boolean = false,
    val dustWarning: String? = null,
    // Solde disponible de la chaîne sélectionnée (lu depuis le cache portefeuille).
    val availableBalance: String? = null,
    // Non-null quand l'utilisateur envoie un token personnalisé ajouté par contrat.
    val customToken: CustomTokenLite? = null,
    // Conversion fiat (devise d'affichage) pour l'écran Envoyer « pro ».
    val currency: String = "USD",
    val priceSelected: Double = 0.0,   // prix de la monnaie envoyée, dans `currency`
    val priceNative: Double = 0.0,     // prix de la monnaie des frais (gas), dans `currency`
    val feeNativeAmount: Double? = null, // montant numérique des frais (unité native)
    val serviceFeeAmount: Double = 0.0, // frais de service VaultEx (BTC), unité crypto
    // Adresse valide mais SOSIE d'une adresse connue (address poisoning probable).
    val poisonWarning: Boolean = false,
    // Adresse jamais utilisée et absente du carnet : c'est le seul cas où une
    // substitution (presse-papiers détourné) ne peut être contredite par
    // l'historique → vérification caractère par caractère demandée à l'écran.
    val newRecipient: Boolean = false
)

@HiltViewModel
class SendViewModel @Inject constructor(
    private val sendCryptoUseCase: SendCryptoUseCase,
    private val pendingSendDao: PendingSendDao,
    private val secureStorage: SecureStorage,
    private val tokenRepository: com.vaultex.data.repository.TokenRepository,
    private val currencyController: com.vaultex.core.session.CurrencyController,
    private val pendingTxStore: com.vaultex.core.session.PendingTxStore,
    private val pendingTxManager: com.vaultex.core.tx.PendingTxManager,
    private val toastController: com.vaultex.core.session.ToastController,
    private val hub: com.vaultex.core.session.NotificationHub,
    private val notifPrefs: com.vaultex.core.session.NotifPrefs,
    private val contactDao: com.vaultex.data.local.dao.ContactDao,
    private val transactionDao: com.vaultex.data.local.dao.TransactionDao,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    /** Notre propre adresse sur [chain] (BTC/ETH/BNB/SOL/TRX) — pour l'entrée
     *  locale « Récent » créée immédiatement après un envoi réussi. */
    private suspend fun myAddressFor(chain: String): String = try {
        val mnemonic = secureStorage.getMnemonic() ?: ""
        val a = com.vaultex.core.crypto.WalletManager.deriveAddresses(mnemonic, secureStorage.getPassphrase())
        when (chain) {
            "BTC" -> a.btc; "ETH" -> a.eth; "BNB" -> a.bnb; "SOL" -> a.sol; "TRX" -> a.trx
            else -> ""
        }
    } catch (_: Exception) { "" }

    // ─── Anti « address poisoning » ─────────────────────────────────────
    // Adresses de CONFIANCE (carnet + destinataires déjà utilisés). Une
    // adresse saisie qui RESSEMBLE à l'une d'elles (mêmes premiers/derniers
    // caractères) sans être identique = très probablement une adresse
    // empoisonnée copiée depuis l'historique → alerte rouge.
    @Volatile private var knownAddresses: Set<String> = emptySet()

    private fun isPoisonLookalike(addr: String): Boolean {
        val a = addr.trim()
        if (a.length < 12) return false
        return knownAddresses.any { k ->
            k.length >= 12 && !k.equals(a, ignoreCase = true) &&
                k.take(5).equals(a.take(5), ignoreCase = true) &&
                k.takeLast(4).equals(a.takeLast(4), ignoreCase = true)
        }
    }

    /** Aucun envoi passé ni contact enregistré vers cette adresse. */
    private fun isNewRecipient(addr: String): Boolean {
        val a = addr.trim()
        if (a.length < 12) return false
        return knownAddresses.none { it.equals(a, ignoreCase = true) }
    }

    private val _state = MutableStateFlow(SendState())
    val state: StateFlow<SendState> = _state.asStateFlow()

    /** Ressources dans la langue CHOISIE dans l'app (pas celle du système). */
    private fun locStr(id: Int, vararg args: Any): String =
        com.vaultex.core.session.LocaleManager.wrap(appContext).getString(id, *args)

    /** Transactions en attente de confirmation (pour l'écran « En attente X/Y »). */
    val pendingTxs: StateFlow<List<com.vaultex.core.session.PendingTxStore.PendingTx>> = pendingTxStore.items

    /** Tokens personnalisés à afficher en plus des 8 chaînes natives. */
    private val _customTokens = MutableStateFlow<List<CustomTokenLite>>(emptyList())
    val customTokens: StateFlow<List<CustomTokenLite>> = _customTokens.asStateFlow()

    private val gson = com.google.gson.Gson()

    init {
        val cur = currencyController.currency.value
        _state.update {
            it.copy(
                currency = cur,
                availableBalance = availableFor(it.selectedChain),
                priceSelected = priceFor(it.selectedChain, cur),
                priceNative = priceFor(nativeUnit(effectiveChain(it)), cur)
            )
        }
        fetchFee(_state.value.selectedChain)
        viewModelScope.launch {
            _customTokens.value = try {
                tokenRepository.getCustom().map {
                    CustomTokenLite(it.symbol, it.contractAddress, it.decimals, it.blockchain)
                }
            } catch (_: Exception) { emptyList() }
        }
        // Adresses de confiance pour la détection d'« address poisoning ».
        viewModelScope.launch {
            knownAddresses = try {
                // Carnet : chaque contact stocke ses adresses en JSON {chaîne: adresse}.
                val parser = com.google.gson.Gson()
                val contacts: List<String> = contactDao.observeAll().first().flatMap { c ->
                    try {
                        (parser.fromJson(c.addressesJson, Map::class.java) as? Map<*, *>)
                            ?.values?.mapNotNull { it as? String } ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                }
                val pastSends: List<String> = transactionDao.observeAll().first()
                    .filter { it.type == "sent" }.map { it.toAddress }
                // Les adresses DU PORTEFEUILLE comptent aussi : une attaque
                // courante consiste à fabriquer un sosie de VOTRE propre adresse
                // (vue dans l'historique) pour détourner un transfert interne.
                val own: List<String> = try {
                    val m = secureStorage.getMnemonic()
                    if (m == null) emptyList() else {
                        val d = com.vaultex.core.crypto.WalletManager
                            .deriveAddresses(m, secureStorage.getPassphrase())
                        listOf(d.btc, d.eth, d.bnb, d.sol, d.trx)
                    }
                } catch (_: Exception) { emptyList() }
                (contacts + pastSends + own).filter { it.isNotBlank() }.toSet()
            } catch (_: Exception) { emptySet() }
        }
    }

    /** Prix unitaire d'une monnaie (par symbole) dans la devise d'affichage,
     *  lu depuis l'instantané portefeuille (aucun appel réseau). */
    private fun priceFor(symbol: String, currency: String): Double {
        val json = secureStorage.getPortfolioSnapshot() ?: return 0.0
        return try {
            val snap = gson.fromJson(json, SnapshotLite::class.java) ?: return 0.0
            val t = snap.tokens?.firstOrNull { it.symbol == symbol } ?: return 0.0
            when (currency) { "EUR" -> t.priceEur; "XOF" -> t.priceXof; else -> t.priceUsd }
        } catch (_: Exception) { 0.0 }
    }

    /** Chaîne effective passée à l'envoi : encodée pour un token personnalisé. */
    private fun effectiveChain(s: SendState): String =
        s.customToken?.let { "ERC20:${it.blockchain}:${it.contractAddress}:${it.decimals}" }
            ?: s.selectedChain

    /**
     * Sélection d'une monnaie depuis la page Marché (« Envoyer »). Chaîne native
     * ou variante USDT → sélection directe. Token du registre d'échange
     * (ERC-20 / BEP-20) → AUTO-ajout au portefeuille via son contrat CONNU, pour
     * qu'il soit envoyable IMMÉDIATEMENT, sans que l'utilisateur ait à saisir
     * l'adresse du contrat à la main. Le tout AVANT setChain, donc pas de course
     * (la monnaie est déjà dans _customTokens quand on sélectionne la chaîne).
     */
    fun selectAsset(symbol: String) {
        viewModelScope.launch {
            if (symbol in NATIVE_CHAINS) { setChain(symbol); return@launch }
            // Token personnalisé DÉJÀ enregistré (ex. résolu depuis la fiche
            // Marché d'une monnaie non échangeable via son contrat ETH/BSC) :
            // pas besoin du registre swap, setChain() le reconnaît par symbole.
            if (_customTokens.value.any { it.symbol == symbol }) { setChain(symbol); return@launch }
            val asset = com.vaultex.ui.viewmodel.SwapViewModel.assetForSymbol(symbol)
                ?: com.vaultex.ui.viewmodel.SwapViewModel.SWAP_ASSETS.firstOrNull { it.key.equals(symbol, ignoreCase = true) }
                ?: return@launch   // symbole inconnu : on n'y touche pas
            com.vaultex.ui.viewmodel.SwapViewModel.tokenEntityFor(asset)?.let { entity ->
                if (_customTokens.value.none { it.symbol == entity.symbol }) {
                    try { tokenRepository.addToken(entity) } catch (_: Exception) { /* déjà présent */ }
                    _customTokens.value = _customTokens.value +
                        CustomTokenLite(entity.symbol, entity.contractAddress, entity.decimals, entity.blockchain)
                }
            }
            setChain(symbol)
        }
    }

    fun setChain(chain: String) {
        // Un token personnalisé est identifié par son symbole (chip). On le
        // reconnaît seulement s'il ne porte pas le nom d'une chaîne native.
        val custom = if (chain in NATIVE_CHAINS) null
            else _customTokens.value.firstOrNull { it.symbol == chain }
        val addr = _state.value.toAddress
        // Adresse EVM (0x…) pour un token personnalisé ; sinon validation native.
        val valid = when {
            addr.isEmpty() -> false
            custom != null -> AddressValidator.isValidEvm(addr)
            else -> AddressValidator.isValid(addr, chain)
        }
        val warning = dustWarning(chain, _state.value.amount)
        val cur = currencyController.currency.value
        val eff = custom?.let { "ERC20:${it.blockchain}:${it.contractAddress}:${it.decimals}" } ?: chain
        _state.update {
            it.copy(
                selectedChain = chain,
                customToken = custom,
                isAddressValid = valid,
                error = null,
                dustWarning = warning,
                availableBalance = availableFor(chain),
                estimatedFee = "",       // recalcul ci-dessous pour la nouvelle chaîne
                currency = cur,
                priceSelected = priceFor(chain, cur),
                priceNative = priceFor(nativeUnit(eff), cur),
                serviceFeeAmount = serviceFeeCrypto(chain, it.amount.replace(",", ".").toDoubleOrNull() ?: 0.0)
            )
        }
        fetchFee(eff)
    }

    /** Frais réseau réel de la chaîne (gas live) — recalculé à chaque changement. */
    private fun fetchFee(chain: String) {
        viewModelScope.launch {
            val feeNative = sendCryptoUseCase.estimateFeeNative(chain)
            val formatted = feeNative?.let { "≈ " + formatFeeAmount(it) + " " + nativeUnit(chain) } ?: ""
            _state.update { it.copy(estimatedFee = formatted, feeNativeAmount = feeNative) }
        }
    }

    private fun nativeUnit(chain: String): String = when {
        chain.startsWith("ERC20:") -> if (chain.split(":").getOrNull(1) == "BNB") "BNB" else "ETH"
        chain == "ETH" || chain == "USDT-ETH" -> "ETH"
        chain == "BNB" || chain == "USDT-BNB" -> "BNB"
        chain == "BTC" -> "BTC"
        chain == "SOL" -> "SOL"
        chain == "TRX" || chain == "USDT" -> "TRX"
        else -> chain
    }

    private fun formatFeeAmount(value: Double): String =
        java.math.BigDecimal.valueOf(value)
            .setScale(8, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

    fun setToAddress(address: String) {
        val s = _state.value
        // Token personnalisé → adresse EVM (0x…) ; sinon validation par chaîne.
        val valid = if (s.customToken != null) AddressValidator.isValidEvm(address)
            else AddressValidator.isValid(address, s.selectedChain)
        _state.update {
            it.copy(
                toAddress = address,
                isAddressValid = valid,
                poisonWarning = valid && isPoisonLookalike(address),
                newRecipient = valid && isNewRecipient(address)
            )
        }
    }

    fun setAmount(amount: String) {
        // Clavier français : normaliser la virgule décimale en point partout
        // (l'état sert directement aux couches réseau).
        val normalized = amount.replace(',', '.')
        val s = _state.value
        val warning = dustWarning(s.selectedChain, normalized)
        val svc = serviceFeeCrypto(s.selectedChain, normalized.toDoubleOrNull() ?: 0.0)
        _state.update { it.copy(amount = normalized, dustWarning = warning, error = null, serviceFeeAmount = svc) }
    }

    /**
     * Bouton MAX : remplit le montant avec le solde disponible de la chaîne.
     * Pour une monnaie NATIVE (BNB/ETH/BTC/SOL/TRX), on retranche une petite
     * réserve pour couvrir les frais de réseau (sinon l'envoi du solde entier
     * est refusé faute de gas). Pour un token (USDT*), gas payé en natif → on
     * peut envoyer tout le solde. Si aucun solde connu, message explicite.
     */
    fun onMaxClicked() {
        val chain = _state.value.selectedChain
        val balance = availableFor(chain)?.toBigDecimalOrNull()
        if (balance == null || balance.signum() <= 0) {
            _state.update { it.copy(error = locStr(R.string.send_no_balance)) }
            return
        }
        // Réserve de frais retranchée :
        //  • Token (USDT* ou personnalisé) → gas payé en natif → réserve 0.
        //  • Monnaie native → frais réseau RÉELS estimés × marge de sécurité.
        // (Avant : une constante figée — ex. 0.0003 BTC — bloquait Max quand le
        //  solde était plus petit que la réserve, alors que le vrai frais est minime.)
        val isToken = _state.value.customToken != null || chain.startsWith("USDT")
        val reserve = if (isToken) java.math.BigDecimal.ZERO
            else if (chain == "SOL") {
                // SOL : frais FIXE (5000 lamports/signature) → réserve EXACTE,
                // sans marge. La marge ×1.6 laissait ~0.000003 SOL de résidu, or
                // Solana refuse un compte laissé entre 1 lamport et le minimum
                // « rent-exempt » (~0.00089 SOL) → « simulation failed ». MAX
                // doit vider le compte à 0 pile.
                java.math.BigDecimal.valueOf(
                    _state.value.feeNativeAmount?.takeIf { it > 0.0 } ?: 0.000005
                )
            }
            else {
                val liveFee = _state.value.feeNativeAmount
                if (liveFee != null && liveFee > 0.0)
                    java.math.BigDecimal.valueOf(liveFee).multiply(java.math.BigDecimal.valueOf(1.6))
                else NATIVE_FEE_RESERVE[chain]?.let { java.math.BigDecimal.valueOf(it) }
                    ?: java.math.BigDecimal.ZERO
            }
        // Le frais de service VaultEx (BTC) est aussi prélevé → on le retranche
        // pour que MAX laisse de quoi le payer.
        val svc = java.math.BigDecimal.valueOf(serviceFeeCrypto(chain, balance.toDouble()))
        val spendable = balance.subtract(reserve).subtract(svc)
        if (spendable.signum() <= 0) {
            _state.update { it.copy(error = locStr(R.string.send_no_balance)) }
            return
        }
        setAmount(
            spendable.setScale(8, java.math.RoundingMode.DOWN)
                .stripTrailingZeros()
                .toPlainString()
        )
    }

    /**
     * Frais de service VaultEx (BTC uniquement pour l'instant — sortie ajoutée
     * dans la même tx, coût nul). 0.5% plafonné à 0.50 USD ; nul sous la poussière.
     */
    private fun serviceFeeCrypto(chain: String, amount: Double): Double {
        if (chain != "BTC" || amount <= 0.0) return 0.0
        val byPct = amount * (com.vaultex.BuildConfig.VAULTEX_SEND_FEE_PERCENT / 100.0)
        val priceUsd = priceFor(chain, "USD")
        val cap = if (priceUsd > 0.0) com.vaultex.BuildConfig.VAULTEX_SEND_FEE_CAP_USD / priceUsd else byPct
        val fee = minOf(byPct, cap)
        return if (fee >= 0.00000546) fee else 0.0   // < poussière BTC → pas de frais
    }

    /** Décimal lisible pour l'utilisateur : 0.0001 — jamais la notation
     *  scientifique « 1.0E-4 » produite par Double.toString(). */
    private fun plainAmount(v: Double): String =
        java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()

    private fun dustWarning(chain: String, amount: String): String? {
        val value = amount.replace(",", ".").toDoubleOrNull() ?: return null
        val minimum = MINIMUM_AMOUNTS[chain] ?: return null
        return if (value < minimum) "${plainAmount(minimum)} $chain" else null
    }

    /** Lit le solde de [chain] dans l'instantané portefeuille (aucun appel réseau). */
    private fun availableFor(chain: String): String? {
        val json = secureStorage.getPortfolioSnapshot() ?: return null
        return try {
            val snap = gson.fromJson(json, SnapshotLite::class.java) ?: return null
            val t = snap.tokens?.firstOrNull { it.symbol == chain } ?: return null
            // Solde EXACT (amountRaw) si disponible → le bouton Max ne dépasse
            // jamais le solde réel (sinon le montant arrondi faisait revert la tx).
            if (t.amountRaw > 0.0) {
                java.math.BigDecimal.valueOf(t.amountRaw).toPlainString()
            } else {
                val numeric = t.amountFormatted.substringBefore(" ").replace(" ", "").replace(",", ".")
                if (numeric.toDoubleOrNull() != null) numeric else null
            }
        } catch (_: Exception) { null }
    }

    /** Traduit un message d'erreur technique en message utilisateur clair. */
    private fun friendlyError(raw: String?): String {
        val m = (raw ?: "").lowercase()
        return when {
            m.contains("insufficient funds") || m.contains("overshot") || m.contains("tx cost") ->
                locStr(R.string.send_err_insufficient)
            m.contains("nonce") ->
                locStr(R.string.send_err_nonce)
            m.contains("underpriced") || m.contains("fee too low") || m.contains("gas price") ||
                m.contains("min relay") || m.contains("mempool min fee") ->
                locStr(R.string.send_err_fee)
            m.contains("dust") ->
                locStr(R.string.send_err_dust)
            m.contains("invalid address") || m.contains("bad address") || m.contains("checksum") ||
                m.contains("base58") || m.contains("bech32") || m.contains("decode") ->
                locStr(R.string.send_err_addr_rejected)
            m.contains("timeout") || m.contains("timed out") ||
                m.contains("unable to resolve host") || m.contains("failed to connect") ->
                locStr(R.string.send_err_network)
            // Cause inconnue : on AJOUTE le message brut du réseau/nœud pour
            // pouvoir diagnostiquer (ex. rejet de diffusion Bitcoin).
            else -> {
                val g = locStr(R.string.send_err_generic)
                if (!raw.isNullOrBlank()) "$g\n($raw)" else g
            }
        }
    }

    private data class SnapshotLite(val tokens: List<TokenLite>?)
    private data class TokenLite(
        val symbol: String = "",
        val amountFormatted: String = "",
        val amountRaw: Double = 0.0,
        val priceUsd: Double = 0.0,
        val priceEur: Double = 0.0,
        val priceXof: Double = 0.0
    )

    companion object {
        // Chaînes natives connues : un symbole hors de cette liste qui correspond
        // à un token enregistré est traité comme un token personnalisé.
        private val NATIVE_CHAINS = setOf(
            "BTC", "ETH", "BNB", "TRX", "SOL", "USDT", "USDT-ETH", "USDT-BNB"
        )

        // Réserve de frais retranchée par MAX sur les monnaies NATIVES, pour que
        // l'envoi « tout le solde » couvre le gas. Valeurs volontairement
        // prudentes ; les tokens (USDT*) ne sont pas listés (gas payé en natif).
        // Réserve de SECOURS (uniquement si le frais réseau live n'est pas encore
        // chargé). Volontairement PETITE — la vraie réserve = frais live × marge.
        private val NATIVE_FEE_RESERVE = mapOf(
            "BTC" to 0.00002,
            "ETH" to 0.0003,
            "BNB" to 0.00005,
            "SOL" to 0.00001,
            "TRX" to 1.1   // bande passante brûlée (~0.27) + activation éventuelle du destinataire (~1)
        )

        private val MINIMUM_AMOUNTS = mapOf(
            "BTC"      to 0.00000546,
            "ETH"      to 0.0001,
            "BNB"      to 0.0001,
            "SOL"      to 0.000001,
            "TRX"      to 0.000001,
            "USDT"     to 1.0,
            "USDT-ETH" to 1.0,
            "USDT-BNB" to 1.0
        )
    }

    /** Symbole court affiché (USDT pour USDT-ETH / USDT-BNB). */
    private fun displaySymbol(chain: String): String =
        if (chain.startsWith("USDT")) "USDT" else chain

    /**
     * Contrôles AVANT l'envoi → message d'erreur PRÉCIS (montant, adresse,
     * solde, frais/gas) au lieu d'un échec générique après diffusion.
     */
    private fun preflightError(s: SendState): String? {
        val amountNum = s.amount.replace(",", ".").toDoubleOrNull()
        val sym = displaySymbol(s.selectedChain)
        if (s.amount.isBlank()) return locStr(R.string.send_err_enter_amount)
        if (amountNum == null || amountNum <= 0.0) return locStr(R.string.send_err_invalid_amount)
        if (s.toAddress.isBlank()) return locStr(R.string.send_err_enter_address)
        if (!s.isAddressValid) return locStr(R.string.send_err_bad_address, s.selectedChain)

        val available = availableFor(s.selectedChain)?.replace(",", ".")?.toDoubleOrNull()
        val fee = s.feeNativeAmount ?: 0.0
        val isToken = s.customToken != null || s.selectedChain.startsWith("USDT")

        // Montant > solde
        if (available != null && amountNum > available + 1e-12)
            return locStr(R.string.send_err_insufficient_balance, formatFeeAmount(available) + " " + sym)
        // Monnaie native : garder de quoi payer les frais
        if (!isToken && available != null && amountNum + fee > available + 1e-12)
            return locStr(R.string.send_err_keep_fee, formatFeeAmount(fee) + " " + nativeUnit(s.selectedChain))
        // Token : il faut du natif pour le gas
        if (isToken && fee > 0.0) {
            val nativeSym = nativeUnit(effectiveChain(s))
            val nativeBal = availableFor(nativeSym)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            if (nativeBal < fee) return locStr(R.string.send_err_need_gas, nativeSym)
        }
        // Montant sous le minimum réseau
        val min = MINIMUM_AMOUNTS[s.selectedChain]
        if (min != null && amountNum < min)
            return locStr(R.string.send_err_below_min, "${plainAmount(min)} $sym")
        return null
    }

    fun send() {
        val s = _state.value
        if (s.isLoading) return
        preflightError(s)?.let { msg -> _state.update { it.copy(error = msg) }; return }

        /*
        ═══════════════════════════════════════════════════════════════════
        VERROU POSÉ AVANT TOUTE COROUTINE
        ═══════════════════════════════════════════════════════════════════

        `isLoading` n'était mis à vrai qu'à l'intérieur du `launch` du chemin
        EN LIGNE. Ça suffisait pour lui : viewModelScope démarre sur
        Main.immediate, donc le bloc s'exécute jusqu'à la première suspension
        avant de rendre la main, et un second appui trouvait bien le verrou
        posé.

        Le chemin HORS-LIGNE, lui, ne le posait jamais. Sa protection reposait
        entièrement sur `countSamePending`, une requête de base de données —
        donc une SUSPENSION. Deux appuis rapprochés s'y arrêtaient tous les
        deux avant que le premier n'ait inséré quoi que ce soit, et
        repartaient tous les deux avec un compte de zéro.

        Deux lignes en file, et le retour du réseau diffuse DEUX transactions
        réelles. Le montant part deux fois. C'est le seul défaut de cet audit
        qui coûte de l'argent plutôt que de la clarté.

        Le verrou est donc posé ici, hors de toute coroutine : les deux
        chemins le partagent, et un second appui est refusé avant même que le
        premier n'ait eu l'occasion de suspendre.
        ═══════════════════════════════════════════════════════════════════
         */
        _state.update { it.copy(isLoading = true, error = null) }

        // Hors-ligne : on met l'INTENTION en file. Elle sera signée (avec un
        // nonce/blockhash frais) et diffusée automatiquement, une seule fois,
        // au retour du réseau via PendingSendWorker.
        val effective = effectiveChain(s)
        if (!NetworkMonitor.isOnline(appContext)) {
            viewModelScope.launch {
                try {
                    /*
                     * ANTI-DOUBLON. « En attente » n'est pas un echec : la
                     * meme intention relancee ne doit produire QU'UNE
                     * transaction. Sans ce garde, chaque appui inserait une
                     * ligne de plus, et le retour du reseau diffusait autant
                     * d'envois REELS que d'appuis — de l'argent perdu.
                     *
                     * Une intention identique deja en file : on confirme
                     * simplement l'etat « en attente », sans rien ajouter.
                     */
                    val deja = pendingSendDao.countSamePending(effective, s.toAddress, s.amount)
                    if (deja > 0) {
                        _state.update { it.copy(queued = true, error = null, isLoading = false) }
                        return@launch
                    }
                    pendingSendDao.insert(
                        PendingSendEntity(
                            chain = effective,
                            toAddress = s.toAddress,
                            amount = s.amount,
                            status = PendingSendWorker.STATUS_PENDING,
                            txHash = null,
                            lastError = null,
                            attempts = 0,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    PendingSendWorker.enqueue(appContext)
                    _state.update { it.copy(queued = true, error = null, isLoading = false) }
                } catch (e: Exception) {
                    // Le verrou DOIT retomber, sinon l'ecran reste bloque et
                    // l'utilisateur ne peut plus rien envoyer sans redemarrer.
                    _state.update { it.copy(error = friendlyError(e.message), isLoading = false) }
                }
            }
            return
        }

        // Le verrou est deja pose plus haut, pour les deux chemins.
        viewModelScope.launch {
            val result = sendCryptoUseCase.sendByChain(effective, s.toAddress, s.amount, s.serviceFeeAmount)
            when (result) {
                is SendCryptoUseCase.Result.Success -> {
                    // Demande à l'accueil de rafraîchir vite le solde après l'envoi.
                    com.vaultex.core.session.BalanceRefreshSignal.signalTxSent()
                    // Suivi de confirmation (badge dashboard + écran « En attente X/Y »).
                    val nativeChain = nativeUnit(effective)
                    pendingTxManager.track(s.selectedChain, nativeChain, result.txHash)
                    // Entrée LOCALE immédiate dans « Récent » (Dashboard) : sans
                    // ceci, la cloche notifiait à l'instant mais « Récent » restait
                    // vide jusqu'à la prochaine synchro d'Historique (déclenchée
                    // seulement en ouvrant cet écran) — désalignement signalé en
                    // test réel. PendingTxManager mettra le statut à jour tout
                    // seul (« confirmé ») dès la confirmation on-chain.
                    try {
                        val myAddr = myAddressFor(nativeChain)
                        transactionDao.insert(
                            com.vaultex.data.local.entity.TransactionEntity(
                                hash = result.txHash,
                                type = "sent",
                                blockchain = nativeChain,
                                fromAddress = myAddr,
                                toAddress = s.toAddress,
                                amount = s.amount,
                                tokenSymbol = s.customToken?.symbol ?: displaySymbol(s.selectedChain),
                                fee = formatFeeAmount(s.feeNativeAmount ?: 0.0),
                                status = "pending",
                                timestamp = System.currentTimeMillis(),
                                confirmations = 0,
                                blockNumber = null
                            )
                        )
                    } catch (_: Exception) { }
                    _state.update { it.copy(isLoading = false, txHash = result.txHash) }
                    // Toast maison : logo de la crypto + confirmation de l'envoi.
                    val sym = s.selectedChain.substringBefore("-")
                    toastController.show("Envoi effectué : ${s.amount} $sym", sym)
                    // Événement admin (Telegram) : envoi ≥ 1 $ (🚨 si ≥ 20 $).
                    val usdValue = (s.amount.toDoubleOrNull() ?: 0.0) * priceFor(s.selectedChain, "USD")
                    com.vaultex.core.monitoring.AdminBot.reportSend(s.amount, sym, usdValue)
                    // Cloche ET barre système : un envoi est un mouvement de
                    // fonds, il doit s'afficher en haut de l'écran comme une
                    // réception. La clé porte le hash : chaque envoi est unique,
                    // deux envois identiques restent deux notifications.
                    if (notifPrefs.txAlerts.value) {
                        hub.post(
                            key = "sent:${result.txHash}",
                            title = locStr(R.string.notif_sent_title),
                            body = locStr(R.string.notif_sent_body, s.amount, sym),
                            symbol = sym
                        )
                    }
                }
                is SendCryptoUseCase.Result.Error   -> {
                    _state.update { it.copy(isLoading = false, error = friendlyError(result.message)) }
                    // Événement admin (Telegram) : échec d'envoi avec la raison
                    // TECHNIQUE (pas le message traduit) → pannes récurrentes visibles.
                    com.vaultex.core.monitoring.AdminBot.sendFailed(
                        s.selectedChain.substringBefore("-"), result.message)
                }
            }
        }
    }

    fun reset() = _state.update { SendState() }
}
