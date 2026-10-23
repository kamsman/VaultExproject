package com.vaultex.ui.screens.change

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.ui.navigation.Routes
import com.vaultex.R
import com.vaultex.domain.fiat.TauxFcfa
// Imports nommes : com.vaultex.ui.theme.Surface est une COULEUR.
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentGreen
import com.vaultex.ui.theme.AccentOrange
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.OrangeMoney
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.viewmodel.ChangeViewModel
import com.vaultex.ui.viewmodel.EtapeChange
import com.vaultex.ui.viewmodel.SensChange

/*
═══════════════════════════════════════════════════════════════════════════
ACHETER ET VENDRE EN FRANCS
═══════════════════════════════════════════════════════════════════════════

Trois temps, et pas un de moins.

CALCUL. Tout est dit avant le bouton : le taux, la marge en francs par
dollar ET en pourcentage, le montant exact qui sera reçu, le délai annoncé.
Rien ne doit se découvrir après.

PAIEMENT. Le numéro du changeur, le montant au franc près, la référence à
mettre en motif. L'utilisateur paie hors de l'application — nous ne
touchons jamais l'argent.

DÉCLARATION. Il dit qu'il a payé, et la demande part sur Telegram. Pas
avant : sinon le canal du changeur se remplirait de demandes dont la
plupart ne seront jamais payées, et chaque message doit vouloir dire
« quelqu'un affirme avoir payé, va vérifier ».

─── CE QUE CET ÉCRAN DIT, ET QU'IL NE DOIT JAMAIS CESSER DE DIRE ────────

Que l'échange a lieu entre l'utilisateur et une personne, pas avec VaultEx.
Qu'il envoie en premier. Et combien de temps il doit attendre.

Trois phrases désagréables. Les retirer rendrait l'écran plus beau et
l'application malhonnête : quelqu'un qui perd de l'argent sans avoir été
prévenu a raison d'en vouloir à celui qui lui a montré l'écran.
═══════════════════════════════════════════════════════════════════════════
*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeScreen(navController: NavController) {
    val vm: ChangeViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val copier = com.vaultex.ui.components.rememberCopieAvecVibration()

    /*
    ═══════════════════════════════════════════════════════════════════════
    ON RELIT LE DISQUE A CHAQUE FOIS QUE CET ECRAN REVIENT
    ═══════════════════════════════════════════════════════════════════════

    C'EST LA LIGNE QUI MANQUAIT, et son absence suffisait a ce qu'aucune
    vente n'arrive jamais chez le changeur.

    L'ecran d'envoi ecrit le hash sur le disque, puis « Termine » fait
    `popBackStack(Routes.CHANGE, false)`. On REVIENT sur l'entree de pile
    existante : le ViewModel, lui, est attache a cette entree et survit.
    Son `init` ne rejoue pas, `charger()` ne rejoue pas, `reprendre()` non
    plus — et c'etait le seul endroit qui allait lire ce hash.

    Resultat : le hash restait sur le disque, la demande ne partait pas, et
    RIEN NE LE SIGNALAIT. Pas de message d'erreur, pas d'ecran en attente.
    Seul le canal Telegram du changeur, vide, le disait.

    Le composable, lui, QUITTE la composition quand on part vers l'ecran
    d'envoi et y REVIENT au retour — le NavHost ne compose que la
    destination courante. Un `LaunchedEffect(Unit)` rejoue donc exactement
    aux deux moments ou il faut relire : la premiere ouverture, et chaque
    retour.

    A la premiere ouverture, `charger()` est encore en vol et la reference
    est vide : le ramassage ne trouve rien et rend faux, sans effet. C'est
    `reprendre()`, a la fin de `charger()`, qui s'en charge dans ce cas-la.
    Les deux chemins ne se marchent pas dessus — la marque est consommee
    des qu'elle est lue.
    ═══════════════════════════════════════════════════════════════════════
    */
    LaunchedEffect(Unit) { vm.ramasserEnvoi() }


    /*
    ═══════════════════════════════════════════════════════════════════════
    LE BOUTON RETOUR DU TELEPHONE RAMENE AU FORMULAIRE, PAS A L'ACCUEIL
    ═══════════════════════════════════════════════════════════════════════

    Signale a l'usage : depuis l'ecran de paiement d'un achat, le retour
    systeme quittait l'ecran et deposait sur l'accueil. L'ecran precedent,
    du point de vue de celui qui l'utilise, c'est le FORMULAIRE — pas
    l'accueil.

    Il fait donc desormais ce que fait le bouton « Retour » deja present
    en bas de l'ecran. Une seule facon de revenir, un seul resultat.

    ─── POURQUOI SEULEMENT SUR UN ACHAT ────────────────────────────────

    Parce que revenir ABANDONNE la demande : la reference est effacee, et
    la trace sur le disque avec elle.

    Sur un achat, c'est sans consequence tant qu'on n'a pas transmis — le
    changeur ne sait rien, et rien n'est parti.

    Sur une VENTE, non. Le relais impose un montant marque, derive de
    cette reference-la, et l'utilisateur a peut-etre DEJA ENVOYE sa crypto
    quand il appuie. Perdre la reference sur un retour machinal lui
    couterait son versement — irreversiblement. Le retour systeme y garde
    donc son comportement : il quitte l'ecran, et la demande reste sur le
    disque pour etre reprise a la reouverture.

    Deux comportements, parce qu'il y a deux risques. Ce n'est pas une
    incoherence : c'est la seule facon de ne pas faire payer a l'un la
    commodite de l'autre.
    ═══════════════════════════════════════════════════════════════════════
    */
    androidx.activity.compose.BackHandler(
        enabled = state.etape == EtapeChange.PAIEMENT && state.sens == SensChange.ACHAT
    ) {
        vm.retourAuCalcul()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    /*
                    ═══════════════════════════════════════════════════════
                    LE TITRE DIT LEQUEL DES DEUX, PAS « LES DEUX »
                    ═══════════════════════════════════════════════════════

                    Il affichait « Acheter / Vendre » en permanence. Le
                    nom du MENU, pas celui de ce qu'on est en train de
                    faire — et sur l'etape de paiement, c'etait le seul
                    endroit de l'ecran a pouvoir le dire.

                    Car les onglets ne vivent qu'au temps 1. Au temps 2,
                    il reste « Envoie le paiement » ou « Envoie la
                    crypto », qui disent le GESTE sans nommer l'operation.
                    Quelqu'un qui revient d'Orange Money vingt minutes
                    plus tard, ou qui retrouve l'ecran apres qu'Android a
                    ferme l'application, n'avait rien pour verifier d'un
                    coup d'oeil qu'il est bien du bon cote.

                    Et se tromper de sens coute ici une operation entiere,
                    dans un sens irreversible.

                    ─── POURQUOI PAS SIMPLEMENT « ACHETER » ─────────────

                    Parce qu'au temps 1, l'onglet choisi porte deja ce mot
                    exact, en gras, a deux centimetres en dessous. Deux
                    fois le meme mot l'un sous l'autre ne se lit plus. En
                    nommant l'objet — « Acheter de la crypto » — le titre
                    dit quelque chose que l'onglet ne dit pas.
                    ═══════════════════════════════════════════════════════
                    */
                    Text(
                        stringResource(
                            if (state.sens == SensChange.ACHAT) R.string.change_titre_achat
                            else R.string.change_titre_vente
                        ),
                        fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack, stringResource(R.string.back),
                            tint = AccentBlue
                        )
                    }
                },
                /*
                LE BANDEAU BLEU A ETE RETIRE.

                Il donnait un haut a la page, et c'etait vrai. Mais il
                colorait aussi tout ce qui s'en approchait : les onglets y
                baignaient, et la carte du paiement — bleu translucide a
                l'epoque — laissait le degrade passer au travers. Deux
                correctifs ont suivi, un pour la hauteur et un pour
                l'opacite de la carte, avant qu'il soit plus simple de
                constater que la page se tient sans lui.

                La barre reprend donc le fond de la page. Ce qui separe le
                haut de l'ecran du reste, ce sont les cartes blanches et
                leur ombre — ce qui suffit, et ne teinte rien.
                */
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BgPrimary
                )
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            /*
            DIX POINTS ET NON QUATORZE.

            Multiplie par la dizaine d'elements de l'etape de paiement, ce
            sont une quarantaine de points gagnes — de quoi faire tenir
            l'ecran sans defilement, ce qui vaut mieux qu'un peu d'air entre
            des blocs qui se distinguent deja par leur fond.
            */
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val p = state.parametres

            when {
                state.chargement && p == null ->
                    Note(stringResource(R.string.change_chargement), AccentBlue)

                p == null -> SansReglages(state) { vm.charger() }

                !p.utilisable ->
                    Note(stringResource(R.string.change_ferme), AccentOrange)

                state.etape == EtapeChange.TRANSMIS -> EtapeTransmis(state) {
                    vm.recommencer()
                }

                state.etape == EtapeChange.PAIEMENT ->
                    EtapePaiement(vm, state, copier, navController)

                else -> EtapeCalcul(vm, state, copier)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/*
═══════════════════════════════════════════════════════════════════════════
L'ÉCRAN QUAND IL N'Y A PAS DE RÉGLAGES
═══════════════════════════════════════════════════════════════════════════

DEUX CAUSES, DEUX PHRASES, DEUX GESTES.

C'était une seule note : « Service indisponible pour le moment. Réessaie
dans un instant. » Vu sur un vrai téléphone, sans données mobiles — elle
accusait le service alors que c'était la connexion, et elle conseillait
d'attendre, ce qui est précisément le geste qui ne marchera jamais. On
peut rester devant cet écran indéfiniment sans penser à activer ses
données.

Hors ligne, on nomme la connexion. Service injoignable alors que le
téléphone est sorti, on dit l'attente. La distinction vient du TYPE de
l'échec réseau, pas d'une devinette — voir `ResultatParametres`.

─── ET UN BOUTON, PARCE QUE C'ÉTAIT UN CUL-DE-SAC ──────────────────────

L'écran n'affichait que cette phrase. Pour réessayer, il fallait sortir et
revenir — ce que personne ne devine, et qui ressemble à une application
cassée. « Réessaie dans un instant » invitait à un geste que l'écran ne
proposait pas.
═══════════════════════════════════════════════════════════════════════════
*/
@Composable
private fun ColumnScope.SansReglages(
    state: com.vaultex.ui.viewmodel.ChangeState,
    onReessayer: () -> Unit
) {
    /*
    TROIS CAUSES, TROIS PHRASES, TROIS GESTES.

    La troisieme vient d'etre separee : `SSLException` herite
    d'`IOException`, donc un refus de connexion securisee s'affichait
    « verifie tes donnees mobiles ». La cause la plus frequente etant la
    DATE du telephone, on envoyait chercher a l'exact oppose de l'endroit
    utile.
    */
    Note(
        stringResource(
            when {
                state.securise -> R.string.change_tls
                state.horsLigne -> R.string.change_hors_ligne
                else -> R.string.change_indisponible
            }
        ),
        AccentOrange
    )

    /*
    LE NOM DE L'EXCEPTION, EN PETIT.

    Il ne veut rien dire pour l'utilisateur, et c'est assume : il est la
    pour la capture d'ecran qu'il enverra. Sans lui, « pas de donnees »,
    « DNS muet » et « certificat refuse » se ressemblent a l'ecran — et il
    a deja fallu quatre echanges pour trancher une question que cette
    ligne repond seule.
    */
    if (state.causeReseau.isNotBlank()) {
        Text(
            state.causeReseau,
            fontSize = 10.sp,
            color = TextSecondary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
    OutlinedButton(
        onClick = onReessayer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.change_verif_reessayer), fontSize = 13.sp)
    }
}

// ─── Temps 1 : le calcul ────────────────────────────────────────────

@Composable
private fun ColumnScope.EtapeCalcul(
    vm: ChangeViewModel,
    state: com.vaultex.ui.viewmodel.ChangeState,
    copier: (String) -> Unit
) {
    val p = state.parametres ?: return

    /*
    DEUX ONGLETS, ET CHACUN SON ICONE.

    « Acheter » et « Vendre » se lisent vite et se confondent vite — deux
    mots de même longueur, au même endroit. Le panier et la double flèche
    donnent à l'oeil un repère qui ne dépend pas de la lecture, ce qui
    compte sur un écran où se tromper de sens coûte une opération entière.
    */
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OngletSens(
            texte = stringResource(R.string.change_acheter),
            // LE MEME SIGNE QUE LA TUILE DE L'ACCUEIL, et c'est une regle
            // et non une coincidence : deux icones differentes pour le
            // meme geste font hesiter sur l'endroit ou l'on vient
            // d'arriver. Le « $ » est parti des deux cotes — il nomme une
            // monnaie, et ce n'est pas celle d'ici.
            icone = Icons.Default.Add,
            choisi = state.sens == SensChange.ACHAT,
            modifier = Modifier.weight(1f)
        ) { vm.onSens(SensChange.ACHAT) }
        OngletSens(
            texte = stringResource(R.string.change_vendre),
            // L'etiquette de prix, et non la double fleche : vendre n'est
            // pas echanger, et la double fleche designe deja le Swap
            // ailleurs dans l'application.
            icone = Icons.Default.Sell,
            choisi = state.sens == SensChange.VENTE,
            modifier = Modifier.weight(1f)
        ) { vm.onSens(SensChange.VENTE) }
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    DEUX CARTES, « TU DONNES » ET « TU REÇOIS » — COMME SUR LE SWAP
    ═══════════════════════════════════════════════════════════════════════

    Avant, il y avait UN champ de saisie, puis une carte de détails où « ce
    que tu reçois » était la quatrième ligne. Il fallait donc lire un
    montant, descendre, retrouver la bonne ligne, et faire le lien
    soi-même — pour la seule question qui compte : je donne combien, je
    reçois combien.

    Le Swap pose la question dans sa forme : deux cartes, l'une au-dessus
    de l'autre, on remplit celle du haut et on lit celle du bas. Les mêmes
    utilisateurs passent d'un écran à l'autre ; la même question doit avoir
    la même forme.

    ─── LA MONNAIE VIT SUR LA CARTE CRYPTO, QUEL QUE SOIT LE SENS ───────

    À l'achat, elle est en bas (ce qu'on reçoit) ; à la vente, en haut (ce
    qu'on donne). La carte en francs, elle, n'a jamais de sélecteur : il
    n'y a qu'une monnaie locale.

    C'est cet invariant qui remplace trois réglages séparés — la liste
    déroulante dans le champ pour la vente, la rangée de pastilles pour
    l'achat, et le logo collé devant un montant en francs qui laissait
    croire qu'on saisissait des USDT.

    ─── DEUX COMMANDES POUR UN SEUL ÉTAT, ET C'EST VOULU ────────────────

    Ce commentaire disait l'inverse : « on ne met pas de bouton
    d'inversion ». Le bouton était pourtant là, entre les deux cartes,
    depuis plus longtemps que la phrase qui le niait. Un commentaire faux
    est pire qu'un commentaire absent — on s'y fie pour décider.

    Les onglets et le bouton rond écrivent le MÊME état, `state.sens` : ils
    ne peuvent pas se contredire, seulement s'atteindre différemment. Les
    onglets nomment l'engagement en mots, ce qui compte sur un écran où se
    tromper de sens coûte une opération entière. Le bouton rond est le
    geste qu'on connaît du Swap, à l'endroit où l'oeil le cherche — entre
    les deux montants.

    Ce qu'on ne met pas, en revanche, c'est un troisième chemin, ni un
    état séparé « inversé » qui devrait rester d'accord avec celui-là.
    ═══════════════════════════════════════════════════════════════════════
    */
    // Acheter, c'est donner des francs ; vendre, c'est en recevoir. Tout
    // le reste de cet ecran ne depend que de ce booleen.
    val donneFcfa = state.sens == SensChange.ACHAT

    CarteMontantChange(
        libelle = stringResource(R.string.change_tu_donnes),
        mention = stringResource(
            R.string.change_mention_montant,
            if (donneFcfa) "FCFA" else state.monnaie
        ),
        /*
        LES BORNES ENTRENT DANS LA CARTE. Elles decrivent le champ qui est
        juste au-dessus ; posees dehors, entre deux cartes, elles
        n'appartenaient a rien.
        */
        piedDePage = {
            BornesChange(
                minFcfa = p.minimumPour(state.monnaie, state.fcfaParDollar),
                maxFcfa = p.plafondPour(state.monnaie, state.fcfaParDollar),
                enFcfa = donneFcfa,
                monnaie = state.monnaie,
                prix = state.prix,
                // A la vente, c'est le solde qui plafonne, et il a deja son
                // bouton « Tout » en haut de la carte.
                onMax = if (donneFcfa) ({
                    vm.onMontantRapide(p.plafondPour(state.monnaie, state.fcfaParDollar))
                }) else null
            )
        },
        solde = if (donneFcfa) null else state.solde,
        onTout = if (donneFcfa) null else vm::onToutVendre,
        toutValeur = if (donneFcfa) null else state.solde
            ?.let { state.plusGrandMontantVendable(it) }
            ?.takeIf { it > 0.0 }
            ?.let { crypto(it, state.monnaie) },
        rapides = if (donneFcfa) montantsRapides(p, state.monnaie, state.fcfaParDollar)
                  else emptyList(),
        onRapide = vm::onMontantRapide,
        /*
        LE MONTANT DONNÉ EST CELUI QU'ON TAPE, TEL QU'ON LE TAPE.

        On ne le remet pas en forme pendant la frappe : quelqu'un qui écrit
        « 5000 » et voit apparaître « 5 000 » perd la place de son curseur,
        et la virgule décimale devient impossible à poser.
        */
        montant = state.saisie,
        unite = if (donneFcfa) "FCFA" else state.monnaie,
        avecLogo = !donneFcfa,
        monnaies = if (donneFcfa) emptyList() else p.monnaies,
        onMonnaie = vm::onMonnaie,
        onMontant = vm::onSaisie,
        // AUCUNE DECIMALE DANS LES DEUX SENS : le franc n'a pas de
        // centimes, et a la vente le marquage remplace les decimales
        // tapees. Le clavier ne propose donc pas de virgule — ce que le
        // filtre refuserait de toute facon.
        decimalesMax = 0
    )

    /*
    ═══════════════════════════════════════════════════════════════════════
    LES BORNES, AVANT DE TAPER — ET SURTOUT POUR LA VENTE
    ═══════════════════════════════════════════════════════════════════════

    Le minimum et le plafond existaient déjà, mais ne se découvraient
    QU'APRÈS avoir tapé un montant refusé. Or un refus est le pire moment
    pour annoncer une règle : la personne a fait l'effort, et on le lui
    rend en lui disant qu'il ne valait rien.

    ─── POURQUOI C'ÉTAIT PIRE À LA VENTE ────────────────────────────────

    À l'achat, les trois pastilles de montants courants commencent par le
    minimum : il se voit, même sans être nommé. À la vente, il n'y a pas de
    pastilles — on tape de la crypto, pas des francs — et le seuil est
    exprimé en FRANCS. Quelqu'un qui tape « 5 USDT » n'a aucun moyen de
    savoir si ça passe, parce que la réponse dépend d'un cours.

    On convertit donc les bornes dans l'unité où la personne tape. C'est
    approximatif par nature, puisque le cours bouge, et le « ≈ » le dit
    plutôt que de le cacher.

    ─── CE QUE CETTE LIGNE N'EST PAS ────────────────────────────────────

    Un message d'erreur. Elle reste grise et au même endroit, qu'on soit
    dans les clous ou non ; c'est `NoteEnDeuxTemps`, plus bas, qui parle
    quand ça bloque. Deux éléments qui crient la même chose finiraient par
    ne plus être lus ni l'un ni l'autre.
    ═══════════════════════════════════════════════════════════════════════
    */

    /*
    UN TRAIT QUI SÉPARE, ET LE BOUTON POSÉ DESSUS.

    Depuis que les blocs n'ont plus de contour, rien ne disait où finissait
    « tu donnes » et où commençait « tu reçois » — deux libellés et deux
    nombres empilés sans frontière se lisent comme une seule liste.

    Le trait la rend, et le bouton d'inversion s'y assoit : c'est là que
    l'oeil le cherche entre deux montants, et il y gagne la place qu'il
    occupait seul.
    */
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        HorizontalDivider(color = SurfaceColor)
        Surface(
            onClick = {
                vm.onSens(if (donneFcfa) SensChange.VENTE else SensChange.ACHAT)
            },
            shape = androidx.compose.foundation.shape.CircleShape,
            color = BgPrimary,
            border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.4f)),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.SwapVert,
                    stringResource(R.string.change_inverser),
                    tint = AccentBlue,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    CarteMontantChange(
        libelle = stringResource(R.string.change_tu_recois),
        mention = stringResource(R.string.change_mention_estime),
        /*
        « — » PLUTÔT QUE « 0 », et c'est la même leçon que sur le Swap.

        Zéro est une RÉPONSE : « cet échange ne rendrait rien ». Un cours
        absent, ou un montant pas encore saisi, ne dit rien du tout.
        Afficher zéro ferait croire à un taux nul sur un écran où l'on
        s'engage.
        */
        montant = (if (donneFcfa) state.montantCrypto?.let { crypto(it, state.monnaie) }
                   else state.montantFcfa?.let { fcfa(it) }).orEmpty(),
        unite = if (donneFcfa) state.monnaie else "FCFA",
        avecLogo = donneFcfa,
        monnaies = if (donneFcfa) p.monnaies else emptyList(),
        onMonnaie = vm::onMonnaie,
        onMontant = null,
        approx = if (donneFcfa) state.montantCrypto?.let { c ->
            java.text.NumberFormat.getNumberInstance(java.util.Locale.FRANCE).apply {
                maximumFractionDigits = 2
            }.format(c) + " " + state.monnaie
        } else null
    )

    /*
    ═══════════════════════════════════════════════════════════════════════
    UNE SEULE CARTE DE DÉTAIL, ET LE PRIX DU MARCHÉ DEDANS
    ═══════════════════════════════════════════════════════════════════════

    Il y en avait deux : les détails, puis un récapitulatif. Les deux
    affichaient les frais — exactement la redondance que j'avais reprochée
    aux montants quand ils étaient à la fois dans les cartes et dans les
    détails. Deux endroits qui affichent la même somme finissent par ne
    plus l'afficher pareil.

    ─── LE PRIX DU MARCHÉ *ET* LE TAUX APPLIQUÉ ─────────────────────────

    Une seule ligne annonçait « Prix d'un USDT : 636 FCFA » — le cours
    majoré de la marge. C'est le chiffre qui s'applique, mais ce n'est pas
    un prix de marché, et rien ne le disait.

    Les deux sont là maintenant, et ils répondent à deux questions
    différentes : le cours réel permet de COMPARER à un concurrent, le taux
    appliqué dit ce qu'on obtient ici. L'écart entre eux cesse d'être
    invisible — ce que « 636 » tout seul ne permettait pas.

    C'est la même exigence que depuis le début, poussée d'un cran : rien ne
    doit se découvrir après, y compris ce qu'on gagne.
    ═══════════════════════════════════════════════════════════════════════
    */
    val prix = state.prix
    val taux = state.fcfaParDollar
    if (prix != null && taux != null) {
        val envoye = if (donneFcfa) state.montantFcfa else state.montantCrypto
        val recu = if (donneFcfa) state.montantCrypto else state.montantFcfa
        val fraisFcfa = state.montantCrypto?.let { c ->
            c * kotlin.math.abs(
                (if (donneFcfa) prix.achatFcfa else prix.venteFcfa) - prix.baseFcfa
            )
        }
        val pourcent = TauxFcfa.margeEnPourcent(p.margeFcfaParDollar, taux)
            ?.let { "%.2f".format(it) }

        /*
        ═══════════════════════════════════════════════════════════════════
        LE DÉTAIL SE REPLIE, MAIS LES FRAIS NE SE CACHENT JAMAIS
        ═══════════════════════════════════════════════════════════════════

        Quatre lignes de calcul sous deux montants déjà lisibles, c'est de
        l'encombrement pour les neuf utilisateurs sur dix qui veulent juste
        « combien je reçois ». Elles se replient donc.

        ─── MAIS PAS LES FRAIS, ET C'EST TOUTE LA NUANCE ─────────────────

        J'avais proposé de glisser cette carte SOUS le bouton. C'était une
        faute, et elle venait de regarder la maquette plutôt que la règle :
        un repli sous le bouton permet d'appuyer sans avoir vu la
        commission. Dans un marché où la confiance se gagne lentement,
        c'est exactement ce qui fait fermer une application.

        La carte reste donc AVANT le bouton, et sa ligne toujours visible
        porte les frais — le seul chiffre qu'on pourrait être tenté de
        cacher. Ce qui se replie, c'est le prix du marché, le taux appliqué
        et le récapitulatif : de quoi vérifier, pas de quoi décider.
        ═══════════════════════════════════════════════════════════════════
        */
        var detailOuvert by remember { mutableStateOf(false) }

        Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { detailOuvert = !detailOuvert },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        Ligne(
                            stringResource(R.string.change_marge),
                            fraisFcfa?.let { f ->
                                "${fcfa(f)} FCFA" + (pourcent?.let { " ($it %)" } ?: "")
                            } ?: "—",
                            icone = Icons.Default.Payments
                        )
                    }
                    Icon(
                        if (detailOuvert) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        stringResource(
                            if (detailOuvert) R.string.change_replier else R.string.change_deplier
                        ),
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (!detailOuvert) return@Column

                HorizontalDivider(color = BgPrimary)

                Ligne(
                    stringResource(R.string.change_prix_marche, state.monnaie),
                    "${fcfa(prix.baseFcfa)} FCFA",
                    icone = Icons.Default.ShowChart
                )

                /*
                ═════════════════════════════════════════════════════════
                LE TAUX RÉELLEMENT APPLIQUÉ
                ═════════════════════════════════════════════════════════

                Ce chiffre était calculé, et il partait sur Telegram dans
                le champ `taux` de l'ordre — mais l'utilisateur ne l'avait
                jamais vu. Le changeur connaissait donc un nombre que le
                client ne connaissait pas, alors que c'est LE nombre de
                l'opération.

                On montrait le prix du marché et la marge séparément, en se
                disant que la soustraction se fait. Elle ne se fait pas :
                personne ne soustrait une marge affichée en francs par
                dollar pendant qu'il regarde un prix en francs par USDT.

                ─── POURQUOI PAS À LA PLACE DU PRIX DU MARCHÉ ───────────

                Parce que les deux ne répondent pas à la même question. Le
                prix du marché permet de COMPARER à un concurrent ; le taux
                appliqué dit ce qu'on obtient ici. Garder les deux rend
                l'écart visible, ce qui est tout l'objet de cette carte.
                ═════════════════════════════════════════════════════════
                */
                Ligne(
                    stringResource(R.string.change_taux_applique, state.monnaie),
                    "${fcfa(if (donneFcfa) prix.achatFcfa else prix.venteFcfa)} FCFA",
                    icone = Icons.Default.Calculate
                )

                /*
                LE RESTE N'APPARAÎT QU'AVEC UN MONTANT SAISI.

                Sans montant, il n'y a ni somme envoyée ni frais à
                annoncer : seul le cours est connu. Afficher des lignes
                vides, ou pire des zéros, ferait croire à une opération
                qui ne rendrait rien.
                */
                if (envoye != null && recu != null && fraisFcfa != null) {
                    Ligne(
                        stringResource(R.string.change_recap_envoye),
                        if (donneFcfa) "${fcfa(envoye)} FCFA"
                        else "${crypto(envoye, state.monnaie)} ${state.monnaie}",
                        icone = Icons.Default.NorthEast
                    )
                    /*
                    LES FRAIS NE SONT PLUS ICI : ils sont remontés dans
                    l'en-tête, qui reste visible quand la carte est repliée.
                    Les laisser aussi en bas les afficherait deux fois une
                    fois dépliée — et deux endroits qui portent la même
                    somme finissent par ne plus la porter pareil.

                    Le pourcentage les accompagne là-haut : c'est la seule
                    unité comparable à un échangeur en ligne. « 25 FCFA/$ »
                    est une unité, pas un montant, et quelqu'un qui la lit
                    comme un total se trompe d'un facteur cent.
                    */
                    HorizontalDivider(color = BgPrimary)
                    Ligne(
                        stringResource(R.string.change_recevra),
                        if (donneFcfa) "${crypto(recu, state.monnaie)} ${state.monnaie}"
                        else "${fcfa(recu)} FCFA",
                        icone = Icons.Default.AccountBalanceWallet,
                        vedette = true
                    )
                }
            }
        }
    }

    /*
    LE DELAI EST DEHORS, ET C'EST SA PLACE.

    Il n'est pas un prix : le mettre dans la carte des montants le ferait
    lire comme une ligne de calcul. Dehors, discret, il repond a la seule
    question qui reste quand les chiffres sont lus — combien de temps.

    Il reapparait sur l'ecran de paiement, et ce n'est pas un doublon : on
    ne voit jamais les deux a la fois, et c'est au moment d'attendre qu'on
    a le plus besoin de le savoir.
    */
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Schedule, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.change_delai),
            fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f)
        )
        Text(
            "${p.delaiMinutes} min",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
        )
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    L'AVERTISSEMENT « PAS AVEC VAULTEX » A ETE RETIRE
    ═══════════════════════════════════════════════════════════════════════

    Il disait : « L'echange a lieu entre toi et <nom du changeur>, pas avec
    VaultEx. Ton argent ne passe jamais par l'application. »

    IL ETAIT JUSTE QUAND IL A ETE ECRIT, et il a cesse de l'etre. Le
    changeur n'est pas un tiers : c'est le fondateur de VaultEx. La phrase
    annoncait donc une separation qui n'existe pas, en nommant une personne
    que l'utilisateur ne connait pas — ce qui inquiete au lieu de proteger.

    C'EST LA MEME CORRECTION QUE LE VOCABULAIRE. On vient de retirer « le
    changeur » de toutes les reponses de l'assistance pour la meme raison :
    pour celui qui echange, il n'y a pas un intermediaire quelque part, il
    y a VaultEx. Garder cet encadre aurait contredit, sur l'ecran meme de
    l'operation, ce que l'assistance dit a cote.

    ─── CE QUI N'EST PAS PERDU ────────────────────────────────────────────

    La seconde moitie — « ton argent ne passe jamais par l'application » —
    reste vraie et reste dite ailleurs : l'ecran de paiement donne le
    numero Mobile Money a payer directement, et l'ecran de vente donne
    l'adresse a crediter. Le fait se voit dans le geste, ce qui vaut mieux
    qu'une phrase.

    Si un jour le change passe par un changeur TIERS, cet avertissement
    doit revenir — et il redeviendra vrai le jour ou il reviendra.
    ═══════════════════════════════════════════════════════════════════════
    */

    /*
    UN BLOCAGE EST UN ENCADRE, PAS UNE LIGNE DE TEXTE ROUGE.

    Il explique pourquoi le bouton ne repond pas. Pose en texte nu sous
    l'avertissement, il se confond avec lui et se lit en dernier — alors
    que c'est la seule chose qui dise quoi faire pour avancer.

    Meme traitement que les notes du dessus, couleur differente : l'oeil
    sait deja ou regarder.
    */
    val blocage = state.blocage()
    blocage?.takeIf { state.saisie.isNotBlank() || it == "ferme" || it == "pas_de_rachat" }?.let {
        NoteEnDeuxTemps(
            messageBlocage(it, p, state.monnaie, state.fcfaParDollar),
            AccentRed
        )
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    « LE BOUTON CONTINUER NE PASSE PAS » — ET IL NE DISAIT RIEN
    ═══════════════════════════════════════════════════════════════════════

    Signale a l'usage, sur une VENTE, et c'etait exact.

    Sur un ACHAT, « Continuer » passe a l'etape suivante immediatement :
    tout est calcule sur le telephone.

    Sur une VENTE, non. Le bouton appelle d'abord le relais pour obtenir
    le MONTANT MARQUE — « 6,894231 » et non « 6 » — parce qu'il faut le
    connaitre AVANT d'envoyer quoi que ce soit. Cet appel prend le temps
    d'un aller-retour reseau, et il peut echouer.

    Or cet ecran n'affichait NI l'attente NI l'echec. `confirmer()` posait
    bien un message dans `erreur`, mais aucun endroit du temps 1 ne le
    lisait — il n'etait affiche qu'a l'etape de paiement, ou l'on n'arrive
    justement jamais quand l'appel echoue.

    Resultat : on appuyait, l'ecran ne bougeait pas, et rien n'expliquait
    pourquoi. Le defaut le plus couteux d'une interface n'est pas celui
    qui se voit — c'est celui qui ne se voit pas.

    Trois corrections, et les trois comptent :

      — le message d'erreur s'affiche, en rouge, au-dessus du bouton ;
      — le bouton tourne pendant l'appel, pour qu'on sache qu'il se passe
        quelque chose ;
      — et il s'eteint pendant ce temps. `confirmer()` refusait deja un
        second appel, mais un bouton qui reste actif invite a reappuyer,
        et reappuyer sans effet fait conclure qu'il est casse.
    ═══════════════════════════════════════════════════════════════════════
    */
    state.erreur?.let { Text(it, fontSize = 12.sp, color = AccentRed) }

    Button(
        onClick = vm::confirmer,
        enabled = blocage == null && !state.envoiEnCours,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        if (state.envoiEnCours) {
            CircularProgressIndicator(
                Modifier.size(18.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(stringResource(R.string.change_continuer), fontWeight = FontWeight.Bold)
        // ELLE DIT QUE LE BOUTON FAIT AVANCER. A cette etape rien ne
        // s'engage encore : la fleche annonce une etape suivante la ou un
        // bouton nu laisse craindre une validation.
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Default.ArrowForward, null,
            modifier = Modifier.size(18.dp)
        )
    }

    /*
    L'HISTORIQUE EST SOUS LE BOUTON, ET PAS AU-DESSUS.

    Quelqu'un qui ouvre cet ecran veut, neuf fois sur dix, faire une
    nouvelle operation : le champ de saisie et le prix doivent rester les
    premieres choses qu'il voit. La dixieme fois, il vient chercher une
    reference — et il defile, ce qui est exactement le geste qu'on fait
    quand on cherche quelque chose qu'on sait etre la.
    */
    HistoriqueDemandes(state.historique, copier)
}

// ─── L'historique ───────────────────────────────────────────────────

/*
═══════════════════════════════════════════════════════════════════════════
CE QUE L'UTILISATEUR PEUT CITER
═══════════════════════════════════════════════════════════════════════════

Jusqu'ici, la reference disparaissait avec l'ecran. Quelqu'un qui venait
d'envoyer de l'argent par Orange Money a une personne qu'il ne connait pas
n'avait plus rien a citer si rien n'arrivait.

─── TROIS, PUIS LE RESTE ────────────────────────────────────────────────

Une reclamation porte sur aujourd'hui, ou sur hier. Trois lignes couvrent
ce cas et ne poussent pas le bouton hors de l'ecran ; le reste est a un
appui, pour les rares fois ou l'on remonte plus loin.

─── ON NE PRESENTE JAMAIS CA COMME UNE PREUVE ───────────────────────────

C'est l'utilisateur qui l'a ecrit, depuis son telephone. La note le dit en
clair, et elle ne doit pas etre retiree pour gagner trois lignes : laisser
croire a quelqu'un qu'il detient une preuve, alors qu'il n'a qu'un
pense-bete, serait pire que ne rien afficher.
═══════════════════════════════════════════════════════════════════════════
*/
@Composable
private fun ColumnScope.HistoriqueDemandes(
    demandes: List<com.vaultex.domain.fiat.DemandeChange>,
    copier: (String) -> Unit
) {
    if (demandes.isEmpty()) return

    /*
    ═══════════════════════════════════════════════════════════════════════
    REPLIÉE PAR DÉFAUT, ET CE N'EST PAS QU'UNE QUESTION DE PLACE
    ═══════════════════════════════════════════════════════════════════════

    Trois lignes dépliées en permanence poussaient le bouton « Continuer »
    hors de l'écran sur un téléphone ordinaire — et neuf fois sur dix, on
    ouvre cet écran pour faire une NOUVELLE opération, pas pour relire les
    anciennes.

    La dixième fois, on vient chercher une référence, et on sait qu'elle
    est là : un appui la découvre. C'est le bon partage — ce qui sert
    toujours reste visible, ce qui sert parfois s'ouvre.

    LE NOMBRE EST VISIBLE MÊME REPLIÉE. Sans lui, un bandeau fermé ne dit
    pas s'il cache une demande ou vingt, et on l'ouvre pour rien.
    ═══════════════════════════════════════════════════════════════════════
    */
    var ouvert by remember { mutableStateOf(false) }

    Spacer(Modifier.height(6.dp))
    HorizontalDivider(color = SurfaceColor)

    Row(
        Modifier.fillMaxWidth().clickable { ouvert = !ouvert }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.History, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.change_historique_titre),
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
        )
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(6.dp), color = SurfaceColor) {
            Text(
                demandes.size.toString(),
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary
            )
        }
        Spacer(Modifier.weight(1f))
        Icon(
            if (ouvert) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            null, tint = TextSecondary, modifier = Modifier.size(20.dp)
        )
    }

    if (ouvert) {
        /*
        TOUT, ET NON LES TROIS PREMIÈRES.

        Le « Tout voir (12) » d'avant était un second appui pour atteindre
        ce qu'on cherchait. Maintenant que le bandeau lui-même demande un
        appui, en exiger un deuxième serait une marche de trop : qui
        l'ouvre vient chercher quelque chose.
        */
        demandes.forEach { d -> LigneHistorique(d, copier) }
        Note(stringResource(R.string.change_historique_note), AccentBlue)
    }
}

@Composable
private fun LigneHistorique(
    demande: com.vaultex.domain.fiat.DemandeChange,
    copier: (String) -> Unit
) {
    val fcfaTexte = "${demande.montantFcfa} FCFA"
    val cryptoTexte = "${demande.montantCrypto} ${demande.monnaie}"
    /*
    LA FLECHE DIT LE SENS SANS ETIQUETTE.

    « Achat » et « Vente » se confondent d'un coup d'oeil, et surtout : la
    question n'est jamais « etait-ce un achat », mais « qu'est-ce que j'ai
    donne et qu'est-ce que je devais recevoir ». La fleche y repond
    directement, dans l'ordre ou ca s'est passe.
    */
    val ligne = if (demande.estAchat) "$fcfaTexte → $cryptoTexte" else "$cryptoTexte → $fcfaTexte"

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        demande.reference,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(dateCourte(demande.horodatage), fontSize = 11.sp, color = TextSecondary)
                }
                Text(ligne, fontSize = 12.sp, color = TextSecondary)
            }
            /*
            COPIER LA REFERENCE, pour la coller dans un message au changeur.
            C'est le seul geste utile sur cette ligne : huit caracteres
            melant lettres et chiffres sont exactement ce qu'on recopie mal.
            */
            IconButton(onClick = { copier(demande.reference) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.ContentCopy, stringResource(R.string.copy),
                    tint = AccentBlue, modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/**
 * « 9 oct. a 18 h 52 ».
 *
 * PAS D'ANNEE, et pas de secondes. Une reclamation se fait dans les heures
 * qui suivent : ce qu'on cherche, c'est de distinguer deux demandes du meme
 * jour. Le reste est du bruit sur une ligne qui en a peu de place.
 */
private fun dateCourte(horodatage: Long): String =
    if (horodatage <= 0L) ""
    else java.text.SimpleDateFormat("d MMM · HH'h'mm", java.util.Locale.FRANCE)
        .format(java.util.Date(horodatage))

// ─── Temps 2 : le paiement ──────────────────────────────────────────

@Composable
private fun ColumnScope.EtapePaiement(
    vm: ChangeViewModel,
    state: com.vaultex.ui.viewmodel.ChangeState,
    copier: (String) -> Unit,
    navController: NavController
) {
    val p = state.parametres ?: return
    val achat = state.sens == SensChange.ACHAT
    val aCopier = if (achat) p.numeroMobileMoney else p.adresses[state.monnaie].orEmpty()

    /*
    ═══════════════════════════════════════════════════════════════════════
    ELLE EST BLANCHE ET OPAQUE, PAS BLEU TRANSLUCIDE
    ═══════════════════════════════════════════════════════════════════════

    C'etait `AccentBlue` a huit pour cent. Sur un fond de page presque
    blanc, cette teinte suffisait a poser une carte ; depuis que le haut
    de l'ecran porte un bandeau bleu, elle ne pose plus rien — le degrade
    passe AU TRAVERS, et la carte n'a de bord que la ou le fond change.

    Une carte doit avoir sa propre couleur, pas une nuance de celle qui
    est derriere : c'est ce qui la fait exister au-dessus du bandeau au
    lieu de s'y fondre.

    L'OMBRE REMPLACE LE CONTOUR. Un trait suffisait quand la carte et la
    page ne se distinguaient que par une nuance. Pose sur du bleu, il
    dessinerait un liseré ; l'ombre, elle, dit la meme chose dans les deux
    cas — ce bloc est devant.
    ═══════════════════════════════════════════════════════════════════════
    */
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = SurfaceColor,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            /*
            ═══════════════════════════════════════════════════════════
            LE TITRE PORTE SON RANG, ET LA CARTE SON CACHET
            ═══════════════════════════════════════════════════════════

            LE « 1 ». Cet ecran est un passage : payer, puis declarer.
            Rien ne le disait, et on y arrivait sans savoir combien il
            restait a faire. Un chiffre en tete de la premiere carte
            repond a « ou j'en suis » avant qu'on pose la question.

            Il n'y a pas de « 2 » dessine plus bas, et c'est voulu : la
            seconde etape, c'est le bouton de confirmation — on ne
            numerote pas un bouton.

            « PAIEMENT SECURISE ». Quelqu'un s'apprete a envoyer de
            l'argent a un numero qu'il ne connait pas, depuis une
            application qu'il vient d'installer. La mention ne prouve
            rien par elle-meme ; elle dit que le sujet a ete traite, a
            l'endroit exact ou l'inquietude se presente.

            Elle est discrete — un cadenas et deux mots sur un fond
            pale. Ecrite en gros, elle ferait l'effet inverse : on ne
            rassure pas en insistant.
            ═══════════════════════════════════════════════════════════
            */
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentBlue.copy(alpha = 0.12f)
                ) {
                    Text(
                        "1",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue
                    )
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    stringResource(
                        if (achat) R.string.change_payer_titre else R.string.change_envoyer_titre
                    ),
                    fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = AccentBlue.copy(alpha = 0.10f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock, null,
                            tint = AccentBlue, modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            stringResource(R.string.change_paiement_securise),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentBlue,
                            maxLines = 1
                        )
                    }
                }
            }
            /*
            L'OPERATEUR EST DANS LE LIBELLE, et ce n'est pas decoratif : il
            dit QUELLE application ouvrir. « Numero a crediter » seul laisse
            quelqu'un chercher entre Orange Money, Moov et Wave — et payer
            depuis la mauvaise ne marche pas toujours entre operateurs.
            */
            /*
            UNE ICONE PAR LIGNE, POUR QU'ON RETROUVE SANS RELIRE.

            Cinq lignes de detail se ressemblent toutes a l'oeil : meme
            taille, meme couleur, meme alignement. On les relit donc en
            entier a chaque fois qu'on cherche le numero ou le montant,
            alors qu'on est debout, en train de payer.

            Une pastille teintee par ligne donne a chacune une silhouette.
            C'est le meme mecanisme que les quatre cartes de l'assistance,
            et il n'y a rien de plus a dire : ca se balaie au lieu de se
            lire.
            */
            LigneACopier(
                /*
                L'OPERATEUR N'EST PLUS DANS LE LIBELLE.

                Il y etait pour dire QUELLE application ouvrir — « numero a
                crediter » seul laisse chercher entre Orange Money, Moov et
                Wave. C'etait juste, et ca ne l'est plus : le bouton de
                paiement, deux lignes plus bas, porte le nom de l'operateur
                EN GRAND et a ses couleurs. L'information est mieux dite
                qu'avant.

                Et elle coutait une ligne : « Numero a crediter (Orange
                Money) » passait a la ligne sur un telephone ordinaire, sur
                un ecran qu'on demande a tenir sans defilement.
                */
                libelle = if (achat) stringResource(R.string.change_numero)
                else stringResource(R.string.change_adresse),
                valeur = aCopier,
                icone = if (achat) Icons.Default.Smartphone else Icons.Default.AccountBalanceWallet,
                onCopier = { copier(aCopier) }
            )
            if (achat && p.nomChangeur.isNotBlank()) {
                Ligne(
                    stringResource(R.string.change_beneficiaire),
                    p.nomChangeur,
                    icone = Icons.Default.Person
                )
            }
            /*
            ═══════════════════════════════════════════════════════════
            SUR UNE VENTE, LE MONTANT EXACT EST AUSSI IMPORTANT QUE
            L'ADRESSE
            ═══════════════════════════════════════════════════════════

            Le relais impose « 2,004731 » et non « 2 » : c'est ce qui
            empêche quelqu'un de surveiller l'adresse publique du
            changeur, de voir arriver un versement et de déposer une
            demande pour le réclamer.

            Mais un chiffre pareil se recopie mal, et surtout : il a
            l'air d'une erreur. Quelqu'un qui lit « 2,004731 » sur une
            ligne de détail, entre le délai et la référence, enverra 2 —
            et son versement ne sera jamais reconnu.

            Il sort donc des lignes de détail, passe en chasse fixe sur
            toute la largeur, et porte son propre bouton de copie. Même
            traitement que l'adresse, pour la même raison : c'est une
            chaîne de caractères qu'on ne retape pas.
            ═══════════════════════════════════════════════════════════
            */
            if (achat) {
                /*
                LE MONTANT EN VEDETTE, ET EN BLEU.

                C'est le chiffre qu'on recopie dans Orange Money, et le
                seul de cet ecran dont une erreur coute de l'argent. Il
                etait a la meme taille que le benificiaire et le delai.

                En bleu, et non en vert : le vert de `vedette` dit « ce
                que tu recois », ce qui est juste sur une vente. Ici c'est
                ce qu'on PAIE — annoncer une sortie en vert serait a
                contresens.
                */
                Ligne(
                    stringResource(R.string.change_montant_exact),
                    "${fcfa(state.montantFcfa ?: 0.0)} FCFA",
                    icone = Icons.Default.Payments,
                    vedette = true,
                    teinte = AccentBlue
                )
            } else {
                val montantTexte = crypto(state.montantCrypto ?: 0.0, state.monnaie)
                /*
                LA MEME ICONE QUE SUR UN ACHAT, et pour la meme raison :
                c'est le chiffre dont une erreur coute de l'argent, et il
                doit se retrouver sans relire les lignes voisines.

                Il garde en plus son bouton de copie : « 6,894231 » ne se
                retape pas, et c'est justement sa fraction qui rattache le
                versement a la demande.
                */
                LigneACopier(
                    libelle = stringResource(R.string.change_montant_exact),
                    valeur = "$montantTexte ${state.monnaie}",
                    icone = Icons.Default.Payments,
                    onCopier = { copier(montantTexte) }
                )
                /*
                LE BOUTON COPIE LE NOMBRE SEUL, sans « USDT-BNB » derrière.
                C'est ce qu'on colle dans le champ Montant d'un exchange, et
                une unité collée avec ferait refuser la saisie.
                */
                Note(stringResource(R.string.change_montant_exact_note), AccentOrange)
                /*
                ═══════════════════════════════════════════════════════
                CE QU'ON VA RECEVOIR, PARCE QU'IL A CHANGE
                ═══════════════════════════════════════════════════════

                Signale sur capture, et c'etait le defaut le plus
                incomprehensible de l'ecran.

                Le marquage NE S'AJOUTE PAS au montant tape : il repart de
                sa PARTIE ENTIERE et remplace ses decimales. Quelqu'un qui
                tape 2,432 se voit demander 2,708 — pas 2,432 plus
                quelque chose. Ses decimales ont disparu.

                Et l'ecran precedent annoncait « 1 359 FCFA » pendant que
                celui-ci n'affichait plus aucun franc. On perdait donc les
                deux chiffres a la fois : son montant, et ce qu'il
                rapporte. Devant un ecran qui demande d'envoyer de
                l'argent, c'est le moment ou l'on ferme l'application.

                La somme en francs est recalculee sur le montant REEL —
                2,708 et non 2,432 — donc elle est plus elevee qu'annonce.
                C'est une bonne nouvelle, et elle etait cachee.

                EN VERT ET EN VEDETTE : c'est ce qu'on recoit, et c'est la
                seule sortie d'argent de cet ecran qui n'en soit pas une.
                ═══════════════════════════════════════════════════════
                */
                state.montantFcfa?.let { recu ->
                    Ligne(
                        stringResource(R.string.change_tu_recevras),
                        "${fcfa(recu)} FCFA",
                        icone = Icons.Default.Payments,
                        vedette = true
                    )
                }
            }
            /*
            LA RÉFÉRENCE EST CE QUI RATTACHE UN PAIEMENT À UNE DEMANDE.

            Sans elle, le changeur voit arriver de l'argent sans savoir de
            qui, et une demande sans savoir si elle a été payée. Elle est
            donc mise en avant, et l'écran demande explicitement de la
            porter en motif.
            */
            /*
            LA REFERENCE PORTE SA COPIE, comme le numero. C'est ce qui
            permet de retirer le gros bouton plus bas sans perdre le geste.
            */
            LigneACopier(
                libelle = stringResource(R.string.change_reference),
                valeur = state.reference,
                icone = Icons.Default.Description,
                onCopier = { copier(state.reference) }
            )
            /*
            LE DELAI RESTE, LE PAVE ORANGE PART.

            Un encadre « tu envoies en premier » disait trois choses dont
            deux etaient deja dites par l'ecran lui-meme : l'ordre des
            sections — le paiement, PUIS « une fois le paiement envoye » —
            l'annonce sans un mot.

            La troisieme, en revanche, ne se devine nulle part : combien de
            temps attendre. Sans elle, quelqu'un paie, ne voit rien arriver
            au bout de trois minutes, et appelle. Vingt utilisateurs, et
            c'est une soiree. Elle devient une ligne parmi les montants, la
            ou on la lit sans effort.
            */
            CompteARebours(state.confirmeA, p.delaiMinutes)
            /*
            COPIER LA REFERENCE, PAS LE NUMERO.

            Le bouton copiait l'adresse ou le numero. Mais le numero se
            retape sans peine — huit chiffres — alors que la reference,
            huit caracteres melant lettres et chiffres, est exactement ce
            qu'on recopie mal. Et c'est elle qui rattache le paiement a la
            demande : mal recopiee, le changeur voit de l'argent arriver
            sans savoir de qui.

            Le numero garde son icone de copie, en haut de la carte, a cote
            de lui.
            */
            /*
            ═══════════════════════════════════════════════════════════
            CE QU'ON COPIE N'EST PAS LA MEME CHOSE DANS LES DEUX SENS
            ═══════════════════════════════════════════════════════════

            A L'ACHAT, c'est la référence. Le numéro du changeur se retape
            sans peine — huit chiffres — alors que « VX-UK4GWGQH », huit
            caractères mêlant lettres et chiffres, est exactement ce qu'on
            recopie mal. Et c'est elle qui rattache le paiement à la
            demande.

            A LA VENTE, c'est l'ADRESSE. Trente-quatre caractères qu'on ne
            retape pas : une erreur, et les fonds partent chez quelqu'un
            d'autre, définitivement. Le bouton le plus visible proposait
            quand même la référence — qui, sur un transfert TRON ou EVM, ne
            sert à rien puisqu'il n'y a aucun champ pour la mettre.
            ═══════════════════════════════════════════════════════════
            */
            /*
            ═══════════════════════════════════════════════════════════
            LE GROS BOUTON DE COPIE A ETE RETIRE — SUR UN ACHAT
            ═══════════════════════════════════════════════════════════

            Il faisait doublon : la ligne « Ta reference » porte desormais
            sa propre icone de copie, comme le numero juste au-dessus. Deux
            boutons pour le meme geste, a quatre centimetres d'ecart.

            Et il coutait une soixantaine de points de hauteur sur un ecran
            qu'on demandait a tenir SANS DEFILEMENT. Un bouton qu'on peut
            supprimer sans perdre une action est le premier a partir.

            SUR UNE VENTE IL EST PARTI AUSSI. L'argument qui le gardait
            — une adresse de quarante caracteres merite une cible large —
            etait juste, et il ne tenait pas compte du fait que la ligne
            « Adresse de destination » porte elle aussi sa propre icone
            de copie, a trois centimetres. Deux boutons pour un geste.

            La cible reste donc correcte autrement : l'icone passe de
            trente-deux a quarante points. On garde le geste facile sans
            garder soixante points de hauteur sur un ecran qui en demande
            deux pleins pour se lire.
            ═══════════════════════════════════════════════════════════
            */
        }
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    PAYER SANS RECOPIER LE NUMÉRO
    ═══════════════════════════════════════════════════════════════════════

    Pour payer, l'utilisateur tape « *144*2*1*70123456*10000# » sur son
    téléphone. Il recopie donc à la main huit chiffres de numéro et le
    montant — et c'est là que se perdent les paiements : un chiffre de
    travers, et l'argent part chez un inconnu, définitivement.

    Ce bouton écrit la chaîne à sa place et ouvre le composeur. Il n'appelle
    pas : l'utilisateur voit le code, appuie lui-même, et termine par son
    code secret sur son clavier — qui n'entre jamais dans cette chaîne, ni
    dans cette application.

    ─── IL N'APPARAÎT QUE SI LE MODÈLE EST SÛR ──────────────────────────

    Pas de modèle réglé, ou un modèle douteux, et il n'y a PAS de bouton :
    l'écran garde le numéro à copier, ce qui marche depuis le premier jour.
    Un bouton absent est une gêne ; un bouton construit sur un modèle mal
    réglé enverrait quelqu'un valider un transfert vers un numéro tronqué.
    ═══════════════════════════════════════════════════════════════════════
    */

    if (achat) {
        val code = com.vaultex.domain.fiat.CodeUssd.composer(
            p.ussdModele, p.numeroMobileMoney, state.montantFcfa ?: 0.0
        )
        if (code != null) {
            val contexte = androidx.compose.ui.platform.LocalContext.current
            Button(
                onClick = {
                    /*
                    ACTION_DIAL, ET JAMAIS ACTION_CALL.

                    ACTION_CALL lancerait l'appel tout seul — il demande la
                    permission téléphone, et surtout il ferait partir une
                    opération d'argent sans que l'utilisateur ait vu le
                    code. Il doit le lire avant d'appuyer : c'est sa
                    dernière occasion de voir que le montant est le bon.

                    ACTION_DIAL n'exige aucune permission, et n'a besoin
                    d'aucune vérification de disponibilité : tout téléphone
                    a un composeur. Le `runCatching` ne couvre que le cas
                    d'un appareil sans module téléphonique — une tablette.
                    */
                    runCatching {
                        contexte.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_DIAL,
                                android.net.Uri.parse(
                                    "tel:" + com.vaultex.domain.fiat.CodeUssd.pourUriTel(code)
                                )
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                /*
                AUX COULEURS DE L'OPERATEUR, ET PAS A CELLES DES ALERTES.

                `OrangeMoney` est une constante de marque, fixe, posee a
                cote de celles des chaines. `AccentOrange` aurait fait lire
                une mise en garde la ou il y a un geste a faire — et aurait
                banalise la couleur qui porte « ce versement a deja servi »
                ou « on ne te demandera jamais ta phrase secrete ».

                Le texte est blanc : sur ce orange, le bleu ou le gris de
                `onPrimary` ne tiendrait pas le contraste.
                */
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrangeMoney,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                /*
                ═══════════════════════════════════════════════════════
                LE VRAI LOGO, ET NON UN PAVE NUMERIQUE
                ═══════════════════════════════════════════════════════

                C'etait l'icone « clavier ». Elle decrivait le MOYEN —
                un code a composer — la ou il faut dire QUELLE
                application va s'ouvrir. Quelqu'un debout dans la rue
                cherche la marque qu'il connait, pas la description du
                mecanisme.

                `Image` et non `Icon` : `Icon` teinte ce qu'on lui donne
                d'une seule couleur, ce qui effacerait le logo.

                ─── LE FICHIER NE GARDE QUE LA MARQUE ────────────────

                Celui qui a ete fourni portait la marque ET le mot
                « Orange Money », sur un carre de 148 points. A
                vingt-quatre, le mot devenait une texture — et il
                doublait celui que le bouton ecrit en toutes lettres a
                trois centimetres.

                Il est donc recadre sur les deux fleches seules, mesurees
                dans l'image : elles occupent les colonnes 16 a 75, le mot
                commence a 82. Le carre s'arrete avant, puis remonte a 144
                points pour rester net jusqu'en xxxhdpi.

                LE FOND NOIR EST GARDE, et ce n'est pas un oubli : une des
                deux fleches est orange, et elle disparaitrait sur
                l'orange du bouton. Le noir est le contenant prevu par la
                marque, et il fait ici office de pastille.
                ═══════════════════════════════════════════════════════
                */
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(
                        R.drawable.logo_orange_money
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.change_ussd_bouton, p.operateur),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                // IL DIT QUE L'ON SORT DE L'APPLICATION. Ce bouton ouvre le
                // composeur du telephone : le chevron annonce un depart, la
                // ou un bouton nu laisse croire qu'on reste.
                Icon(
                    Icons.Default.ChevronRight, null,
                    modifier = Modifier.size(20.dp)
                )
            }
            /*
            LE CODE EST AFFICHÉ EN CLAIR SOUS LE BOUTON.

            Deux raisons. Il reste composable à la main si le composeur
            s'ouvre vide — ce qui arrive sur certaines surcouches
            constructeur. Et surtout : l'utilisateur voit ce qu'il va
            valider AVANT de l'envoyer, ce qui est la seule vérification
            qui compte sur une opération irréversible.
            */
            /*
            ET IL SE COPIE, CE QUI MANQUAIT.

            Le commentaire ci-dessus dit que le code reste composable a la
            main quand le composeur s'ouvre vide — ce qui arrive sur
            certaines surcouches constructeur. Mais il fallait alors le
            RECOPIER CARACTERE PAR CARACTERE depuis l'ecran, avec ses
            etoiles, ses diese et un montant au franc pres.

            Une raison d'etre qu'on ne peut pas suivre n'en est pas une.
            Le bouton de copie fait tenir la promesse du commentaire.
            */
            /*
            LE CODE A SON PROPRE BLOC, ET IL EST BLEU.

            Il etait pose a nu, en gris, entre le bouton orange et
            l'encadre bleu de la note — c'est-a-dire dans le seul
            intervalle de l'ecran ou rien n'etait dessine. Il y avait
            l'air d'une legende du bouton, alors que c'est une valeur
            qu'on copie.

            Le meme fond que la note qui suit, et la couleur de ce qui
            s'actionne. Il se lit maintenant comme ce qu'il est.
            */
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AccentBlue.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        code,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = AccentBlue
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { copier(code) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.ContentCopy, stringResource(R.string.copy),
                            tint = AccentBlue, modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            /*
            ═══════════════════════════════════════════════════════════
            LA NOTE SOUS LE BOUTON A ETE RETIREE
            ═══════════════════════════════════════════════════════════

            Elle disait : « Ton telephone va ouvrir le composeur avec le
            code deja ecrit. Verifie le montant, appuie sur Appeler, et
            termine avec ton code secret — VaultEx ne le voit jamais. »

            TROIS PHRASES POUR DECRIRE CE QUI SE PASSE DE TOUTE FACON. Le
            composeur s'ouvre, le code y est, on appuie sur Appeler : on
            le decouvre en une seconde, et on ne relit pas une explication
            de ce qu'on vient de voir.

            Elle s'ajoutait a un ecran qui portait deja le numero, le
            beneficiaire, le montant, la reference, le compte a rebours,
            le code USSD en clair et une seconde note. A ce niveau
            d'empilement, chaque encadre supplementaire fait baisser la
            lecture des autres — y compris celle qui compte vraiment, la
            reference a conserver.

            CE QUI ETAIT DOMMAGE D'Y PERDRE — « VaultEx ne le voit
            jamais », a propos du code secret Orange Money — n'a pas
            besoin d'etre ecrit : le code ne se tape pas dans VaultEx. Il
            se tape dans le composeur du telephone, apres que
            l'application a ete quittee. Le geste le dit mieux que la
            phrase.
            ═══════════════════════════════════════════════════════════
            */
        }
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    LE MOTIF N'EXISTE PAS TOUJOURS — Y COMPRIS SUR UN ACHAT
    ═══════════════════════════════════════════════════════════════════════

    Cette note a déjà été corrigée une fois : elle disait « mets VX-… en
    motif du paiement » sur une VENTE, où un transfert TRON ou EVM n'a
    aucun champ où l'écrire. Elle a été restreinte aux achats.

    ─── ET ELLE ÉTAIT ENCORE FAUSSE LÀ ─────────────────────────────────

    Vu sur un vrai écran, juste sous le bouton « Payer avec Orange
    Money » : LE CODE USSD N'A PAS DE CHAMP MOTIF. L'écran demandait donc
    quelque chose d'impossible par le moyen qu'il venait lui-même
    d'offrir, deux centimètres plus haut.

    ─── CE QUI RATTACHE VRAIMENT LE PAIEMENT ───────────────────────────

    Le relais rapproche par RÉFÉRENCE quand il y en a une, et à défaut par
    MONTANT ET NUMÉRO. Le second chemin existe précisément pour les
    paiements sans motif — c'est-à-dire pour la plupart.

    La note dit donc les deux cas, et cesse d'annoncer une catastrophe
    qui n'arrive pas.

    ─── ET ELLE LES DIT EN DEUX LIGNES, PLUS EN QUATRE ─────────────────

    Elle expliquait ce qu'est un champ « motif », puis que le code USSD
    n'en a pas, puis ce qui rattache le paiement à défaut. Trois temps
    pour une consigne qui en vaut un : garde la référence, mets-la en
    motif s'il y en a un, sinon le montant et le numéro suffisent.

    Sur un écran qui porte déjà le numéro, le bénéficiaire, le montant, la
    référence, le compte à rebours et le code USSD, une note longue est
    une note sautée — et c'est celle-ci qui dit quoi garder.
    ═══════════════════════════════════════════════════════════════════════
    */
    if (achat) {
        /*
        LA REFERENCE N'EST PLUS REPETEE DANS LA NOTE.

        Elle y etait en toutes lettres — « Garde ta reference VX-ZV4EPAGW »
        — alors qu'elle est ecrite deux lignes plus haut, en gras, avec son
        bouton de copie. La redire coutait une ligne de texte sur un ecran
        qu'on demande a tenir sans defilement, et n'apprenait rien.

        Ce que la note dit encore, et qu'aucun autre endroit ne dit : OU la
        mettre, et ce qui rattache le paiement quand il n'y a pas de champ
        motif.
        */
        Note(stringResource(R.string.change_motif), AccentBlue)
    }

    /*
    LA VERIFICATION VIENT AVANT LES CHAMPS, ET C'EST L'ORDRE DU REGARD.

    Sur une vente, la question de l'utilisateur a cet instant n'est pas
    « que dois-je saisir » : c'est « est-ce que mon argent est bien
    parti ». Il vient d'envoyer de la crypto, c'est irreversible, et il
    attend. La reponse doit etre la premiere chose qu'il lit.
    */
    /*
    ═══════════════════════════════════════════════════════════════════════
    LA CARTE DE RECHERCHE A ETE RETIREE DE LA VENTE
    ═══════════════════════════════════════════════════════════════════════

    Elle existait pour un parcours qui n'existe plus : copier l'adresse,
    sortir, envoyer depuis un exchange, revenir, et attendre que le relais
    voie le versement apparaitre sur la chaine. D'ou ses mots — « un
    retrait depuis une plateforme d'echange peut mettre une heure » — et
    ses deux sorties, « Reessayer » et « Confirmer quand meme ».

    TOUT SE PASSE DANS VAULTEX MAINTENANT. La crypto part d'ici, son hash
    est connu a la seconde ou elle est diffusee, et la demande arrive chez
    le changeur sans que personne attende quoi que ce soit.

    Une carte qui annonce une recherche, propose de reessayer et parle
    d'une autre plateforme ne decrit plus rien de ce qui se passe. Elle
    inquiete, et elle invite a un geste — « Confirmer quand meme » — qui
    n'a plus d'objet.

    LA RECHERCHE, ELLE, CONTINUE EN SILENCE. Elle sert encore : si elle
    trouve le versement, le bouton de depot s'allume. Mais elle ne rend
    plus compte de son attente.
    ═══════════════════════════════════════════════════════════════════════
    */

    /*
    « UNE FOIS LE PAIEMENT ENVOYE » PART, SUR UN ACHAT.

    Le titre annonçait deux champs qui s'annoncent eux-memes : ils portent
    « Reference du paiement » et « Numero Mobile Money utilise pour le
    paiement », et ils sont sous le bouton de paiement. Quand les lire est
    deja evident, le dire coute une ligne pour rien.

    SUR UNE VENTE IL EST PARTI AUSSI, et pour la meme raison qu'a
    l'achat : le champ qu'il annonce porte « Numero Mobile Money OU
    RECEVOIR ». Le titre disait « Ou recevoir tes francs » — les memes
    mots, trois centimetres plus haut.

    Il avait un sens quand le libelle du champ ne disait que « Numero
    Mobile Money ». Il l'a perdu le jour ou ce libelle a ete precise, et
    il est reste.
    */

    /*
    LA REFERENCE MOBILE MONEY N'A DE SENS QUE SUR UN ACHAT.

    Sur une vente, il n'y a eu aucun paiement Mobile Money : c'est le
    changeur qui va en faire un. Demander « la reference du paiement » a
    quelqu'un qui n'a rien paye le laisse chercher dans ses SMS une chose
    qui n'existe pas — et le bouton restait eteint tant qu'il n'avait pas
    invente quelque chose.
    */
    if (achat) {
        // Traduit une fois ici : la fonction d'etat rend une cause, pas un
        // texte — elle n'a pas a connaitre les ressources.
        val probleme = when (state.problemeReference()) {
            "ref_demande" -> R.string.change_ref_probleme_demande
            "courte" -> R.string.change_ref_probleme_courte
            "longue" -> R.string.change_ref_probleme_longue
            "sans_chiffre" -> R.string.change_ref_probleme_sans_chiffre
            else -> null
        }
        ChampChange(
            valeur = state.referencePaiement,
            onValeur = vm::onReferencePaiement,
            libelle = stringResource(R.string.change_ref_paiement),
            exemple = stringResource(R.string.change_ref_paiement_exemple),
            aide = stringResource(R.string.change_ref_paiement_aide),
            icone = Icons.Default.Description,
            clavier = androidx.compose.ui.text.input.KeyboardType.Text,
            erreur = probleme?.let { stringResource(it) }
        )
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    LE MEME CHAMP, DEUX QUESTIONS OPPOSEES
    ═══════════════════════════════════════════════════════════════════════

    A l'achat, c'est le numero qui A PAYE : il sert au changeur a
    retrouver l'operation dans son releve.

    A la vente, c'est le numero qui VA RECEVOIR. Sans lui, le changeur lit
    « ENVOYER 5 000 FCFA au numero du client » — sans numero. Il n'a aucun
    moyen de payer, et l'utilisateur a deja envoye sa crypto.

    Un seul libelle pour les deux faisait que ce champ passait pour
    accessoire dans le sens ou il est indispensable.
    ═══════════════════════════════════════════════════════════════════════
    */
    /*
    ═══════════════════════════════════════════════════════════════════════
    SUR UNE VENTE, LE NUMERO N'EST PLUS DEMANDE ICI
    ═══════════════════════════════════════════════════════════════════════

    Il l'etait aux DEUX etapes : sur cet ecran, et sur l'ecran d'envoi.
    Dans le parcours normal, celui-ci ne servait a rien — c'est le numero
    tape sur l'ecran d'envoi qui est retenu, et cet ecran-ci le relit pour
    deposer la demande.

    Deux champs pour une seule information, dont un inutile neuf fois sur
    dix. Il reste donc UN champ dans toute la vente, la ou l'on appuie.

    ─── IL REVIENT QUAND IL MANQUE VRAIMENT ────────────────────────────

    Un cas le demande encore : quelque chose a ete envoye — le relais a
    trouve un versement, ou un envoi a reussi dont le depot a echoue — et
    aucun numero n'est connu. Ca arrive si l'envoi est parti avant qu'on
    arrive ici.

    Sans ce champ, le bouton de depot resterait eteint sans rien a
    remplir : des fonds partis, et aucun moyen de prevenir qui que ce
    soit. Il n'apparait donc que la, et il disparait des qu'il est
    rempli.

    SUR UN ACHAT IL RESTE TOUJOURS. Il n'y a pas d'ecran d'envoi : c'est
    le seul endroit ou declarer le numero qui a paye.
    ═══════════════════════════════════════════════════════════════════════
    */
    /*
    ─── IL EST REVENU, ET IL NE REPARTIRA PAS ──────────────────────────

    Je l'avais retire d'ici pour ne le demander qu'une fois, sur l'ecran
    d'envoi. C'etait elegant et c'etait fragile : le SEUL endroit ou
    donner son numero se trouvait derriere un ecran qui peut ne pas
    s'ouvrir — et quand il ne s'est pas ouvert, il n'y avait plus aucun
    moyen de dire ou l'on veut etre paye.

    CE NUMERO APPARTIENT A LA VENTE, pas a l'envoi. Il dit ou le changeur
    doit payer ; l'envoi, lui, ne fait que deplacer de la crypto. Le
    collecter dans le parcours qui en a besoin, avant d'en sortir, c'est
    aussi la seule facon de ne jamais partir sans lui.

    L'ecran d'envoi le relit et l'affiche avant le PIN — en gros, groupe
    par deux — mais ne le demande plus.
    */
    if (true) {
    ChampChange(
        valeur = state.telephone,
        onValeur = vm::onTelephone,
        libelle = stringResource(
            if (achat) R.string.change_telephone else R.string.change_telephone_vente
        ),
        exemple = stringResource(
            if (achat) R.string.change_telephone_exemple
            else R.string.change_telephone_vente_exemple
        ),
        aide = stringResource(
            if (achat) R.string.change_telephone_aide
            else R.string.change_telephone_vente_aide
        ),
        icone = Icons.Default.Smartphone,
        clavier = androidx.compose.ui.text.input.KeyboardType.Phone,
        /*
        SUR UNE VENTE, UN NUMERO INCOMPLET ETEINT LE BOUTON : la raison
        doit se voir a l'endroit ou on la corrige. Sur un achat il ne
        bloque rien tant qu'une reference est donnee, donc on ne l'accuse
        pas.
        */
        erreur = if (!achat && state.telephone.isNotBlank() && !state.telephoneValide) {
            stringResource(R.string.change_telephone_vente_aide)
        } else null
    )

    /*
    ─── ET SA CONFIRMATION, QUAND IL EST NOUVEAU ───────────────────────

    La meme regle que sur l'ecran d'envoi : elle n'apparait qu'a la
    premiere saisie, ou le jour ou l'on change de numero. Pre-rempli
    depuis la derniere vente, il ne redemande rien.

    Les deux chemins — envoyer depuis VaultEx, ou copier l'adresse et
    envoyer d'ailleurs — demandent le meme numero. Un seul des deux qui
    le fasse confirmer serait le genre d'incoherence qu'on remarque sans
    savoir la nommer.
    */
    if (!achat && state.confirmationNumeroDemandee) {
        ChampChange(
            valeur = state.telephoneConfirmation,
            onValeur = vm::onTelephoneConfirmation,
            libelle = stringResource(R.string.change_numero_confirme_libelle),
            exemple = stringResource(R.string.change_telephone_vente_exemple),
            aide = stringResource(
                if (state.numerosDifferents) R.string.change_numero_different
                else R.string.change_numero_confirme
            ),
            icone = Icons.Default.Smartphone,
            clavier = androidx.compose.ui.text.input.KeyboardType.Phone,
            erreur = if (state.numerosDifferents) {
                stringResource(R.string.change_numero_different)
            } else null
        )
    }
    }

    /*
    ═══════════════════════════════════════════════════════════
    ENVOYER DEPUIS VAULTEX, AU LIEU DE SORTIR ET DE REVENIR
    ═══════════════════════════════════════════════════════════

    « Copie l'adresse et va envoyer » suppose que la crypto est
    AILLEURS — sur un exchange, dans un autre portefeuille. Quand
    elle est deja dans VaultEx, on demandait a quelqu'un de sortir
    de l'application pour y revenir.

    Ce bouton ouvre l'envoi avec tout de pose : l'adresse du
    changeur, la chaine, et le montant marque au millieme. Il
    supprime la faute la plus couteuse de cet ecran — recopier
    « 2,004731 » a la main dans une autre application.

    ─── IL N'APPARAIT QUE S'IL PEUT ABOUTIR ───────────────────

    Seulement quand le solde VaultEx couvre le montant marque.
    Sinon la crypto est ailleurs, et proposer un envoi qui
    echouera remplace un aller-retour par une impasse.

    ─── IL NOMME L'OPERATION, PAS LE MECANISME ────────────────

    Deux libelles ont precede celui-ci, et chacun disait la mauvaise
    chose.

    « Envoyer depuis VaultEx » datait du temps ou il y avait DEUX
    chemins — envoyer d'ici, ou copier l'adresse et envoyer depuis
    un exchange. Il fallait alors dire d'OU part l'envoi. Depuis
    qu'il n'y en a plus qu'un, nommer l'origine suggere un choix qui
    n'existe pas — et on est deja dans VaultEx.

    « Envoyer la crypto » decrivait le MECANISME. Or celui qui est
    sur cet ecran ne vient pas envoyer : il vient VENDRE. Le
    transfert est ce que l'operation exige, pas ce qu'il a demande.

    La carte au-dessus, elle, garde « Envoie la crypto » : elle
    decrit ce qui va se passer, et il faut qu'il sache que sa crypto
    part. Le titre dit le mecanisme, le bouton nomme l'intention.

    ─── ET IL NE S'AFFICHAIT PAS ──────────────────────────────

    Ce bloc etait ecrit DANS le `if (achat)` du code USSD, donc
    sous la condition `achat && !achat`. Il n'a jamais pu
    apparaitre. Vu sur capture, avec la bonne question : « rien
    n'a ete, on n'avance pas » — et pour cause, il ne restait
    aucun chemin. « Confirmer l'envoi » attend, lui, que la chaine
    montre le versement, ce qui ne peut pas arriver tant que
    personne n'a envoye.

    ─── CE QUI SE PASSE AU RETOUR ─────────────────────────────

    L'envoi note sa reference et son hash sur le disque, cet ecran
    les relit a l'ouverture et depose la demande tout seul. Plus
    d'attente de la chaine, plus de bouton de confirmation :
    l'authentification de l'envoi EST la confirmation.
    ═══════════════════════════════════════════════════════════
    */
    if (!achat) {
        val marque = com.vaultex.domain.fiat.SaisieMontant.valeur(state.saisie)
        val detenu = state.solde
        /*
        ═══════════════════════════════════════════════════════════════════
        UNE FOIS LA CRYPTO PARTIE, IL DISPARAIT
        ═══════════════════════════════════════════════════════════════════

        `txidSaisi` ne porte un hash que si un envoi a REUSSI. S'il est
        la, c'est que la crypto est diffusee et que seul le depot de la
        demande reste a faire.

        Laisser le bouton proposerait d'envoyer une SECONDE FOIS le meme
        montant, a la meme adresse — et rien ne le rattraperait : le
        relais ne reconnaitrait qu'un versement, et l'autre serait perdu.

        C'est le pire geste que cet ecran puisse offrir, et il ne le
        pouvait que depuis que le depot peut echouer apres un envoi
        reussi. Le rattrapage est le bouton de confirmation, plus bas.
        ═══════════════════════════════════════════════════════════════════
        */
        if (state.txidSaisi.isNotBlank()) {
            Note(stringResource(R.string.change_crypto_partie), AccentGreen)
        } else if (marque != null && detenu != null && detenu >= marque) {
            /*
            ═══════════════════════════════════════════════════════════
            C'EST LUI, L'ACTION PRINCIPALE
            ═══════════════════════════════════════════════════════════

            Il etait en contour, discret, au-dessus d'un « Confirmer
            l'envoi » pleine largeur et gris. Le gris occupait la place ou
            l'on cherche le bouton qui fait avancer, et il est eteint
            puisque rien n'a ete envoye.

            Signale sur capture, deux fois : « on n'avance toujours pas ».
            On regardait le mauvais bouton, et l'ecran le designait.

            Celui-ci est donc PLEIN et bleu — c'est le geste de cet ecran.
            L'autre descend au rang qu'il occupe vraiment : un recours,
            pour qui a envoye depuis ailleurs.
            */
            Button(
                /*
                ETEINT TANT QUE LE NUMERO N'EST PAS DONNE ET CONFIRME.

                La crypto part de facon irreversible ; sans numero, le
                message au changeur dit « envoyer 1 527 FCFA au numero du
                client » sans numero. L'exiger AVANT est la seule place
                qui tienne : apres, les fonds sont partis.
                */
                enabled = state.telephonePret,
                onClick = {
                    /*
                    ═══════════════════════════════════════════════════
                    UNE NAVIGATION QUI ECHOUE NE DOIT PAS FERMER
                    L'APPLICATION
                    ═══════════════════════════════════════════════════

                    Signale sur capture : « je clique sur vendre, l'app se
                    ferme ». Un `navigate` qui ne resout pas sa route leve,
                    et rien ne l'attrape — l'application disparait, et il ne
                    reste RIEN a lire : ni a celui qui vend, ni a celui qui
                    cherche la cause.

                    Ce n'est pas masquer le defaut. C'est le rendre
                    lisible : le message nomme l'exception, s'affiche sous
                    le bouton, et l'ecran reste ouvert avec la demande
                    intacte — le prix est encore garanti, on peut
                    reessayer.

                    Et la demande n'est pas perdue : la trace est sur le
                    disque depuis la confirmation.
                    */
                    runCatching {
                        navController.navigate(
                            Routes.sendPourVente(
                                adresse = aCopier,
                                // LE MONTANT EXACT, avec un POINT : c'est
                                // celui que lira la couche reseau, pas
                                // celui qu'on affiche.
                                montant = state.saisie.replace(',', '.'),
                                jeton = state.monnaie,
                                reference = state.reference
                            )
                        )
                    }.onFailure { e ->
                        vm.signalerErreur(
                            "Impossible d'ouvrir l'écran d'envoi : " +
                                (e.message ?: e::class.java.simpleName)
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.change_vendre_maintenant),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp))
            }
        }
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    LA CONFIRMATION DU NUMERO PAR SMS A ETE RETIREE
    ═══════════════════════════════════════════════════════════════════════

    Elle faisait envoyer sa reference par SMS au numero du changeur, qui
    lisait alors « ✔ confirme par SMS » sur la ligne du numero. Ce qu'elle
    protegeait est reel : un chiffre de travers dans son propre numero
    envoie les francs chez un inconnu, sans recours.

    MAIS ELLE ETAIT FACULTATIVE ET PAYANTE. Un SMS coute, et presque
    personne ne l'envoyait — le bloc occupait donc l'ecran pour une
    protection que peu de gens prenaient.

    ─── CE QUI LA REMPLACE, ET QUI VAUT MIEUX ──────────────────────────

    Le numero est RETENU d'une operation a l'autre. On le tape une fois,
    il est pre-rempli ensuite. Le risque de faute de frappe tombe a la
    premiere vente au lieu de revenir a chacune — et ca retire un bloc au
    lieu d'en ajouter un.

    Si le SMS doit revenir un jour, c'est `/change/otp` cote relais qui le
    porte, et il n'a pas ete touche.
    ═══════════════════════════════════════════════════════════════════════
    */
    /*
    ═══════════════════════════════════════════════════════════════════════
    SUR UN ACHAT, LA VÉRIFICATION VIENT APRÈS LES CHAMPS
    ═══════════════════════════════════════════════════════════════════════

    C'est l'inverse de la vente, et c'est l'ordre du regard dans les deux
    cas.

    À LA VENTE, la question à cet instant est « est-ce que mon argent est
    bien parti ». Il n'y a rien à remplir pour y répondre : la carte vient
    donc en premier.

    À L'ACHAT, la réponse DÉPEND de ce qu'on vient de taper — la référence
    recopiée, le numéro. Un verdict posé au-dessus de ces champs porterait
    sur un formulaire vide, et dirait « aucun paiement ne correspond » à
    quelqu'un qui n'a encore rien déclaré. Mise en dessous, elle se lit
    pour ce qu'elle est : la réponse à ce qu'on vient d'écrire.

    Et elle est JUSTE AU-DESSUS DU BOUTON, parce que c'est là qu'elle
    change quelque chose — un ❌ y éteint le bouton, et il faut que les
    deux soient dans le même regard.

    ─── ELLE N'EXISTE PAS AVANT QU'ON AIT DÉCLARÉ QUELQUE CHOSE ────────

    Tant que ni la référence ni le numéro ne sont remplis, il n'y a rien à
    chercher — et la carte afficherait « vérification impossible » sur un
    formulaire vide, ce qui est une accusation gratuite doublée d'un
    mensonge : on n'a pas essayé.

    Elle apparaît donc à la première référence tapée, c'est-à-dire au
    moment où elle a un verdict à rendre.
    ═══════════════════════════════════════════════════════════════════════
    */
    /*
    ─── ET SUR UNE VENTE, DES QU'UN HASH EST EN MAIN ────────────────────

    Elle n'existait pas du tout a la vente, et l'objection ci-dessus
    expliquait pourquoi : sur un formulaire vide, elle accuserait sans
    avoir cherche.

    Mais un hash en main n'est pas un formulaire vide. C'est une
    transaction diffusee, et le relais la cherche vraiment — pendant les
    quarante-cinq secondes ou la demande attend que la chaine parle avant
    de partir chez le changeur.

    Sans cette carte, ces quarante-cinq secondes etaient un ecran immobile
    juste apres un code PIN. Rien ne tournait, rien ne disait qu'on
    attendait quelque chose, et le reflexe est alors de rappuyer ou de
    fermer — exactement au moment ou il ne faut faire ni l'un ni l'autre.
    */
    if (achat && (state.referencePaiementUtile.isNotBlank() || state.telephoneValide)) {
        CarteVerification(vm, state)
    } else if (!achat && state.txidSaisi.isNotBlank()) {
        CarteVerification(vm, state)
    }

    state.erreur?.let { Text(it, fontSize = 12.sp, color = AccentRed) }

    /*
    ═══════════════════════════════════════════════════════════════════════
    SUR UNE VENTE, IL N'APPARAIT QUE QUAND IL PEUT SERVIR
    ═══════════════════════════════════════════════════════════════════════

    Il etait toujours la, pleine largeur et gris, a la place ou l'on
    cherche le bouton qui fait avancer. Eteint, puisqu'il attend que la
    chaine montre un versement que personne n'a encore fait.

    Signale deux fois sur capture : « on n'avance toujours pas ». La
    reponse etait au-dessus — « Envoyer depuis VaultEx » — mais le gros
    bouton gris disait le contraire.

    Il suit donc la meme regle que la carte de verification : il apparait
    sur un verdict qui dit quelque chose, ou au bout des trois minutes,
    quand quelqu'un a pu envoyer depuis ailleurs. Avant cela, il n'a rien
    a confirmer.

    SUR UN ACHAT IL RESTE TOUJOURS LA. Le paiement se fait hors de
    l'application, par Orange Money, et c'est ce bouton qui le declare :
    il n'y a pas d'autre chemin a designer.
    ═══════════════════════════════════════════════════════════════════════
    */
    /*
    ═══════════════════════════════════════════════════════════════════════
    SUR UNE VENTE, IL NE SERT QUE DE RATTRAPAGE
    ═══════════════════════════════════════════════════════════════════════

    Le chemin normal ne passe plus par lui : on appuie sur « Envoyer
    depuis VaultEx », la crypto part, et la demande arrive chez le
    changeur toute seule au retour. Ce bouton n'a alors rien a faire.

    IL RESTE POUR DEUX CAS, ET ILS COMPTENT TOUS LES DEUX.

    Le depot a echoue — le reseau a coupe entre la diffusion et l'appel
    au relais. La crypto est partie, son hash est en main, et il faut
    pouvoir reessayer. Sans ce bouton, quelqu'un resterait avec ses fonds
    envoyes et aucun moyen de prevenir qui que ce soit.

    Le relais a trouve le versement par lui-meme. Ca arrive si l'envoi a
    ete fait avant d'arriver ici, ou depuis ailleurs malgre tout.

    DANS LES DEUX CAS, QUELQUE CHOSE A ETE ENVOYE. C'est la condition :
    un hash en main, ou un versement confirme. Jamais « au cas ou ».
    ═══════════════════════════════════════════════════════════════════════
    */
    val rattrapageVente = state.txidSaisi.isNotBlank() ||
        state.verification?.estVert == true
    if (achat || rattrapageVente) {
    Button(
        onClick = vm::transmettre,
        enabled = state.peutTransmettre(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        if (state.envoiEnCours) {
            CircularProgressIndicator(
                Modifier.size(18.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(10.dp))
        }
        /*
        LE BOUTON DIT CE QU'IL ENVOIE, et dans les deux sens il dit la
        meme chose : « confirmer ». A l'achat on confirme un PAIEMENT, a
        la vente un ENVOI — c'est le geste que l'utilisateur vient de
        faire, et c'est ce qu'il atteste en appuyant.

        Quand la chaine ou le releve l'a deja confirme, le libelle le dit
        avant qu'il appuie : c'est la derniere chose utile a lui
        apprendre.

        ─── POURQUOI PLUS « TRANSMETTRE » ────────────────────────────

        Le mot est administratif, et personne ne l'emploie en parlant. Il
        avait un merite — il se distinguait d'« envoyer », qui dans toute
        l'application veut dire deplacer de l'argent — et « confirmer » le
        garde : on ne confirme pas des fonds, on confirme un fait.

        « Transmettre quand meme », juste a cote, suit : deux boutons
        voisins qui nomment differemment la meme action feraient chercher
        une difference qui n'existe pas.

        Les TEXTES explicatifs, eux, gardent « transmettre ». La ils ne
        nomment pas une commande, ils decrivent ce qui se passe — et la
        demande part bien chez quelqu'un.
        */
        Text(
            stringResource(
                when {
                    state.verification?.estVert == true && achat ->
                        R.string.change_jai_paye_verifie
                    achat -> R.string.change_jai_paye
                    state.verification?.estVert == true -> R.string.change_transmettre_verifie
                    else -> R.string.change_transmettre
                }
            ),
            fontWeight = FontWeight.Bold
        )
        // MEME SIGNE QUE SUR « CONTINUER » ET SUR LE BOUTON DE PAIEMENT :
        // les trois font avancer, et trois chevrons identiques se lisent
        // comme un seul geste repris d'ecran en ecran.
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp))
    }
    }

    /*
    LE RETOUR EST UN LIEN, DONC IL EST BLEU.

    Il etait gris, de la couleur des notes et des libelles — c'est-a-dire
    de ce qui se lit et ne se touche pas. Seul element actif de l'ecran a
    ne pas porter la couleur de l'action, il se trouvait en le cherchant.
    */
    TextButton(onClick = vm::retourAuCalcul, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(
            stringResource(R.string.change_retour),
            color = AccentBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

/*
═══════════════════════════════════════════════════════════════════════════
UN COMPTE À REBOURS PLUTÔT QU'UN DÉLAI ANNONCÉ
═══════════════════════════════════════════════════════════════════════════

« Délai annoncé : 30 min » ne dit rien d'utile une fois qu'on attend. Trente
minutes à partir de quand ? On regarde l'écran, on ne sait pas si on vient
d'arriver ou si on attend depuis vingt minutes, et on rappelle le changeur.

Un chiffre qui descend répond à la seule question qu'on se pose, et il y
répond sans qu'on la pose.

─── CE QU'IL DÉCOMPTE, ET IL FAUT ÊTRE EXACT ────────────────────────────

Le prix a été figé à la confirmation. Ce décompte est la durée pendant
laquelle VaultEx le tient — pas une promesse sur le changeur, qui n'est lié
par aucune horloge que nous contrôlions.

Dire « le changeur paiera dans 10 minutes » serait engager quelqu'un
d'autre. Dire « ton prix est garanti encore 9:47 » n'engage que nous, et
c'est vrai.

─── À ZÉRO, ON N'EFFACE RIEN ────────────────────────────────────────────

Le prix cesse d'être garanti, et on le dit. On ne bloque pas, on n'annule
pas : ses fonds sont peut-être déjà partis, et lui retirer son écran à
cet instant précis serait le pire moment pour le faire.
═══════════════════════════════════════════════════════════════════════════
*/
@Composable
private fun CompteARebours(confirmeA: Long, delaiMinutes: Int) {
    if (confirmeA <= 0L) return
    val fin = confirmeA + delaiMinutes * 60_000L

    /*
    UNE SECONDE, ET PAS PLUS FIN.

    Un compte à rebours au dixième de seconde donne un écran agité pour
    une information qui se lit à la minute. Et chaque recomposition coûte,
    sur les téléphones modestes qui sont la cible.
    */
    var maintenant by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(fin) {
        while (System.currentTimeMillis() < fin) {
            maintenant = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000)
        }
        maintenant = System.currentTimeMillis()
    }

    val restant = fin - maintenant
    val fini = restant <= 0L
    /*
    ORANGE D'ENTREE, ROUGE SOUS DEUX MINUTES.

    Il etait noir, puis orange a deux minutes. Un chiffre noir au milieu de
    chiffres noirs ne se distingue pas : on ne le regardait qu'en le
    cherchant, alors qu'il dit combien de temps le prix tient encore.

    Orange des le depart : c'est un delai, donc quelque chose qui s'epuise.
    Rouge sous deux minutes : il ne reste plus de quoi payer tranquillement,
    et il faut le voir sans lire.
    */
    val couleur = when {
        fini -> AccentRed
        restant < 2 * 60_000L -> AccentRed
        else -> AccentOrange
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Schedule, null, tint = couleur, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(
                if (fini) R.string.change_prix_expire else R.string.change_prix_garanti
            ),
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        if (!fini) {
            val m = restant / 60_000L
            val sec = (restant % 60_000L) / 1_000L
            Text(
                "%d:%02d".format(m, sec),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = couleur,
                // Chasse fixe : sans elle, les chiffres changent de largeur
                // a chaque seconde et la ligne tremble.
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

// ─── La vérification on-chain ───────────────────────────────────────

/*
═══════════════════════════════════════════════════════════════════════════
« EST-CE QUE MON ARGENT EST BIEN PARTI ? »
═══════════════════════════════════════════════════════════════════════════

C'est la seule question de l'utilisateur à cet instant. Il vient d'envoyer
de la crypto à l'adresse d'une personne qu'il ne connaît pas. C'est
IRRÉVERSIBLE, et jusqu'ici l'application ne savait rien lui répondre : elle
lui demandait de recopier une référence et de faire confiance.

Maintenant le relais regarde la chaîne, et cette carte dit ce qu'il y voit.

─── CE N'EST PAS LE TÉLÉPHONE QUI VÉRIFIE ───────────────────────────────

Et ça change tout. Une vérification faite ici n'aurait aucune valeur pour
le changeur : l'APK se décompile, et un écran vert se forge en changeant
une ligne. Ce qui s'affiche est le VERDICT DU RELAIS — la même réponse qui
partira dans son message Telegram.

C'est pourquoi le vert est honnête : il ne dit pas « l'application pense
que », il dit « le relais a lu, et le changeur lira la même chose ».

─── QUATRE ÉTATS, QUATRE CHOSES À FAIRE ─────────────────────────────────

  Recherche    Attendre. On dit depuis combien de temps, pour que
               l'attente ne ressemble pas à un blocage.
  Vert         Transmettre. Le montant affiché est celui de la chaîne.
  Introuvable  Attendre encore, ou coller le hash. Et pouvoir passer
               outre, parce que les fonds sont déjà partis.
  Impossible   Transmettre quand même : le changeur vérifiera à la main,
               comme avant. On ne fait pas semblant d'avoir vérifié.
═══════════════════════════════════════════════════════════════════════════
*/
@Composable
private fun ColumnScope.CarteVerification(
    vm: ChangeViewModel,
    state: com.vaultex.ui.viewmodel.ChangeState
) {
    val affichage = affichageVerification(state)
    val v = state.verification
    val vert = affichage == AffichageVerif.VERT
    /*
    ═══════════════════════════════════════════════════════════════════════
    LA MÊME CARTE, DEUX VOCABULAIRES
    ═══════════════════════════════════════════════════════════════════════

    Les cinq états sont les mêmes, les sorties sont les mêmes, les couleurs
    sont les mêmes. Ce qui change, c'est CE QU'ON A REGARDÉ : une chaîne
    publique pour une vente, le SMS de l'opérateur reçu par le changeur
    pour un achat.

    Écrire une seconde carte aurait doublé la logique des sorties — celle
    qui décide si l'on peut encore réessayer, et si l'on a le droit de
    passer outre. C'est la partie qu'il ne faut écrire qu'une fois.

    Mais les PHRASES doivent être justes : « le relais interroge la chaîne
    USDT » sur un paiement Orange Money ferait chercher sur une chaîne
    quelqu'un qui vient de payer avec son téléphone, et lui apprendrait à
    ne plus lire cette carte.
    ═══════════════════════════════════════════════════════════════════════
    */
    val achat = state.sens == SensChange.ACHAT

    val couleur = when (affichage) {
        AffichageVerif.VERT -> AccentGreen
        AffichageVerif.DEJA_SERVI -> AccentRed
        AffichageVerif.RECHERCHE -> AccentBlue
        else -> AccentOrange
    }

    Surface(shape = RoundedCornerShape(14.dp), color = couleur.copy(alpha = 0.10f)) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                /*
                UN INDICATEUR QUI TOURNE PENDANT LA RECHERCHE.

                Une icône fixe sur un écran qui attend se lit comme un
                écran bloqué — et quelqu'un qui vient d'envoyer de l'argent
                ferme une application qui a l'air bloquée.
                */
                if (affichage == AffichageVerif.RECHERCHE) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp), strokeWidth = 2.dp, color = couleur
                    )
                } else {
                    Icon(
                        if (vert) Icons.Default.CheckCircle else Icons.Default.Search,
                        null, tint = couleur, modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(titreVerification(affichage, achat)),
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary
                )
            }

            Text(
                detailVerification(state, affichage),
                fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp
            )

            if (vert && v != null) {
                HorizontalDivider(color = BgPrimary)
                /*
                LE MONTANT DE LA CHAINE, ET IL EST DIT COMME TEL.

                Si le téléphone attendait 8 USDT et que la chaîne en montre
                7,9, c'est 7,9 qui s'affiche. Sans la mention « lu sur la
                chaîne », l'utilisateur croirait relire sa propre saisie et
                ne verrait pas l'écart.
                */
                v.montant?.let { m ->
                    Ligne(
                        stringResource(
                            if (achat) R.string.change_verif_achat_recu
                            else R.string.change_verif_recu
                        ),
                        // Des FRANCS sur un achat : c'est le chiffre du SMS
                        // de l'opérateur. Afficher « 10 000 USDT » parce que
                        // la carte a été écrite pour les ventes serait pire
                        // que de ne rien afficher.
                        if (achat) fcfa(m) else "${crypto(m, state.monnaie)} ${state.monnaie}",
                        icone =
                            if (achat) Icons.Default.Payments
                            else Icons.Default.AccountBalanceWallet,
                        vedette = true
                    )
                }
                /*
                LE NUMÉRO QUI A PAYÉ, SUR UN ACHAT SEULEMENT.

                C'est la preuve que le rapprochement s'est fait sur le bon
                abonné. Quelqu'un qui a payé depuis le téléphone de son
                frère et déclaré son propre numéro le voit ici, avant que
                le changeur ne le lui demande.

                Sur une vente, `de` est l'adresse d'un portefeuille chaud
                d'exchange : elle n'apprend rien à personne.
                */
                if (achat && v.de.isNotBlank()) {
                    Ligne(
                        stringResource(R.string.change_verif_achat_de),
                        v.de,
                        icone = Icons.Default.Sms
                    )
                }
                if (v.quand > 0L) {
                    Ligne(stringResource(R.string.change_verif_quand), dateCourte(v.quand))
                }
                if (!v.confirme) {
                    Note(stringResource(R.string.change_verif_pas_mine), AccentOrange)
                }
                if (v.txid.isNotBlank()) {
                    Text(
                        v.txid,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        lineHeight = 14.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }

            /*
            ═══════════════════════════════════════════════════════════════
            DEUX SORTIES, ET LES DEUX DOIVENT EXISTER
            ═══════════════════════════════════════════════════════════════

            REESSAYER : le cas du retrait encore en file chez une
            plateforme d'échange. Les dix minutes de sondage automatique
            s'arrêtent ; lui peut vouloir attendre plus.

            TRANSMETTRE QUAND MEME : le cas où rien n'arrivera. Ses fonds
            sont partis — c'est irréversible. L'empêcher de déposer sa
            demande le laisserait avec de la crypto envoyée et rien chez le
            changeur, ce qui est la situation même qu'on cherche à
            supprimer. On freine, on n'interdit pas.

            Sauf sur DEJA_SERVI : là, passer outre n'aboutirait à rien. Le
            relais refera la vérification au dépôt et le message partira
            marqué « déjà servi ». Mieux vaut lui dire de regarder son
            historique.
            ═══════════════════════════════════════════════════════════════
            */
            val avecSorties = affichage == AffichageVerif.ABSENT ||
                affichage == AffichageVerif.IMPOSSIBLE
            /*
            LES DEUX SORTIES SONT L'UNE SOUS L'AUTRE, ET NON CÔTE À CÔTE.

            Mises en colonnes de moitié, « Transmettre quand même » se
            coupait en deux lignes et débordait de l'alignement du bouton
            voisin. Vu sur un téléphone, pas en relisant le code.

            Empilées, chacune a toute la largeur. Et ça sépare deux gestes
            qui n'ont pas le même poids : réessayer ne coûte rien, passer
            outre est une décision. Les poser côte à côte, de même taille,
            les faisait se ressembler.
            */
            if (avecSorties) {
                // Un nœud muet ou une monnaie illisible ne deviendra pas
                // lisible en réessayant : on ne propose pas un geste qui
                // ne peut rien changer.
                if (v == null || !v.sansEspoir) {
                    OutlinedButton(
                        // Lambda plutot que reference : `demarrerVerification`
                        // prend desormais un delai d'attente par defaut, et
                        // « Reessayer » veut dire tout de suite.
                        onClick = { vm.demarrerVerification() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.change_verif_reessayer), fontSize = 13.sp)
                    }
                }
                if (!state.forcer) {
                    TextButton(
                        onClick = vm::forcerTransmission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.change_verif_quand_meme),
                            fontSize = 12.sp, color = TextSecondary, maxLines = 1
                        )
                    }
                }
            }

            /*
            LE CHAMP DU HASH N'APPARAIT PAS TOUT DE SUITE.

            Tant que la recherche n'a pas échoué, il ne sert à rien : sur
            l'USDT de Tron — la quasi-totalité des ventes ici — le relais
            trouve sans. Le montrer d'emblée demanderait à tout le monde de
            recopier soixante-quatre caractères hexadécimaux pour rien, ce
            qu'on rate une fois sur deux.

            Il n'arrive donc qu'une fois qu'on en a besoin.

            ET JAMAIS SUR UN ACHAT. Il n'y a pas de hash : le paiement est
            passé par Orange Money, pas par une chaîne. Ce qui rattache un
            achat, c'est la référence de l'opérateur — un champ qui est
            déjà à l'écran, juste au-dessus de cette carte. En demander un
            second enverrait chercher dans ses SMS une chose qui n'y est
            pas.
            */
            /*
            ═══════════════════════════════════════════════════════════
            LE CHAMP DU HASH A ETE RETIRE
            ═══════════════════════════════════════════════════════════

            Il acceleratait une recherche qui aboutit SEULE : le relais
            trouve le versement sur la chaine par son montant marque, et
            le hash ne faisait que lui epargner de chercher.

            Il coutait plus qu'il ne rapportait. Il introduisait un mot —
            « hash » — que la plupart des vendeurs n'ont jamais vu, pour
            un gain de quinze secondes sur une operation qui en prend six
            cents. Et il demandait soixante-quatre caracteres hexadecimaux
            qu'on recopie mal une fois sur deux.

            CE QU'ON PERD. Quand le relais ne peut pas lire la chaine, le
            hash tape a la main partait quand meme dans le message du
            changeur, qui pouvait verifier lui-meme. Ce cas existe encore,
            et il se traite comme avant : « Confirmer quand meme », puis
            le changeur regarde son portefeuille.

            `onTxid` et le champ `txidSaisi` restent dans le modele : le
            relais sait toujours lire un hash, et `transmettre` l'envoie
            encore s'il y en a un. Seule la SAISIE disparait.
            ═══════════════════════════════════════════════════════════
            */
        }
    }
}

/*
═══════════════════════════════════════════════════════════════════════════
CINQ ETATS A L'ECRAN, ET L'UN N'EXISTE PAS DANS LE RELAIS
═══════════════════════════════════════════════════════════════════════════

Le relais rend quatre verdicts. L'écran en affiche cinq, parce qu'il a un
cas de plus à traiter : CELUI OU LA BOUCLE N'A JAMAIS TOURNE.

Ça arrive si `montantCrypto` est illisible au moment d'entrer dans l'étape
— rare, mais possible. Sans ce cinquième état, la carte restait sur
« Recherche de ton versement… », indéfiniment, SANS aucune sortie : pas de
bouton « réessayer », pas de « transmettre quand même », et le bouton
principal éteint. L'utilisateur avait envoyé sa crypto et se retrouvait
devant un écran dont on ne peut plus rien faire.

On le traite donc comme IMPOSSIBLE, ce qui est la vérité : rien n'a été
vérifié. Les deux sorties apparaissent, et il peut avancer.
═══════════════════════════════════════════════════════════════════════════
*/
private enum class AffichageVerif { RECHERCHE, VERT, ABSENT, DEJA_SERVI, IMPOSSIBLE }

/**
 * Le temps laisse a quelqu'un pour envoyer sa crypto avant qu'on dise
 * n'avoir rien trouve. Voir [affichageVerification].
 */
private const val GRACE_VENTE_MS = 3 * 60 * 1000L

private fun affichageVerification(
    state: com.vaultex.ui.viewmodel.ChangeState
): AffichageVerif {
    val v = state.verification
    if (state.rechercheEnCours) return AffichageVerif.RECHERCHE

    /*
    ═══════════════════════════════════════════════════════════════════════
    SUR UNE VENTE, ON N'ACCUSE PAS AVANT D'AVOIR LAISSE LE TEMPS D'ENVOYER
    ═══════════════════════════════════════════════════════════════════════

    Signale sur capture, et c'est exact : trente secondes apres l'arrivee
    sur l'ecran, la carte passait en orange — « Pas encore visible sur la
    chaine, rien trouve apres 2 essais » — avec « Reessayer » et
    « Confirmer quand meme ».

    A cet instant l'utilisateur n'a rien envoye. Il vient de lire une
    adresse de quarante caracteres et un montant a six decimales. Il lui
    reste a sortir de l'application, ouvrir son portefeuille ou son
    exchange, coller, verifier, confirmer, et attendre un bloc.

    La recherche, elle, part des la premiere seconde — et c'est juste :
    quelqu'un peut avoir envoye avant d'arriver ici, ou revenir d'un
    envoi fait entre-temps. Ce qui est faux, c'est de RENDRE COMPTE de
    son echec avant que l'echec ait un sens.

    Le code pose deja cette regle pour l'achat, deux fonctions plus haut :
    « une accusation gratuite doublee d'un mensonge : on n'a pas essaye ».
    Elle manquait ici.

    ─── TROIS MINUTES, ET POURQUOI PAS DEUX NI DIX ──────────────────────

    Le prix est garanti dix minutes. En passer trois dans un etat neutre
    laisse sept minutes ou l'orange dit quelque chose d'utile — assez pour
    qu'on puisse encore coller un hash ou passer outre avant l'expiration.

    En dessous, on retombe dans le cas qu'on corrige. Au-dela, quelqu'un
    qui a vraiment un probleme — mauvaise chaine, montant arrondi —
    l'apprendrait trop tard pour le rattraper dans la fenetre du prix.

    ─── CE QUI RECOMPOSE L'ECRAN ────────────────────────────────────────

    `essais`, qui avance a chaque sondage. L'horloge ne reveille pas
    Compose toute seule ; le compteur, si, et il bat toutes les quinze
    secondes. Le passage a l'orange a donc lieu au premier sondage qui
    suit la troisieme minute, pas a la seconde pres — ce qui est
    exactement la precision que ce seuil merite.
    ═══════════════════════════════════════════════════════════════════════
    */
    if (state.sens == SensChange.VENTE && v?.etat == com.vaultex.domain.fiat.EtatVerification.ABSENT) {
        val depuis = System.currentTimeMillis() - state.confirmeA
        if (state.confirmeA > 0L && depuis < GRACE_VENTE_MS) return AffichageVerif.RECHERCHE
    }

    if (v == null) {
        // Aucune réponse et aucun sondage en vol : soit la boucle n'a
        // jamais démarré, soit elle est morte. Dans les deux cas rien n'a
        // été vérifié, et il faut une sortie.
        return AffichageVerif.IMPOSSIBLE
    }
    return when (v.etat) {
        com.vaultex.domain.fiat.EtatVerification.CONFIRME -> AffichageVerif.VERT
        com.vaultex.domain.fiat.EtatVerification.ABSENT -> AffichageVerif.ABSENT
        com.vaultex.domain.fiat.EtatVerification.DEJA_SERVI -> AffichageVerif.DEJA_SERVI
        else -> AffichageVerif.IMPOSSIBLE
    }
}

private fun titreVerification(affichage: AffichageVerif, achat: Boolean): Int =
    if (achat) when (affichage) {
        AffichageVerif.RECHERCHE -> R.string.change_verif_achat_recherche
        AffichageVerif.VERT -> R.string.change_verif_achat_trouve
        AffichageVerif.ABSENT -> R.string.change_verif_achat_absent
        AffichageVerif.DEJA_SERVI -> R.string.change_verif_achat_deja_servi
        AffichageVerif.IMPOSSIBLE -> R.string.change_verif_achat_impossible
    } else when (affichage) {
        AffichageVerif.RECHERCHE -> R.string.change_verif_recherche
        AffichageVerif.VERT -> R.string.change_verif_trouve
        AffichageVerif.ABSENT -> R.string.change_verif_absent
        AffichageVerif.DEJA_SERVI -> R.string.change_verif_deja_servi
        AffichageVerif.IMPOSSIBLE -> R.string.change_verif_impossible
    }

@Composable
private fun detailVerification(
    state: com.vaultex.ui.viewmodel.ChangeState,
    affichage: AffichageVerif
): String {
    /*
    LA RAISON DU RELAIS EST TRANSMISE TELLE QUELLE quand il en donne une.
    « Le nœud n'a pas répondu » et « aucune adresse réglée pour BTC »
    appellent deux actions différentes — la première se réessaie, la
    seconde est un réglage à poser côté serveur. Les fondre dans
    « vérification impossible » enverrait quelqu'un réessayer indéfiniment
    une opération qui ne peut pas aboutir.
    */
    val raison = state.verification?.raison.orEmpty()

    if (state.sens == SensChange.ACHAT) {
        return when (affichage) {
            AffichageVerif.RECHERCHE ->
                stringResource(R.string.change_verif_achat_recherche_detail)
            AffichageVerif.VERT -> stringResource(R.string.change_verif_achat_trouve_detail)
            /*
            SUR UN ACHAT, « ABSENT » DÉSIGNE SA PROPRE CAUSE LA PLUS
            PROBABLE : la référence recopiée de travers. C'est tout
            l'intérêt de montrer ce verdict au client — lui seul peut la
            relire, et il l'a sous les yeux dans le SMS de l'opérateur.
            */
            AffichageVerif.ABSENT ->
                stringResource(R.string.change_verif_achat_absent_detail, state.essais)
            AffichageVerif.DEJA_SERVI ->
                stringResource(R.string.change_verif_achat_deja_servi_detail)
            AffichageVerif.IMPOSSIBLE ->
                if (raison.isNotBlank()) {
                    stringResource(R.string.change_verif_achat_impossible_detail_raison, raison)
                } else {
                    stringResource(R.string.change_verif_achat_impossible_detail)
                }
        }
    }

    return when (affichage) {
        AffichageVerif.RECHERCHE ->
            stringResource(R.string.change_verif_recherche_detail, state.monnaie)
        AffichageVerif.VERT -> stringResource(R.string.change_verif_trouve_detail)
        AffichageVerif.ABSENT ->
            stringResource(R.string.change_verif_absent_detail, state.essais)
        AffichageVerif.DEJA_SERVI -> stringResource(R.string.change_verif_deja_servi_detail)
        AffichageVerif.IMPOSSIBLE ->
            if (raison.isNotBlank()) {
                stringResource(R.string.change_verif_impossible_detail_raison, raison)
            } else {
                stringResource(R.string.change_verif_impossible_detail)
            }
    }
}

// ─── Temps 3 : transmis ─────────────────────────────────────────────

@Composable
private fun ColumnScope.EtapeTransmis(
    state: com.vaultex.ui.viewmodel.ChangeState,
    onRecommencer: () -> Unit
) {
    Surface(shape = RoundedCornerShape(14.dp), color = AccentGreen.copy(alpha = 0.10f)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.change_transmis_titre),
                    fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary
                )
            }
            Text(
                stringResource(R.string.change_transmis_detail, state.reference),
                fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp
            )
            /*
            ═══════════════════════════════════════════════════════════════
            DIRE DANS QUEL ETAT LA DEMANDE EST PARTIE
            ═══════════════════════════════════════════════════════════════

            « Ta demande est partie » et « ta demande est partie, et le
            changeur a lu que la chaîne confirme ton versement » ne sont
            pas la même phrase. La seconde est celle qui permet d'attendre
            sans rappeler toutes les cinq minutes.

            L'INVERSE COMPTE AUTANT. Si la demande est partie sans
            vérification, le lui cacher serait le laisser croire qu'il n'a
            plus rien à faire alors que le changeur a reçu un message
            marqué « introuvable » — et qu'il ne paiera pas avant d'avoir
            regardé lui-même.

            C'est l'état RENDU PAR LE DEPOT qu'on affiche, pas celui du
            dernier sondage : c'est lui qui est parti dans le message.
            ═══════════════════════════════════════════════════════════════
            */
            val v = state.verification
            if (state.sens == SensChange.VENTE && v != null) {
                HorizontalDivider(color = BgPrimary)
                if (v.estVert) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.CheckCircle, null,
                            tint = AccentGreen, modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.change_transmis_verifie),
                            fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Warning, null,
                            tint = AccentOrange, modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.change_transmis_non_verifie),
                            fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
    Button(
        onClick = onRecommencer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Text(stringResource(R.string.change_nouvelle), fontWeight = FontWeight.Bold)
    }
}

/** Un onglet de sens, avec son icone. */
@Composable
private fun OngletSens(
    texte: String,
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    choisi: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    /*
    L'ONGLET CHOISI EST BLEU PLEIN, PAS BLEU PALE.

    Il portait un fond a 18 % et un contour. Pose sur un fond de page
    deja bleute, l'ecart entre choisi et non choisi tenait dans une
    nuance — lisible a l'arret, beaucoup moins en plein soleil, qui est
    la condition ordinaire d'usage ici.

    Se tromper de sens coute une operation entiere. La distinction doit
    donc etre celle qui resiste au pire eclairage : une surface pleine
    contre une surface vide.
    */
    val plein = androidx.compose.ui.graphics.Color.White
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (choisi) AccentBlue else SurfaceColor,
        border = if (choisi) null else androidx.compose.foundation.BorderStroke(
            1.dp, BgTertiary
        ),
        modifier = modifier.height(52.dp)
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icone, null,
                tint = if (choisi) plein else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                texte,
                fontSize = 15.sp,
                fontWeight = if (choisi) FontWeight.Bold else FontWeight.Medium,
                color = if (choisi) plein else TextSecondary
            )
        }
    }
}

/**
 * Les bornes de l'opération, dans l'unité où l'on tape.
 *
 * EN FRANCS À L'ACHAT, EN CRYPTO À LA VENTE. Les deux chiffres sont réglés
 * par le changeur en francs ; les montrer tels quels à quelqu'un qui tape
 * des USDT lui demanderait de faire la conversion de tête, avec un cours
 * qu'il n'a pas.
 *
 * N'AFFICHE RIEN plutôt qu'une ligne fausse quand le cours manque. Une
 * borne en crypto sans cours serait un chiffre inventé, et celui-ci
 * servirait à décider d'un montant à envoyer.
 */
@Composable
private fun BornesChange(
    minFcfa: Double,
    maxFcfa: Double,
    enFcfa: Boolean,
    monnaie: String,
    prix: com.vaultex.domain.fiat.PrixFcfa?,
    /** Ecrit le plafond dans le champ, ou null pour ne rien proposer. */
    onMax: (() -> Unit)? = null
) {
    // Un plafond au-dessous du minimum ne borne rien : c'est un réglage
    // incohérent, et l'annoncer ferait lire une règle impossible à tenir.
    if (maxFcfa <= minFcfa) return

    val texte = if (enFcfa) {
        stringResource(R.string.change_bornes, "${fcfa(minFcfa)} FCFA", "${fcfa(maxFcfa)} FCFA")
    } else {
        /*
        À LA VENTE, LA CONVERSION PASSE PAR LE PRIX DE VENTE — le même que
        celui qui calcule ce qu'on recevra. Prendre le prix d'achat
        donnerait une borne qui ne correspond pas à celle qu'on appliquera,
        et l'écart serait exactement la marge : quelqu'un se verrait refusé
        juste au-dessus d'un seuil qu'on lui a montré.
        */
        val v = prix?.venteFcfa ?: return
        if (v <= 0.0) return
        /*
        ═══════════════════════════════════════════════════════════════
        DEUX DECIMALES, PAS SIX
        ═══════════════════════════════════════════════════════════════

        `crypto` rend la precision d'AFFICHAGE d'un solde — six
        decimales pour l'USDT. Vu sur un vrai ecran, cette ligne
        donnait :

            Entre ≈ 1,075269 USDT-BNB et ≈ 89,605735 USDT-BNB

        Quatorze caracteres de nombre pour une borne, sur une ligne de
        onze points qui doit tenir sans couper. Et surtout : le « ≈ »
        annonce une approximation, les six decimales en promettent une
        exactitude. Les deux ne peuvent pas etre vrais en meme temps.

        Ces chiffres ne servent qu'a situer un ordre de grandeur avant
        de taper — et le cours dont ils derivent bouge de plus que leur
        sixieme decimale entre deux rafraichissements.

        LE MINIMUM EST ARRONDI VERS LE HAUT, le plafond vers le BAS.
        Arrondir le minimum a 1,07 quand il vaut 1,075269 afficherait
        une borne que le controle refuse — on taperait le chiffre lu, et
        on se ferait reprendre par la regle qu'on vient de lire.
        ═══════════════════════════════════════════════════════════════
        */
        /*
        ─── DES ENTIERS, PARCE QUE C'EST TOUT CE QU'ON PEUT TAPER ───────

        Le champ n'accepte plus de decimales a la vente. Annoncer
        « entre ≈ 1,08 et ≈ 89,44 » designerait donc deux montants qu'on
        ne peut PAS saisir — et le bas serait refuse si on tapait 1.

        Le minimum monte a l'entier superieur, le plafond descend a
        l'inferieur : les deux bornes deviennent des valeurs qu'on peut
        reellement ecrire, et qui passent.
        */
        val bas = kotlin.math.ceil(minFcfa / v)
        val haut = kotlin.math.floor(maxFcfa / v)
        if (haut < bas) return
        val entier = java.text.NumberFormat
            .getNumberInstance(java.util.Locale.FRANCE).apply {
                maximumFractionDigits = 0
            }
        stringResource(
            R.string.change_bornes_approx,
            "${entier.format(bas)} $monnaie",
            "${entier.format(haut)} $monnaie"
        )
    }

    /*
    ─── ELLES SONT DANS UNE BOITE, AVEC UN « i » ET UN RACCOURCI ────────

    Posees a nu entre deux cartes, elles n'appartenaient a rien : ni au
    champ qu'elles decrivent, ni a la carte du dessous. Dans la carte,
    sous les pastilles, elles se lisent pour ce qu'elles sont — la regle
    du champ juste au-dessus.

    LE « MAX » EST LA PARCE QUE LE PLAFOND EST LA. Donner le chiffre et
    laisser le taper est une demi-mesure : la borne haute est aussi un
    montant qu'on veut parfois choisir, et elle est deja sous les yeux.
    Il n'apparait qu'a l'achat — a la vente, c'est le solde qui plafonne,
    et son propre bouton « Tout » est en haut de la carte.
    */
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgTertiary.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info, null,
                tint = AccentBlue, modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                texte,
                fontSize = 12.sp, color = TextSecondary,
                maxLines = 1, modifier = Modifier.weight(1f)
            )
            onMax?.let { max ->
                Text(
                    stringResource(R.string.change_max),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentBlue,
                    modifier = Modifier.clickable { max() }
                )
            }
        }
    }
}

// ─── Les deux cartes de montant ─────────────────────────────────────

/*
═══════════════════════════════════════════════════════════════════════════
UNE CARTE DE MONTANT, DANS LA GRAMMAIRE DU SWAP
═══════════════════════════════════════════════════════════════════════════

Le libellé en haut, l'unité à gauche, le montant à droite en gros. C'est
exactement la disposition du Swap, et ce n'est pas de l'imitation : les
mêmes utilisateurs passent d'un écran à l'autre, et la même question — je
donne combien, je reçois combien — doit se lire au même endroit.

─── POURQUOI PAS SwapCoinCard DIRECTEMENT ───────────────────────────────

Parce qu'elle porte un solde, trois pastilles de part (25 %, 50 %, MAX) et
une feuille de sélection de jetons avec les soldes de chaque chaîne. Rien
de tout ça n'a de sens pour des francs CFA, qu'on ne détient pas dans
l'application. L'adapter aurait demandé de rendre la moitié de ses
paramètres facultatifs — donc une carte qui fait deux choses à moitié.

─── LE CONTOUR PORTE SEUL LA DISTINCTION ────────────────────────────────

Plus épais et bleu sur celle qu'on remplit, fin et discret sur celle qu'on
lit. Pas de fond coloré : deux surfaces pleines côte à côte se disputent
le regard, et c'est le montant qui doit l'emporter, pas la carte.
═══════════════════════════════════════════════════════════════════════════
*/
/*
═══════════════════════════════════════════════════════════════════════════
LA TAILLE DU NOMBRE SUIT SA LONGUEUR
═══════════════════════════════════════════════════════════════════════════

Un nombre géant est ce qui rend cet écran lisible d'un coup d'oeil. Mais
« 17 590 » et « 0,000123456789 » n'occupent pas la même place, et une
taille fixe oblige à choisir entre deux défauts : un petit nombre perdu
dans le vide, ou un long nombre coupé — sur un écran où ce nombre est la
somme qu'on va envoyer.

Compose n'a pas de texte auto-ajusté en version stable. Trois paliers
suffisent, et ils ont l'avantage d'être prévisibles : on sait toujours de
quoi aura l'air un montant donné.

Le palier le plus bas reste nettement au-dessus d'un libellé. En dessous,
le montant cesserait d'être ce qu'on lit en premier — ce qui est toute la
raison d'être de ce bloc.
═══════════════════════════════════════════════════════════════════════════
*/
/** Le drapeau du Burkina Faso. Deux indicateurs regionaux : B et F. */
private const val DRAPEAU_BF = "\uD83C\uDDE7\uD83C\uDDEB"

private fun tailleMontant(texte: String) = when {
    texte.length <= 8 -> 34.sp
    texte.length <= 12 -> 26.sp
    else -> 20.sp
}

@Composable
private fun CarteMontantChange(
    libelle: String,
    montant: String,
    unite: String,
    /**
     * La mention grise a droite du libelle — « Montant en FCFA »,
     * « Montant estime ».
     *
     * ELLE DIT CE QUE LE CHIFFRE EST, pas ce qu'il vaut. « Tu recois »
     * seul laisse croire a un engagement ; « Montant estime » rappelle
     * que le cours bouge, a l'endroit meme ou on lit le nombre.
     *
     * Elle cede la place au solde sur la carte d'une vente : les deux
     * occupent le meme coin, et le solde est l'information qui decide.
     */
    mention: String? = null,
    /**
     * Ce qui se pose SOUS les pastilles, dans la carte.
     *
     * Les bornes y vivent desormais. Dehors, elles flottaient entre deux
     * cartes sans appartenir a aucune, alors qu'elles decrivent le champ
     * juste au-dessus.
     */
    piedDePage: (@Composable () -> Unit)? = null,
    /** Solde detenu, pour la carte qu'on remplit lors d'une vente. */
    solde: Double? = null,
    /** « Tout », qui ecrit le plus grand montant vendable dans le champ. */
    onTout: (() -> Unit)? = null,
    /**
     * Ce que « Tout » va ecrire, deja mis en forme — ou null quand aucun
     * montant ne passe, et le bouton ne s'affiche alors pas.
     *
     * CALCULE PAR L'APPELANT, parce que la regle appartient au modele :
     * elle depend du marquage, et cette carte ne sait rien de lui.
     */
    toutValeur: String? = null,
    /** Montants courants proposes en pastilles. Vide pour n'en proposer aucun. */
    rapides: List<Double> = emptyList(),
    onRapide: (Double) -> Unit = {},
    /** Le logo de la monnaie : sur la carte crypto, jamais sur celle en francs. */
    avecLogo: Boolean,
    /**
     * Les monnaies entre lesquelles choisir, ou vide si le choix n'existe pas.
     *
     * Vide sur la carte en francs — il n'y a qu'une monnaie locale — et vide
     * aussi quand le changeur n'en accepte qu'une : une liste déroulante à un
     * seul élément invite à un geste qui ne change rien.
     */
    monnaies: List<String>,
    onMonnaie: (String) -> Unit,
    /**
     * Ce qui est tapé, ou null sur le bloc qu'on lit seulement.
     *
     * C'ÉTAIT `saisissable: Boolean`, du temps où le pavé numérique de
     * l'application était le seul clavier. Le pavé est parti ; le champ
     * reprend donc la frappe, et il lui faut de nouveau où la rendre.
     */
    onMontant: ((String) -> Unit)?,
    /**
     * Combien de décimales la monnaie saisie possède — zéro pour les francs.
     *
     * Sert UNIQUEMENT à choisir le clavier. Le franc CFA n'ayant pas de
     * centimes, lui offrir une touche virgule n'est pas seulement inutile :
     * « 5000,50 » tapé dedans ne peut pas donner un montant juste, et c'est
     * au filtre de rattraper ce que le clavier aurait dû ne pas proposer.
     *
     * Les règles, elles, restent dans `SaisieMontant` : un clavier ne
     * protège de rien contre un collage.
     */
    decimalesMax: Int = 0,
    /** Le même montant, arrondi, affiché en dessous s'il diffère. */
    approx: String? = null
) {
    var listeOuverte by remember { mutableStateOf(false) }
    val choix = monnaies.size > 1

    /*
    PLUS DE CONTOUR : L'ENCRE PORTE SEULE LA DISTINCTION.

    Deux rectangles dessinés autour de deux nombres, sur un écran dont ces
    deux nombres SONT le sujet. Ce qui distingue les blocs tient maintenant
    dans ce qu'on vient lire — montant donné en blanc avec un trait bleu,
    montant reçu en vert sans trait.

    La hauteur plancher reste : sans elle, le bloc qui porte un sélecteur
    dépasse l'autre, et deux hauteurs différentes se lisent comme un défaut
    d'alignement, y compris sans contour pour le souligner.
    */
    /*
    ═══════════════════════════════════════════════════════════════════════
    LA CARTE REVIENT, ET LE CHAMP DEVIENT UN BLOC
    ═══════════════════════════════════════════════════════════════════════

    Un commentaire de cette fonction disait « plus de contour : l'encre
    porte seule la distinction ». C'etait vrai tant que la page n'avait
    qu'elles ; ca a cesse de l'etre quand la carte des details a pris un
    fond blanc. Deux blocs nus au-dessus d'un bloc dessine ne se lisaient
    plus comme une suite, mais comme un element manquant.

    Deux niveaux, et chacun dit quelque chose :

      la CARTE blanche delimite une question — « tu donnes », « tu
      recois » — sur le fond bleute de la page ;

      le BLOC teinte, a l'interieur, delimite ce qu'on manipule : le
      nombre et son unite. C'est lui qui dit ou l'on tape, sans curseur
      et sans avoir a essayer.

    LE BLOC DU BAS EST VERT, et ce n'est pas un rappel du chiffre : c'est
    le meme vert que le montant recu portait deja. La couleur separe
    l'entree de la sortie avant qu'on lise un seul mot.
    ═══════════════════════════════════════════════════════════════════════
    */
    Surface(shape = RoundedCornerShape(18.dp), color = SurfaceColor) {
      Box(Modifier.fillMaxWidth().heightIn(min = 92.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    libelle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (solde == null) mention?.let {
                    Text(it, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
                }
                /*
                LE SOLDE, SUR LA CARTE QU'ON REMPLIT, ET SEULEMENT POUR
                UNE VENTE.

                Quelqu'un qui ignore combien il detient tape un montant au
                hasard, se fait refuser, recommence. Le lui montrer la ou
                il tape supprime ce tatonnement.

                A l'achat il n'y a rien a montrer : on ne detient pas de
                francs dans l'application, et afficher « Solde : 0 FCFA »
                — comme le fait la maquette — laisserait croire qu'il
                faudrait d'abord approvisionner quelque chose.
                */
                solde?.let { s ->
                    Text(
                        stringResource(R.string.change_solde, crypto(s, unite)),
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                    /*
                    ═══════════════════════════════════════════════════════
                    LE BOUTON DIT LE CHIFFRE QU'IL VA ECRIRE
                    ═══════════════════════════════════════════════════════

                    Il disait « TOUT », et il ne met pas tout : le marquage
                    ajoute jusqu'a 0,999 au montant tape, donc vendre un
                    solde entier demanderait d'en envoyer plus qu'on n'en a.
                    Il ecrit le plus grand montant qui passe a coup sur.

                    Garder le mot « tout » sur un bouton qui en laisse une
                    partie serait un mensonge d'un mot. Afficher « Tout : 2 »
                    dit les deux choses — le geste, et son resultat — avant
                    qu'on appuie, la ou l'ancien libelle laissait decouvrir
                    l'ecart apres coup.

                    ET IL DISPARAIT QUAND IL N'Y A RIEN A PROPOSER. Sous un
                    solde de 1,99, aucun montant ne passe : un bouton qui
                    ecrirait zero inviterait a un geste sans issue.
                    */
                    if (onTout != null && toutValeur != null) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentBlue.copy(alpha = 0.14f),
                            modifier = Modifier.clickable { onTout() }
                        ) {
                            Text(
                                stringResource(R.string.change_tout_valeur, toutValeur),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentBlue
                            )
                        }
                    }
                }
            }

            /*
            ═══════════════════════════════════════════════════════════
            LE NOMBRE À GAUCHE, LA MONNAIE À DROITE
            ═══════════════════════════════════════════════════════════

            C'était l'inverse : la pastille à gauche, le montant poussé à
            droite. On lisait donc « USDT-BNB » avant de lire combien.

            Or la question de cet écran est « combien », et la monnaie n'en
            est que l'unité. On lit de gauche à droite : le chiffre vient
            en premier, l'unité le qualifie. C'est aussi la disposition de
            toutes les calculatrices et de tous les écrans de retrait que
            ces utilisateurs connaissent déjà.
            ═══════════════════════════════════════════════════════════
            */
            Surface(
                shape = RoundedCornerShape(14.dp),
                // Teinte bleue sur ce qu'on remplit, verte sur ce qu'on
                // lit : la meme distinction que porte deja le chiffre.
                color = if (onMontant != null) BgTertiary
                        else AccentGreen.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth()
            ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {

                    if (onMontant != null) {
                        /*
                        ═════════════════════════════════════════════════════
                        LE CLAVIER DU SYSTÈME EST REVENU
                        ═════════════════════════════════════════════════════

                        Le pavé construit dans l'application a été retiré. Le
                        champ reprend donc la frappe, avec le clavier décimal
                        d'Android.

                        CE QUE LE PAVÉ GARANTISSAIT, LE FILTRE LE GARANTIT.
                        Le clavier système accepte le point, le moins, et
                        autant de décimales qu'on veut — y compris plus que
                        le jeton n'en possède. `SaisieMontant.filtrer`, dans
                        le ViewModel, écarte tout ça avant que la saisie ne
                        soit gardée. Sans lui, l'écran réafficherait
                        « 8,19672131 USDT » : une somme que personne ne peut
                        envoyer.

                        La taille suit toujours la longueur du nombre, et le
                        curseur est celui du champ — on peut donc corriger au
                        milieu, ce que le pavé ne permettait pas.
                        ═════════════════════════════════════════════════════
                        */
                        androidx.compose.foundation.text.BasicTextField(
                            value = montant,
                            onValueChange = onMontant,
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = tailleMontant(montant.ifEmpty { "0" }),
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentBlue),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = if (decimalesMax > 0) {
                                    androidx.compose.ui.text.input.KeyboardType.Decimal
                                } else androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { interieur ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    // Un « 0 » en gris pâle montre où taper
                                    // sans prétendre être une valeur saisie.
                                    if (montant.isEmpty()) {
                                        Text(
                                            "0",
                                            fontSize = tailleMontant("0"),
                                            fontWeight = FontWeight.Bold,
                                            color = TextSecondary.copy(alpha = 0.4f)
                                        )
                                    }
                                    interieur()
                                }
                            }
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                montant.ifEmpty { "—" },
                                fontSize = tailleMontant(montant.ifEmpty { "—" }),
                                fontWeight = FontWeight.Bold,
                                // Le montant reçu en vert : c'est le seul chiffre
                                // qui décide, et il garde la couleur qu'il avait
                                // quand il était la ligne vedette des détails.
                                color = if (montant.isEmpty()) TextSecondary else AccentGreen,
                                maxLines = 1
                            )
                            /*
                            L'ARRONDI SOUS LE CHIFFRE EXACT.

                            « 1,609509 USDT » est juste et illisible : on ne
                            retient pas six décimales. « ≈ 1,61 » donne l'ordre
                            de grandeur d'un coup d'oeil, et le chiffre exact
                            reste au-dessus pour qui vérifie.

                            On ne le montre que s'il apporte quelque chose : sur
                            un montant déjà court, deux lignes pour le même
                            nombre ne feraient que du bruit.
                            */
                            approx?.takeIf { it != montant }?.let { a ->
                                Text("≈ $a", fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                            }
                        }
                    }
                }

                Spacer(Modifier.width(10.dp))

                Box {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceColor,
                        modifier = if (choix) {
                            Modifier.clickable { listeOuverte = true }
                        } else Modifier
                    ) {
                        Row(
                            Modifier.padding(
                                start = if (avecLogo) 5.dp else 12.dp,
                                end = if (choix) 6.dp else 12.dp,
                                top = 6.dp, bottom = 6.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            /*
                            MÊME SOURCE DE LOGO QUE PARTOUT AILLEURS, donc
                            même image que sur l'accueil et le Marché. Un
                            logo propre à cet écran finirait par être le seul
                            à ne pas suivre, comme ça s'est produit pour Pi.
                            */
                            if (avecLogo) {
                                coil.compose.AsyncImage(
                                    model = com.vaultex.ui.components.CryptoIcon.url(unite),
                                    contentDescription = unite,
                                    modifier = Modifier.size(26.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                )
                            } else {
                                /*
                                LE DRAPEAU, EN EMOJI PLUTOT QU'EN IMAGE.

                                La pastille crypto porte un logo ; celle en
                                francs n'avait rien, et deux pastilles cote a
                                cote dont une seule porte une image se lisent
                                comme un chargement rate.

                                L'emoji est deja le motif employe ailleurs
                                dans l'application (OptionPickerSheet). Il ne
                                coute aucun fichier, aucun telechargement, et
                                la ou il ne s'affiche pas — quelques vieux
                                Android — il rend « BF », qui reste juste.
                                */
                                Text(DRAPEAU_BF, fontSize = 20.sp)
                            }
                            Column {
                                Text(
                                    unite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                /*
                                ═════════════════════════════════════════
                                LE RÉSEAU EN TOUTES LETTRES
                                ═════════════════════════════════════════

                                « USDT » et « USDT-BNB » côte à côte dans
                                une liste déroulante, ça ne suffit plus
                                depuis qu'il y a deux chaînes. Trois
                                caractères de différence, et un BEP-20
                                envoyé à une adresse TRON est perdu
                                définitivement — sans réclamation possible,
                                sans support à contacter.

                                Le nom du réseau sous le symbole coûte une
                                ligne de neuf points. Une erreur coûte tout
                                ce que la personne a envoyé.
                                ═════════════════════════════════════════
                                */
                                reseauDe(unite)?.let { r ->
                                    Text(
                                        r,
                                        fontSize = 9.sp,
                                        color = AccentOrange,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                            if (choix) {
                                Icon(
                                    Icons.Default.ArrowDropDown, null,
                                    tint = TextSecondary, modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = listeOuverte,
                        onDismissRequest = { listeOuverte = false }
                    ) {
                        monnaies.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = { onMonnaie(m); listeOuverte = false }
                            )
                        }
                    }
                }
            }
            }

            /*
            LES MONTANTS COURANTS, SOUS LE CHAMP.

            « 5 000 » se tape en quatre frappes sur un téléphone qu'on
            tient d'une main, dans la rue. Les pastilles suppriment ces
            frappes pour les sommes que les gens échangent réellement.

            Et elles disent autre chose, sans un mot : l'ordre de grandeur
            attendu. Quelqu'un qui ouvre cet écran pour la première fois ne
            sait pas s'il s'échange ici des centaines ou des millions. Trois
            chiffres le lui apprennent mieux qu'une phrase.
            */
            if (rapides.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rapides.forEach { v ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            // BLANC SUR BLANC DEPUIS QUE LA CARTE EXISTE :
                            // la pastille prend la teinte du champ.
                            color = BgTertiary,
                            modifier = Modifier.weight(1f).clickable { onRapide(v) }
                        ) {
                            Text(
                                fcfa(v),
                                modifier = Modifier.padding(vertical = 9.dp),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                // EN BLEU : ce sont des commandes, pas des
                                // valeurs. Du texte sombre les faisait lire
                                // comme trois nombres poses la.
                                color = AccentBlue,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            piedDePage?.invoke()
        }
      }
    }
}

/*
═══════════════════════════════════════════════════════════════════════════
LE RÉSEAU D'UNE MONNAIE, EN TOUTES LETTRES
═══════════════════════════════════════════════════════════════════════════

Depuis qu'il y a deux chaînes, « USDT » et « USDT-BNB » côte à côte dans
une liste ne suffisent plus. Trois caractères de différence, et un BEP-20
envoyé à une adresse TRON est perdu définitivement.

Null pour les francs, qui ne sont sur aucune chaîne — et pour une monnaie
qu'on ne connaît pas, plutôt qu'un nom deviné. Mieux vaut ne rien dire que
nommer le mauvais réseau.
═══════════════════════════════════════════════════════════════════════════
*/
private fun reseauDe(monnaie: String): String? = when (monnaie.uppercase()) {
    "USDT", "USDT-TRX", "TRX" -> "TRC20 · TRON"
    "USDT-BNB", "BNB" -> "BEP20 · BNB Smart Chain"
    "USDT-ETH", "ETH" -> "ERC20 · Ethereum"
    "BTC" -> "Bitcoin"
    "SOL" -> "Solana"
    "PI" -> "Pi Network"
    else -> null
}

/**
 * Trois montants courants, déduits des bornes du changeur.
 *
 * DÉDUITS PLUTÔT QUE CODÉS. Le minimum et le plafond sont réglables à
 * distance, et par monnaie ; des pastilles figées finiraient par proposer
 * des sommes refusées — ce qui est exactement le contraire de leur but.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * LE MINIMUM N'EST PAS ARRONDI, ET IL A FALLU UNE CAPTURE POUR LE VOIR
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Il y avait trois pastilles dans le code et deux à l'écran. Cause :
 * `kotlin.math.round` arrondit les demis VERS LE PAIR, pas vers le haut.
 * Sur un minimum de 500, `round(500 / 1000.0)` rend 0 — pas 1 — donc la
 * pastille valait zéro, sortait des bornes, et disparaissait en silence.
 *
 * Le piège est que l'arrondi « naturel » qu'on a en tête est celui de
 * l'école, et que celui de la bibliothèque est celui des statisticiens.
 * Les deux ne diffèrent que sur les demis exacts — donc presque jamais,
 * donc jamais quand on teste.
 *
 * Le minimum est maintenant rendu TEL QUEL. C'est un chiffre qui a du
 * sens en lui-même : la plus petite somme acceptée. L'arrondir pour faire
 * joli la rendrait fausse dans un sens ou dans l'autre — en dessous elle
 * serait refusée, au-dessus elle cacherait ce qui est permis.
 * ═══════════════════════════════════════════════════════════════════════
 */
private fun montantsRapides(
    p: com.vaultex.domain.fiat.ParametresChange,
    monnaie: String,
    /** Sert au plafond par monnaie, qui est exprime en dollars. */
    fcfaParDollar: Double?
): List<Double> {
    // PAR MONNAIE : TRON et BNB Chain n'ont pas le même seuil, parce
    // qu'elles n'ont pas les mêmes frais de réseau.
    val min = p.minimumPour(monnaie, fcfaParDollar)
    val max = p.plafondPour(monnaie, fcfaParDollar)
    if (max <= min) return emptyList()
    val milieu = kotlin.math.round((min + max) / 2000.0) * 1000.0
    return listOf(min, milieu, max)
        .map { kotlin.math.round(it) }
        .distinct()
        .map { it.toDouble() }
        .filter { it in min..max }
}

/*
═══════════════════════════════════════════════════════════════════════════
UNE VALEUR LONGUE PASSE À LA LIGNE SUIVANTE, PAS À CÔTÉ
═══════════════════════════════════════════════════════════════════════════

Ce bloc existe à cause d'un défaut vu sur un téléphone, et qui n'avait
jamais pu se montrer avant : le premier écran de VENTE affiché.

[Ligne] met le libellé à gauche avec `weight(1f)` et la valeur à droite
sans contrainte. Sur « Téléphone : 64088910 », tout va bien. Sur une
adresse TRON de trente-quatre caractères, la valeur prend toute la largeur
et écrase le libellé — qui s'affiche alors UNE LETTRE PAR LIGNE :

    Ad
    re
    ss
    e
    ...

`weight` ne réserve pas de place, il PARTAGE CE QUI RESTE. Quand l'autre
élément ne cède rien, il ne reste rien à partager. C'est le genre de
défaut qui n'apparaît qu'avec une vraie donnée, et une adresse de
changeur n'existait pas dans ce projet avant ce soir.

─── POURQUOI LA PILE PLUTÔT QU'UN RÉTRÉCISSEMENT ───────────────────────

On pourrait contraindre la valeur et la laisser se couper en deux lignes à
droite. Ce serait pire : une adresse coupée au milieu, alignée à droite,
se relit mal — et c'est précisément la chaîne qu'on vérifie caractère par
caractère avant d'envoyer des fonds.

Libellé au-dessus, valeur en dessous sur toute la largeur, en chasse fixe.
C'est ainsi qu'on lit une adresse partout ailleurs dans l'application.

─── COURTE OU LONGUE, LE COMPOSANT DÉCIDE ──────────────────────────────

Un numéro de huit chiffres n'a pas besoin de deux lignes. Le seuil évite
d'avoir à choisir au bon endroit — et donc d'oublier de le faire le jour
où un libellé s'allonge.
═══════════════════════════════════════════════════════════════════════════
*/
@Composable
private fun LigneACopier(
    libelle: String,
    valeur: String,
    icone: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onCopier: () -> Unit
) {
    if (valeur.length <= VALEUR_COURTE) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Ligne(libelle, valeur, icone = icone, fort = true) }
            /*
            LA PASTILLE AUTOUR DE L'ICONE DE COPIE.

            Posee a nu, elle ne se distinguait pas des icones de tete des
            lignes voisines — meme taille, meme bleu — alors que celle-ci
            SE TOUCHE et les autres non. Un fond teinte dit qu'il y a
            quelque chose a appuyer, ce qu'aucune icone ne dit par
            elle-meme.
            */
            IconButton(onClick = onCopier, modifier = Modifier.size(40.dp)) {
                Box(
                    Modifier.size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ContentCopy, stringResource(R.string.copy),
                        tint = AccentBlue, modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(libelle, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            /*
            LA PASTILLE AUTOUR DE L'ICONE DE COPIE.

            Posee a nu, elle ne se distinguait pas des icones de tete des
            lignes voisines — meme taille, meme bleu — alors que celle-ci
            SE TOUCHE et les autres non. Un fond teinte dit qu'il y a
            quelque chose a appuyer, ce qu'aucune icone ne dit par
            elle-meme.
            */
            IconButton(onClick = onCopier, modifier = Modifier.size(40.dp)) {
                Box(
                    Modifier.size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ContentCopy, stringResource(R.string.copy),
                        tint = AccentBlue, modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        /*
        EN CHASSE FIXE, comme le txid de la carte de vérification.

        Tous les caractères y ont la même largeur, donc les groupes se
        comparent à l'oeil — c'est ce qui permet de contrôler le début et
        la fin d'une adresse sans la lire en entier.
        */
        Text(
            valeur,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            lineHeight = 20.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

/*
═══════════════════════════════════════════════════════════════════════════
UN CHAMP OU LE LIBELLE ET L'EXEMPLE TIENNENT ENSEMBLE
═══════════════════════════════════════════════════════════════════════════

C'etait un `OutlinedTextField`, et il ne pouvait pas donner ce dessin.
Son LIBELLE occupe la case tant que le champ est vide et non touche ;
l'exemple n'apparait qu'au focus, c'est-a-dire une fois qu'on a deja
decide quoi taper. On avait contourne en mettant l'exemple dans l'aide,
sous la case — lisible, mais pas ce qui etait demande.

Ici les deux coexistent, parce qu'ils ne disent pas la meme chose : le
libelle nomme CE QU'ON DEMANDE, l'exemple montre A QUOI CA RESSEMBLE.
Quelqu'un qui cherche dans ses SMS a besoin des deux en meme temps.

LA PASTILLE DE L'ICONE EST CELLE DES LIGNES DE DETAIL, plus haut sur le
meme ecran. Trois formes d'icones dans un meme parcours donnent
l'impression d'ecrans assembles a la hate.

L'AIDE RESTE SOUS LA CASE, et c'est la qu'elle porte aussi le message
d'erreur : un seul endroit sous le champ pour tout ce qui le commente,
plutot qu'une ligne qui apparait et repousse les autres.
═══════════════════════════════════════════════════════════════════════════
*/
@Composable
private fun ChampChange(
    valeur: String,
    onValeur: (String) -> Unit,
    libelle: String,
    exemple: String,
    aide: String,
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    clavier: androidx.compose.ui.text.input.KeyboardType,
    /** Ce qui cloche, ou null. Remplace l'aide et colore la bordure. */
    erreur: String? = null
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        /*
        ─── LES COTES VIENNENT DE LA MAQUETTE, MESUREES ─────────────────

        Elle a ete fournie en capture pleine largeur, donc a l'echelle
        exacte de l'ecran : un pixel de l'image vaut 411/841 point. Les
        valeurs ci-dessous en sortent, elles ne sont pas estimees a
        l'oeil.

            case          132 px  ->  64,5 dp de haut
            pastille       86 px  ->  42 dp
            marge gauche   17 px  ->  8,3 dp
            libelle        25 px de capitale  ->  ~17 sp
            exemple        23 px               ->  ~16 sp

        La hauteur de la case n'est pas imposee : elle tombe toute seule
        a 64 points avec une pastille de 42 et onze points de marge
        verticale. L'imposer figerait la case si quelqu'un grossit les
        polices de son telephone.
        */
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceColor,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, if (erreur != null) AccentRed else BgTertiary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icone, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        libelle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    androidx.compose.foundation.text.BasicTextField(
                        value = valeur,
                        onValueChange = onValeur,
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 16.sp,
                            color = TextPrimary
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentBlue),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = clavier
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { interieur ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (valeur.isEmpty()) {
                                    Text(
                                        exemple,
                                        fontSize = 16.sp,
                                        color = TextSecondary.copy(alpha = 0.75f),
                                        maxLines = 1
                                    )
                                }
                                interieur()
                            }
                        }
                    )
                }
            }
        }
        Text(
            erreur ?: aide,
            fontSize = 11.sp,
            color = if (erreur != null) AccentRed else TextSecondary
        )
    }
}

/** Au-delà, une valeur ne tient plus à côté de son libellé. Voir [LigneACopier]. */
private const val VALEUR_COURTE = 20

// ─── Briques ────────────────────────────────────────────────────────

/**
 * Une ligne de detail, avec son icone.
 *
 * L'ICONE N'EST PAS UN ORNEMENT. Ces lignes se ressemblent toutes — un
 * libelle a gauche, un chiffre a droite — et l'oeil doit retrouver « ce
 * que je recois » sans relire les quatre. Un pictogramme distinct par
 * ligne donne ce point d'ancrage.
 *
 * [vedette] met la valeur en vert et en grand : c'est reserve au seul
 * chiffre qui decide, celui qu'on recoit. Deux vedettes sur un ecran n'en
 * font aucune.
 */
@Composable
private fun Ligne(
    libelle: String,
    valeur: String,
    icone: androidx.compose.ui.graphics.vector.ImageVector? = null,
    fort: Boolean = false,
    vedette: Boolean = false,
    /*
    LA TEINTE D'UNE VEDETTE, QUAND LE VERT NE VEUT RIEN DIRE.

    Le vert etait cable en dur : il dit « ce que tu recois », et c'est
    juste sur une vente. Sur un achat, la vedette est le montant qu'on
    DOIT PAYER — le vert y annoncerait un gain la ou il y a une sortie.

    Le bleu est la couleur de l'action dans toute l'application, et payer
    est une action.
    */
    teinte: Color? = null
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        icone?.let {
            /*
            L'ICONE DANS UNE PASTILLE TEINTEE.

            Posee a nu, elle se confond avec le texte et n'ancre rien. Sur
            un fond legerement colore elle devient un repere, et c'est tout
            ce qu'on lui demande : permettre de retrouver « ce que je
            recois » sans relire les quatre lignes.
            */
            val couleurIcone = teinte ?: if (vedette) AccentGreen else AccentBlue
            Box(
                Modifier.size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(couleurIcone.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(it, null, tint = couleurIcone, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(libelle, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(
            valeur,
            fontSize = if (vedette) 18.sp else if (fort) 14.sp else 12.sp,
            fontWeight = if (vedette || fort) FontWeight.Bold else FontWeight.Medium,
            color = teinte ?: if (vedette) AccentGreen else TextPrimary
        )
    }
}

/**
 * Un encadre en deux temps : le constat, puis quoi faire.
 *
 * LA PREMIERE PHRASE EST LE CONSTAT, le reste est l'action. Un pave de
 * trois lignes en rouge se lit comme un reproche et se saute ; un constat
 * en gras suivi d'une consigne se lit en deux coups d'oeil.
 *
 * Le decoupage se fait au premier point, ce qui suppose que la premiere
 * phrase des messages de blocage soit le constat — elles le sont toutes,
 * et c'est verifiable en les relisant. Sans point, tout reste en titre
 * plutot que de disparaitre.
 */
@Composable
private fun NoteEnDeuxTemps(texte: String, couleur: androidx.compose.ui.graphics.Color) {
    val coupe = texte.indexOf('.')
    val titre = if (coupe > 0) texte.substring(0, coupe + 1) else texte
    val suite = if (coupe > 0) texte.substring(coupe + 1).trim() else ""
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = couleur.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.ErrorOutline, null, tint = couleur, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titre, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = couleur, lineHeight = 16.sp)
                if (suite.isNotBlank()) {
                    Text(suite, fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun Note(texte: String, couleur: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = couleur.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
            Icon(
                when (couleur) {
                    AccentOrange -> Icons.Default.Warning
                    AccentRed -> Icons.Default.ErrorOutline
                    else -> Icons.Default.Info
                },
                null, tint = couleur, modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(texte, fontSize = 11.sp, color = TextPrimary, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun messageBlocage(
    code: String,
    p: com.vaultex.domain.fiat.ParametresChange,
    monnaie: String,
    /** Le plafond annonce doit etre CELUI QUI S'APPLIQUE. Voir plus bas. */
    fcfaParDollar: Double?
): String = when (code) {
    "ferme" -> stringResource(R.string.change_ferme)
    "pas_de_rachat" -> stringResource(R.string.change_pas_de_rachat)
    "sans_cours" -> stringResource(R.string.change_sans_cours)
    /*
    LE CHIFFRE AFFICHE EST CELUI DE LA MONNAIE CHOISIE.

    Montrer le seuil general pendant qu'un autre s'applique enverrait
    quelqu'un retaper un montant qui sera refuse une seconde fois. C'est
    exactement le genre de message qui fait fermer l'application.
    */
    "minimum" -> stringResource(
        R.string.change_sous_minimum,
        fcfa(p.minimumPour(monnaie, fcfaParDollar))
    )
    /*
    MEME RAISON QUE POUR LE MINIMUM, un cran plus haut dans ce when :
    montrer le plafond general pendant qu'un autre s'applique enverrait
    quelqu'un retaper un montant qui sera refuse une seconde fois.
    */
    "plafond" -> stringResource(
        R.string.change_sur_plafond,
        fcfa(p.plafondPour(monnaie, fcfaParDollar))
    )
    "solde" -> stringResource(R.string.change_solde_insuffisant)
    else -> stringResource(R.string.change_montant_invalide)
}

private fun fcfa(v: Double): String =
    java.text.NumberFormat.getIntegerInstance(java.util.Locale.FRANCE).format(v)

/**
 * Un montant de crypto, avec les décimales que la monnaie possède vraiment.
 *
 * Huit pour tout le monde annonçait « 8,19672131 USDT » — deux chiffres
 * qui n'existent pas sur un jeton à six décimales, et que personne ne peut
 * donc recevoir. Voir TauxFcfa.decimalesAffichage.
 */
private fun crypto(v: Double, monnaie: String): String =
    java.text.NumberFormat.getNumberInstance(java.util.Locale.FRANCE).apply {
        maximumFractionDigits = TauxFcfa.decimalesAffichage(monnaie)
        minimumFractionDigits = 0
    }.format(v)
