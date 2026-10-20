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
import com.vaultex.domain.fiat.SaisieMontant
import com.vaultex.domain.fiat.PrixFcfa
import com.vaultex.domain.fiat.ResultatOrdre
import com.vaultex.domain.fiat.ResultatParametres
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
    /*
    ═══════════════════════════════════════════════════════════════════════
    SANS RÉGLAGES, IL FAUT ENCORE DIRE POURQUOI
    ═══════════════════════════════════════════════════════════════════════

    `parametres == null` servait pour les deux causes, et l'écran affichait
    « Service indisponible pour le moment. Réessaie dans un instant. »

    Vu sur un vrai téléphone, sans données mobiles : l'écran accusait le
    service alors que c'était la connexion, et conseillait d'attendre —
    exactement le geste qui ne marchera jamais. On peut rester devant cet
    écran indéfiniment sans penser à activer ses données.

    Ce drapeau est la réponse à « est-ce nous ou est-ce toi », et c'est le
    relais qui tranche par le type de son échec, pas une devinette.
    ═══════════════════════════════════════════════════════════════════════
    */
    val horsLigne: Boolean = false,
    /**
     * La connexion securisee a ete refusee — ce n'est PAS « hors ligne ».
     *
     * `SSLException` herite d'`IOException` : tout ce qui touche au TLS
     * tombait dans `horsLigne`, et l'ecran disait « verifie tes donnees
     * mobiles » a quelqu'un dont les donnees marchaient. La cause la plus
     * frequente est la DATE du telephone, qu'il peut corriger seul.
     */
    val securise: Boolean = false,
    /**
     * Le nom de l'exception, affiche en petit sous le message.
     *
     * Sans lui, une capture d'ecran ne permet pas de distinguer
     * « pas de donnees » de « DNS muet » ou « certificat refuse » — et on
     * en est reduit a deviner. Il a deja fallu quatre echanges pour
     * trancher une question que cette ligne repond seule.
     */
    val causeReseau: String = "",
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
    /** Solde detenu de la monnaie choisie, pour la vente. */
    val solde: Double? = null,
    /**
     * Instant ou le prix a ete fige, en millisecondes.
     *
     * Sert au compte a rebours de l'etape de paiement. Zero tant qu'on
     * n'a rien confirme.
     */
    val confirmeA: Long = 0L,
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
    /*
    ═══════════════════════════════════════════════════════════════════════
    UNE SEULE LECTURE DU TEXTE SAISI
    ═══════════════════════════════════════════════════════════════════════

    C'était `saisie.replace(',', '.').replace(" ", "").toDoubleOrNull()`,
    écrit deux fois. Deux copies d'une même règle finissent par ne plus
    dire la même chose — et celle-ci laissait passer un cas :

        "2," → "2." → toDoubleOrNull() rend 2.0

    Java accepte un point final. On affichait donc le montant reçu pour
    DEUX à l'instant où la virgule se pose, avant le premier chiffre
    décimal. `SaisieMontant.valeur` refuse cet état de frappe, et c'est
    elle qui tranche désormais pour tout le monde.
    ═══════════════════════════════════════════════════════════════════════
    */
    private val saisi: Double? get() = SaisieMontant.valeur(saisie)

    /** Montant en francs de l'opération, quel que soit le sens de la saisie. */
    val montantFcfa: Double?
        get() {
            val n = saisi ?: return null
            val p = prix ?: return null
            return if (sens == SensChange.ACHAT) n else TauxFcfa.fcfaPourCrypto(n, p)
        }

    /** Montant en crypto, quel que soit le sens de la saisie. */
    val montantCrypto: Double?
        get() {
            val n = saisi ?: return null
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
        /*
        ═══════════════════════════════════════════════════════════════
        L'ACHAT AUSSI PASSE PAR LE VERDICT, MAIS IL FREINE SANS INTERDIRE
        ═══════════════════════════════════════════════════════════════

        Ce que ça attrape : une référence Orange Money recopiée de
        travers — un O pour un zéro, un chiffre sauté. Avant, l'écran
        disait « demande transmise » et c'est le CHANGEUR qui découvrait
        le ❌, dans son canal, parmi d'autres messages. Il fallait
        rappeler, faire corriger, redéposer ; pendant ce temps le client
        croyait sa demande en cours.

        ─── TROIS CAS, ET UN SEUL FREINE ─────────────────────────────

        VERT : rien à dire, le bouton s'allume.

        ABSENT : aucun encaissement ne correspond. C'est là que le
        freinage sert, et c'est le seul endroit. Il faut alors un geste
        explicite — « transmettre quand même » — parce que l'argent est
        déjà parti et qu'on n'a pas le droit de le bloquer.

        RIEN VÉRIFIÉ : le changeur n'a pas installé la lecture des SMS,
        ou le relais n'a pas répondu. On n'a RIEN à opposer, donc on
        n'oppose rien : le bouton reste comme avant. Freiner ici aurait
        fait payer à chaque client, sur chaque achat, l'absence d'un
        réglage côté changeur.
        ═══════════════════════════════════════════════════════════════
        */
        sens == SensChange.ACHAT -> when {
            referencePaiement.isBlank() -> false
            verification?.estVert == true -> true
            forcer -> true
            // Un sondage est en vol : la réponse arrive dans l'instant,
            // et laisser passer pendant ce temps viderait le contrôle de
            // son sens.
            rechercheEnCours -> false
            /*
            RIEN VÉRIFIÉ : soit la boucle n'a pas encore rendu de verdict,
            soit elle a rendu « on n'a pas pu regarder ». Dans les deux cas
            on n'a rien à opposer.

            ─── INCONNUE COMPTE AUSSI, ET C'EST UN PIÈGE ÉVITÉ ──────────

            Elle ne vient pas du relais : elle vient d'une réponse que
            l'application n'a pas su lire. Le cas concret est le jour du
            déploiement — l'APK connaît /change/verifier-achat, le Worker
            pas encore. La requête tombe alors dans le relais de cours,
            qui répond poliment autre chose, et `etat` est illisible.

            Sans cette branche, le bouton « J'ai payé » resterait éteint
            sur CHAQUE achat jusqu'à la mise à jour du Worker, et il
            faudrait passer outre à la main à chaque fois. Un écran qui se
            bloque parce qu'un serveur n'a pas suivi est pire que pas de
            vérification du tout.

            Appel sûr plutôt que cast implicite : ce `when` n'a pas de
            sujet, et compter sur la propagation d'un `== null` écrit dans
            une autre branche est le genre de pari qui se perd en
            réordonnant les lignes.
            */
            else -> verification?.etat.let {
                it == null ||
                    it == EtatVerification.INDISPONIBLE ||
                    it == EtatVerification.INCONNUE
            }
        }
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
        // Par monnaie : TRON coute 13 a 27 TRX par transfert, BNB Chain
        // vingt centimes. Un seuil unique ferait du plus cher un piege.
        if (fcfa < p.minimumPour(monnaie)) return "minimum"
        if (fcfa > p.plafondFcfa) return "plafond"
        /*
        ═══════════════════════════════════════════════════════════════
        ON NE PEUT PAS VENDRE CE QU'ON N'A PAS
        ═══════════════════════════════════════════════════════════════

        Ce controle existait — mais dans `confirmer()`, APRES l'appel au
        relais, et sur le montant MARQUE. Deux consequences.

        La premiere est un aller-retour reseau pour rien, qui enregistre
        au passage la naissance d'une demande qui n'aboutira jamais.

        La seconde est pire : le bouton restait ALLUME. On appuyait, on
        attendait, et un message rouge venait dire que le solde ne suffit
        pas — alors que l'ecran savait des la saisie.

        Ici, le bouton s'eteint et la raison s'ecrit au-dessus, comme
        pour le minimum et le plafond. C'est la meme famille de refus :
        un chiffre qui ne peut pas marcher.

        LE MONTANT MARQUE RESTE CONTROLE DANS `confirmer()`, et il le
        faut : il s'AJOUTE a la saisie, jusqu'a 0,999. Quelqu'un qui
        detient 6,5 et tape 6 passe ici, et se verra refuser la-bas si
        la marque porte le total a 6,7. Les deux controles ne disent pas
        la meme chose.
        ═══════════════════════════════════════════════════════════════
        */
        if (sens == SensChange.VENTE) {
            val detenu = solde
            val demande = saisi
            if (detenu != null && demande != null && demande > detenu) return "solde"
        }
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
        /*
        ═══════════════════════════════════════════════════════════════════
        LE NUMERO ET LA REFERENCE DE PAIEMENT SONT DANS LA TRACE
        ═══════════════════════════════════════════════════════════════════

        Ils n'y etaient pas, et c'est l'ACHAT qui payait l'oubli — le sens
        le plus utilise.

        Sur un achat, ces deux champs ne sont pas du decor : ce sont les
        CRITERES avec lesquels le relais retrouve l'encaissement. Tant
        qu'ils sont vides, `boucleVerification` ne cherche rien, et c'est
        voulu.

        Or ils se tapent APRES la confirmation, sur l'ecran de paiement,
        donc apres l'ecriture de la trace. Et juste apres les avoir tapes,
        l'utilisateur part dans Orange Money — le moment precis ou Android
        ferme l'application sur un telephone peu puissant.

        Il revenait donc sur un ecran de paiement correct mais avec ses
        deux champs vides, a retaper son numero debout dans la rue, et sans
        verdict : la recherche ne redemarrait pas non plus pour un achat.

        Le commentaire de `reprendre` disait deja que c'est « le cas qui en
        a le plus besoin ». Il ne couvrait que la vente.
        ═══════════════════════════════════════════════════════════════════
        */
        val referencePaiement: String = "",
        val telephone: String = "",
        val horodatage: Long = 0L
    )

    /**
     * La trace, construite en UN SEUL endroit.
     *
     * Elle s'ecrit a deux moments — a la confirmation, puis a chaque fois
     * que le numero ou la reference de paiement changent. Deux listes de
     * champs recopiees cote a cote finiraient par diverger, et le champ
     * oublie serait invisible jusqu'au jour ou quelqu'un revient d'Orange
     * Money sans lui.
     */
    private fun traceDe(
        s: ChangeState,
        reference: String,
        prix: PrixFcfa,
        saisie: String,
        horodatage: Long
    ) = ChangeEnCours(
        reference = reference,
        sens = if (s.sens == SensChange.ACHAT) "achat" else "vente",
        monnaie = s.monnaie,
        saisie = saisie,
        baseFcfa = prix.baseFcfa,
        achatFcfa = prix.achatFcfa,
        venteFcfa = prix.venteFcfa,
        fcfaParDollar = s.fcfaParDollar ?: 0.0,
        referencePaiement = s.referencePaiement,
        telephone = s.telephone,
        horodatage = horodatage
    )

    /**
     * Reecrit la trace depuis l'etat courant, en gardant son horodatage
     * d'origine.
     *
     * L'HORODATAGE NE BOUGE PAS. C'est lui qui fait courir le compte a
     * rebours et qui decide qu'une trace est perimee ; le rafraichir a
     * chaque frappe rendrait indefiniment valide un prix fige il y a une
     * heure.
     *
     * Ne fait rien hors de l'etape de paiement : il n'y a alors aucune
     * demande en cours a decrire.
     */
    private fun sauverTrace() {
        val s = _state.value
        val prix = s.prix ?: return
        if (s.etape != EtapeChange.PAIEMENT || s.reference.isBlank()) return
        secureStorage.saveChangeEnCours(
            gson.toJson(traceDe(s, s.reference, prix, s.saisie, s.confirmeA))
        )
    }

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
                fcfaParDollar = trace.fcfaParDollar,
                /*
                L'INSTANT D'ORIGINE, PAS CELUI DU RETOUR.

                Le compte a rebours part de la confirmation. Le repartir a
                l'ouverture de l'ecran rendrait a quelqu'un dix minutes
                qu'il a deja consommees — et lui laisserait croire que son
                prix tient encore alors que le marche a bouge.
                */
                confirmeA = trace.horodatage,
                // Sur un achat, les criteres de recherche. Vides sur une
                // vente, qui se lit sur la chaine et n'en a pas besoin.
                referencePaiement = trace.referencePaiement,
                telephone = trace.telephone
            )
        }
        /*
        LA RECHERCHE REPREND, et c'est le cas qui en a le plus besoin.

        Si l'application a ete fermee par Android pendant que l'utilisateur
        envoyait sa crypto depuis une autre application, il revient ici
        sans rien. C'est exactement le moment ou le versement a eu le temps
        d'arriver sur la chaine : on redemande.

        ─── ET MAINTENANT POUR L'ACHAT AUSSI ───────────────────────────

        La ligne ne couvrait que la vente. Sur un achat, celui qui revient
        d'Orange Money est dans la meme situation, en plus courante : son
        paiement vient d'etre encaisse, le SMS de l'operateur est peut-etre
        deja arrive au relais, et l'ecran n'allait pas le chercher.

        A une condition pres : il faut un critere. Sans numero ni reference
        recopiee, `boucleVerification` s'arrete immediatement — on ne lance
        pas une boucle pour qu'elle reparte aussitot.
        */
        val reprise = _state.value
        if (reprise.sens == SensChange.VENTE ||
            reprise.referencePaiement.isNotBlank() || reprise.telephoneValide
        ) {
            demarrerVerification()
        }
    }

    fun charger() {
        viewModelScope.launch {
            _state.update { it.copy(chargement = true, erreur = null) }
            val lu = withContext(Dispatchers.IO) { service.parametres() }
            val p = (lu as? ResultatParametres.Ok)?.parametres
            _state.update {
                it.copy(
                    parametres = p,
                    horsLigne = lu is ResultatParametres.HorsLigne,
                    securise = lu is ResultatParametres.Securise,
                    causeReseau = when (lu) {
                        is ResultatParametres.HorsLigne -> lu.detail
                        is ResultatParametres.Securise -> lu.detail
                        else -> ""
                    },
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
        val solde = soldeDe(s.monnaie)
        _state.update { it.copy(prix = prix, fcfaParDollar = taux, solde = solde) }
    }

    /** Solde detenu de cette monnaie, ou null si l'instantane ne le dit pas. */
    /*
    ═══════════════════════════════════════════════════════════════════════
    NULL VEUT DIRE « INCONNU », PAS « ZERO »
    ═══════════════════════════════════════════════════════════════════════

    C'etait `?.amountRaw?.takeIf { it > 0.0 }` : un solde de zero rendait
    null, exactement comme une monnaie absente de l'instantane.

    ET CA DESACTIVAIT LE GARDE-FOU QUI COMPTE. `confirmer()` refuse une
    vente dont le montant marque depasse le solde — mais seulement si le
    solde est CONNU :

        if (exactNombre != null && soldeConnu != null && exactNombre > soldeConnu)

    Avec un solde nul rendu comme inconnu, la condition ne se declenchait
    jamais. Quelqu'un qui ne detient rien pouvait aller jusqu'a l'ecran de
    paiement et se voir demander d'envoyer 6,47 USDT qu'il n'a pas — et
    l'ecran lui donnait une adresse, un montant, un compte a rebours, tout
    l'appareil d'une operation qui ne peut pas aboutir.

    Le garde-fou etait precisement la pour ce cas-la, et il etait
    desactive par le cas qu'il devait attraper.

    DESORMAIS : null UNIQUEMENT quand la monnaie n'est pas dans
    l'instantane — c'est-a-dire quand on ne sait vraiment pas. Un zero
    present est un zero.
    ═══════════════════════════════════════════════════════════════════════
    */
    private fun soldeDe(symbole: String): Double? = try {
        val json = secureStorage.getPortfolioSnapshot()
        val snap = json?.let { gson.fromJson(it, SnapLiteChange::class.java) }
        snap?.tokens?.firstOrNull { it.symbol.equals(symbole, ignoreCase = true) }
            ?.amountRaw
    } catch (_: Exception) { null }

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

    /*
    ═══════════════════════════════════════════════════════════════════════
    CE QUI EST TAPÉ PASSE PAR UN FILTRE
    ═══════════════════════════════════════════════════════════════════════

    C'était `it.copy(saisie = valeur)`, sans rien regarder. Un pavé
    numérique construit dans l'application tenait alors les règles : il
    n'autorisait que les frappes valides.

    LE PAVÉ A ÉTÉ RETIRÉ, ET LES RÈGLES NE DOIVENT PAS PARTIR AVEC LUI.
    Le clavier décimal d'Android accepte le point, plusieurs séparateurs,
    et autant de décimales qu'on veut — y compris plus que le jeton n'en
    possède. Sans filtre, l'écran réaffiche « 8,19672131 USDT » : deux
    chiffres qui n'existent pas sur un jeton à six décimales, donc une
    somme que personne ne peut envoyer. Ce défaut a déjà existé ici.

    ─── LE NOMBRE DE DÉCIMALES DÉPEND DE CE QU'ON TAPE ─────────────────

    Zéro pour les francs, qui n'ont pas de centimes. Celui de la monnaie
    pour une vente — six sur l'USDT, huit sur le bitcoin. C'est le sens de
    l'opération qui le dit, donc il se calcule ici et nulle part ailleurs.
    ═══════════════════════════════════════════════════════════════════════
    */
    fun onSaisie(valeur: String) = _state.update { etat ->
        val decimalesMax =
            if (etat.sens == SensChange.ACHAT) 0
            else TauxFcfa.decimalesAffichage(etat.monnaie)
        etat.copy(saisie = SaisieMontant.filtrer(valeur, decimalesMax), erreur = null)
    }

    /**
     * Remplit le champ d'un coup, depuis une pastille de montant.
     *
     * TROIS MONTANTS COURANTS PLUTOT QU'UN CLAVIER. « 5 000 » se tape en
     * quatre frappes sur un telephone qu'on tient d'une main, dans la rue.
     * Les pastilles suppriment ces frappes pour les montants que les gens
     * echangent reellement — et elles rappellent au passage l'ordre de
     * grandeur attendu a celui qui ouvre l'ecran pour la premiere fois.
     */
    fun onMontantRapide(valeur: Double) = _state.update {
        it.copy(saisie = montantTexte(valeur), erreur = null)
    }

    /**
     * Met tout le solde dans le champ, pour une vente.
     *
     * ON NE RETIRE RIEN POUR LES FRAIS, contrairement au bouton MAX d'un
     * envoi ordinaire. Les frais de reseau se paient en TRX ou en BNB,
     * pas dans le jeton vendu : vendre tout son USDT est possible, a
     * condition d'avoir de quoi payer le gaz — ce que l'ecran d'envoi
     * verifiera a son tour.
     */
    fun onToutVendre() = _state.update { etat ->
        val solde = etat.solde ?: return@update etat
        etat.copy(saisie = montantTexte(solde), erreur = null)
    }
    /*
    ═══════════════════════════════════════════════════════════════════════
    SUR UN ACHAT, CES DEUX CHAMPS SONT LE CRITÈRE DE RECHERCHE
    ═══════════════════════════════════════════════════════════════════════

    La référence d'abord : le client la recopie depuis son SMS, l'opérateur
    l'a écrite dans celui du changeur, et les deux doivent coïncider. À
    défaut, le montant et le numéro — c'est le chemin de ceux qui n'ont
    rien recopié, c'est-à-dire la plupart.

    Les modifier relance donc la vérification. Sans ça, quelqu'un qui
    corrige une référence fausse resterait devant le ❌ qu'il vient de
    corriger, et conclurait que la correction n'a servi à rien.
    ═══════════════════════════════════════════════════════════════════════
    */
    fun onReferencePaiement(v: String) {
        _state.update { it.copy(referencePaiement = v) }
        // SAUVE A CHAQUE FRAPPE, et non a la fin de la saisie : la frappe
        // n'a pas de fin reperable, et c'est juste apres le dernier
        // caractere qu'on part dans Orange Money.
        sauverTrace()
        relancerVerificationAchat()
    }

    fun onTelephone(v: String) {
        _state.update { it.copy(telephone = v) }
        sauverTrace()
        relancerVerificationAchat()
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    ON ATTEND LA FIN DE LA FRAPPE
    ═══════════════════════════════════════════════════════════════════════

    « MP251008123456 » fait quatorze caractères. Relancer à chaque frappe
    enverrait quatorze requêtes pour une seule référence, dont treize sur
    des références incomplètes qui répondraient toutes « absent » — et
    c'est ce dernier point qui compte : l'écran clignoterait en rouge
    pendant qu'on écrit, sur un champ correctement rempli.

    Une seconde et deux dixièmes après la dernière touche, on cherche.
    ═══════════════════════════════════════════════════════════════════════
    */
    private fun relancerVerificationAchat() {
        val s = _state.value
        if (s.sens != SensChange.ACHAT || s.etape != EtapeChange.PAIEMENT) return

        /*
        RIEN À CHERCHER : on efface le verdict précédent plutôt que de le
        laisser à l'écran. Un ❌ qui survit à l'effacement du champ qui
        l'a causé accuse un formulaire vide.
        */
        if (s.referencePaiement.isBlank() && !s.telephoneValide) {
            arreterVerification()
            _state.update {
                it.copy(verification = null, rechercheEnCours = false, essais = 0)
            }
            return
        }

        _state.update { it.copy(verification = null, essais = 0) }
        demarrerVerification(ATTENTE_FRAPPE)
    }

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
        if (s.envoiEnCours) return
        val reference = OrdreChange.nouvelleReference()

        /*
        ═══════════════════════════════════════════════════════════════════
        ON DEMANDE LA CONSIGNE AVANT D'ENVOYER QUELQU'UN TRANSFERER
        ═══════════════════════════════════════════════════════════════════

        Sur une vente, le relais impose un montant EXACT, derive de cette
        reference par un secret que lui seul detient : 2,004731 plutot que
        2. C'est ce qui empeche quelqu'un de surveiller l'adresse publique
        du changeur, de voir arriver un versement, et de deposer une
        demande pour le reclamer.

        Il faut donc le connaitre AVANT. Avancer d'abord et corriger le
        chiffre ensuite serait pire que tout : l'utilisateur aurait le
        temps de lire « 2 », de partir envoyer 2, et son versement ne
        serait jamais reconnu — irreversiblement.

        ─── UN ECHEC ARRETE L'OPERATION ─────────────────────────────────

        Si le relais ne repond pas, on NE PASSE PAS a l'etape de paiement.
        L'alternative serait de laisser partir avec un montant rond que le
        relais ne reconnaitra pas. Mieux vaut un ecran qui refuse d'avancer
        qu'un envoi irreversible vers un chiffre faux.
        ═══════════════════════════════════════════════════════════════════
        */
        if (s.sens != SensChange.VENTE) {
            poserEtapePaiement(s, prix, reference, s.saisie)
            return
        }

        _state.update { it.copy(envoiEnCours = true, erreur = null) }
        viewModelScope.launch {
            val exact = withContext(Dispatchers.IO) {
                service.consigne(s.monnaie, s.saisie, reference)
            }
            if (exact == null) {
                _state.update {
                    it.copy(
                        envoiEnCours = false,
                        erreur = "Le relais n'a pas pu fixer le montant exact à envoyer. " +
                            "Vérifie ta connexion et réessaie — n'envoie rien avant."
                    )
                }
                return@launch
            }
            /*
            ═══════════════════════════════════════════════════════════════
            LE MONTANT MARQUE PEUT DEPASSER LE SOLDE
            ═══════════════════════════════════════════════════════════════

            Le marquage s'AJOUTE : il occupe les trois decimales au-dessus
            de la partie entiere, donc le montant a envoyer est toujours
            superieur a celui qui a ete tape, d'au plus 0,999.

            Quelqu'un qui detient 17,4 USDT et tape 17 se verrait demander
            17,472 — qu'il ne peut pas envoyer. Et il ne l'apprendrait
            qu'au moment de l'envoi, apres avoir quitte cette application,
            sur un ecran qui ne sait rien de cette demande.

            C'est ICI qu'il faut le dire, parce que c'est ici qu'on connait
            le solde : le relais, lui, ne le connait pas.

            ─── ON PROPOSE LE MONTANT QUI MARCHE ────────────────────────

            Un refus sec laisserait chercher combien retirer. Le message
            donne le chiffre : une unite de moins suffit toujours, puisque
            le marquage ne depasse jamais 0,999.
            ═══════════════════════════════════════════════════════════════
            */
            val exactNombre = exact.replace(',', '.').toDoubleOrNull()
            val soldeConnu = _state.value.solde
            if (exactNombre != null && soldeConnu != null && exactNombre > soldeConnu) {
                /*
                UNE UNITE DE MOINS SUFFIT TOUJOURS, puisque le marquage ne
                depasse jamais 0,999. Mais sur un petit solde, « une unite
                de moins » peut tomber a zero : il faut alors dire que le
                solde ne suffit pas, et non proposer d'envoyer rien.
                */
                val propose = kotlin.math.floor(soldeConnu).toInt() - 1
                _state.update {
                    it.copy(
                        envoiEnCours = false,
                        erreur = if (propose >= 1) {
                            "Avec sa marque, le montant à envoyer serait de " +
                                "$exact ${s.monnaie}, or tu n'en détiens que " +
                                "${decimal(soldeConnu, s.monnaie)}. Réessaie avec " +
                                "$propose ou moins."
                        } else {
                            "Avec sa marque, le montant à envoyer serait de " +
                                "$exact ${s.monnaie}, or tu n'en détiens que " +
                                "${decimal(soldeConnu, s.monnaie)}. Ce solde ne " +
                                "suffit pas pour une vente."
                        }
                    )
                }
                return@launch
            }

            /*
            LA SAISIE DEVIENT LE MONTANT EXACT, et tout le reste suit.

            montantFcfa et montantCrypto se calculent depuis `saisie` : en
            la remplacant, l'utilisateur recoit des francs pour ce qu'il
            envoie REELLEMENT, y compris la fraction du marquage. Corriger
            l'un sans l'autre lui ferait cadeau de cette fraction.
            */
            _state.update { it.copy(envoiEnCours = false) }
            poserEtapePaiement(_state.value, prix, reference, exact)
            demarrerVerification()
        }
    }

    /** Fige le prix, ecrit la trace, et passe a l'etape de paiement. */
    private fun poserEtapePaiement(
        s: ChangeState,
        prix: PrixFcfa,
        reference: String,
        saisie: String
    ) {
        /*
        UN SEUL INSTANT POUR LES DEUX. `System.currentTimeMillis()` etait
        appele deux fois, une fois pour la trace et une fois pour l'etat :
        le compte a rebours de l'ecran et celui que la reprise restaure ne
        partaient donc pas du meme point. L'ecart est de quelques
        millisecondes, et c'est sans consequence — mais deux horodatages
        pour un seul evenement est le genre d'ecart qui grandit.
        */
        val maintenant = System.currentTimeMillis()
        /*
        LA REFERENCE DE PAIEMENT NE SE REPORTE PAS D'UNE OPERATION A
        L'AUTRE, mais le numero, si.

        La reference est celle que l'operateur a ecrite dans UN SMS, pour
        UN encaissement. La garder ferait chercher le paiement precedent
        avec le montant du nouveau : le relais repondrait « absent », et
        l'utilisateur lirait un refus sur un paiement qu'il n'a pas encore
        fait.

        Le numero, lui, est le sien. Le reclamer a chaque operation serait
        huit chiffres a retaper pour rien.
        */
        val neuf = s.copy(referencePaiement = "")

        // ECRITE AVANT, jamais apres : le moment dangereux est precisement
        // celui ou l'utilisateur quitte l'application pour aller payer.
        secureStorage.saveChangeEnCours(
            gson.toJson(traceDe(neuf, reference, prix, saisie, maintenant))
        )
        _state.update {
            it.copy(
                etape = EtapeChange.PAIEMENT,
                reference = reference,
                saisie = saisie,
                confirmeA = maintenant,
                referencePaiement = "",
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

    /*
    ═══════════════════════════════════════════════════════════════════════
    UN NUMÉRO DE TOUR, POUR QUE L'ANCIENNE BOUCLE N'ÉTEIGNE PAS LA NOUVELLE
    ═══════════════════════════════════════════════════════════════════════

    `cancel()` ne tue pas sur place : la coroutine reprend plus tard pour
    exécuter son `finally`. Depuis que la frappe relance la recherche, deux
    boucles se succèdent en quelques millisecondes — et le `finally` de
    l'ancienne arrivait APRÈS que la nouvelle ait allumé l'indicateur, donc
    l'éteignait aussitôt.

    L'écran serait resté sans indicateur devant une recherche en cours, ce
    qui est exactement le défaut que ce `finally` existe pour éviter, à
    l'envers.

    Chaque boucle garde son numéro et ne touche l'état que si elle est
    encore la dernière.
    ═══════════════════════════════════════════════════════════════════════
    */
    private var tourRecherche = 0

    private fun arreterVerification() {
        rechercheJob?.cancel()
        rechercheJob = null
    }

    fun demarrerVerification(attente: Long = 0L) {
        arreterVerification()
        val mien = ++tourRecherche
        rechercheJob = viewModelScope.launch {
            try {
                if (attente > 0L) {
                    /*
                    L'INDICATEUR S'ALLUME AVANT L'ATTENTE, pas après. Il
                    dit « on va chercher », ce qui est vrai, et il ferme
                    la seconde où le bouton principal serait passé pour
                    allumé faute de verdict.
                    */
                    _state.update { it.copy(rechercheEnCours = true) }
                    kotlinx.coroutines.delay(attente)
                }
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

                MAIS SEULEMENT SI PERSONNE N'A PRIS LA RELEVE — voir le
                numero de tour ci-dessus. Et on ne sort PAS d'ici par
                `return` : un `return` depuis un `finally` avalerait
                l'annulation, et la coroutine passerait pour terminee
                normalement.
                */
                if (tourRecherche == mien) {
                    _state.update { it.copy(rechercheEnCours = false) }
                }
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
            if (s.etape != EtapeChange.PAIEMENT) return

            /*
            ═══════════════════════════════════════════════════════════════
            DEUX SENS, DEUX RÉSEAUX, UNE SEULE BOUCLE
            ═══════════════════════════════════════════════════════════════

            Une vente se lit sur une chaîne publique. Un achat se lit dans
            le SMS que l'opérateur a envoyé au changeur, et que son robot a
            transmis au relais.

            Mais la CADENCE est la même, les verdicts sont les mêmes, et
            les conditions d'arrêt sont les mêmes. Écrire une seconde
            boucle pour l'achat aurait doublé tout ça — y compris le
            `finally` qui éteint l'indicateur, la relecture de l'état à
            chaque tour, et la règle « on n'insiste pas sur un verdict
            définitif ». C'est exactement là qu'on finit par corriger un
            sens en oubliant l'autre.
            ═══════════════════════════════════════════════════════════════
            */
            val achat = s.sens == SensChange.ACHAT

            /*
            SUR UN ACHAT, SANS CRITÈRE IL N'Y A RIEN À CHERCHER.

            Ni référence recopiée ni numéro complet : le relais ne saurait
            pas quel encaissement rapprocher, et répondrait « absent » sur
            un paiement parfaitement réel. Mieux vaut ne pas demander que
            de collecter un faux non.
            */
            if (achat && s.referencePaiement.isBlank() && !s.telephoneValide) return

            // Des FRANCS pour un achat — c'est ce que dit le SMS de
            // l'opérateur. De la crypto pour une vente.
            val montant = (if (achat) s.montantFcfa else s.montantCrypto) ?: return

            _state.update { it.copy(rechercheEnCours = true, essais = tour + 1) }
            val v = withContext(Dispatchers.IO) {
                if (achat) {
                    service.verifierAchat(
                        montantCanonique(montant),
                        s.telephone,
                        s.referencePaiement
                    )
                } else {
                    service.verifier(
                        s.monnaie,
                        /*
                        ═══════════════════════════════════════════════════════
                        UN POINT SUR LE RESEAU, UNE VIRGULE A L'ECRAN
                        ═══════════════════════════════════════════════════════

                        C'etait `montantTexte`, et ca a casse la verification
                        de TOUTES les ventes — « montant illisible », sur un
                        vrai ecran de paiement, pendant un vrai essai.

                        En ecrivant le pave numerique, j'ai fait rendre une
                        VIRGULE a `montantTexte` pour que la saisie affichee
                        soit francaise. Mais la meme fonction bâtissait le
                        parametre envoye au relais, qui le lit avec `Number()`
                        — et `Number("6,894")` vaut NaN.

                        Une fonction qui sert a la fois l'oeil et le reseau
                        finit toujours par trahir l'un des deux. Les deux usages
                        sont separes : virgule pour l'ecran, point pour le fil.
                        ═══════════════════════════════════════════════════════
                        */
                        montantCanonique(montant),
                        s.txidSaisi.trim(),
                        // Sans elle, le relais ne saurait pas quel montant
                        // marqué chercher — et répondrait « absent » sur une
                        // vente parfaitement valide.
                        s.reference
                    )
                }
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
                montantCrypto = decimal(crypto, s.monnaie),
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

    /**
     * Un montant de crypto pour le message du changeur.
     *
     * AVEC LES DECIMALES QUE LA MONNAIE POSSEDE. Huit pour tout le monde
     * ecrivait « Crypto : 8,19672131 USDT » dans le message Telegram — un
     * montant que le changeur ne peut PAS envoyer, l'USDT en ayant six. Il
     * enverrait 8,196721, et l'ecart entre ce qui est ecrit et ce qui est
     * envoye serait a son nom.
     */
    private fun decimal(v: Double, monnaie: String): String =
        java.text.NumberFormat.getNumberInstance(java.util.Locale.FRANCE).apply {
            maximumFractionDigits = TauxFcfa.decimalesAffichage(monnaie)
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

        /**
         * Le temps qu'on laisse a la frappe avant de chercher.
         *
         * « MP251008123456 » fait quatorze caracteres. Sans cette attente,
         * c'est quatorze requetes pour une reference, dont treize sur des
         * references incompletes qui repondent « absent » — donc un ecran
         * qui clignote en rouge pendant qu'on ecrit correctement.
         */
        const val ATTENTE_FRAPPE = 1_200L
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
    /*
    ═══════════════════════════════════════════════════════════════════════
    LE SÉPARATEUR EST LA VIRGULE, COMME SUR LE PAVÉ
    ═══════════════════════════════════════════════════════════════════════

    `toPlainString` rend « 12.345678 », avec un point. Tant que le clavier
    du système servait de saisie, ça ne gênait personne : il accepte les
    deux, et `montantFcfa` remplace la virgule par un point avant de lire.

    Depuis que le pavé est le seul clavier, le point devient un corps
    étranger. Deux conséquences, toutes deux silencieuses :

    — ON VERRAIT « 12.345678 » dans un champ dont toutes les touches
      écrivent des virgules ;
    — ET SURTOUT, `SaisieMontant` cherche la virgule pour savoir s'il est
      dans la partie décimale. Sur « 12.345678 » il n'en trouve pas, croit
      être dans la partie entière, et le chiffre suivant s'ajoute à la fin
      SANS compter comme une décimale — donc sans la limite de six.

    Un appui sur « Tout » suivi d'un chiffre suffisait à produire ça.
    ═══════════════════════════════════════════════════════════════════════
    */
    /**
     * Le montant tel qu'il part sur le reseau : un point, jamais de virgule.
     *
     * Separe de `montantTexte` a dessein. Celui-ci s'adresse a une machine
     * qui lit `Number()` ; l'autre s'adresse a quelqu'un qui tape sur un
     * pave francais. Les confondre a deja coute une vente.
     */
    private fun montantCanonique(v: Double): String =
        java.math.BigDecimal(v).setScale(8, java.math.RoundingMode.DOWN)
            .stripTrailingZeros().toPlainString()

    private fun montantTexte(v: Double): String =
        java.math.BigDecimal(v).setScale(8, java.math.RoundingMode.DOWN)
            .stripTrailingZeros().toPlainString()
            .replace('.', SaisieMontant.VIRGULE)

    private data class SnapLiteChange(val tokens: List<TokenLiteChange>?)
    private data class TokenLiteChange(
        val symbol: String = "",
        val priceUsd: Double = 0.0,
        val priceXof: Double = 0.0,
        /**
         * Solde exact, non arrondi.
         *
         * Il ne sert qu'a la VENTE, et il y sert vraiment : quelqu'un qui
         * ignore combien il detient tape un montant au hasard, se fait
         * refuser, et recommence. Le lui montrer sur la carte qu'il
         * remplit supprime ce tatonnement.
         *
         * A l'achat, il n'y a pas de solde a montrer — on ne detient pas
         * de francs dans l'application.
         */
        val amountRaw: Double = 0.0
    )
}
