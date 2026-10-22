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

/**
 * Une consigne obtenue AVANT que l'utilisateur appuie sur « Continuer ».
 *
 * ═══════════════════════════════════════════════════════════════════════
 * POURQUOI ON PEUT LA PREPARER
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Le relais derive le montant a envoyer ainsi :
 *
 *     a_envoyer = floor(montant tape) + HMAC(reference) / 1000
 *
 * Il ne depend donc que de DEUX choses : la reference, et la PARTIE
 * ENTIERE du montant. Les deux sont connues des que la frappe s'arrete.
 *
 * On peut donc poser la question pendant que l'utilisateur relit son
 * ecran, au lieu d'attendre qu'il appuie — et sur un reseau mobile
 * burkinabe, cet aller-retour prend trois a cinq secondes pendant
 * lesquelles il regarde un bouton qui tourne.
 *
 * ─── CE QU'ON NE PREPARE PAS ─────────────────────────────────────────
 *
 * Rien n'est engage : aucune demande n'est transmise au changeur, aucun
 * fonds ne bouge. Le relais note seulement qu'une reference existe et
 * quand elle est nee.
 *
 * ─── D'OU LA DUREE DE VALIDITE ───────────────────────────────────────
 *
 * Cette naissance sert a l'anteriorite : un versement arrive AVANT elle
 * ne compte pas pour cette demande. Preparer tot la recule d'autant, ce
 * qui elargit la fenetre. Deux minutes est le prix qu'on accepte de
 * payer ; au-dela on redemande, et la naissance redevient celle du
 * moment ou l'on confirme.
 */
data class ConsignePreparee(
    val reference: String,
    val monnaie: String,
    /** La partie entiere du montant tape, seule chose dont depend le calcul. */
    val socle: Long,
    /** Le montant exact a envoyer, tel que le relais l'a rendu. */
    val exact: String,
    val quand: Long
)

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
    val forcer: Boolean = false,
    /** La consigne obtenue d'avance, quand la frappe s'est arretee. */
    val consignePrete: ConsignePreparee? = null
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

    /**
     * La partie entiere du montant tape — la seule chose, avec la
     * reference, dont depende le montant marque. Taper 2,9 ou 2,0 donne
     * le meme socle, donc la meme consigne.
     */
    val socleVente: Long?
        get() = saisi?.takeIf { it > 0.0 }?.let { kotlin.math.floor(it).toLong() }

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
     * Un achat, lui, exige de quoi RATTACHER le paiement a la demande :
     * la reference recopiee depuis le SMS de l'operateur, ou le numero
     * qui a paye. L'un des deux, jamais rien — c'est exactement ce que
     * la recherche demande de son cote.
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
            /*
            ═══════════════════════════════════════════════════════════
            LA REFERENCE *OU* LE NUMERO, LE MEME CRITERE QUE LA
            RECHERCHE
            ═══════════════════════════════════════════════════════════

            C'etait « referencePaiement.isBlank() -> false » : la
            reference, et rien d'autre.

            Or la recherche, elle, accepte les deux. `boucleVerification`
            ne s'arrete que si la reference ET le numero manquent, et
            `relancerVerificationAchat` repart sur une modification du
            numero seul. Le commentaire qui l'explique dit meme pourquoi :
            le numero est « le chemin de ceux qui n'ont rien recopie,
            c'est-a-dire la plupart ».

            Les deux moities se contredisaient donc, et le resultat est
            le pire qui soit : quelqu'un qui tape seulement son numero
            voit le relais retrouver son paiement, lit « paiement recu »
            en vert — et le bouton reste eteint. Son argent est parti, et
            l'ecran lui affirme que tout va bien en lui refusant le seul
            geste qui depose sa demande.

            Un seul critere, ecrit une fois : il faut de quoi rattacher
            le paiement a la demande, et ce lien est la reference de
            l'operateur OU le numero qui a paye. Jamais rien.

            ─── SI LA REFERENCE DOIT RESTER OBLIGATOIRE ────────────────

            Alors c'est l'autre moitie qu'il faut corriger : cesser de
            chercher sur le numero seul, pour ne plus jamais afficher un
            verdict vert qu'on refuse ensuite d'honorer. Ce qui ne peut
            pas tenir, c'est que les deux regles different.
            ═══════════════════════════════════════════════════════════
            */
            referencePaiementUtile.isBlank() && !telephoneValide -> false
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

    /*
    ═══════════════════════════════════════════════════════════════════════
    DEUX FAUTES DE SAISIE QUI SE RECONNAISSENT SANS LE RESEAU
    ═══════════════════════════════════════════════════════════════════════

    Le vrai controle, celui qui compte, est ailleurs : le relais compare
    la reference au SMS reel de l'operateur. Rien d'ecrit ici ne peut le
    remplacer, et rien ici ne doit le doubler.

    Ce que ces controles ajoutent est autre chose : DIRE LEQUEL DES DEUX
    EST EN CAUSE. Jusqu'ici, une reference mal tapee et un paiement
    absent rendaient le meme ❌ — « aucun encaissement ne porte cette
    reference » — qui accuse le paiement alors que c'est le champ qui est
    faux. Quelqu'un qui vient de payer lit que son argent n'est pas
    arrive.

    ─── ON NE VALIDE PAS UN FORMAT D'OPERATEUR ──────────────────────────

    Et c'est deliberement. Le relais accepte ce qui suit « ref », « id »
    ou « transaction », sur 5 a 32 caracteres, parce qu'Orange, Moov et
    Wave redigent differemment et changent leurs libelles. Coder
    « MP + 12 chiffres » ici ferait refuser une reference valable le jour
    ou l'un d'eux change de forme — un refus que personne ne saurait
    expliquer, sur un paiement deja fait.

    On ne reconnait donc que ce qui ne PEUT PAS etre une reference
    d'operateur, jamais ce qui devrait en etre une.
    ═══════════════════════════════════════════════════════════════════════
    */

    /**
     * La reference telle que le relais la rangera : alphanumerique, en
     * capitales. C'est `verifierAchat` qui impose cette forme — il
     * applique exactement ce nettoyage avant de chercher sa cle.
     */
    val referenceNormalisee: String
        /*
        L'ASCII SEUL, parce que le relais retire `[^a-zA-Z0-9]`.
        `isLetterOrDigit` laisserait passer les lettres accentuees : la
        reference envoyee ne serait plus celle que le relais range, et la
        longueur mesuree ici ne serait pas la sienne.
        */
        get() = referencePaiement
            .filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
            .uppercase()

    /**
     * Ce qui cloche dans la reference saisie, ou `null` si elle est
     * plausible. Un champ vide ne cloche pas : on ne reproche pas a
     * quelqu'un de ne pas avoir encore tape.
     */
    fun problemeReference(): String? {
        if (referencePaiement.isBlank()) return null
        val r = referenceNormalisee
        /*
        LA CONFUSION ENTRE LES DEUX REFERENCES, et c'est le piege le plus
        probable de cet ecran : les deux y sont affichees, et les deux
        s'appellent « reference ».

        « VX-A7K2M4P9 » est celle de la DEMANDE, celle qu'on met en motif.
        Le champ, lui, attend celle du PAIEMENT, lue dans le SMS.

        Le format VaultEx se reconnait sans ambiguite utile : VX suivi de
        huit caracteres d'un alphabet qui exclut O, I, 0 et 1. Un
        identifiant d'operateur qui tomberait par hasard dans ce moule
        serait une coincidence; la confusion, elle, est quotidienne.
        */
        if (OrdreChange.MOTIF_REFERENCE_DEMANDE.matches(r)) return "ref_demande"
        /*
        UNE LONGUEUR IMPOSSIBLE. Le relais ne range jamais que des
        references de 5 a 32 caracteres : sa propre extraction ne peut
        pas en produire d'autres. En dessous ou au-dessus, il n'y a rien
        a trouver, et le dire tout de suite epargne un aller-retour et un
        ❌ trompeur.
        */
        if (r.length < 5) return "courte"
        if (r.length > 32) return "longue"
        // Aucun chiffre : aucun operateur ne numerote ses transactions
        // en lettres seules. C'est un mot recopie a la place du numero.
        if (r.none { it.isDigit() }) return "sans_chiffre"
        return null
    }

    /**
     * La reference qu'on envoie reellement — vide tant qu'elle cloche.
     *
     * Une reference mal formee n'est pas un lien vers un paiement : la
     * chercher rendrait un « absent » sur un paiement bien reel, et la
     * transmettre occuperait chez le relais une cle de dedoublonnage
     * avec du bruit.
     *
     * Le numero, lui, reste utilisable : quelqu'un qui s'est trompe de
     * reference mais a donne son numero sera retrouve par le montant et
     * le numero, et c'est exactement ce que fait le relais.
     */
    val referencePaiementUtile: String
        get() = if (problemeReference() == null) referenceNormalisee else ""

    /**
     * Le plus grand montant qu'on puisse vendre d'un solde donne.
     *
     * UNE SEULE DEFINITION, parce qu'elle sert a deux endroits qui
     * doivent s'accorder : le bouton « Tout », qui l'ecrit dans le champ,
     * et le message d'erreur du marquage, qui le PROPOSE quand on a tape
     * trop haut. Deux formules voisines finiraient par differer d'une
     * unite, et l'ecran proposerait un chiffre que son propre bouton
     * n'ecrit pas.
     *
     * Rend 0 quand le solde ne permet aucune vente — a l'appelant de ne
     * rien proposer dans ce cas plutot que d'ecrire zero.
     */
    fun plusGrandMontantVendable(solde: Double): Double {
        val v = kotlin.math.floor(solde - 0.999)
        return if (v >= 1.0) v else 0.0
    }

    fun blocage(): String? {
        val p = parametres ?: return "indisponible"
        if (!p.utilisable) return "ferme"
        if (sens == SensChange.VENTE && !p.rachetable(monnaie)) return "pas_de_rachat"
        if (prix == null) return "sans_cours"
        val fcfa = montantFcfa ?: return "montant"
        // Par monnaie : TRON coute 13 a 27 TRX par transfert, BNB Chain
        // vingt centimes. Un seuil unique ferait du plus cher un piege.
        if (fcfa < p.minimumPour(monnaie)) return "minimum"
        /*
        LE PLAFOND EFFECTIF, PAS LE GENERAL. Certaines monnaies portent
        le leur, en dollars — le BNB ne sert qu'a payer le gaz, et dix
        dollars en couvrent des centaines de transferts. `plafondPour`
        rend le plus bas des deux.
        */
        if (fcfa > p.plafondPour(monnaie, fcfaParDollar)) return "plafond"
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
        /*
        ═══════════════════════════════════════════════════════════════════
        L'ENVOI FAIT DEPUIS VAULTEX DEPOSE LA DEMANDE TOUT SEUL
        ═══════════════════════════════════════════════════════════════════

        Quand la vente est partie de l'ecran d'envoi, la crypto est DEJA
        diffusee et son hash est connu. Il ne reste rien a decider : ni a
        attendre que la chaine la montre, ni a appuyer sur un bouton de
        confirmation. La demande part.

        C'est le formulaire sans retour : un montant, un numero, une
        authentification — et le changeur est prevenu au moment ou la
        transaction passe.

        ─── ON NE DEPOSE QUE SI L'ON SAIT OU PAYER ─────────────────────

        Sans numero valide, le message dirait « envoyer 1 513 FCFA au
        numero du client » sans numero. On pose alors le hash et on laisse
        l'ecran de paiement demander ce qui manque ; le bouton s'allumera,
        puisque le versement est verifiable.

        ─── LA TRACE DE L'ENVOI EST EFFACEE AVANT L'APPEL ──────────────

        Si le depot echoue, l'utilisateur retrouve son ecran de paiement
        avec le hash en place et peut reessayer a la main. La rejouer a
        chaque ouverture deposerait la meme demande en boucle.
        ═══════════════════════════════════════════════════════════════════
        */
        val envoi = try {
            secureStorage.getEnvoiDeChange()
                ?.let { gson.fromJson(it, EnvoiDeChange::class.java) }
        } catch (_: Exception) { null }

        if (envoi != null && envoi.reference == trace.reference && envoi.hash.isNotBlank()) {
            secureStorage.saveEnvoiDeChange(null)
            _state.update {
                it.copy(
                    txidSaisi = envoi.hash,
                    // Le numero tape sur l'ecran d'envoi.
                    telephone = it.telephone.ifBlank { secureStorage.getNumeroChange() }
                )
            }
            if (_state.value.telephoneValide) {
                transmettre()
                return
            }
        }

        val reprise = _state.value
        if (reprise.sens == SensChange.VENTE ||
            reprise.referencePaiementUtile.isNotBlank() || reprise.telephoneValide
        ) {
            demarrerVerification()
        }
    }

    /** Ce que l'ecran d'envoi laisse derriere lui apres une vente. */
    private data class EnvoiDeChange(
        val reference: String = "",
        val hash: String = ""
    )

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

    fun onSens(sens: SensChange) {
        _state.update {
        // La saisie change d'unité avec le sens : la garder tromperait.
        it.copy(sens = sens, saisie = "", erreur = null)
        }
        preparerConsigne()
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
        preparerConsigne()
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
    fun onSaisie(valeur: String) {
        _state.update { etat ->
            /*
            ═══════════════════════════════════════════════════════════════
            AUCUNE DECIMALE, DANS LES DEUX SENS
            ═══════════════════════════════════════════════════════════════

            A L'ACHAT, parce que le franc CFA n'a pas de centimes. C'etait
            deja le cas.

            A LA VENTE, parce que LE MARQUAGE LES JETTE. Le relais calcule
            floor(montant tape) plus un marquage : taper 2,432 fait envoyer
            2,708, et non 2,432 plus quelque chose. Les decimales ne sont
            pas arrondies, elles sont remplacees.

            Un champ qui accepte 2,432 pour en faire 2,708 demande une
            explication a chaque fois — et on l'a vu sur capture, trois
            fois, sans que personne comprenne. N'acceptant que des entiers,
            le marquage se lit enfin pour ce qu'il est : une fraction
            ajoutee a un nombre rond.

            CE QU'ON PERD. Vendre 2,5 USDT devient impossible ; on vend 2.
            Sur les montants echanges ici — le minimum tourne autour d'une
            unite — c'est une perte reelle mais faible, et elle achete la
            seule chose qui comptait : que le chiffre affiche ensuite ne
            surprenne personne.
            ═══════════════════════════════════════════════════════════════
            */
            etat.copy(saisie = SaisieMontant.filtrer(valeur, 0), erreur = null)
        }
        preparerConsigne()
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
    fun onMontantRapide(valeur: Double) {
        _state.update {
        it.copy(saisie = montantTexte(valeur), erreur = null)
        }
        preparerConsigne()
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
    /*
    ═══════════════════════════════════════════════════════════════════════
    « TOUT » NE MET PAS TOUT, ET IL NE PEUT PAS
    ═══════════════════════════════════════════════════════════════════════

    Il mettait le solde entier. Ça ne pouvait pas aboutir.

    Le relais derive le montant a envoyer ainsi :

        marque  = 1 a 999
        a_envoyer = floor(montant tape) + marque / 1000

    Avec un solde de 3, taper 3 demande d'envoyer entre 3,001 et 3,999 —
    toujours plus que ce qu'on detient. `confirmer()` le voyait et le
    disait, mais apres l'aller-retour, et apres avoir laisse croire que le
    bouton menait quelque part.

    Pour un solde ENTIER, « tout » echouait a tous les coups. Pour un
    solde decimal — 3,5 — environ une fois sur deux, selon le marquage
    tire. Un bouton qui echoue au hasard est pire qu'un bouton absent :
    on l'essaie deux fois avant de renoncer.

    ─── LES DECIMALES QU'ON TAPE SONT JETEES ────────────────────────────

    C'est la cle, et elle n'est pas evidente : le marquage repart de la
    PARTIE ENTIERE. Taper 2,9 ou 2,0 donne exactement le meme montant a
    envoyer. Proposer des parts — 25 %, 50 % — n'aurait donc aucun sens
    ici : trois pastilles sur quatre tomberaient sur la meme valeur reelle.

    ─── LE PLUS GRAND MONTANT QUI PASSE TOUJOURS ────────────────────────

    Il faut floor(tape) + 0,999 <= solde, donc floor(tape) <= solde − 0,999.
    Le plus grand entier qui convienne est floor(solde − 0,999), et il
    passe quel que soit le marquage tire.

    Sur 3 il rend 2 ; sur 3,999 il rend 3 — la ou « une unite de moins »
    aurait rendu 2 et perdu une unite vendable pour rien.
    ═══════════════════════════════════════════════════════════════════════
    */
    fun onToutVendre() {
        _state.update { etat ->
        val solde = etat.solde ?: return@update etat
        val vendable = etat.plusGrandMontantVendable(solde)
        if (vendable <= 0.0) return@update etat
        etat.copy(saisie = montantTexte(vendable), erreur = null)
        }
        preparerConsigne()
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
        /*
        ═══════════════════════════════════════════════════════════════════
        ON NE RETIENT QUE LE NUMERO D'UNE VENTE
        ═══════════════════════════════════════════════════════════════════

        Le champ est partage par les deux sens, et il n'y veut pas dire la
        meme chose.

        A LA VENTE, c'est celui qui doit ETRE PAYE : une preference, qui
        ne change pas d'une operation a l'autre. La retenir a du sens.

        A L'ACHAT, c'est celui qui A PAYE : une declaration sur un fait
        passe. Elle appartient a cette operation-la et a aucune autre.

        LES CONFONDRE COUTE L'ARGENT DE QUELQU'UN. Le code anticipe
        explicitement qu'on paie depuis le telephone d'un proche — le
        message au changeur porte « ⚠️ Ce numero n'est PAS celui declare ».
        Retenir ce numero-la ferait pre-remplir la vente suivante avec
        celui du proche, et les francs partiraient chez lui.

        RETENU DES QU'IL EST COMPLET, et seulement alors. Garder une
        saisie en cours — « 70 12 » — le pre-remplirait a moitie la fois
        suivante, ce qui est pire que vide : on corrige un debut de numero
        au lieu d'en taper un.
        */
        val etat = _state.value
        if (etat.sens == SensChange.VENTE && etat.telephoneValide) {
            secureStorage.saveNumeroChange(v)
        }
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
        if (s.referencePaiementUtile.isBlank() && !s.telephoneValide) {
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
    /*
    ═══════════════════════════════════════════════════════════════════════
    ON DEMANDE LA CONSIGNE PENDANT QU'IL REGARDE, PAS QUAND IL APPUIE
    ═══════════════════════════════════════════════════════════════════════

    Mesure a l'usage : sur une vente, « Continuer » prenait trois a cinq
    secondes avant de changer d'ecran. Le bouton tournait, donc l'ecran
    ne paraissait pas casse — mais c'est trois a cinq secondes passees a
    attendre, a chaque vente, sur un reseau mobile.

    Ce que cet aller-retour rapporte ne peut pas etre supprime : le
    montant marque vient d'un secret du serveur, et il faut le connaitre
    AVANT que quelqu'un parte envoyer sa crypto.

    Mais il n'a pas a etre demande au dernier moment. Il ne depend que de
    la reference et de la partie entiere du montant, toutes deux connues
    des que la frappe s'arrete. On le demande donc la, pendant que
    l'utilisateur relit ses chiffres — et au moment ou il appuie, la
    reponse est deja posee.

    ─── CE QU'ON NE PREPARE PAS ────────────────────────────────────────

    Rien n'est engage : aucune demande ne part chez le changeur, aucun
    fonds ne bouge, et le solde n'est verifie qu'a la confirmation. Une
    preparation abandonnee ne laisse qu'une reference nee pour rien.

    ─── ON NE PREPARE QUE CE QUI PEUT ABOUTIR ──────────────────────────

    Seulement une VENTE, seulement a l'etape du calcul, et seulement
    quand rien ne bloque. Preparer sur un montant hors bornes ou sans
    cours ferait naitre des references pour des ventes impossibles.
    ═══════════════════════════════════════════════════════════════════════
    */
    private var preparationJob: kotlinx.coroutines.Job? = null

    private fun preparerConsigne() {
        preparationJob?.cancel()
        val depart = _state.value
        if (depart.sens != SensChange.VENTE || depart.etape != EtapeChange.CALCUL) return
        if (depart.blocage() != null) return
        val socle = depart.socleVente ?: return

        // Deja prete pour ce socle et cette monnaie : rien a redemander.
        val prete = depart.consignePrete
        if (prete != null && prete.monnaie == depart.monnaie && prete.socle == socle &&
            System.currentTimeMillis() - prete.quand < VALIDITE_PREPARATION
        ) return

        preparationJob = viewModelScope.launch {
            kotlinx.coroutines.delay(ATTENTE_PREPARATION)
            // L'etat a pu bouger pendant l'attente : on ne prepare que ce
            // qui est encore a l'ecran.
            val avant = _state.value
            if (avant.sens != SensChange.VENTE || avant.etape != EtapeChange.CALCUL) return@launch
            if (avant.blocage() != null || avant.socleVente != socle) return@launch
            if (avant.monnaie != depart.monnaie) return@launch

            val reference = OrdreChange.nouvelleReference()
            val exact = withContext(Dispatchers.IO) {
                service.consigne(avant.monnaie, avant.saisie, reference)
            } ?: return@launch

            /*
            ON NE POSE QUE SI RIEN N'A BOUGE. Entre la requete et sa
            reponse, l'utilisateur a pu changer de montant ou de monnaie.
            Poser quand meme donnerait une consigne qui ne decrit plus ce
            qu'il voit — et `confirmer` l'ecarterait, donc l'attente
            reviendrait sans qu'on sache pourquoi.
            */
            _state.update {
                if (it.sens != SensChange.VENTE || it.etape != EtapeChange.CALCUL ||
                    it.monnaie != avant.monnaie || it.socleVente != socle
                ) it
                else it.copy(
                    consignePrete = ConsignePreparee(
                        reference = reference,
                        monnaie = avant.monnaie,
                        socle = socle,
                        exact = exact,
                        quand = System.currentTimeMillis()
                    )
                )
            }
        }
    }

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

        /*
        ═══════════════════════════════════════════════════════════════════
        LA CONSIGNE EST PEUT-ETRE DEJA LA
        ═══════════════════════════════════════════════════════════════════

        `preparerConsigne` l'a demandee une seconde apres la fin de la
        frappe, pendant que l'utilisateur relisait son ecran. Quand elle
        correspond encore — meme monnaie, meme partie entiere, moins de
        deux minutes — l'ecran change immediatement.

        ON REPREND SA REFERENCE, et c'est obligatoire : le montant marque
        derive d'ELLE. Confirmer avec une reference neuve rendrait le
        chiffre prepare faux, et l'utilisateur enverrait un montant que le
        relais ne rattacherait a rien.

        Elle est consommee au passage. Deux demandes qui partageraient une
        reference seraient refusees par le dedoublonnage du relais, et la
        seconde le serait sans qu'on comprenne pourquoi.
        ═══════════════════════════════════════════════════════════════════
        */
        val prete = s.consignePrete?.takeIf {
            it.monnaie == s.monnaie &&
                it.socle == s.socleVente &&
                System.currentTimeMillis() - it.quand < VALIDITE_PREPARATION
        }
        if (prete != null) {
            _state.update { it.copy(consignePrete = null, erreur = null) }
            if (soldeSuffitApresMarquage(prete.exact, s)) {
                poserEtapePaiement(_state.value, prix, prete.reference, prete.exact)
                demarrerVerification()
            }
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
            // Le meme controle que sur la voie rapide, ecrit une fois.
            if (!soldeSuffitApresMarquage(exact, s)) {
                _state.update { it.copy(envoiEnCours = false) }
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

    /*
    ═══════════════════════════════════════════════════════════════════════
    LE MONTANT MARQUE PEUT DEPASSER LE SOLDE
    ═══════════════════════════════════════════════════════════════════════

    Le marquage s'AJOUTE : il occupe les trois decimales au-dessus de la
    partie entiere, donc le montant a envoyer est toujours superieur a
    celui qui a ete tape, d'au plus 0,999.

    Quelqu'un qui detient 17,4 USDT et tape 17 se verrait demander 17,472
    — qu'il ne peut pas envoyer. Et il ne l'apprendrait qu'au moment de
    l'envoi, apres avoir quitte cette application, sur un ecran qui ne
    sait rien de cette demande.

    C'est ICI qu'il faut le dire, parce que c'est ici qu'on connait le
    solde : le relais, lui, ne le connait pas.

    ─── ON PROPOSE LE MONTANT QUI MARCHE ────────────────────────────────

    Un refus sec laisserait chercher combien retirer. Le message donne le
    chiffre, et c'est exactement celui qu'ecrit le bouton « Tout » : une
    seule definition, `plusGrandMontantVendable`, sinon l'ecran proposerait
    un montant que son propre bouton refuse d'inscrire.

    ─── POURQUOI C'EST UNE FONCTION ─────────────────────────────────────

    Depuis que la consigne peut arriver d'avance, deux chemins menent ici
    — celui qui attend le reseau et celui qui trouve la reponse deja
    prete. Le controle ecrit deux fois aurait fini par ne valoir que sur
    un seul, et ce serait le rapide, donc le plus frequent, qui l'aurait
    perdu.
    ═══════════════════════════════════════════════════════════════════════
    */
    private fun soldeSuffitApresMarquage(exact: String, s: ChangeState): Boolean {
        val exactNombre = exact.replace(',', '.').toDoubleOrNull() ?: return true
        val soldeConnu = _state.value.solde ?: return true
        if (exactNombre <= soldeConnu) return true

        val propose = _state.value.plusGrandMontantVendable(soldeConnu).toInt()
        _state.update {
            it.copy(
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
        return false
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
                /*
                LE NUMERO RETENU REVIENT ICI, ET NULLE PART AILLEURS.

                C'est le seul moment ou il sert, et le seul ou l'on sait
                de quel sens il s'agit. Le poser au demarrage de l'ecran
                le ferait apparaitre aussi sur un achat, ou il signifie
                autre chose — « qui a paye » et non « qui doit etre
                paye ».

                ET SEULEMENT S'IL EST VIDE : quelqu'un qui vient de taper
                un autre numero pour cette vente-ci a voulu etre paye
                ailleurs. L'ecraser par le precedent reprendrait une
                decision qu'il vient de prendre.
                */
                telephone = if (s.sens == SensChange.VENTE && s.telephone.isBlank()) {
                    secureStorage.getNumeroChange()
                } else s.telephone,
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
            if (achat && s.referencePaiementUtile.isBlank() && !s.telephoneValide) return

            // Des FRANCS pour un achat — c'est ce que dit le SMS de
            // l'opérateur. De la crypto pour une vente.
            val montant = (if (achat) s.montantFcfa else s.montantCrypto) ?: return

            _state.update { it.copy(rechercheEnCours = true, essais = tour + 1) }
            val v = withContext(Dispatchers.IO) {
                if (achat) {
                    service.verifierAchat(
                        montantCanonique(montant),
                        s.telephone,
                        s.referencePaiementUtile
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
                referencePaiement = s.referencePaiementUtile,
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

        /**
         * Le temps de silence avant de preparer la consigne d'une vente.
         *
         * Plus court que l'attente de la verification : ici une requete
         * de trop ne fait clignoter aucun verdict, elle coute seulement
         * un aller-retour. Mais preparer a chaque frappe de « 1500 »
         * poserait quatre references pour une seule vente.
         */
        const val ATTENTE_PREPARATION = 900L

        /**
         * Au-dela, la consigne preparee est jetee et redemandee.
         *
         * La preparation fait naitre la demande cote relais, et cette
         * naissance sert a l'anteriorite : un versement arrive avant elle
         * ne compte pas. Preparer tot la recule, donc elargit la fenetre
         * d'autant. Deux minutes est l'elargissement qu'on accepte ; au
         * dela on redemande, et la naissance redevient celle du moment ou
         * l'on confirme.
         */
        const val VALIDITE_PREPARATION = 2 * 60 * 1000L
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
