package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.security.SecureStorage
import com.vaultex.domain.fiat.ChangeService
import com.vaultex.domain.fiat.OrdreChange
import com.vaultex.domain.fiat.ParametresChange
import com.vaultex.domain.fiat.PrixFcfa
import com.vaultex.domain.fiat.ResultatOrdre
import com.vaultex.domain.fiat.TauxFcfa
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SensChange { ACHAT, VENTE }

/** Les trois temps de l'opération, dans l'ordre. */
enum class EtapeChange { CALCUL, PAIEMENT, TRANSMIS }

data class ChangeState(
    val parametres: ParametresChange? = null,
    val chargement: Boolean = true,
    val sens: SensChange = SensChange.ACHAT,
    val monnaie: String = "USDT",
    /** Saisie libre : des francs pour un achat, de la crypto pour une vente. */
    val saisie: String = "",
    val prix: PrixFcfa? = null,
    val fcfaParDollar: Double? = null,
    val etape: EtapeChange = EtapeChange.CALCUL,
    val reference: String = "",
    /** Ce que l'utilisateur déclare après avoir payé. */
    val referencePaiement: String = "",
    val telephone: String = "",
    val envoiEnCours: Boolean = false,
    val erreur: String? = null
) {
    /** Montant en francs de l'opération, quel que soit le sens de la saisie. */
    val montantFcfa: Double?
        get() {
            val n = saisie.replace(',', '.').replace(" ", "").toDoubleOrNull() ?: return null
            if (n <= 0.0) return null
            val p = prix ?: return null
            return if (sens == SensChange.ACHAT) n else TauxFcfa.fcfaPourCrypto(n, p)
        }

    /** Montant en crypto, quel que soit le sens de la saisie. */
    val montantCrypto: Double?
        get() {
            val n = saisie.replace(',', '.').replace(" ", "").toDoubleOrNull() ?: return null
            if (n <= 0.0) return null
            val p = prix ?: return null
            return if (sens == SensChange.ACHAT) TauxFcfa.cryptoPourFcfa(n, p) else n
        }

    /**
     * Pourquoi le bouton est éteint, ou null s'il ne l'est pas.
     *
     * UNE RAISON PLUTÔT QU'UN BOUTON GRIS. Un bouton éteint sans explication
     * laisse chercher : est-ce le montant ? la monnaie ? le service ? Sur un
     * écran où l'on vient pour envoyer de l'argent, cette hésitation suffit
     * à faire fermer l'application.
     */
    fun blocage(): String? {
        val p = parametres ?: return "indisponible"
        if (!p.utilisable) return "ferme"
        if (sens == SensChange.VENTE && !p.rachetable(monnaie)) return "pas_de_rachat"
        if (prix == null) return "sans_cours"
        val fcfa = montantFcfa ?: return "montant"
        if (fcfa < p.minimumFcfa) return "minimum"
        if (fcfa > p.plafondFcfa) return "plafond"
        return null
    }
}

@HiltViewModel
class ChangeViewModel @Inject constructor(
    private val service: ChangeService,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _state = MutableStateFlow(ChangeState())
    val state: StateFlow<ChangeState> = _state.asStateFlow()

    private val gson = com.google.gson.Gson()

    init { charger() }

    fun charger() {
        viewModelScope.launch {
            _state.update { it.copy(chargement = true, erreur = null) }
            val p = withContext(Dispatchers.IO) { service.parametres() }
            _state.update {
                it.copy(
                    parametres = p,
                    chargement = false,
                    monnaie = p?.monnaies?.firstOrNull() ?: it.monnaie
                )
            }
            recalculer()
        }
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    LE PRIX VIENT DU PORTEFEUILLE, PAS D'UN APPEL DE PLUS
    ═══════════════════════════════════════════════════════════════════════

    L'instantané du portefeuille contient déjà, pour chaque monnaie, son
    cours en dollars ET en francs. Leur rapport donne le taux du jour ; le
    cours en francs donne la base. Tout est là, sur le disque, sans réseau.

    Un appel supplémentaire n'apporterait rien et coûterait une attente sur
    un écran où l'on tape un montant — donc un prix qui saute pendant la
    saisie. Ici il est posé dès l'ouverture et ne bouge plus.

    SI LE PORTEFEUILLE N'A PAS ENCORE DE COURS, ON N'AFFICHE PAS DE PRIX.
    C'est la règle de TauxFcfa : mieux vaut « indisponible » qu'un montant
    inventé sur lequel quelqu'un s'engagerait.
    ═══════════════════════════════════════════════════════════════════════
    */
    private fun recalculer() {
        val s = _state.value
        val p = s.parametres ?: return
        val cours = coursDe(s.monnaie) ?: run {
            _state.update { it.copy(prix = null, fcfaParDollar = null) }
            return
        }
        val taux = TauxFcfa.fcfaParDollar(cours.second, cours.first)
        val prix = taux?.let { TauxFcfa.prix(cours.second, p.margeFcfaParDollar, it) }
        _state.update { it.copy(prix = prix, fcfaParDollar = taux) }
    }

    /** (cours en dollars, cours en francs) depuis l'instantané du portefeuille. */
    private fun coursDe(symbole: String): Pair<Double, Double>? = try {
        val json = secureStorage.getPortfolioSnapshot()
        val snap = json?.let { gson.fromJson(it, SnapLiteChange::class.java) }
        snap?.tokens?.firstOrNull { it.symbol.equals(symbole, ignoreCase = true) }
            ?.takeIf { it.priceUsd > 0.0 && it.priceXof > 0.0 }
            ?.let { it.priceUsd to it.priceXof }
    } catch (_: Exception) { null }

    fun onSens(sens: SensChange) = _state.update {
        // La saisie change d'unité avec le sens : la garder tromperait.
        it.copy(sens = sens, saisie = "", erreur = null)
    }

    fun onMonnaie(monnaie: String) {
        _state.update { it.copy(monnaie = monnaie, saisie = "", erreur = null) }
        recalculer()
    }

    fun onSaisie(valeur: String) = _state.update { it.copy(saisie = valeur, erreur = null) }
    fun onReferencePaiement(v: String) = _state.update { it.copy(referencePaiement = v) }
    fun onTelephone(v: String) = _state.update { it.copy(telephone = v) }

    /**
     * Passe à l'étape du paiement, en FIGEANT le prix.
     *
     * ═══════════════════════════════════════════════════════════════════
     * CE QUI EST AFFICHÉ EST CE QUI ENGAGE
     * ═══════════════════════════════════════════════════════════════════
     *
     * La marge est réglable à distance — c'est tout l'intérêt. Elle peut
     * donc changer entre le moment où l'utilisateur lit un montant et celui
     * où il paie.
     *
     * À partir d'ici, plus rien ne recalcule. Le prix, le montant et la
     * référence sont ceux qui partiront sur Telegram, même si le réglage
     * bouge dans la seconde qui suit.
     *
     * C'est le même garde-fou que sur l'échange sur place : « le chiffre
     * signé est celui qu'on a montré ». Ici, le chiffre affiché est celui
     * qui engage.
     * ═══════════════════════════════════════════════════════════════════
     */
    fun confirmer() {
        val s = _state.value
        if (s.blocage() != null) return
        _state.update {
            it.copy(etape = EtapeChange.PAIEMENT, reference = OrdreChange.nouvelleReference())
        }
    }

    fun retourAuCalcul() = _state.update {
        it.copy(etape = EtapeChange.CALCUL, reference = "", erreur = null)
    }

    /**
     * Déclare le paiement et transmet la demande au changeur.
     *
     * LA DEMANDE NE PART QU'ICI, et pas à l'étape précédente. Si elle
     * partait dès la confirmation, le canal du changeur se remplirait de
     * demandes dont la plupart ne seront jamais payées — quelqu'un regarde
     * un prix, change d'avis, ferme l'écran. Chaque message doit vouloir
     * dire « quelqu'un affirme avoir payé, va vérifier ».
     */
    fun transmettre() {
        val s = _state.value
        if (s.envoiEnCours || s.reference.isBlank()) return
        val p = s.parametres ?: return
        val fcfa = s.montantFcfa ?: return
        val crypto = s.montantCrypto ?: return
        val prix = s.prix ?: return
        val taux = s.fcfaParDollar ?: return

        _state.update { it.copy(envoiEnCours = true, erreur = null) }
        viewModelScope.launch {
            val adresse = if (s.sens == SensChange.ACHAT) adresseDeReception(s.monnaie) else ""
            val pourcent = TauxFcfa.margeEnPourcent(p.margeFcfaParDollar, taux)
            val ordre = OrdreChange(
                reference = s.reference,
                sens = if (s.sens == SensChange.ACHAT) "achat" else "vente",
                monnaie = s.monnaie,
                montantFcfa = entier(fcfa),
                montantCrypto = decimal(crypto),
                taux = "${entier(if (s.sens == SensChange.ACHAT) prix.achatFcfa else prix.venteFcfa)} FCFA",
                marge = "${entier(p.margeFcfaParDollar)} FCFA/$ " +
                    (pourcent?.let { "(%.2f %%)".format(it) } ?: ""),
                adresse = adresse,
                telephone = s.telephone.trim(),
                referencePaiement = s.referencePaiement.trim()
            )
            val res = withContext(Dispatchers.IO) { service.transmettre(ordre) }
            _state.update {
                when (res) {
                    is ResultatOrdre.Transmis -> it.copy(
                        envoiEnCours = false, etape = EtapeChange.TRANSMIS
                    )
                    is ResultatOrdre.Echec -> it.copy(
                        envoiEnCours = false, erreur = res.raison
                    )
                }
            }
        }
    }

    /**
     * Repart d'un écran vierge, en gardant les réglages déjà lus.
     *
     * On ne les redemande pas : ils viennent d'être obtenus, et une attente
     * réseau juste après une opération réussie donnerait l'impression que
     * quelque chose s'est bloqué.
     */
    fun recommencer() {
        _state.update {
            ChangeState(parametres = it.parametres, chargement = false, monnaie = it.monnaie)
        }
        recalculer()
    }

    /** Adresse du portefeuille où le changeur enverra la crypto achetée. */
    private fun adresseDeReception(monnaie: String): String = try {
        val m = secureStorage.getMnemonic() ?: ""
        if (m.isBlank()) "" else {
            val a = com.vaultex.core.crypto.WalletManager
                .deriveAddresses(m, secureStorage.getPassphrase())
            when (monnaie.uppercase()) {
                "BTC" -> a.btc
                "ETH", "USDT-ETH" -> a.eth
                "BNB", "USDT-BNB" -> a.bnb
                "SOL" -> a.sol
                "TRX", "USDT" -> a.trx
                "PI" -> a.pi
                else -> ""
            }
        }
    } catch (_: Exception) { "" }

    private fun entier(v: Double): String =
        java.text.NumberFormat.getIntegerInstance(java.util.Locale.FRANCE).format(v)

    private fun decimal(v: Double): String =
        java.text.NumberFormat.getNumberInstance(java.util.Locale.FRANCE).apply {
            maximumFractionDigits = 8
            minimumFractionDigits = 0
        }.format(v)

    private data class SnapLiteChange(val tokens: List<TokenLiteChange>?)
    private data class TokenLiteChange(
        val symbol: String = "",
        val priceUsd: Double = 0.0,
        val priceXof: Double = 0.0
    )
}
