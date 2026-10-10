package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.security.SecureStorage
import com.vaultex.domain.fiat.ChangeService
import com.vaultex.domain.fiat.DemandeChange
import com.vaultex.domain.fiat.EtatVerification
import com.vaultex.domain.fiat.HistoriqueChange
import com.vaultex.domain.fiat.OrdreChange
import com.vaultex.domain.fiat.ParametresChange
import com.vaultex.domain.fiat.PrixFcfa
import com.vaultex.domain.fiat.ResultatOrdre
import com.vaultex.domain.fiat.TauxFcfa
import com.vaultex.domain.fiat.Verification
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
    val erreur: String? = null,
    /** Les demandes deja transmises, de la plus recente a la plus ancienne. */
    val historique: List<DemandeChange> = emptyList(),
    /*
    ═══════════════════════════════════════════════════════════════════════
    LA VERIFICATION ON-CHAIN, POUR UNE VENTE
    ═══════════════════════════════════════════════════════════════════════

    Ce que le RELAIS a lu sur la chaine, pas ce que le telephone a calcule.
    L'application affiche ce verdict ; elle ne le produit pas et ne peut pas
    le changer — c'est ce qui en fait une preuve pour le changeur.
    ═══════════════════════════════════════════════════════════════════════
    */
    val verification: Verification? = null,
    /** Un sondage est en vol. */
    val rechercheEnCours: Boolean = false,
    /** Combien de fois on a demande : affiche pour que l'attente soit lisible. */
    val essais: Int = 0,
    /**
     * Hash de la transaction, si l'utilisateur l'a sous la main.
     *
     * FACULTATIF, et l'ecran le dit. Sur les chaines ou les versements
     * s'enumerent, le relais trouve sans. On ne demande a personne de
     * recopier soixante-quatre caracteres hexadecimaux.
     */
    val txidSaisi: String = "",
    /**
     * L'utilisateur a choisi de transmettre sans attendre la confirmation.
     *
     * CE CHOIX DOIT EXISTER. Ses fonds sont deja partis — c'est
     * irreversible. Si son retrait traine chez une plateforme d'echange, ou
     * si le relais n'arrive pas a lire la chaine, l'empecher de deposer sa
     * demande le laisserait avec de la crypto envoyee et rien chez le
     * changeur. On freine, on n'interdit pas.
     */
    val forcer: Boolean = false
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
    /**
     * Vrai si la demande peut partir depuis l'etape du paiement.
     *
     * ═══════════════════════════════════════════════════════════════════
     * UNE VENTE N'ATTEND PLUS UNE REFERENCE RECOPIEE
     * ═══════════════════════════════════════════════════════════════════
     *
     * Elle attend la CHAINE. La reference Mobile Money n'a aucun sens
     * ici : il n'y a pas eu de paiement Mobile Money, c'est le changeur
     * qui va en faire un.
     *
     * Un achat, lui, garde son exigence : rien d'automatique ne peut lire
     * un relevé Orange Money, donc la reference que l'utilisateur recopie
     * reste le seul fil entre son paiement et sa demande.
     * ═══════════════════════════════════════════════════════════════════
     */
    fun peutTransmettre(): Boolean = when {
        envoiEnCours -> false
        reference.isBlank() -> false
        sens == SensChange.ACHAT -> referencePaiement.isNotBlank()
        /*
        ═══════════════════════════════════════════════════════════════
        SUR UNE VENTE, LE NUMERO EST CE QUI PERMET D'ETRE PAYE
        ═══════════════════════════════════════════════════════════════

        Il n'etait pas exige. Une vente pouvait donc partir sans numero,
        et le message disait au changeur « ENVOYER 5 000 FCFA au numero
        du client » — sans numero. Il n'avait aucun moyen de payer, et
        l'utilisateur avait deja envoye sa crypto.

        Ce n'est pas la verification qui a introduit ce defaut : il etait
        la. Il s'est vu en relisant la ligne « A FAIRE » du message, qui
        est justement ce que la verification a rendu lisible.
        ═══════════════════════════════════════════════════════════════
        */
        !telephoneValide -> false
        verification?.estVert == true -> true
        else -> forcer
    }

    /**
     * Huit chiffres, la longueur d'un numero au Burkina Faso.
     *
     * On compte les CHIFFRES et non les caracteres : « 70 12 34 56 »
     * s'ecrit naturellement avec des espaces, et refuser cette forme
     * ferait buter quelqu'un sur un champ qu'il a correctement rempli.
     */
    val telephoneValide: Boolean
        get() = telephone.count { it.isDigit() } == 8

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
    private val secureStorage: SecureStorage,
    private val historique: HistoriqueChange
) : ViewModel() {

    private val _state = MutableStateFlow(ChangeState())
    val state: StateFlow<ChangeState> = _state.asStateFlow()

    private val gson = com.google.gson.Gson()

    init {
        /*
        L'historique est POSE AVANT tout appel reseau, et sans attendre.
        C'est une lecture de preferences : elle coute moins qu'une frame.
        Quelqu'un qui ouvre l'ecran pour retrouver une reference — parce
        qu'il attend sa crypto depuis vingt minutes — la voit tout de suite,
        meme si le relais ne repond pas.
        */
        _state.update { it.copy(historique = historique.lire()) }
        charger()
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    CE QU'ON RETROUVE AU RETOUR D'ORANGE MONEY
    ═══════════════════════════════════════════════════════════════════════

    Entre la confirmation d'un prix et la declaration du paiement,
    l'utilisateur QUITTE l'application. Sur un telephone peu puissant,
    Android la ferme pendant ce temps — c'est le fonctionnement normal du
    systeme, pas un incident.

    Sans cette reprise, il reviendrait sur un ecran de calcul vierge apres
    avoir envoye de l'argent en portant une reference que l'application ne
    connait plus. Il n'aurait plus aucun moyen de rattacher son paiement a
    sa demande, et nous non plus.

    On le ramene donc exactement ou il etait : a l'etape du paiement, avec
    SA reference et LES MONTANTS QU'IL A LUS. Rien n'est recalcule — le
    prix a pu bouger entre-temps, et c'est celui qu'on lui a montre qui
    engage.
    ═══════════════════════════════════════════════════════════════════════
    */
    private data class ChangeEnCours(
        val reference: String = "",
        val sens: String = "",
        val monnaie: String = "",
        val saisie: String = "",
        val baseFcfa: Double = 0.0,
        val achatFcfa: Double = 0.0,
        val venteFcfa: Double = 0.0,
        val fcfaParDollar: Double = 0.0,
        val horodatage: Long = 0L
    )

    private fun reprendre() {
        val json = secureStorage.getChangeEnCours() ?: return
        val trace = try {
            gson.fromJson(json, ChangeEnCours::class.java)
        } catch (_: Exception) { null }

        /*
        Une trace illisible, sans reference, ou VIEILLE DE PLUS D'UN JOUR
        est effacee. Les deux premieres ne menent nulle part ; la troisieme
        est pire : ramener quelqu'un sur un prix d'hier l'enverrait payer un
        montant qui n'a plus cours, et le changeur recevrait une somme qui
        ne correspond a rien.
        */
        if (trace == null || trace.reference.isBlank() ||
            System.currentTimeMillis() - trace.horodatage > VALIDITE_TRACE
        ) {
            secureStorage.saveChangeEnCours(null)
            return
        }
        _state.update {
            it.copy(
                etape = EtapeChange.PAIEMENT,
                reference = trace.reference,
                sens = if (trace.sens == "vente") SensChange.VENTE else SensChange.ACHAT,
                monnaie = trace.monnaie.ifBlank { it.monnaie },
                saisie = trace.saisie,
                prix = PrixFcfa(trace.baseFcfa, trace.achatFcfa, trace.venteFcfa),
                fcfaParDollar = trace.fcfaParDollar
            )
        }
        /*
        LA RECHERCHE REPREND, et c'est le cas qui en a le plus besoin.

        Si l'application a ete fermee par Android pendant que l'utilisateur
        envoyait sa crypto depuis une autre application, il revient ici
        sans rien. C'est exactement le moment ou le versement a eu le temps
        d'arriver sur la chaine : on redemande.
        */
        if (_state.value.sens == SensChange.VENTE) demarrerVerification()
    }

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
            // APRES recalculer : la reprise ecrase le prix frais par celui
            // qui a ete montre a l'utilisateur, et c'est l'ordre voulu.
            reprendre()
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

    /**
     * Change la monnaie traitee.
     *
     * ═══════════════════════════════════════════════════════════════════
     * LA SAISIE NE S'EFFACE QUE SI SON UNITE CHANGE
     * ═══════════════════════════════════════════════════════════════════
     *
     * A LA VENTE, elle doit s'effacer : on y tape des unites de crypto, et
     * huit USDT ne sont pas huit bitcoins. Garder le chiffre reviendrait a
     * proposer une vente de huit bitcoins a quelqu'un qui en voulait huit
     * dollars — soit six cents mille fois trop.
     *
     * A L'ACHAT, non. On y tape des FRANCS, et cinq mille francs restent
     * cinq mille francs qu'on achete de l'USDT ou du bitcoin. Les effacer
     * obligeait a tout retaper pour comparer deux monnaies, ce qui est
     * exactement le geste que le selecteur existe pour permettre.
     *
     * Ca ne se voyait pas tant que la monnaie vivait DANS le champ de
     * saisie. Depuis que « tu donnes » et « tu recois » sont deux cartes,
     * le selecteur de l'achat est sur l'autre carte que le montant — et
     * voir disparaitre un chiffre en touchant une carte voisine n'a plus
     * aucun sens.
     * ═══════════════════════════════════════════════════════════════════
     */
    fun onMonnaie(monnaie: String) {
        _state.update {
            it.copy(
                monnaie = monnaie,
                saisie = if (it.sens == SensChange.VENTE) "" else it.saisie,
                erreur = null
            )
        }
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
        val prix = s.prix ?: return
        val reference = OrdreChange.nouvelleReference()
        // ECRITE AVANT, jamais apres : le moment dangereux est precisement
        // celui ou l'utilisateur quitte l'application pour aller payer.
        secureStorage.saveChangeEnCours(
            gson.toJson(
                ChangeEnCours(
                    reference = reference,
                    sens = if (s.sens == SensChange.ACHAT) "achat" else "vente",
                    monnaie = s.monnaie,
                    saisie = s.saisie,
                    baseFcfa = prix.baseFcfa,
                    achatFcfa = prix.achatFcfa,
                    venteFcfa = prix.venteFcfa,
                    fcfaParDollar = s.fcfaParDollar ?: 0.0,
                    horodatage = System.currentTimeMillis()
                )
            )
        )
        _state.update {
            it.copy(
                etape = EtapeChange.PAIEMENT,
                reference = reference,
                // Une nouvelle operation ne garde rien de la precedente :
                // ni son verdict, ni son hash, ni la permission de passer
                // outre. Reconduire « forcer » ferait sauter le garde-fou
                // sans que personne ne le redemande.
                verification = null,
                essais = 0,
                txidSaisi = "",
                forcer = false
            )
        }
        if (s.sens == SensChange.VENTE) demarrerVerification()
    }

    fun retourAuCalcul() {
        secureStorage.saveChangeEnCours(null)
        arreterVerification()
        _state.update {
            it.copy(
                etape = EtapeChange.CALCUL,
                reference = "",
                erreur = null,
                verification = null,
                rechercheEnCours = false,
                essais = 0,
                forcer = false
            )
        }
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    LA RECHERCHE DU VERSEMENT SUR LA CHAINE
    ═══════════════════════════════════════════════════════════════════════

    L'utilisateur vient d'envoyer de la crypto a une adresse. C'est
    irreversible, et il attend sans rien savoir. Cette boucle demande au
    relais, toutes les quinze secondes, s'il voit le versement.

    ─── QUINZE SECONDES, ET DIX MINUTES EN TOUT ─────────────────────────

    Un bloc Tron sort toutes les trois secondes, un bloc BNB Chain toutes
    les trois aussi, un bloc Bitcoin toutes les dix minutes. Quinze
    secondes ne manquent donc rien, et c'est assez lent pour ne pas
    maltraiter TronGrid depuis des centaines de telephones.

    Dix minutes d'insistance, puis on s'arrete. Au-dela, ce n'est plus un
    retard de reseau : soit le retrait est en file chez une plateforme
    d'echange — ca peut prendre une heure, et aucun sondage n'y changera
    rien — soit rien n'a ete envoye. Dans les deux cas, continuer a
    interroger ne ferait que vider la batterie en affichant la meme chose.

    ─── ON NE RECOMMENCE PAS CE QUI EST FINI ────────────────────────────

    La boucle s'arrete des que c'est CONFIRME : le verdict ne changera
    plus. Elle s'arrete aussi sur INDISPONIBLE et DEJA_SERVI, qui ne
    deviendront pas verts en insistant. Seul ABSENT merite d'etre
    reessaye — c'est le cas du versement qui n'est pas encore arrive.
    ═══════════════════════════════════════════════════════════════════════
    */
    private var rechercheJob: kotlinx.coroutines.Job? = null

    private fun arreterVerification() {
        rechercheJob?.cancel()
        rechercheJob = null
    }

    fun demarrerVerification() {
        arreterVerification()
        rechercheJob = viewModelScope.launch {
            try {
                boucleVerification()
            } finally {
                /*
                L'INDICATEUR S'ETEINT MEME SI LA BOUCLE EST ANNULEE.

                Sans ce `finally`, une annulation en plein sondage —
                l'utilisateur revient au calcul, ou transmet — laissait
                `rechercheEnCours` a vrai pour toujours. L'ecran gardait un
                indicateur qui tourne devant une boucle morte, et un ecran
                qui tourne sans fin se lit comme un ecran bloque.

                `update` n'est pas une fonction suspendue : elle marche dans
                un contexte deja annule, ce qui est exactement le cas ici.
                */
                _state.update { it.copy(rechercheEnCours = false) }
            }
        }
    }

    private suspend fun boucleVerification() {
        repeat(ESSAIS_MAX) { tour ->
            val s = _state.value
            /*
            ON RELIT L'ETAT A CHAQUE TOUR, et pas une seule fois au
            depart. Entre deux sondages, l'utilisateur a pu coller un
            hash, revenir au calcul, ou changer de monnaie. Travailler
            sur une copie figee interrogerait la chaine pour une
            operation qui n'existe plus.
            */
            if (s.etape != EtapeChange.PAIEMENT || s.sens != SensChange.VENTE) return
            val montant = s.montantCrypto ?: return

            _state.update { it.copy(rechercheEnCours = true, essais = tour + 1) }
            val v = withContext(Dispatchers.IO) {
                service.verifier(s.monnaie, montantTexte(montant), s.txidSaisi.trim())
            }
            _state.update { it.copy(verification = v, rechercheEnCours = false) }

            if (v.estVert || v.sansEspoir) return
            kotlinx.coroutines.delay(INTERVALLE_VERIFICATION)
        }
    }

    /** L'utilisateur colle un hash : la recherche redemarre aussitot. */
    fun onTxid(valeur: String) {
        _state.update { it.copy(txidSaisi = valeur, erreur = null) }
        /*
        UN HASH COMPLET RELANCE TOUT DE SUITE. Sans ca, quelqu'un qui colle
        son hash attendrait jusqu'a quinze secondes devant un ecran qui dit
        toujours « introuvable » — et conclurait que le collage n'a servi a
        rien.

        Soixante-quatre caracteres : la longueur d'un hash sur les trois
        chaines traitees, avec ou sans le « 0x » des chaines EVM. En dessous,
        c'est une saisie en cours, et relancer a chaque frappe enverrait
        soixante requetes.
        */
        val nu = valeur.trim().removePrefix("0x").removePrefix("0X")
        if (nu.length == 64 && nu.all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' }) {
            demarrerVerification()
        }
    }

    /**
     * Transmettre sans attendre la chaine.
     *
     * C'EST UN CHOIX, PAS UN CONTOURNEMENT. Ses fonds sont partis. Si son
     * retrait traine chez une plateforme d'echange, ou si le relais
     * n'arrive pas a lire la chaine, l'empecher de deposer sa demande le
     * laisserait avec de la crypto envoyee et rien chez le changeur.
     *
     * Le relais refera la verification de son cote, et le message portera
     * son resultat reel. Forcer ici ne rend donc rien « verifie » — ca
     * permet seulement de deposer.
     */
    fun forcerTransmission() {
        arreterVerification()
        _state.update { it.copy(forcer = true, rechercheEnCours = false) }
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
        // La demande part : plus rien a chercher, et un sondage qui
        // continuerait ecraserait le verdict rendu par le depot lui-meme.
        arreterVerification()
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
                referencePaiement = s.referencePaiement.trim(),
                /*
                LE HASH TROUVE PAR LE RELAIS PASSE DEVANT CELUI QU'ON A
                SAISI, et c'est l'ordre qui compte.

                Si la recherche a abouti, le relais a deja identifie LA
                transaction : la renvoyer lui evite de chercher a nouveau,
                et surtout elle est forcement juste. Le hash tape a la main
                ne sert que quand la recherche n'a rien trouve — un
                versement natif, ou un sondage trop tot.
                */
                txid = s.verification?.txid?.takeIf { t -> t.isNotBlank() }
                    ?: s.txidSaisi.trim()
            )
            val res = withContext(Dispatchers.IO) { service.transmettre(ordre) }
            when (res) {
                is ResultatOrdre.Transmis -> {
                    /*
                    ═══════════════════════════════════════════════════════
                    LES ÉCRITURES SONT DEHORS, PAS DANS `update`
                    ═══════════════════════════════════════════════════════

                    `MutableStateFlow.update` boucle sur un
                    compare-and-set : son bloc PEUT ÊTRE REJOUÉ si l'état
                    change entre la lecture et l'écriture. Tout effet de
                    bord qu'on y place s'exécute alors deux fois.

                    Ici elles sont idempotentes — effacer une trace déjà
                    effacée ne fait rien, et `ajouter` dédoublonne sur la
                    référence — donc rien ne casserait. Mais compter sur
                    cette chance à chaque ajout futur est exactement le
                    genre de pari qu'on perd une fois.
                    ═══════════════════════════════════════════════════════
                    */
                    // La demande est chez le changeur : la trace a fait son
                    // travail et ne doit plus ramener personne ici.
                    secureStorage.saveChangeEnCours(null)
                    /*
                    ON N'INSCRIT QUE CE QUI EST PARTI. Une demande refusée
                    par le relais n'existe pour personne : le changeur ne
                    l'a jamais vue, et la faire figurer dans l'historique
                    donnerait à l'utilisateur une référence à réclamer qui
                    ne correspond à rien.

                    On reprend les champs de l'ordre tel qu'il a été
                    transmis, et non l'état de l'écran : ce sont ces
                    chaînes-là, mises en forme, que le changeur a lues.
                    */
                    val demande = DemandeChange(
                        reference = res.reference,
                        sens = ordre.sens,
                        monnaie = ordre.monnaie,
                        montantFcfa = ordre.montantFcfa,
                        montantCrypto = ordre.montantCrypto,
                        taux = ordre.taux,
                        horodatage = System.currentTimeMillis()
                    )
                    /*
                    ON RELIT LE DISQUE plutôt que de recalculer en mémoire.

                    `ajouter` écrit, puis l'écran affiche. S'il affichait
                    une liste construite à côté, l'utilisateur pourrait voir
                    une demande qui n'a pas été enregistrée — le cas d'un
                    stockage plein, que `ajouter` avale volontairement pour
                    ne pas faire échouer une opération réussie. Ce qui est
                    montré est donc ce qui a été gardé.
                    */
                    val liste = withContext(Dispatchers.IO) {
                        historique.ajouter(demande)
                        historique.lire()
                    }
                    _state.update {
                        it.copy(
                            envoiEnCours = false,
                            etape = EtapeChange.TRANSMIS,
                            historique = liste,
                            /*
                            LE VERDICT DU DEPOT REMPLACE CELUI DU SONDAGE.

                            Ce sont deux verifications distinctes, faites a
                            deux instants, et c'est la SECONDE qui est
                            partie dans le message du changeur. Garder
                            l'ancienne pourrait annoncer « verifie » a
                            l'utilisateur alors que le changeur a recu un
                            message marque « deja servi » — par exemple si
                            quelqu'un a depose le meme transfert entre les
                            deux.

                            On n'ecrase que si le depot a dit quelque
                            chose : un vieux relais qui ne renvoie pas ce
                            champ laisserait sinon l'ecran sans verdict.
                            */
                            verification = if (res.verification == EtatVerification.INCONNUE) {
                                it.verification
                            } else {
                                Verification(etat = res.verification, txid = res.txid)
                            }
                        )
                    }
                }
                is ResultatOrdre.Echec -> _state.update {
                    it.copy(envoiEnCours = false, erreur = res.raison)
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
        secureStorage.saveChangeEnCours(null)
        arreterVerification()
        _state.update {
            ChangeState(
                parametres = it.parametres,
                chargement = false,
                monnaie = it.monnaie,
                // L'historique n'appartient pas a l'operation qu'on vient de
                // finir : c'est justement maintenant qu'il sert.
                historique = it.historique
            )
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

    private companion object {
        /**
         * Un jour. Au-dela, une trace ramenerait quelqu'un sur un prix
         * d'hier : il paierait un montant qui n'a plus cours, et le
         * changeur recevrait une somme ne correspondant a rien.
         */
        const val VALIDITE_TRACE = 24 * 60 * 60 * 1000L

        /**
         * Quinze secondes entre deux sondages.
         *
         * Un bloc sort toutes les trois secondes sur Tron comme sur BNB
         * Chain : quinze secondes ne manquent rien. Et c'est assez lent
         * pour que des centaines de telephones n'epuisent pas le quota
         * public de TronGrid.
         */
        const val INTERVALLE_VERIFICATION = 15_000L

        /**
         * Quarante essais, soit dix minutes.
         *
         * Au-dela, ce n'est plus un retard de reseau : soit le retrait est
         * en file chez une plateforme d'echange — ca prend parfois une
         * heure, et aucun sondage n'y changera rien — soit rien n'a ete
         * envoye. Continuer ne ferait que vider la batterie en affichant la
         * meme chose.
         */
        const val ESSAIS_MAX = 40
    }

    /**
     * Le montant pour la requete : des chiffres et un point, rien d'autre.
     *
     * PAS LE FORMAT FRANCAIS, et c'est volontaire. Les montants du message
     * Telegram sont mis en forme pour un humain — « 8,33 » — mais celui-ci
     * part dans une URL et sera relu par une machine. Une virgule et une
     * espace insecable dans un parametre de requete sont deux occasions de
     * se tromper pour rien.
     */
    private fun montantTexte(v: Double): String =
        java.math.BigDecimal(v).setScale(8, java.math.RoundingMode.DOWN)
            .stripTrailingZeros().toPlainString()

    private data class SnapLiteChange(val tokens: List<TokenLiteChange>?)
    private data class TokenLiteChange(
        val symbol: String = "",
        val priceUsd: Double = 0.0,
        val priceXof: Double = 0.0
    )
}
