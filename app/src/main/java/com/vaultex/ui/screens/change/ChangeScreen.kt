package com.vaultex.ui.screens.change

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
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
import com.vaultex.ui.theme.VertWhatsApp
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
        /*
        ═══════════════════════════════════════════════════════════════
        LE CLAVIER RECOUVRAIT LE CHAMP QU'ON VENAIT D'OUVRIR
        ═══════════════════════════════════════════════════════════════

        Signale sur capture : en touchant « Numéro Mobile Money où
        recevoir », le clavier montait et le champ restait dessous. On
        tapait huit chiffres sans les voir.

        LE MANIFESTE DIT POURTANT `adjustResize`. Il ne suffit plus : à
        partir d'Android 15, un `targetSdk` de 35 ou plus impose le bord à
        bord, et la fenêtre n'est PLUS redimensionnée par le système. C'est
        à la composition de réserver la place, et rien ne le faisait ici.

        `imePadding` est posé AVANT `verticalScroll` : il réduit alors la
        fenêtre de défilement elle-même. Le champ qui prend le focus se
        ramène tout seul dans cette fenêtre — c'est le système de focus de
        Compose qui s'en charge, à condition que la fenêtre connaisse sa
        vraie hauteur. Posé après, il n'aurait rogné que le contenu, et le
        défilement aurait continué de croire disposer de tout l'écran.

        Six autres écrans de l'application l'emploient déjà — l'assistance,
        l'import de phrase, la feuille d'options. Celui-ci avait été oublié,
        et il est le seul où l'on saisisse un numéro qui décide d'un
        paiement.
        ═══════════════════════════════════════════════════════════════
        */
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState())
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

                state.etape == EtapeChange.TRANSMIS -> EtapeTransmis(
                    state = state,
                    copier = copier,
                    onRecommencer = { vm.recommencer() },
                    onAccueil = {
                        /*
                        ON DEPILE PLUTOT QUE DE NAVIGUER. L'accueil est SOUS
                        nous dans la pile : il n'y a rien a creer, seulement
                        a revenir. Et `popBackStack` rend un booleen, la ou
                        `navigate` peut etre ignore en silence — c'est le
                        meme choix que sur le recu d'envoi.

                        L'etat est remis a zero AVANT, pour que la pile,
                        si elle garde cette entree, n'y laisse pas une
                        demande deja transmise.
                        */
                        vm.recommencer()
                        if (!navController.popBackStack(Routes.DASHBOARD, false)) {
                            runCatching {
                                navController.navigate(Routes.DASHBOARD) {
                                    popUpTo(Routes.DASHBOARD) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    }
                )

                /*
                ═══════════════════════════════════════════════════════════
                LE DEPOT AUTOMATIQUE N'EST PAS UN FORMULAIRE
                ═══════════════════════════════════════════════════════════

                Apres « Terminé », on revenait sur l'etape de paiement
                complete : les deux champs de numero, la carte de
                verification, et un bouton « Versement vérifié — confirmer »
                grise avec un rond qui tourne.

                C'est-a-dire un ecran qui a TOUT L'AIR de demander quelque
                chose, juste apres un ecran qui vient de dire « Ta vente est
                partie ». On vient de finir, et on retombe devant des champs
                a remplir et un bouton a appuyer — qui ne se laisse pas
                appuyer. Le reflexe est de chercher ce qu'on a rate.

                Or il n'y a rien a faire : le hash est sur le disque, la
                boucle interroge la chaine, et la demande partira d'elle-meme
                des que le verdict tombe. Quelques secondes, pendant
                lesquelles la seule chose honnete a montrer est qu'on
                travaille.

                ─── CE QUI RAMENE AU FORMULAIRE ──────────────────────────

                L'echec. Si le depot ne passe pas — reseau coupe entre la
                diffusion et le relais — `depotAutoRestant` retombe a zero
                et `envoiEnCours` a faux : l'etape de paiement reapparait,
                avec le hash en place et son bouton de rattrapage. C'est le
                seul cas ou quelqu'un a de nouveau quelque chose a faire, et
                c'est alors qu'il faut le lui montrer.
                ═══════════════════════════════════════════════════════════
                */
                state.etape == EtapeChange.PAIEMENT &&
                    state.sens == SensChange.VENTE &&
                    (state.depotAutoRestant > 0 || state.envoiEnCours) ->
                    /*
                    ═══════════════════════════════════════════════════════
                    L'ATTENTE DU DEPOT N'EST PLUS UN ECRAN A ELLE
                    ═══════════════════════════════════════════════════════

                    Une vente finissait sur trois ecrans pleins : le recu de
                    l'envoi (rond vert, coche), l'attente du depot (sablier),
                    puis le recu du change (rond vert, coche). Un succes, une
                    attente, un succes — pour un seul evenement vecu.

                    Les deux derniers n'en font plus qu'un. C'est le MEME
                    appel, avec `enCours` : la carte, le rond, le halo et les
                    points sont deja a leur place quand on arrive, le sablier
                    tourne DANS le rond, puis il devient la coche. Rien ne se
                    remplace, une chose finit.

                    ─── LA CONDITION N'A PAS BOUGE D'UN CARACTERE ─────────

                    Et c'est voulu : c'est elle qui porte le chemin de
                    secours. Quand `depotAutoRestant` retombe a zero sans
                    verdict, elle devient fausse, et l'etape de paiement
                    reparait avec le hash en place et son bouton de
                    rattrapage. Fusionner les deux ecrans ne devait toucher
                    a RIEN de ce chemin-la — c'est le seul endroit du
                    parcours ou quelqu'un a encore quelque chose a faire
                    apres avoir envoye ses fonds.
                    ═══════════════════════════════════════════════════════
                    */
                    EtapeTransmis(
                        state = state,
                        copier = copier,
                        // Sans sortie pendant l'attente : `EtapeTransmis`
                        // n'affiche aucun des deux boutons tant que
                        // `enCours` est vrai. Voir son commentaire.
                        onRecommencer = {},
                        onAccueil = {},
                        enCours = true
                    )

                /*
                ═══════════════════════════════════════════════════════════
                L'ACHAT ATTEND LUI AUSSI, ET IL NE LE DISAIT PAS
                ═══════════════════════════════════════════════════════════

                « Confirmer le paiement » gardait son rond DANS le bouton
                pendant que la demande partait au relais. Une seconde ou
                trois ou l'ecran reste entier — ses deux champs, sa carte de
                verification, ses boutons — et rien ne dit que quelque chose
                se passe.

                Sur un ecran ou l'on vient de payer de l'argent reel, cette
                seconde d'incertitude se solde par un second appui. Le
                relais le rattrape — la meme reference ne passe qu'une fois
                par heure — mais c'est un garde-fou qui travaille pour
                masquer un silence qu'on peut simplement cesser de faire.
                ═══════════════════════════════════════════════════════════
                */
                state.etape == EtapeChange.PAIEMENT &&
                    state.sens == SensChange.ACHAT &&
                    state.envoiEnCours ->
                    /*
                    ═══════════════════════════════════════════════════════
                    « TRANSACTION EN COURS » SUR UN ACHAT, ET IL N'Y EN A PAS
                    ═══════════════════════════════════════════════════════

                    Ce titre etait `send_processing_title` — la chaine meme
                    de l'ecran de diffusion du Send. Deux problemes, et ils
                    vont ensemble.

                    IL EST FAUX. Sur un achat, rien ne part sur une chaine :
                    la personne a paye par Orange Money, et le telephone
                    appelle le relais. Annoncer une transaction promet un
                    hash et un explorateur qui n'existent pas.

                    ET IL EST EMPRUNTE. Le traitement, le recu et le retour
                    du change doivent se distinguer de ceux de l'envoi et du
                    swap ; partager le titre exact de l'ecran de diffusion
                    etait la facon la plus directe de les confondre.

                    ─── ET C'EST LE MEME ECRAN QU'A LA VENTE ──────────────

                    `EtapeDepotAuto` a disparu : les deux sens attendaient
                    sous un sablier avant un recu qui porte deja un rond au
                    milieu. Un achat voit donc, lui aussi, le recu se poser
                    d'abord et le sablier se changer en coche dedans.

                    Ce qui RESTE tu pendant l'attente, ce sont les trois
                    lignes du recu — ce qu'on recoit, ce qu'on a paye, la
                    reference. Elles n'ont pas encore de valeur : le relais
                    peut refuser la demande, et annoncer « Vous recevrez
                    3,27 USDT » avant qu'il l'accepte promettrait un chiffre
                    qui n'engage personne. Elles arrivent avec la coche.
                    ═══════════════════════════════════════════════════════
                    */
                    EtapeTransmis(
                        state = state,
                        copier = copier,
                        onRecommencer = {},
                        onAccueil = {},
                        enCours = true
                    )

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

    /*
    ═══════════════════════════════════════════════════════════════════════
    HORS LIGNE, L'ECRAN NE SE REDUIT PLUS A SON MESSAGE D'ERREUR
    ═══════════════════════════════════════════════════════════════════════

    SIGNALE A L'USAGE. Sans reseau, tout l'ecran disparaissait : il ne
    restait qu'un bandeau orange, un nom d'exception et un bouton
    « Reessayer ». On ouvrait « Acheter de la crypto » et on tombait sur
    une page d'erreur.

    Ce n'est pas une panne de l'application, c'est une absence de reseau —
    et les deux ne se ressemblent que parce qu'on les affichait pareil. La
    seconde est ordinaire ici, et temporaire.

    ─── L'INTERFACE RESTE, INERTE ET HONNETE ────────────────────────────

    Les onglets basculent, le champ accepte un montant. Ce que l'ecran ne
    peut PAS faire, il ne le feint pas : « Vous recevez » affiche « — »,
    parce qu'il n'y a pas de cours. C'est la meme regle que partout —
    zero serait une reponse, le tiret dit qu'on ne sait pas.

    Et le selecteur de monnaie reste ferme : la liste des monnaies vient
    du changeur, et on ne l'a pas. Offrir un choix qu'on ne connait pas
    serait inventer.

    ─── LE BOUTON PROPOSE DE SE CONNECTER ───────────────────────────────

    « Continuer » ne refuse pas en silence et ne grise pas : il dit ce qui
    manque et relance la connexion dans le meme geste. Si le reseau revient,
    les vrais parametres arrivent et cet ecran disparait de lui-meme —
    remplace par le vrai, avec le montant a retaper.

    ON NE GARDE PAS CE MONTANT. Le reproduire dans le vrai ecran
    demanderait de le faire traverser le ViewModel, et il aurait ete tape
    sans voir un seul prix. Mieux vaut qu'il soit retape en face du cours
    que de le retrouver fige a cote d'un chiffre qui a change.
    ═══════════════════════════════════════════════════════════════════════
    */
    Spacer(Modifier.height(4.dp))

    var sens by remember { mutableStateOf(SensChange.ACHAT) }
    var saisie by remember { mutableStateOf("") }
    var demande by remember { mutableStateOf(false) }
    val donneFcfa = sens == SensChange.ACHAT

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OngletSens(
            texte = stringResource(R.string.change_acheter),
            icone = Icons.Default.Add,
            choisi = donneFcfa,
            modifier = Modifier.weight(1f)
        ) { sens = SensChange.ACHAT }
        OngletSens(
            texte = stringResource(R.string.change_vendre),
            icone = Icons.Default.Sell,
            choisi = !donneFcfa,
            modifier = Modifier.weight(1f)
        ) { sens = SensChange.VENTE }
    }

    CarteMontantChange(
        libelle = stringResource(R.string.change_tu_donnes),
        montant = saisie,
        unite = if (donneFcfa) "FCFA" else state.monnaie,
        avecLogo = !donneFcfa,
        // Vide : la liste des monnaies appartient au changeur, et on ne
        // l'a pas. Un choix invente serait pire qu'aucun choix.
        monnaies = emptyList(),
        onMonnaie = {},
        onMontant = { saisie = it; demande = false },
        decimalesMax = 0
    )

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        HorizontalDivider(color = SurfaceColor)
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(BgPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.SwapVert, null,
                tint = AccentBlue.copy(alpha = 0.4f), modifier = Modifier.size(22.dp)
            )
        }
    }

    CarteMontantChange(
        libelle = stringResource(R.string.change_tu_recois),
        // Vide, donc « — » : sans cours, on ne sait pas. Zero serait une
        // reponse, et une fausse.
        montant = "",
        unite = if (donneFcfa) state.monnaie else "FCFA",
        avecLogo = donneFcfa,
        monnaies = emptyList(),
        onMonnaie = {},
        onMontant = null
    )

    if (demande) NoteEnDeuxTemps(stringResource(R.string.change_hors_ligne_action), AccentOrange)

    Button(
        onClick = { demande = true; onReessayer() },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text(stringResource(R.string.change_continuer), fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
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

    /*
    ═══════════════════════════════════════════════════════════════════════
    DEUX CARTES, UN BOUTON, ET RIEN D'AUTRE
    ═══════════════════════════════════════════════════════════════════════

    Cet ecran portait, entre la saisie et le bouton : la mention « Montant
    en FCFA », trois pastilles de montants courants, la ligne des bornes,
    la mention « Montant estime », l'arrondi « ≈ », une carte de detail
    repliable, et le delai annonce. Huit elements autour de deux nombres.

    Ils etaient tous defendables un par un — et c'est exactement comme ca
    qu'un ecran se remplit. Pris ensemble, ils repoussaient le bouton sous
    la ligne de flottaison, pour une question qui en tient deux : je donne
    combien, je recois combien.

    ─── CE QUI DISPARAIT N'EST PAS PERDU ────────────────────────────────

    Les BORNES se disaient en permanence ; elles se disent maintenant au
    moment du refus, et le message nomme le seuil qui s'applique.

    Le DELAI reapparait sur l'ecran de paiement, la ou l'on attend.

    La COMMISSION descend elle aussi sur l'ecran de paiement, au-dessus du
    bouton qui engage. Elle n'a pas ete supprimee, et elle ne doit jamais
    l'etre : un echange qui cache ce qu'il prend se fait fermer une fois.

    Les MENTIONS et l'ARRONDI, eux, ne reviennent nulle part. « Montant en
    FCFA » repetait la pastille posee juste a cote, et « ≈ 3,28 » repetait
    le chiffre pose juste au-dessus.

    Ce qui reste n'est pas moins d'information : c'est la meme, la ou elle
    sert.
    ═══════════════════════════════════════════════════════════════════════
    */
    CarteMontantChange(
        libelle = stringResource(R.string.change_tu_donnes),
        solde = if (donneFcfa) null else state.solde,
        onTout = if (donneFcfa) null else vm::onToutVendre,
        toutValeur = if (donneFcfa) null else state.solde
            ?.let { state.plusGrandMontantVendable(it) }
            ?.takeIf { it > 0.0 }
            ?.let { crypto(it, state.monnaie) },
        /*
        LE MONTANT DONNÉ EST CELUI QU'ON TAPE, TEL QU'ON LE TAPE.

        On ne le remet pas en forme pendant la frappe : quelqu'un qui écrit
        « 5000 » et voit apparaître « 5 000 » perd la place de son curseur,
        et la virgule décimale devient impossible à poser.
        */
        montant = state.saisie,
        unite = if (donneFcfa) "FCFA" else state.monnaie,
        avecLogo = !donneFcfa,
        /*
        ═══════════════════════════════════════════════════════════════════
        A LA VENTE, ON NE PROPOSE QUE CE QU'ON RACHETE
        ═══════════════════════════════════════════════════════════════════

        L'ecran basculait deja sur une monnaie vendable a l'ouverture. Mais
        la liste, elle, montrait encore les autres — et les choisir menait
        au pave rouge « cette monnaie n'est pas rachetee ».

        Proposer un choix qui ne mene nulle part, c'est faire faire un
        geste pour le refuser ensuite. La meme regle qu'a l'ouverture, un
        cran plus loin : si le refus est connu d'avance, l'option n'a pas a
        exister.

        A L'ACHAT ELLES RESTENT TOUTES, et c'est la raison d'etre de cette
        distinction : `rachetable` ne dit rien de l'achat. Une monnaie que
        le changeur vend sans la reprendre s'achete parfaitement — c'est
        exactement le cas de l'USDT-BNB aujourd'hui.

        SI AUCUNE N'EST RACHETEE, ON LES MONTRE TOUTES. Une liste vide
        n'expliquerait rien ; avec la liste entiere, on choisit, on lit le
        message rouge, et on sait pourquoi. C'est le seul cas ou il doit
        s'afficher.
        ═══════════════════════════════════════════════════════════════════
        */
        monnaies = if (donneFcfa) emptyList() else {
            p.monnaies.filter { p.rachetable(it) }.ifEmpty { p.monnaies }
        },
        onMonnaie = vm::onMonnaie,
        onMontant = vm::onSaisie,
        // AUCUNE DECIMALE DANS LES DEUX SENS : le franc n'a pas de
        // centimes, et a la vente le marquage remplace les decimales
        // tapees. Le clavier ne propose donc pas de virgule — ce que le
        // filtre refuserait de toute facon.
        decimalesMax = 0
    )

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
        /*
        ═══════════════════════════════════════════════════════════════════
        SEULS LES REFUS QUI PARLENT DU CHIFFRE ETEIGNENT LE CHIFFRE
        ═══════════════════════════════════════════════════════════════════

        Minimum, plafond, solde : dans ces trois cas la conversion est
        juste, mais elle ne se produira pas — le gris le dit.

        « gaz », « sans_adresse », « ferme », « pas_de_rachat » ne sont PAS
        de cette famille. Ils bloquent l'operation pour une raison qui n'a
        rien a voir avec le montant tape, et eteindre le resultat
        laisserait croire qu'on s'est trompe de nombre. Le message rouge
        dit deja ce qui manque ; le calcul, lui, reste bon.
        ═══════════════════════════════════════════════════════════════════
        */
        horsBornes = state.blocage() in setOf("minimum", "plafond", "solde")
    )

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
    /*
    LE MANQUE DE GAZ S'ANNONCE SANS ATTENDRE UN MONTANT, comme « ferme » et
    « pas de rachat ». Les autres refus parlent du CHIFFRE qu'on vient de
    taper — les annoncer sur un champ vide serait un reproche gratuit.
    Celui-ci parle du portefeuille : il est vrai avant la premiere frappe,
    et le dire tout de suite epargne de taper un montant pour rien.
    */
    blocage?.takeIf {
        state.saisie.isNotBlank() || it == "ferme" || it == "pas_de_rachat" ||
            it == "gaz" || it == "sans_adresse"
    }?.let {
        NoteEnDeuxTemps(
            messageBlocage(
                it, p, state.monnaie, state.fcfaParDollar, state.jetonGaz,
                state.prix, donneFcfa, state
            ),
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

/*
═══════════════════════════════════════════════════════════════════════════
CE QUE L'ÉCHANGE PREND, ET À QUEL COURS
═══════════════════════════════════════════════════════════════════════════

Cette carte vivait sur l'écran de calcul, entre les montants et le bouton
« Continuer ». Elle en est descendue avec la simplification : cet écran-là
ne porte plus que deux nombres et un bouton.

ELLE N'A PAS ÉTÉ SUPPRIMÉE, ET ELLE NE DOIT PAS L'ÊTRE. Un échange qui
n'affiche nulle part ce qu'il prend se fait fermer une fois, et ne se
rouvre pas. Elle s'est déplacée vers l'écran où l'on va payer — plus près
du moment qui engage, pas plus loin.

─── CE QUI RESTE VISIBLE, ET CE QUI SE REPLIE ─────────────────────────────

La COMMISSION ne se replie jamais : c'est le seul chiffre qu'on pourrait
être tenté de cacher, donc c'est celui qui reste.

Le PRIX DU MARCHÉ et le TAUX APPLIQUÉ se replient. Ils ne servent pas à
décider, ils servent à vérifier — et ils répondent à deux questions
différentes : le cours réel permet de COMPARER à un concurrent, le taux
appliqué dit ce qu'on obtient ici. L'écart entre les deux cesse d'être
invisible, ce qu'une seule des deux lignes ne permettait pas.

Le RÉCAPITULATIF, lui, n'est pas descendu : l'écran de paiement affiche
déjà le montant exact à envoyer et ce qu'on recevra. Deux endroits qui
portent la même somme finissent par ne plus la porter pareil.
═══════════════════════════════════════════════════════════════════════════
*/
@Composable
private fun CarteDetailChange(
    state: com.vaultex.ui.viewmodel.ChangeState,
    p: com.vaultex.domain.fiat.ParametresChange,
    donneFcfa: Boolean
) {
    val prix = state.prix ?: return
    val taux = state.fcfaParDollar ?: return

    val fraisFcfa = state.montantCrypto?.let { c ->
        c * kotlin.math.abs(
            (if (donneFcfa) prix.achatFcfa else prix.venteFcfa) - prix.baseFcfa
        )
    }
    val pourcent = TauxFcfa.margeEnPourcent(p.margeFcfaParDollar, taux)
        ?.let { "%.2f".format(it) }

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
                        /*
                        ═══════════════════════════════════════════════
                        « 0 FCFA (4,27 %) » SE CONTREDIT TOUT SEUL
                        ═══════════════════════════════════════════════

                        `fcfa` arrondit au franc — c'est juste pour les
                        montants de cet ecran, qui se comptent en
                        milliers. Sur une saisie minuscule, les frais
                        tombent a 0,085 franc et s'affichent « 0 ».

                        Une commission annoncee nulle A COTE de son
                        pourcentage est la pire des deux : elle a l'air
                        d'une erreur, et sur la seule ligne de la carte
                        qu'on ne replie jamais.

                        « < 1 FCFA » est vrai, lisible, et ne promet
                        pas la gratuite.
                        ═══════════════════════════════════════════════
                        */
                        fraisFcfa?.let { f ->
                            val montant =
                                if (f > 0.0 && f < 1.0) "< 1 FCFA" else "${fcfa(f)} FCFA"
                            montant + (pourcent?.let { " ($it %)" } ?: "")
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
            Ligne(
                stringResource(R.string.change_taux_applique, state.monnaie),
                "${fcfa(if (donneFcfa) prix.achatFcfa else prix.venteFcfa)} FCFA",
                icone = Icons.Default.Calculate
            )
        }
    }
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
                sous = if (achat) p.nomChangeur.takeIf { it.isNotBlank() } else null,
                onCopier = { copier(aCopier) }
            )
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
                    teinte = AccentBlue,
                    separateur = true
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
                    separateur = true,
                    onCopier = { copier(montantTexte) }
                )
                /*
                LE BOUTON COPIE LE NOMBRE SEUL, sans « USDT-BNB » derrière.
                C'est ce qu'on colle dans le champ Montant d'un exchange, et
                une unité collée avec ferait refuser la saisie.
                */
                /*
                ═══════════════════════════════════════════════════════
                LA NOTE SUR LE MONTANT AJUSTE EST RETIREE
                ═══════════════════════════════════════════════════════

                Elle disait : « Ce montant a ete ajuste a partir du tien :
                ses decimales identifient TON versement. Envoie-le
                exactement — arrondi, il ne sera pas reconnu. »

                Retiree sur demande, et je dois dire ce qu'elle protegeait :
                c'etait la seule phrase qui disait de NE PAS ARRONDIR. Le
                libelle « Montant exact » le sous-entend ; cette note
                l'enoncait, avec la consequence.

                Ce qui reste pour tenir ce role : le libelle, le bouton de
                copie a cote du chiffre — qui est la bonne facon de le
                reporter, et la seule qui ne perde aucune decimale — et le
                fait que le relais cherche precisement ce montant-la. Un
                versement arrondi n'est pas perdu, mais il n'est pas trouve
                tout seul : il faudra coller le hash a la main.
                ═══════════════════════════════════════════════════════
                */
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
                        vedette = true,
                        separateur = true
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
                separateur = true,
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
    LA COMMISSION EST ICI, ET PLUS SUR L'ECRAN PRECEDENT.

    Celui-la ne porte plus que deux nombres et un bouton. Celui-ci est le
    dernier avant qu'on sorte de l'argent — c'est donc ici que doit se
    lire ce que l'echange prend, pas un ecran plus tot.
    */
    CarteDetailChange(state, p, donneFcfa = achat)

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
            /*
            ═══════════════════════════════════════════════════════════
            LE CODE PERD SON BLOC, PAS SA RAISON D'ETRE
            ═══════════════════════════════════════════════════════════

            Il occupait un encadre bleu pleine largeur avec son bouton de
            copie : deux fois la hauteur d'une ligne, pour un repli — celui
            du composeur qui s'ouvre vide sur certaines surcouches
            constructeur.

            Un repli ne merite pas la place d'une action principale. Il
            devient une ligne, sous le bouton qui le compose deja, et c'est
            le TEXTE ENTIER qui se touche pour le copier — plus besoin d'une
            icone a cote.

            Il reste lisible avant d'etre envoye, ce qui etait sa seconde
            raison d'etre et la plus importante : on voit ce qu'on va
            valider sur une operation irreversible.
            */
            Text(
                code,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = AccentBlue,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { copier(code) }
                    .padding(vertical = 6.dp)
            )
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
        /*
        L'AIDE MONTE EN SOUS-TITRE, ET C'EST UNE LIGNE GAGNEE PAR CHAMP.

        Elle etait SOUS la case : le titre occupait une ligne a cote d'une
        pastille de quarante points — qui en laissait vingt vides — et
        l'aide en prenait une autre, entiere, plus bas.

        En sous-titre elle se loge dans la place deja reservee par la
        pastille. L'ecran d'achat demande deja beaucoup de defilement ; ces
        lignes-la ne coutaient rien a deplacer.

        Et elle se lit AVANT la case, ce qui est sa raison d'etre : « le
        numero de transaction recu par SMS » dit quoi chercher, pas quoi
        penser de ce qu'on vient de taper.
        */
        ChampChange(
            valeur = state.referencePaiement,
            onValeur = vm::onReferencePaiement,
            libelle = stringResource(R.string.change_ref_paiement),
            sousTitre = stringResource(R.string.change_ref_paiement_aide),
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
        /*
        ═══════════════════════════════════════════════════════════════
        LE LIBELLE NOMME L'OPERATEUR, ET NE L'ECRIT PAS EN DUR
        ═══════════════════════════════════════════════════════════════

        C'etait « Numéro Mobile Money où recevoir ». Demande : « Numéro
        Orange Money ». Plus court et plus juste — personne a Ouagadougou
        ne dit « Mobile Money », on dit Orange Money ou Moov Money.

        MAIS ON NE L'ECRIT PAS DANS LA CHAINE. Le relais donne deja le nom
        de l'operateur dans ses parametres, et c'est lui qui apparait sur
        le bouton USSD trois cents lignes plus bas. Le figer ici ferait
        afficher « Orange Money » le jour ou le changeur passe a Moov —
        c'est-a-dire envoyer des gens vers le mauvais operateur depuis un
        libelle qu'aucun reglage ne corrige.

        Le repli est « Mobile Money », pose par ChangeService quand le
        relais ne dit rien : on perd en precision, jamais en justesse.
        ═══════════════════════════════════════════════════════════════
        */
        libelle = if (achat) {
            stringResource(R.string.change_telephone)
        } else {
            stringResource(
                R.string.change_telephone_vente,
                state.parametres?.operateur ?: "Mobile Money"
            )
        },
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
        } else null,
        /*
        ═══════════════════════════════════════════════════════════════
        A QUI EST CE NUMERO, ET POUR QUOI FAIRE
        ═══════════════════════════════════════════════════════════════

        « Entrez votre numéro Orange Money » disait a qui il est, et rien
        de ce qu'il fait. Sur un ecran ou le changeur a lui aussi un numero
        — il est sur la carte du dessus, a l'achat — ca laisse la question
        ouverte : lequel des deux ? Et surtout : pourquoi ?

        « Ton numéro, pour recevoir tes francs » repond aux deux en six
        mots. C'est le seul endroit du parcours ou la personne donne une
        information sur ELLE, et la seule chose qui compte est que l'argent
        y arrive.

        ─── ET EN TUTOYANT, COMME LE RESTE DE L'ECRAN ────────────────

        « Entrez votre » etait de moi, et c'etait un corps etranger : cet
        ecran dit « Tu reçois », « Ta référence », « Ta vente est partie ».
        Une seule phrase au vouvoiement au milieu se lit comme un texte
        colle d'ailleurs — et sur un ecran d'argent, ce qui a l'air colle
        d'ailleurs fait douter du reste.

        Le nom de l'operateur n'est plus dans cette phrase : le titre,
        juste au-dessus, le porte deja et le tient du relais. Le repeter a
        dix points d'intervalle coutait la place qui sert maintenant a dire
        a quoi sert le champ.
        ═══════════════════════════════════════════════════════════════
        */
        sousTitre = if (achat) {
            // Meme gain qu'au-dessus : « Le numero qui a fait le paiement »
            // se loge a cote de la pastille au lieu d'occuper sa propre
            // ligne sous la case.
            stringResource(R.string.change_telephone_aide)
        } else {
            stringResource(R.string.change_telephone_vente_consigne)
        },
        marque = if (achat) null else R.drawable.logo_orange_money,
        indicatif = stringResource(R.string.indicatif_pays)
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
            } else null,
            /*
            LA CONSIGNE MONTE SOUS LE TITRE. « Retape le même numéro, pour
            être sûr » etait SOUS la case — c'est-a-dire apres l'avoir
            remplie. Une consigne qu'on lit une fois le geste fait n'est
            plus une consigne, c'est un commentaire.

            L'aide sous la case ne la repete pas : `ChampChange` l'ecarte
            quand elle est deja en sous-titre, et garde la place pour
            l'erreur — « Les deux numéros ne sont pas identiques », qui
            est la seule chose qui doive encore apparaitre la.
            */
            sousTitre = stringResource(R.string.change_numero_confirme),
            /*
            LA MARQUE DANS LA CASE, MAIS PAS EN PASTILLE. Ce champ ne
            demande pas un numero d'operateur, il demande de REPETER celui
            du dessus : son telephone bleu dit « meme chose qu'au-dessus »
            la ou un second logo Orange Money ferait croire a un second
            numero a fournir. Dans la case, en revanche, c'est bien le
            meme numero Orange Money qu'on retape.
            */
            marque = R.drawable.logo_orange_money,
            indicatif = stringResource(R.string.indicatif_pays)
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
        /*
        ═══════════════════════════════════════════════════════════════════
        LE PRIX EXPIRE ETEINT LE BOUTON — CE QU'IL NE POUVAIT PAS FAIRE AVANT
        ═══════════════════════════════════════════════════════════════════════

        Le compte a rebours disait « Le prix affiché n'est plus garanti » et
        le bouton restait allume. On pouvait vendre sur un prix vieux de
        vingt minutes, et la perte etait pour le changeur : le client a
        envoye sa crypto au prix d'il y a vingt minutes, et il faut payer ce
        prix-la.

        ─── POURQUOI IL ETAIT RESTE ALLUME, ET POURQUOI CA A CHANGE ───────

        Dans l'ancien parcours, on copiait l'adresse, on SORTAIT envoyer sa
        crypto ailleurs, puis on revenait. Eteindre le bouton a ce
        moment-la aurait piege quelqu'un qui avait DEJA ENVOYE : crypto
        partie, demande impossible a deposer. C'etait la bonne decision pour
        ce parcours.

        Ce n'est plus le parcours. « Vendre maintenant » est aujourd'hui
        presse AVANT que quoi que ce soit ne parte — il ouvre l'ecran
        d'envoi, et la crypto ne bouge qu'apres le code PIN. L'eteindre ne
        piege donc plus personne : on revient au calcul, on reprend un prix
        frais, trente secondes.

        ─── ET LE RATTRAPAGE, LUI, RESTE VIVANT ──────────────────────────

        « Confirmer l'envoi », plus bas, sert a qui a deja envoye. Il n'est
        pas touche : c'est exactement le cas que l'ancienne decision
        protegeait, et il a maintenant son propre bouton. Les deux
        situations sont enfin separees.

        LE RELAIS RESTE LE VRAI GARDE-FOU. Il recalcule le prix du marche au
        depot et refuse au-dela de trois pour cent d'ecart. Ceci ne le
        remplace pas : ca evite d'y arriver.
        ═══════════════════════════════════════════════════════════════════
        */
        val expire = prixExpire(state.confirmeA, p.delaiMinutes)
        if (state.txidSaisi.isNotBlank()) {
            Note(stringResource(R.string.change_crypto_partie), AccentGreen)
        } else if (expire) {
            /*
            ─── PAS UN BOUTON GRIS, LE BOUTON QUI DEBLOQUE ────────────────

            Eteindre « Vendre maintenant » aurait laisse un rectangle gris
            au milieu de l'ecran, et la question qui va avec — deja posee
            sur une capture : « le bouton confirmer est gris pourquoi ».

            La ligne rouge du dessus dit la cause, mais elle est au-dessus
            des deux champs de numero : on la lit avant, on bute apres.

            Le bouton devient donc l'action qui sort de la situation.
            Reprendre un prix coute trente secondes : le montant est garde,
            seul le cours est refait.
            */
            Button(
                onClick = vm::retourAuCalcul,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Calculate, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.change_reprendre_prix),
                    fontWeight = FontWeight.Bold
                )
            }
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
                                reference = state.reference,
                                // Mis en forme ICI, pas la-bas : un second
                                // arrondi donnerait deux chiffres differents
                                // pour la meme vente.
                                fcfa = state.montantFcfa?.let { fcfa(it) } ?: ""
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

    /*
    EN BAS, ET PAS EN HAUT. C'est l'ecran ou l'on paie : ce qu'il doit
    porter en premier, ce sont le numero, le montant et la reference. Une
    offre d'aide posee avant eux ferait craindre une difficulte avant
    qu'elle existe.

    Mais il faut qu'elle soit LA, sur cet ecran-ci : c'est celui qu'on
    regarde en hesitant a envoyer de l'argent a quelqu'un qu'on ne connait
    pas. Un numero burkinabe lisible au bas de la page repond a cette
    hesitation mieux qu'une phrase rassurante.
    */
    AssistanceChange(state.reference)
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
/**
 * Vrai quand le prix garanti a expire, et il bascule TOUT SEUL.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * POURQUOI UNE HORLOGE PLUTOT QU'UNE SIMPLE COMPARAISON
 * ═══════════════════════════════════════════════════════════════════════
 *
 * `System.currentTimeMillis() > fin` suffirait — a la premiere lecture. Mais
 * Compose ne recompose pas parce que l'heure avance : l'ecran resterait fige
 * sur la reponse calculee a l'ouverture, et le bouton resterait allume une
 * heure apres l'expiration.
 *
 * Le compte a rebours, lui, bat deja a la seconde. Il le fait pour lui-meme,
 * dans son propre composable, et rien de cet etat ne sortait. Cette fonction
 * tient la meme horloge et la rend lisible ailleurs.
 *
 * ELLE S'ARRETE A L'EXPIRATION. Une boucle qui continue de battre apres coup
 * reveille l'ecran chaque seconde pour redire la meme chose, sur des
 * telephones qui sont la cible.
 * ═══════════════════════════════════════════════════════════════════════
 */
@Composable
private fun prixExpire(confirmeA: Long, delaiMinutes: Int): Boolean {
    if (confirmeA <= 0L) return false
    val fin = confirmeA + delaiMinutes * 60_000L
    var maintenant by remember(fin) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(fin) {
        while (System.currentTimeMillis() < fin) {
            kotlinx.coroutines.delay(1_000)
            maintenant = System.currentTimeMillis()
        }
        maintenant = System.currentTimeMillis()
    }
    return maintenant >= fin
}

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

    androidx.compose.material3.HorizontalDivider(
        color = BgTertiary.copy(alpha = 0.7f),
        thickness = 1.dp
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        /*
        LA MEME PASTILLE QUE LES AUTRES LIGNES, A SA COULEUR.

        L'horloge etait posee a nu, a quinze points, quand les quatre
        lignes au-dessus portent une pastille de quarante. C'etait la
        derniere ligne de la carte, et elle avait l'air d'une note de bas
        de page plutot que d'une information de meme rang.

        Sa teinte suit le compte a rebours — ambre, puis rouge sous deux
        minutes — donc la pastille change de couleur avec le chiffre. Ce
        n'est pas un ornement : c'est la seule ligne de cet ecran dont la
        valeur se perime.
        */
        Box(
            Modifier.size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(couleur.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Schedule, null, tint = couleur, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(
                if (fini) R.string.change_prix_expire else R.string.change_prix_garanti
            ),
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        if (!fini) {
            val m = restant / 60_000L
            val sec = (restant % 60_000L) / 1_000L
            Text(
                "%d:%02d".format(m, sec),
                fontSize = 16.sp,
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
                stringResource(R.string.change_verif_achat_absent_detail)
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
            stringResource(R.string.change_verif_absent_detail)
        AffichageVerif.DEJA_SERVI -> stringResource(R.string.change_verif_deja_servi_detail)
        AffichageVerif.IMPOSSIBLE ->
            if (raison.isNotBlank()) {
                stringResource(R.string.change_verif_impossible_detail_raison, raison)
            } else {
                stringResource(R.string.change_verif_impossible_detail)
            }
    }
}

/**
 * Une ligne du recu : le libelle a gauche, la valeur a droite.
 *
 * PAS `Ligne`, ET C'EST VOULU. Celle-ci porte une pastille de quarante
 * points par ligne — juste, sur la carte de paiement ou cinq lignes se
 * ressemblent et ou l'oeil a besoin de reperes. Ici il y en a trois, dans
 * une carte deja dominee par un grand rond vert : trois pastilles de plus
 * feraient un inventaire la ou il faut un recu.
 */
/**
 * « 0xbde137a9…5831e327e » — assez pour reconnaitre, pas assez pour noyer.
 *
 * Six caracteres de chaque cote : la forme reste identifiable d'un coup
 * d'oeil, et la valeur entiere reste a un appui sur l'icone de copie. En
 * dessous de vingt caracteres, on affiche tout : une adresse courte
 * abregee serait plus longue a lire que l'originale.
 */
private fun abregerAdresse(a: String): String =
    if (a.length <= 20) a else "${a.take(8)}…${a.takeLast(6)}"

@Composable
private fun LigneRecu(
    libelle: String,
    valeur: String,
    vedette: Boolean = false,
    onCopier: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(libelle, fontSize = 14.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(
            valeur,
            fontSize = if (vedette) 18.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (vedette) AccentGreen else TextPrimary
        )
        onCopier?.let {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.ContentCopy, stringResource(R.string.copy),
                tint = AccentBlue,
                modifier = Modifier.size(18.dp).clickable { it() }
            )
        }
    }
}

// ─── Temps 3 : transmis ─────────────────────────────────────────────

@Composable
private fun ColumnScope.EtapeTransmis(
    state: com.vaultex.ui.viewmodel.ChangeState,
    /** Pour copier la reference du recu d'achat. */
    copier: (String) -> Unit,
    onRecommencer: () -> Unit,
    /*
    ═══════════════════════════════════════════════════════════════════
    IL N'Y AVAIT AUCUNE SORTIE
    ═══════════════════════════════════════════════════════════════════

    Cet ecran est le dernier du parcours : la demande est partie, il n'y a
    plus rien a faire. Et il ne proposait qu'une chose, « Nouvelle
    demande », c'est-a-dire recommencer.

    Celui qui vient de vendre ne veut, neuf fois sur dix, ni recommencer
    ni rester la. Il lui restait le bouton retour du telephone — qui ne
    dit nulle part ou il mene — et la barre du bas si elle est visible.

    Un ecran terminal doit nommer sa sortie. C'est la meme regle que sur
    le recu d'envoi, dont le bouton principal est « Terminé » : une
    operation finie se ferme, elle ne se relance pas par defaut.

    ─── EN SECOND, PAS EN PREMIER ────────────────────────────────────

    « Nouvelle demande » garde le bouton plein. Quelqu'un qui est encore
    sur cet ecran y est reste pour une raison, et la plus courante est
    d'en relancer une — sinon il serait deja parti. L'accueil est en
    texte, comme « Revenir au calcul » deux etapes plus tot : une sortie
    qui ne se cherche pas, mais qui ne reclame pas le regard.
    ═══════════════════════════════════════════════════════════════════
    */
    onAccueil: () -> Unit,
    /*
    ═══════════════════════════════════════════════════════════════════
    LE MEME ECRAN AVANT ET APRES — C'EST TOUT L'INTERET
    ═══════════════════════════════════════════════════════════════════

    Une vente finissait par TROIS ecrans pleins pour un seul evenement
    vecu : le recu de l'envoi (vert, coche), l'attente du depot (sablier),
    puis ce recu-ci (vert, coche). Un succes, une attente, un succes.

    L'attente est reelle — la demande part au relais, ca prend quelques
    secondes, et ca peut echouer. Ce qui ne l'etait pas, c'est le besoin
    d'un ECRAN pour elle. Cet ecran-ci existe deja, il connait tout ce
    qu'il faut dire, et il ne lui manquait qu'un etat.

    `enCours` est donc une attente qui se resout SUR PLACE : la silhouette
    ne bouge pas, le rond reste vert et a la meme taille, les quatre
    points restent. Seul le dedans du rond passe du sablier a la coche, et
    les deux lignes de texte changent. Rien ne saute, rien ne se recharge
    — on voit une chose finir, pas un ecran remplacer un autre.

    ─── POURQUOI LE VERT DES L'ATTENTE ────────────────────────────────

    Parce que ce qui comptait a deja reussi : la crypto est partie, c'est
    irreversible, et c'est le seul moment du parcours ou l'utilisateur
    engageait quelque chose. Ce qui reste est une notification qui se
    rejoue toute seule. Du bleu, puis du vert, ferait lire un basculement
    d'etat la ou il n'y a qu'une etape qui se termine.

    ─── LES DEUX BOUTONS DISPARAISSENT, ET C'EST OBLIGATOIRE ──────────

    `onRecommencer` remet l'etat a zero. Offert pendant que la demande est
    en vol, il permettrait d'effacer une vente dont la crypto est deja
    partie — le pire bouton qu'on puisse poser sur un ecran. `onAccueil`
    passe par le meme chemin. Aucune sortie tant que ce n'est pas fini :
    c'est justement ce que dit « ne fermez pas l'application ».

    ─── ET L'ECHEC RAMENE OU IL RAMENAIT DEJA ─────────────────────────

    La condition qui choisit cet etat est celle qui choisissait l'ecran
    d'attente, inchangee. Quand `depotAutoRestant` retombe a zero sans
    verdict, elle devient fausse et l'etape de paiement reparait avec son
    bouton de rattrapage, exactement comme avant la fusion.
    ═══════════════════════════════════════════════════════════════════
    */
    enCours: Boolean = false
) {
    /*
    ═══════════════════════════════════════════════════════════════════
    UNE CARTE BLANCHE, ET LE ROND AU MILIEU
    ═══════════════════════════════════════════════════════════════════

    C'etait un aplat vert pale avec une coche de vingt points a gauche
    d'un titre : la forme d'une NOTE, celle qu'on emploie dans cet ecran
    pour un avertissement ou une precision. Or c'est le dernier ecran du
    parcours, celui qui annonce que tout est fini. Il se lisait comme un
    encadre au milieu d'un formulaire.

    La carte blanche et le grand rond bleu lui donnent le poids d'une
    conclusion — la meme grammaire que le recu d'envoi, qui porte lui
    aussi son rond coche au centre. Deux ecrans de fin, deux fois la meme
    silhouette.

    ─── LE HALO ET LES QUATRE POINTS ─────────────────────────────────

    Decoratifs, et ils le restent : ils ne portent aucune information.
    Mais ce sont eux qui font la difference entre une coche posee sur un
    fond et un ecran qui CELEBRE quelque chose — et quelqu'un qui vient de
    confier de l'argent merite que la fin le dise.

    ─── VERT, COMME LE RECU D'ENVOI ──────────────────────────────────

    Je l'avais mis en bleu, en raisonnant que le vert dit « ce que tu
    recois » et qu'on ne recoit rien a cet instant precis. C'etait vrai de
    la couleur prise isolement, et faux de l'ecran.

    Parce que le recu d'envoi, deux ecrans plus tot, porte exactement le
    meme grand rond coche, en vert. Deux ecrans de fin a trente secondes
    d'intervalle, dans le meme parcours, avec la meme silhouette et deux
    couleurs : on ne lit pas « l'un recoit et l'autre non », on lit deux
    applications differentes.

    La coherence d'un parcours passe avant la logique d'une couleur prise
    seule. Vert aux deux bouts.

    ─── MAIS PAS DE DEGRADE ──────────────────────────────────────────

    La maquette montre un bouton en degrade. Il est pose en aplat : les
    degrades ont ete retires de cet ecran a la demande, et en reintroduire
    un ici ferait revenir par la fenetre ce qu'on a sorti par la porte.
    ═══════════════════════════════════════════════════════════════════
    */
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(112.dp).clip(CircleShape)
                        .background(AccentGreen.copy(alpha = 0.10f))
                )
                Box(
                    Modifier.size(82.dp).clip(CircleShape).background(AccentGreen),
                    contentAlignment = Alignment.Center
                ) {
                    // LA SEULE CHOSE QUI BOUGE entre l'attente et la fin.
                    // Le rond, le halo et les points restent identiques :
                    // on voit une etape se terminer, pas un ecran changer.
                    if (enCours) {
                        CircularProgressIndicator(
                            Modifier.size(34.dp),
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Check, null,
                            tint = Color.White, modifier = Modifier.size(42.dp)
                        )
                    }
                }
                // Les quatre points, poses a la main : une poignee de
                // pixels qui transforment une coche en evenement.
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = 16.dp)
                        .size(9.dp).clip(CircleShape).background(AccentGreen)
                )
                Box(
                    Modifier.align(Alignment.TopStart).offset(x = 18.dp, y = 30.dp)
                        .size(7.dp).clip(CircleShape).background(AccentGreen.copy(alpha = 0.55f))
                )
                Box(
                    Modifier.align(Alignment.CenterStart).offset(x = 8.dp, y = 18.dp)
                        .size(6.dp).clip(CircleShape).background(AccentGreen.copy(alpha = 0.45f))
                )
                Box(
                    Modifier.align(Alignment.CenterEnd).offset(x = (-6).dp, y = 10.dp)
                        .size(8.dp).clip(CircleShape).background(AccentGreen.copy(alpha = 0.7f))
                )
            }
            Text(
                stringResource(
                    when {
                        // Les deux attentes ne font pas le meme travail, et
                        // la difference compte : une vente a deja mis sa
                        // crypto sur la chaine et il reste a PREVENIR, un
                        // achat a paye par Orange Money et il reste a
                        // TRANSMETTRE. Le titre nomme ce qui se passe.
                        !enCours -> R.string.change_transmis_titre
                        state.sens == SensChange.ACHAT ->
                            R.string.change_depot_achat_titre
                        else -> R.string.change_depot_vente_titre
                    }
                ),
                fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            /*
            ═══════════════════════════════════════════════════════════════
            UNE PHRASE QUI RASSURE, AU LIEU D'UN PARAGRAPHE QUI INSTRUIT
            ═══════════════════════════════════════════════════════════════

            C'etait : « Ta demande VX-4N7SVT2D est partie. Garde cette
            référence : c'est elle qui identifie ton opération. Tu recevras
            tes fonds une fois le paiement vérifié. »

            Trois phrases, dont deux consignes, sur l'ecran qui annonce que
            tout est fini. On venait de confier de l'argent : la seule chose
            qu'on cherche a cet instant est de savoir qu'il va revenir, et
            il fallait lire jusqu'au bout de la troisieme phrase pour le
            trouver.

            « Garde cette référence » demandait en plus un geste que
            personne ne fait — on ne recopie pas huit caracteres sur un
            ecran qu'on va quitter. Et c'est inutile : la reference est dans
            « Mes dernières demandes », sur l'ecran de change, avec son
            montant et sa date. Elle n'etait pas gardee la, elle y est
            rangee.

            Reste ce qui compte : « Tu recevras tes fonds dans un instant. »
            Le format de la chaine ne prend plus la reference — elle n'y a
            plus sa place, et la passer quand meme ferait trainer un
            argument que personne ne lit.
            ═══════════════════════════════════════════════════════════════
            */
            Text(
                // « Ne fermez pas l'application » VIENT DE L'ECRAN D'ATTENTE,
                // et c'est la seule chose qu'il disait qu'on ne pouvait pas
                // perdre : la boucle de depot s'arrete avec la composition.
                stringResource(
                    if (enCours) R.string.change_depot_ne_ferme_pas
                    else R.string.change_transmis_detail
                ),
                fontSize = 15.sp, color = TextSecondary, lineHeight = 21.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            /*
            ═══════════════════════════════════════════════════════════════
            UN RECU POUR L'ACHAT, PARCE QU'IL N'EN AVAIT AUCUN
            ═══════════════════════════════════════════════════════════════

            La vente en a un : apres le code PIN, l'ecran dit « Ta vente est
            partie », avec les francs en grand et le numero qui les recevra.

            L'achat n'avait rien. On venait de payer de l'argent reel par
            Orange Money, et l'ecran final disait « Demande transmise. Tu
            recevras tes fonds dans un instant. » Pas un chiffre. Ni ce
            qu'on a paye, ni ce qu'on va recevoir, ni sous quelle
            reference — alors que c'est l'ecran qu'on regarde en se
            demandant si on a bien fait.

            TROIS LIGNES, ET CHACUNE REPOND A UNE QUESTION :

              — ce qu'on va RECEVOIR, en vert et en vedette : c'est ce
                qu'on est venu chercher, et le seul chiffre qui decide ;
              — ce qu'on a PAYE, pour le rapprocher du SMS d'Orange Money
                qui vient d'arriver ;
              — la REFERENCE, avec sa copie. Je l'avais retiree du texte
                au-dessus en disant qu'elle est dans « Mes dernières
                demandes ». C'est vrai, et ca suffit pour une vente —
                verifiee sur la chaine, dont rien ne depend d'un humain.
                Pas pour un achat : la seule chose qui rattache un
                paiement Orange Money a cette demande, c'est elle, et
                c'est elle qu'on donnera si rien n'arrive.

            SUR UNE VENTE, RIEN DE TOUT CA. Elle a deja eu son recu deux
            ecrans plus tot, avec les memes chiffres. Les redire ici en
            ferait une troisieme lecture.
            ═══════════════════════════════════════════════════════════════
            */
            if (state.sens == SensChange.ACHAT && !enCours) {
                HorizontalDivider(color = BgPrimary)
                state.montantCrypto?.let { recu ->
                    LigneRecu(
                        stringResource(R.string.change_tu_recevras),
                        "${crypto(recu, state.monnaie)} ${state.monnaie}",
                        vedette = true
                    )
                }
                state.montantFcfa?.let { paye ->
                    LigneRecu(stringResource(R.string.change_paye), "${fcfa(paye)} FCFA")
                }
                if (state.reference.isNotBlank()) {
                    LigneRecu(
                        stringResource(R.string.change_reference),
                        state.reference,
                        onCopier = { copier(state.reference) }
                    )
                }
                /*
                ═══════════════════════════════════════════════════════════
                OU LA CRYPTO VA ARRIVER
                ═══════════════════════════════════════════════════════════

                C'est le seul moment du parcours ou quelque chose de
                l'utilisateur part sans qu'il l'ait vu : l'application
                derive son adresse et la glisse dans la demande, en
                silence. Sur une vente, il voit l'adresse du changeur avant
                d'envoyer ; ici, rien.

                Le recu ferme la boucle — voila ou ca arrivera — et donne
                la reponse a la premiere question d'un depannage : « sur
                quelle adresse ? ».

                ─── RACCOURCIE, ET C'EST SANS RISQUE ICI ─────────────────

                Quarante-deux caracteres transformeraient ce recu en mur.
                Et l'avertissement contre l'« address poisoning », ecrit
                ailleurs dans ce projet, ne s'applique pas : il vaut pour
                une adresse VERS LAQUELLE on envoie, ou un attaquant peut
                en glisser une qui se ressemble. Celle-ci est la sienne,
                derivee de sa phrase — personne ne peut la substituer sans
                deja tout controler.

                Le bouton de copie rend la valeur entiere.
                ═══════════════════════════════════════════════════════════
                */
                state.adresseReception?.takeIf { it.isNotBlank() }?.let { adr ->
                    LigneRecu(
                        stringResource(R.string.change_adresse_reception),
                        abregerAdresse(adr),
                        onCopier = { copier(adr) }
                    )
                }
            }
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
            // IL N'Y A PAS ENCORE DE VERDICT PENDANT L'ATTENTE : c'est le
            // depot qui le rend, et le depot est justement en cours.
            val v = state.verification
            if (state.sens == SensChange.VENTE && v != null && !enCours) {
                HorizontalDivider(color = BgPrimary)
                if (v.estVert) {
                    Row(verticalAlignment = Alignment.Top) {
                        // La coche DANS une pastille, comme la maquette : a
                        // quinze points nus elle se perdait contre le texte.
                        Box(
                            Modifier.size(28.dp).clip(CircleShape)
                                .background(AccentGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check, null,
                                tint = AccentGreen, modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.change_transmis_verifie),
                            fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp
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
    /*
    AUCUNE SORTIE TANT QUE LA DEMANDE EST EN VOL.

    `onRecommencer` remet l'etat a zero : offert pendant le depot, il
    effacerait une vente dont la crypto est deja partie. `onAccueil` passe
    par le meme chemin. Les deux reviennent quand c'est fini — et d'ici la
    le texte juste au-dessus dit pourquoi il n'y a rien a toucher.
    */
    if (enCours) return

    /*
    DEUX BOUTONS DE MEME TAILLE, ET LA HIERARCHIE PASSE PAR LE REMPLISSAGE.

    « Retour à l'accueil » etait un simple texte. A cote d'un bouton plein,
    un texte se lit comme un lien secondaire — ce qu'il est — mais sur un
    ecran ou il n'y a QUE deux sorties, il devenait difficile a viser : pas
    de contour, pas de surface, une ligne au milieu du vide.

    Il prend le meme gabarit, en contour. L'un est plein, l'autre creux :
    la hierarchie reste lisible, et les deux se touchent aussi facilement.
    */
    Button(
        onClick = onRecommencer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(54.dp)
    ) {
        Icon(Icons.Default.AddCircleOutline, null, modifier = Modifier.size(20.dp))
        Text(
            stringResource(R.string.change_nouvelle),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp))
    }
    OutlinedButton(
        onClick = onAccueil,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BgTertiary),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceColor),
        modifier = Modifier.fillMaxWidth().height(54.dp)
    ) {
        Icon(
            Icons.Default.Home, null,
            tint = AccentBlue, modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.change_retour_accueil),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = AccentBlue
        )
    }

    /*
    APRES LES DEUX SORTIES, ET C'EST SA PLACE.

    Neuf personnes sur dix quittent cet ecran sans rien attendre : la
    demande est partie, les fonds arrivent. L'aide ne doit pas s'imposer a
    elles.

    La dixieme revient ici — par « Mes dernieres demandes » — parce que
    rien n'est arrive. Elle descend, et le numero est au bout. C'est le
    seul endroit de l'application ou elle le cherchera.

    LA REFERENCE PART AVEC LE MESSAGE WHATSAPP. C'est exactement ce qu'on
    lui redemanderait, et elle vient de quitter l'ecran qui l'affichait.
    */
    AssistanceChange(state.reference)
}

/*
═══════════════════════════════════════════════════════════════════════════
LE NUMÉRO QU'ON CHERCHE QUAND ÇA NE SE PASSE PAS COMME PRÉVU
═══════════════════════════════════════════════════════════════════════════

Tout le reste de ce parcours suppose que ça marche. Le paiement part, le
changeur reçoit, les fonds arrivent. Et neuf fois sur dix c'est vrai.

La dixième, quelqu'un a envoyé de l'argent réel et il attend. Il relit
l'écran, il cherche une sortie, il n'en trouve pas — et il conclut, parce
que c'est la seule conclusion disponible, qu'il n'y a personne au bout.
Les minutes qu'il passe là sont les pires de tout le parcours.

─── ET CE N'EST PAS QU'UN DÉPANNAGE ──────────────────────────────────────

Un numéro visible AVANT de payer ne sert pas à appeler : il sert à ne pas
avoir à le faire. Sur un marché où l'on confie son argent à une
application récente, pouvoir lire un numéro burkinabè qui répond est ce
qui fait appuyer sur le bouton. Il est donc posé sur l'écran de paiement,
et pas seulement sur celui qui annonce que c'est fini.

C'est aussi pour ça que le numéro s'affiche EN TOUTES LETTRES au lieu de
se cacher derrière deux boutons : un bouton promet un appel, un numéro
écrit prouve qu'il existe. On peut le recopier, le montrer à quelqu'un, le
chercher dans ses contacts — trois gestes qu'un bouton ne permet pas.

─── DEUX CANAUX, PARCE QU'ON S'EN SERT DIFFÉREMMENT ──────────────────────

On appelle quand c'est urgent. On écrit sur WhatsApp quand il faut joindre
une capture du SMS Orange Money — et c'est souvent ce qu'on demandera.

LE MESSAGE PART DÉJÀ RÉDIGÉ, avec la référence dedans. C'est la première
chose qu'on redemanderait, et justement celle que la personne n'a plus
sous les yeux une fois WhatsApp ouvert.

ACTION_DIAL ET JAMAIS ACTION_CALL, comme pour le code USSD plus haut : on
compose le numéro, on ne le lance pas. Aucune permission, et personne ne
voit un appel partir sans l'avoir décidé.
═══════════════════════════════════════════════════════════════════════════
*/
@Composable
private fun AssistanceChange(reference: String) {
    val contexte = androidx.compose.ui.platform.LocalContext.current
    val message = if (reference.isBlank()) {
        stringResource(R.string.change_assistance_message_simple)
    } else {
        stringResource(R.string.change_assistance_message, reference)
    }

    /*
    DEUX ACTIONS, ET PAS LA MEME POUR LES DEUX BOUTONS.

    ACTION_DIAL pour le numero : c'est l'action documentee pour « tel: »,
    elle n'exige aucune permission, et elle COMPOSE sans appeler — la regle
    deja posee pour le code USSD plus haut. ACTION_VIEW resout « tel: » sur
    la plupart des telephones, mais « la plupart » n'est pas une garantie.

    ACTION_VIEW pour le lien wa.me, qui est une URL : il ouvre WhatsApp s'il
    est installe, le navigateur sinon.

    `runCatching` sur les deux : une tablette n'a pas de composeur, et un
    telephone sans navigateur ni WhatsApp ne resout pas le lien. Dans les
    deux cas le numero reste lisible juste au-dessus, et c'est la vraie
    issue de secours.
    */
    fun ouvrir(action: String, lien: String) {
        runCatching {
            contexte.startActivity(
                android.content.Intent(action, android.net.Uri.parse(lien))
            )
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .background(AccentBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SupportAgent, null,
                        tint = AccentBlue, modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.change_assistance_titre),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                    )
                    Text(
                        com.vaultex.domain.support.ContactsVaultex.ASSISTANCE_CHANGE_AFFICHE,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentBlue
                    )
                }
            }
            Text(
                stringResource(R.string.change_assistance_detail),
                fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        ouvrir(
                            android.content.Intent.ACTION_DIAL,
                            com.vaultex.domain.support.ContactsVaultex
                                .LIEN_ASSISTANCE_CHANGE_APPEL
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BgTertiary),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = BgTertiary),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(
                        Icons.Default.Call, null,
                        tint = AccentBlue, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        stringResource(R.string.change_assistance_appel),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentBlue,
                        maxLines = 1
                    )
                }
                OutlinedButton(
                    onClick = {
                        ouvrir(
                            android.content.Intent.ACTION_VIEW,
                            com.vaultex.domain.support.ContactsVaultex
                                .lienAssistanceChange(message)
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    // AUX COULEURS DE WHATSAPP, comme le bouton USSD porte
                    // celles d'Orange Money : on reconnait le canal avant
                    // d'avoir lu le mot.
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, VertWhatsApp.copy(alpha = 0.45f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = VertWhatsApp.copy(alpha = 0.10f)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(
                        Icons.Default.Chat, null,
                        tint = VertWhatsApp, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        stringResource(R.string.change_assistance_whatsapp),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VertWhatsApp,
                        maxLines = 1
                    )
                }
            }
        }
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
    /**
     * Vrai quand le montant saisi ne peut pas passer — sous le minimum,
     * au-dessus du plafond, au-dessus du solde.
     *
     * NE CHANGE QUE LA COULEUR, jamais le chiffre. Voir plus bas pourquoi
     * on l'eteint plutot que de l'effacer.
     */
    horsBornes: Boolean = false
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
                // Grise quand le montant ne passe pas : le vert est la
                // couleur de ce qu'on va recevoir, et on ne va rien
                // recevoir de cette saisie-la.
                color = if (onMontant != null) BgTertiary
                        else if (horsBornes) TextSecondary.copy(alpha = 0.08f)
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
                                /*
                                ═══════════════════════════════════════════
                                LE VERT EST UNE PROMESSE, ET ELLE S'ETEINT
                                ═══════════════════════════════════════════

                                Le montant recu en vert : c'est le seul
                                chiffre qui decide, et il garde la couleur
                                qu'il avait quand il etait la ligne vedette
                                des details.

                                MAIS LE VERT DIT « VOILA CE QUE TU AURAS ».
                                Sous le minimum, l'ecran affichait ce vert
                                sous une saisie refusee : un chiffre qui a
                                l'air d'un resultat, au-dessus d'un bouton
                                eteint et d'un message rouge. Trois signaux,
                                dont un qui contredit les deux autres.

                                ─── POURQUOI L'ETEINDRE ET NON L'EFFACER ──

                                Parce que le chiffre RENSEIGNE encore. « 1
                                000 FCFA donneraient 1,57 USDT » dit de
                                combien on est loin du seuil, et c'est ce
                                qui permet de corriger du premier coup. Le
                                masquer rendrait la carte vide a chaque
                                frappe intermediaire — on tape « 1 », puis
                                « 10 », puis « 100 » : trois etats sous le
                                minimum avant le bon.

                                Le gris retire la promesse sans retirer
                                l'information. C'est exactement ce que fait
                                deja le bouton a cote : il reste la, il ne
                                repond plus.
                                ═══════════════════════════════════════════
                                */
                                color = if (montant.isEmpty() || horsBornes) TextSecondary
                                        else AccentGreen,
                                maxLines = 1
                            )
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
                    /*
                    ═══════════════════════════════════════════════════════
                    LA LISTE RESSEMBLE ENFIN A CE QU'ELLE CHOISIT
                    ═══════════════════════════════════════════════════════

                    C'etait `DropdownMenuItem(text = { Text(m) })` : trois
                    lignes de texte nu — « USDT-BNB », « USDT », « USDT-ETH »
                    — dans une application ou chaque autre liste porte le
                    logo de sa monnaie.

                    LE PROBLEME N'EST PAS L'ELEGANCE. La pastille fermee, au-
                    dessus, affiche DEJA le logo et le reseau en toutes
                    lettres ; c'est la liste qui les perdait, au moment precis
                    ou l'on choisit. On lisait le reseau pour verifier son
                    choix, puis on ouvrait une liste qui ne le disait plus.

                    Et trois caracteres separent « USDT » de « USDT-BNB ». Le
                    commentaire du reseau en toutes lettres, trente lignes
                    plus haut, dit ce que coute cette confusion : un BEP-20
                    envoye a une adresse TRON est perdu definitivement.

                    ─── CE QUE PORTE CHAQUE LIGNE ────────────────────────

                    Le logo de la monnaie a gauche, de la MEME source que
                    partout ailleurs. Le symbole, le reseau en toutes lettres
                    dessous. Puis, a droite, le logo de la CHAINE — le rouge
                    de TRON, le losange d'Ethereum : la monnaie se ressemble
                    d'une chaine a l'autre, c'est la chaine qui differe, et
                    c'est elle qu'on doit reconnaitre sans lire.

                    Enfin la marque de selection. Elle est sur TOUTES les
                    lignes, pleine sur celle qui est choisie et vide sur les
                    autres : une marque qui n'apparait que sur un element se
                    lit comme une decoration, deux etats se lisent comme un
                    choix.

                    ─── DES LIGNES CONSTRUITES, PAS DES DropdownMenuItem ──

                    `DropdownMenuItem` impose sa hauteur et ses marges. A
                    cinquante-six points il serre le logo de quarante contre
                    les bords, et le reseau passe sous la ligne. Les lignes
                    sont donc posees a la main dans le menu.
                    ═══════════════════════════════════════════════════════
                    */
                    DropdownMenu(
                        expanded = listeOuverte,
                        onDismissRequest = { listeOuverte = false }
                    ) {
                        monnaies.forEach { m ->
                            val actuelle = m.equals(unite, ignoreCase = true)
                            Row(
                                Modifier
                                    /*
                                    LA LARGEUR EST BORNEE, ET `fillMaxWidth`
                                    RESTE NECESSAIRE.

                                    Sans `fillMaxWidth`, la ligne epouse son
                                    contenu — et dans un Row de largeur libre,
                                    un enfant en `weight(1f)` recoit ZERO : la
                                    colonne du symbole disparaitrait. C'est le
                                    piege classique du poids dans un parent non
                                    borne.

                                    Avec lui seul, le menu s'etale sur toute la
                                    largeur de l'ecran. `widthIn` tranche :
                                    trois cents points suffisent au logo, au
                                    symbole, au reseau et aux deux pastilles,
                                    et `fillMaxWidth` remplit cette borne-la au
                                    lieu de l'ecran.
                                    */
                                    .widthIn(max = 250.dp)
                                    .fillMaxWidth()
                                    .background(
                                        if (actuelle) AccentBlue.copy(alpha = 0.08f)
                                        else Color.Transparent
                                    )
                                    .clickable { onMonnaie(m); listeOuverte = false }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                coil.compose.AsyncImage(
                                    model = com.vaultex.ui.components.CryptoIcon.url(m),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp).clip(CircleShape)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        m,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = TextPrimary,
                                        maxLines = 1
                                    )
                                    reseauDe(m)?.let { r ->
                                        Text(
                                            r,
                                            fontSize = 10.sp,
                                            color = AccentOrange,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                                chaineDe(m)?.let { c ->
                                    coil.compose.AsyncImage(
                                        model = com.vaultex.ui.components.CryptoIcon.url(c),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp).clip(CircleShape)
                                    )
                                }
                                if (actuelle) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        null,
                                        tint = AccentBlue,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Box(
                                        Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .border(
                                                2.dp,
                                                TextSecondary.copy(alpha = 0.35f),
                                                CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
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
    "USDT-SOL", "SOL" -> "SPL · Solana"
    "USDT-ETH", "ETH" -> "ERC20 · Ethereum"
    "BTC" -> "Bitcoin"
    "SOL" -> "Solana"
    "PI" -> "Pi Network"
    else -> null
}

/**
 * Le symbole de la CHAINE qui porte cette monnaie, pour en afficher le logo.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * POURQUOI UN LOGO DE CHAINE A COTE D'UN LOGO DE MONNAIE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Les trois USDT portent le MEME logo — le jeton vert de Tether — parce
 * que c'est la meme monnaie. Trois lignes identiques a l'oeil, que seuls
 * trois caracteres de suffixe distinguent.
 *
 * Ce qui differe, et qui coute tout quand on se trompe, c'est la CHAINE.
 * Son logo est la seule chose qui rende les trois lignes reconnaissables
 * sans les lire : le rouge de TRON, le losange d'Ethereum, le jaune de BNB.
 *
 * Null pour une monnaie native — son propre logo EST deja celui de sa
 * chaine, et le repeter a droite n'ajouterait rien qu'une redite.
 * ═══════════════════════════════════════════════════════════════════════
 */
private fun chaineDe(monnaie: String): String? = when (monnaie.uppercase()) {
    "USDT", "USDT-TRX" -> "TRX"
    "USDT-BNB" -> "BNB"
    "USDT-ETH" -> "ETH"
    "USDT-SOL" -> "SOL"
    else -> null
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
    separateur: Boolean = false,
    /*
    ═══════════════════════════════════════════════════════════════════
    LE BENEFICIAIRE SOUS LE NUMERO, PAS SUR SA PROPRE LIGNE
    ═══════════════════════════════════════════════════════════════════

    « Numéro à créditer : 64088910 » et « Bénéficiaire : KOANDA MOUMOUNI »
    occupaient deux lignes completes, pastille comprise, pour une seule
    information : A QUI on envoie.

    Et le nom ne vaut que RAPPORTE AU NUMERO — on le lit pour verifier
    qu'Orange Money affiche bien celui-la apres avoir compose celui-ci.
    Separes par un filet, ils se lisaient comme deux verifications
    independantes.

    Sous la valeur, en petit, le nom devient ce qu'il est : la confirmation
    du numero juste au-dessus. Une ligne gagnee sur un ecran qui en demande
    trop.
    ═══════════════════════════════════════════════════════════════════
    */
    sous: String? = null,
    onCopier: () -> Unit
) {
    if (valeur.length <= VALEUR_COURTE) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Ligne(
                    libelle, valeur, icone = icone, fort = true,
                    separateur = separateur, sous = sous
                )
            }
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

    /*
    ═══════════════════════════════════════════════════════════════════
    L'ADRESSE AVAIT PERDU SA PASTILLE, ET ELLE ETAIT LA SEULE
    ═══════════════════════════════════════════════════════════════════

    Cette branche sert aux valeurs trop longues pour tenir a cote de leur
    libelle — en pratique, l'adresse du changeur et elle seule. Elle etait
    ecrite sans l'icone : le libelle montait en haut a gauche, la valeur
    passait dessous sur toute la largeur, et le parametre `icone` arrivait
    ici pour n'etre jamais utilise.

    Resultat visible sur la maquette : quatre lignes a pastille et une
    sans, et c'est la PREMIERE de la carte. La colonne d'icones commencait
    a la deuxieme ligne, ce qui se lit comme un defaut d'alignement.

    La pastille est donc posee a gauche, et le libelle avec son adresse
    tiennent dans la colonne de droite — la meme disposition que les
    autres lignes, avec deux etages au lieu d'un.
    ═══════════════════════════════════════════════════════════════════
    */
    if (separateur) {
        androidx.compose.material3.HorizontalDivider(
            color = BgTertiary.copy(alpha = 0.7f),
            thickness = 1.dp
        )
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        icone?.let {
            Box(
                Modifier.size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(it, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(libelle, fontSize = 14.sp, color = TextSecondary)
            /*
            EN CHASSE FIXE, comme le txid de la carte de vérification.

            Tous les caractères y ont la même largeur, donc les groupes se
            comparent à l'oeil — c'est ce qui permet de contrôler le début
            et la fin d'une adresse sans la lire en entier.
            */
            Text(
                valeur,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                lineHeight = 21.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        /*
        LA PASTILLE AUTOUR DE L'ICONE DE COPIE.

        Posee a nu, elle ne se distinguait pas des icones de tete des
        lignes voisines — meme taille, meme bleu — alors que celle-ci
        SE TOUCHE et les autres non. Un fond teinte dit qu'il y a quelque
        chose a appuyer, ce qu'aucune icone ne dit par elle-meme.
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
    erreur: String? = null,
    /*
    ═══════════════════════════════════════════════════════════════════
    UNE SECONDE LIGNE SOUS LE TITRE
    ═══════════════════════════════════════════════════════════════════

    Le titre NOMME le champ — « Numéro Orange Money ». Deux mots, et pour
    qui decouvre l'ecran ils ne disent pas ce qu'on attend de lui : est-ce
    le numero du changeur ? le mien ? celui qui a paye ?

    La seconde ligne le dit a l'imperatif : « Entrez votre numéro Orange
    Money ». C'est la meme difference qu'entre une etiquette et une
    consigne, et c'est la consigne qui fait agir.

    Elle remplace l'aide sous la case quand celle-ci ne servait qu'a ca —
    la confirmation disait « Retape le même numéro, pour être sûr » SOUS
    le champ, c'est-a-dire apres l'avoir rempli. Au-dessus, elle est lue
    avant.
    ═══════════════════════════════════════════════════════════════════
    */
    sousTitre: String? = null,
    /*
    ═══════════════════════════════════════════════════════════════════
    LA MARQUE DANS LA CASE, PAS SEULEMENT DANS LE TITRE
    ═══════════════════════════════════════════════════════════════════

    Huit chiffres nus ne disent pas de quel reseau ils sont. Le logo pose
    en tete de case, suivi d'un filet, repond avant qu'on ait lu le titre —
    et c'est la seule information qui compte si l'on se trompe : un numero
    Moov tape ici n'arrivera jamais.

    C'est aussi la forme que prend le meme logo sur le bouton USSD, plus
    bas : meme image, meme pastille noire. La marque demande ce fond noir
    parce qu'une de ses deux fleches est orange et disparaitrait autrement.

    FACULTATIF : les champs qui ne portent pas de numero d'operateur — une
    reference de paiement, par exemple — n'ont aucune marque a montrer.
    ═══════════════════════════════════════════════════════════════════
    */
    /*
    ─── UNE SEULE FOIS, ET DANS LA CASE ─────────────────────────────

    Le logo a d'abord ete pose AUSSI en pastille de tete, en quarante-deux
    points. Deux fois la meme marque a trois centimetres d'ecart, dont une
    en grand : le logo prenait le pas sur le titre et sur la consigne,
    c'est-a-dire sur les deux seules choses que quelqu'un doit lire la.

    Il ne reste que celui de la case. C'est la qu'il travaille — il dit de
    quel reseau sont les chiffres qu'on tape, juste a cote d'eux — et la
    pastille de tete reprend son pictogramme, qui s'efface derriere le
    texte au lieu de lui disputer la place.
    */
    marque: Int? = null,
    /** L'indicatif affiche devant les chiffres, ou null. Voir dans le corps. */
    indicatif: String? = null
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
        /*
        ═══════════════════════════════════════════════════════════════
        LA MAQUETTE SEPARE LE NOM DE LA CASE, ET ELLE A RAISON
        ═══════════════════════════════════════════════════════════════

        Le libelle et le champ de saisie etaient EMPILES DANS LA CASE, a
        droite de la pastille : « Numéro Orange Money » sur la premiere
        ligne, les chiffres tapes juste dessous, le tout dans un seul
        cadre borde.

        Ca marche et ca se lit mal. Les deux ne sont pas de meme nature —
        l'un NOMME ce qu'on demande, l'autre RECOIT ce qu'on tape — et
        dans un cadre unique ils se disputent le meme espace : le libelle
        a l'air d'etre la valeur du dessus, et la zone ou appuyer n'est
        pas delimitee. On touche le libelle en croyant ouvrir le champ.

        La maquette les separe : la pastille et le nom en titre de carte,
        puis la case grise en dessous, sur toute la largeur. Le titre se
        lit comme un titre, la case se voit comme une case, et la surface
        a toucher triple.

        ─── ET UNE CROIX POUR EFFACER ────────────────────────────────

        Huit chiffres mal tapes se corrigent au clavier, chiffre par
        chiffre. La croix remet a zero d'un geste, et c'est exactement ce
        qu'on veut quand on s'apercoit qu'on a saisi l'ancien numero :
        recommencer, pas rafistoler.

        Elle n'apparait que s'il y a quelque chose a effacer — une croix
        devant un champ vide n'est qu'un bouton qui ne fait rien.
        ═══════════════════════════════════════════════════════════════
        */
        Row(
            Modifier.fillMaxWidth(),
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
            Column {
                Text(
                    libelle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1
                )
                /*
                PAS DE `maxLines = 1` : sur un petit ecran ou avec une
                police systeme agrandie, la phrase se replie plutot que
                d'etre coupee en plein milieu. Un titre tronque reste
                reconnaissable ; une consigne tronquee ne dit plus rien.
                */
                sousTitre?.let {
                    Text(it, fontSize = 13.sp, color = TextSecondary, lineHeight = 17.sp)
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgTertiary.copy(alpha = 0.6f),
            /*
            UN CONTOUR PERMANENT, ET NON SEULEMENT EN CAS D'ERREUR.

            La case n'avait qu'un fond gris leger. Sur le fond gris leger
            de la carte, la frontiere tenait a une nuance — et c'est la
            zone ou il faut appuyer. La maquette lui donne un trait fin.

            Rouge quand quelque chose cloche : le meme trait, une autre
            couleur, donc rien ne bouge dans la mise en page au moment ou
            l'erreur apparait.
            */
            border = androidx.compose.foundation.BorderStroke(
                1.dp, if (erreur != null) AccentRed else BgTertiary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                marque?.let { image ->
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(image),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.height(24.dp).width(1.dp)
                            .background(BgTertiary)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                /*
                ═══════════════════════════════════════════════════════════
                L'INDICATIF EST AFFICHE, PAS SAISI
                ═══════════════════════════════════════════════════════════

                « +226 » se lit, ne se tape pas, et ne fait PAS partie de la
                valeur. Et ce n'est pas un detail de mise en forme.

                Tout le parcours compte huit chiffres : `telephoneValide`
                eteint le bouton en dessous, `numeroComparable` rapproche le
                SMS de l'operateur cote relais, et `numeroGroupe` affiche
                « 64 64 64 64 » sur le recu. Faire entrer l'indicatif dans
                le champ ferait onze chiffres, et les trois casseraient
                ensemble.

                ─── POURQUOI L'AFFICHER QUAND MEME ───────────────────────

                Parce qu'un numero a huit chiffres nu ne dit pas de quel
                pays il est. Sur l'ecran ou l'on indique ou recevoir de
                l'argent, c'est la derniere chose qui reste implicite — et
                l'application se destine a des gens qui ont de la famille
                en Cote d'Ivoire, au Mali, en France.

                ECRIT EN DUR, et c'est assume : le service est burkinabe de
                bout en bout — francs CFA, Orange Money Burkina, code USSD
                *144*. Le jour ou il franchit une frontiere, ce n'est pas
                cette ligne qu'il faudra changer, c'est tout le reste.
                ═══════════════════════════════════════════════════════════
                */
                indicatif?.let {
                    Text(
                        it,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = valeur,
                    onValueChange = onValeur,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 17.sp,
                        color = TextPrimary
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentBlue),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = clavier
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { interieur ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (valeur.isEmpty()) {
                                Text(
                                    exemple,
                                    fontSize = 17.sp,
                                    color = TextSecondary.copy(alpha = 0.75f),
                                    maxLines = 1
                                )
                            }
                            interieur()
                        }
                    }
                )
                if (valeur.isNotEmpty()) {
                    IconButton(
                        onClick = { onValeur("") },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Cancel,
                            stringResource(R.string.change_vider),
                            tint = TextSecondary.copy(alpha = 0.55f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
        /*
        L'AIDE NE S'AFFICHE PLUS DEUX FOIS. Quand elle est passee en
        sous-titre, la repeter sous la case ferait lire la meme phrase a
        dix points d'intervalle.

        L'ERREUR, ELLE, RESTE TOUJOURS LA. C'est le seul endroit sous le
        champ ou quelque chose apparaisse, et elle doit pouvoir y
        apparaitre meme quand l'aide est partie en haut.
        */
        val sousLaCase = erreur ?: aide.takeIf { it.isNotBlank() && it != sousTitre }
        sousLaCase?.let {
            Text(
                it,
                fontSize = 11.sp,
                color = if (erreur != null) AccentRed else TextSecondary
            )
        }
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
    teinte: Color? = null,
    /*
    ═══════════════════════════════════════════════════════════════════
    UN FILET AU-DESSUS, PARCE QUE LA MAQUETTE EN MET UN
    ═══════════════════════════════════════════════════════════════════

    Cinq lignes separees par du vide seul forment un bloc : l'oeil ne sait
    pas ou l'une finit et ou la suivante commence, et il relit. Un filet
    d'un point les decoupe en cinq, et c'est ce qui permet de viser
    directement « Tu recevras » sans parcourir les autres.

    IL EST AU-DESSUS, ET NON EN DESSOUS. La premiere ligne n'en porte donc
    pas — un filet sous le titre de la carte la detacherait de son numero
    et de son cadenas, qui forment un tout avec elle.
    ═══════════════════════════════════════════════════════════════════
    */
    separateur: Boolean = false,
    /** Une precision sous la valeur, en petit. Voir [LigneACopier]. */
    sous: String? = null
) {
    if (separateur) {
        androidx.compose.material3.HorizontalDivider(
            color = BgTertiary.copy(alpha = 0.7f),
            thickness = 1.dp
        )
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        icone?.let {
            /*
            L'ICONE DANS UNE PASTILLE TEINTEE.

            Posee a nu, elle se confond avec le texte et n'ancre rien. Sur
            un fond legerement colore elle devient un repere, et c'est tout
            ce qu'on lui demande : permettre de retrouver « ce que je
            recois » sans relire les quatre lignes.
            */
            /*
            QUARANTE POINTS, ET NON VINGT-HUIT.

            La maquette donne aux pastilles des lignes de detail la MEME
            taille qu'a celle du champ de saisie, plus bas sur le meme
            ecran. A vingt-huit elles paraissaient etre d'un autre
            inventaire — deux familles d'icones dans une colonne, et le
            regard bute dessus avant de lire quoi que ce soit.
            */
            val couleurIcone = teinte ?: if (vedette) AccentGreen else AccentBlue
            Box(
                Modifier.size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(couleurIcone.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(it, null, tint = couleurIcone, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(libelle, fontSize = 14.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                valeur,
                fontSize = if (vedette) 20.sp else if (fort) 16.sp else 14.sp,
                fontWeight = if (vedette || fort) FontWeight.Bold else FontWeight.Medium,
                color = teinte ?: if (vedette) AccentGreen else TextPrimary
            )
            sous?.let {
                Text(it, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
            }
        }
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
    fcfaParDollar: Double?,
    /** Le jeton qui paie le gaz, pour le nommer dans le message. */
    jetonGaz: String,
    /**
     * Le prix applique, sans lequel une borne en dollars se convertit
     * faux. Voir `ChangeService.facteurPrix`.
     */
    prix: com.vaultex.domain.fiat.PrixFcfa?,
    /** Vrai sur un achat. Decide du sens de la borne ET de son unite. */
    achat: Boolean,
    /**
     * L'etat, pour `minimumFcfa()` et `fraisGaz`.
     *
     * LE SEUIL AFFICHE DOIT ETRE CELUI QUI REFUSE. Il se recalculait ici,
     * a cote de celui de `blocage()` — deux ecritures de la meme regle, et
     * depuis que les frais de reseau y entrent, deux chances de diverger.
     * L'ecran annoncerait alors un minimum qu'il refuse quand meme.
     */
    state: com.vaultex.ui.viewmodel.ChangeState
): String = when (code) {
    /*
    LE MESSAGE NOMME LE JETON ET DIT QUOI FAIRE. « Frais insuffisants »
    laisse chercher : insuffisants de quoi, et ou en trouver ? Le nom du
    jeton et le geste suivant tiennent dans la meme phrase.
    */
    /*
    AVEC LE CHIFFRE QUAND ON L'A, et la difference n'est pas mince : sur
    Tron, « il vous faut du TRX » et « il vous faut environ 27 TRX » ne
    menent pas au meme geste. Le premier laisse acheter deux TRX et revenir
    se faire refuser.
    */
    "gaz" -> state.fraisGaz?.takeIf { it > 0.0 }?.let { f ->
        stringResource(R.string.change_pas_de_gaz_montant, montantGaz(f), jetonGaz)
    } ?: stringResource(R.string.change_pas_de_gaz, jetonGaz)
    /*
    MEME FAMILLE QUE LE GAZ : un refus qui ne vient pas du chiffre tape,
    mais de l'etat du portefeuille. Il nomme la monnaie et le geste
    suivant — ouvrir l'ecran Recevoir la derive et la met en cache.
    */
    "sans_adresse" -> stringResource(R.string.change_sans_adresse, monnaie)
    "ferme" -> stringResource(R.string.change_ferme)
    "pas_de_rachat" -> stringResource(R.string.change_pas_de_rachat)
    "sans_cours" -> stringResource(R.string.change_sans_cours)
    /*
    LE CHIFFRE AFFICHE EST CELUI DE LA MONNAIE CHOISIE.

    Montrer le seuil general pendant qu'un autre s'applique enverrait
    quelqu'un retaper un montant qui sera refuse une seconde fois. C'est
    exactement le genre de message qui fait fermer l'application.
    */
    "minimum" -> borne(
        R.string.change_sous_minimum, R.string.change_sous_minimum_crypto,
        // CELUI DE L'ETAT, donc frais de reseau compris. Voir `minimumFcfa`.
        state.minimumFcfa(),
        monnaie, prix, achat, versLeHaut = true
    )
    /*
    MEME RAISON QUE POUR LE MINIMUM, un cran plus haut dans ce when :
    montrer le plafond general pendant qu'un autre s'applique enverrait
    quelqu'un retaper un montant qui sera refuse une seconde fois.
    */
    "plafond" -> borne(
        R.string.change_sur_plafond, R.string.change_sur_plafond_crypto,
        p.plafondPour(monnaie, fcfaParDollar, prix, achat),
        monnaie, prix, achat, versLeHaut = false
    )
    /*
    ═══════════════════════════════════════════════════════════════════
    « PAS ASSEZ » QUAND ON N'A RIEN DU TOUT
    ═══════════════════════════════════════════════════════════════════

    SIGNALE A L'USAGE, sur un portefeuille vide : « Vous n'en detenez pas
    assez pour cette vente. Reduisez le montant. » On lit ca avec zero en
    solde, et on cherche quel montant serait assez petit. Aucun ne l'est.

    « Pas assez » suppose qu'on en a un peu. Quand on n'en a pas du tout,
    la phrase envoie reflechir a un chiffre alors que le geste a faire est
    ailleurs : en recevoir.

    Deux situations, deux gestes, deux phrases.
    ═══════════════════════════════════════════════════════════════════
    */
    "solde" -> if ((state.solde ?: 0.0) <= 0.0) {
        stringResource(R.string.change_sans_solde, monnaie)
    } else {
        stringResource(R.string.change_solde_insuffisant)
    }
    else -> stringResource(R.string.change_montant_invalide)
}

/**
 * Une borne, dite dans l'unite ou la personne tape.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * « MINIMUM : 610 FCFA » PENDANT QU'ON TAPE DES USDT
 * ═══════════════════════════════════════════════════════════════════════
 *
 * A l'achat, on tape des francs et le message en parle : il se lit et il
 * se corrige. A la vente, on tape de la crypto — et le message annoncait
 * quand meme un seuil en francs, que personne ne peut convertir de tete
 * sans le cours.
 *
 * Le refus etait donc exact et inutilisable. On retapait un peu plus, on
 * se faisait refuser encore, et c'est a ce moment-la qu'on ferme.
 *
 * ─── DES ENTIERS, PARCE QUE C'EST TOUT CE QU'ON PEUT TAPER ─────────────
 *
 * Le champ n'accepte pas de decimales a la vente. Annoncer « 1,075269 »
 * designerait un montant qu'on ne peut PAS saisir.
 *
 * LE MINIMUM MONTE, LE PLAFOND DESCEND. Arrondir le minimum a 1 quand il
 * vaut 1,07 afficherait une borne que le controle refuse — on taperait le
 * chiffre lu, et on se ferait reprendre par la regle qu'on vient de lire.
 * ═══════════════════════════════════════════════════════════════════════
 */
@Composable
private fun borne(
    enFrancs: Int,
    enCrypto: Int,
    seuilFcfa: Double,
    monnaie: String,
    prix: com.vaultex.domain.fiat.PrixFcfa?,
    achat: Boolean,
    versLeHaut: Boolean
): String {
    val v = prix?.venteFcfa ?: 0.0
    // A l'achat on tape des francs : le seuil est deja dans la bonne
    // unite. Sans cours, on ne convertit pas — un chiffre invente servirait
    // a decider d'un montant a envoyer.
    if (achat || v <= 0.0) return stringResource(enFrancs, fcfa(seuilFcfa))
    val n = if (versLeHaut) kotlin.math.ceil(seuilFcfa / v) else kotlin.math.floor(seuilFcfa / v)
    if (n < 1.0) return stringResource(enFrancs, fcfa(seuilFcfa))
    return stringResource(enCrypto, fcfa(n), monnaie)
}

/**
 * Un montant de jeton de gaz, lisible.
 *
 * DEUX CHIFFRES SIGNIFICATIFS, parce que les ordres de grandeur n'ont rien
 * a voir : 27 TRX et 0,000105 BNB doivent tous deux se lire. Un nombre fixe
 * de decimales rendrait « 0,00 » pour l'un ou « 27,000105 » pour l'autre.
 *
 * ARRONDI VERS LE HAUT : un seuil annonce trop bas enverrait quelqu'un
 * acheter exactement ce qu'il faut, moins un cheveu, et se faire refuser.
 */
private fun montantGaz(v: Double): String =
    java.math.BigDecimal.valueOf(v)
        .round(java.math.MathContext(2, java.math.RoundingMode.CEILING))
        .stripTrailingZeros()
        .toPlainString()
        .replace('.', ',')

private fun fcfa(v: Double): String =
    java.text.NumberFormat.getIntegerInstance(java.util.Locale.FRANCE).format(v)

/**
 * Un montant de crypto, avec les décimales que la monnaie possède vraiment.
 *
 * Huit pour tout le monde annonçait « 8,19672131 USDT » — deux chiffres
 * qui n'existent pas sur un jeton à six décimales, et que personne ne peut
 * donc recevoir. Voir TauxFcfa.decimalesAffichage.
 */
// VERS LE BAS, TOUJOURS : `TauxFcfa.plancher` dit pourquoi. Le formateur
// ne garde ici que la virgule francaise et les milliers espaces — la
// precision, elle, est deja fixee.
private fun crypto(v: Double, monnaie: String): String =
    java.text.NumberFormat.getNumberInstance(java.util.Locale.FRANCE).apply {
        maximumFractionDigits = TauxFcfa.decimalesAffichage(monnaie)
        minimumFractionDigits = 0
    }.format(TauxFcfa.plancher(v, monnaie))
