package com.vaultex.ui.screens.change

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Sms
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.domain.fiat.TauxFcfa
// Imports nommes : com.vaultex.ui.theme.Surface est une COULEUR.
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentGreen
import com.vaultex.ui.theme.AccentOrange
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.change_titre),
                        fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val p = state.parametres

            when {
                state.chargement && p == null ->
                    Note(stringResource(R.string.change_chargement), AccentBlue)

                p == null ->
                    Note(stringResource(R.string.change_indisponible), AccentOrange)

                !p.utilisable ->
                    Note(stringResource(R.string.change_ferme), AccentOrange)

                state.etape == EtapeChange.TRANSMIS -> EtapeTransmis(state) {
                    vm.recommencer()
                }

                state.etape == EtapeChange.PAIEMENT -> EtapePaiement(vm, state, copier)

                else -> EtapeCalcul(vm, state, copier)
            }

            Spacer(Modifier.height(24.dp))
        }
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
            // Le meme signe que la tuile de l'accueil : deux icones
            // differentes pour le meme geste font hesiter sur l'endroit
            // ou l'on vient d'arriver.
            icone = Icons.Default.AttachMoney,
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

    ─── CE QU'ON NE MET PAS : UN BOUTON D'INVERSION ──────────────────────

    Le Swap en a un, et il y est juste : échanger A contre B ou B contre A
    sont la même opération. Ici non. « Acheter » et « Vendre » sont deux
    engagements distincts — l'un envoie des francs, l'autre envoie de la
    crypto de façon irréversible — et ils portent des noms que les gens
    emploient. Deux commandes pour un seul état se contrediraient ; on
    garde celle qui est écrite en mots.
    ═══════════════════════════════════════════════════════════════════════
    */
    // Acheter, c'est donner des francs ; vendre, c'est en recevoir. Tout
    // le reste de cet ecran ne depend que de ce booleen.
    val donneFcfa = state.sens == SensChange.ACHAT

    CarteMontantChange(
        libelle = stringResource(R.string.change_tu_donnes),
        solde = if (donneFcfa) null else state.solde,
        onTout = if (donneFcfa) null else vm::onToutVendre,
        rapides = if (donneFcfa) montantsRapides(p, state.monnaie) else emptyList(),
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
        saisissable = true
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
    BornesChange(
        minFcfa = p.minimumPour(state.monnaie),
        maxFcfa = p.plafondFcfa,
        enFcfa = donneFcfa,
        monnaie = state.monnaie,
        prix = state.prix
    )

    /*
    LE PAVÉ EST ICI, ENTRE CE QU'ON TAPE ET LE DÉTAIL.

    Juste sous les deux cartes : le chiffre qu'on compose reste visible
    pendant qu'on le compose, ce qu'un clavier système ne permettait pas —
    il recouvrait la moitié basse de l'écran, donc le montant reçu.

    Et AU-DESSUS du détail et du bouton, qui restent dans leur ordre : rien
    de ce qui engage ne passe sous le pavé. C'est la règle que cet écran
    tient depuis le début — rien ne doit se découvrir après.

    ─── LE NOMBRE DE DÉCIMALES VIENT DE L'UNITÉ QU'ON TAPE ──────────────

    Zéro pour les francs, qui n'ont pas de centimes. Celui de la monnaie
    pour une vente — six sur l'USDT, huit sur le bitcoin. C'est ce chiffre
    qui grise la virgule, et c'est lui qui empêche de promettre des
    décimales que le jeton ne possède pas.
    */
    PaveDeSaisie(
        saisie = state.saisie,
        decimalesMax = if (donneFcfa) 0 else TauxFcfa.decimalesAffichage(state.monnaie),
        onSaisie = vm::onSaisie
    )

    /*
    LE BOUTON D'INVERSION, ENTRE LES DEUX CARTES.

    Il fait la même chose que les onglets du haut, et c'est assumé : entre
    deux cartes « tu donnes » et « tu reçois », l'oeil cherche ce bouton
    là. Le refuser au nom de la non-redondance obligerait à remonter en
    haut de l'écran pour un geste dont la place naturelle est ici.

    Les onglets restent parce qu'ils NOMMENT l'opération. « Acheter » et
    « Vendre » sont deux engagements distincts, et une flèche ne dit pas
    lequel on est en train de prendre.
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
        saisissable = false,
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

    Note(
        stringResource(R.string.change_avertissement, p.nomChangeur.ifBlank { "un changeur" }),
        AccentOrange
    )

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
        NoteEnDeuxTemps(messageBlocage(it, p, state.monnaie), AccentRed)
    }

    Button(
        onClick = vm::confirmer,
        enabled = blocage == null,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text(stringResource(R.string.change_continuer), fontWeight = FontWeight.Bold)
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
    copier: (String) -> Unit
) {
    val p = state.parametres ?: return
    val achat = state.sens == SensChange.ACHAT
    val aCopier = if (achat) p.numeroMobileMoney else p.adresses[state.monnaie].orEmpty()

    Surface(shape = RoundedCornerShape(14.dp), color = AccentBlue.copy(alpha = 0.08f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(
                    if (achat) R.string.change_payer_titre else R.string.change_envoyer_titre
                ),
                fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary
            )
            /*
            L'OPERATEUR EST DANS LE LIBELLE, et ce n'est pas decoratif : il
            dit QUELLE application ouvrir. « Numero a crediter » seul laisse
            quelqu'un chercher entre Orange Money, Moov et Wave — et payer
            depuis la mauvaise ne marche pas toujours entre operateurs.
            */
            LigneACopier(
                libelle = if (achat) "${stringResource(R.string.change_numero)} (${p.operateur})"
                else stringResource(R.string.change_adresse),
                valeur = aCopier,
                onCopier = { copier(aCopier) }
            )
            if (achat && p.nomChangeur.isNotBlank()) {
                Ligne(stringResource(R.string.change_beneficiaire), p.nomChangeur)
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
                Ligne(
                    stringResource(R.string.change_montant_exact),
                    "${fcfa(state.montantFcfa ?: 0.0)} FCFA",
                    fort = true
                )
            } else {
                val montantTexte = crypto(state.montantCrypto ?: 0.0, state.monnaie)
                LigneACopier(
                    libelle = stringResource(R.string.change_montant_exact),
                    valeur = "$montantTexte ${state.monnaie}",
                    onCopier = { copier(montantTexte) }
                )
                /*
                LE BOUTON COPIE LE NOMBRE SEUL, sans « USDT-BNB » derrière.
                C'est ce qu'on colle dans le champ Montant d'un exchange, et
                une unité collée avec ferait refuser la saisie.
                */
                Note(stringResource(R.string.change_montant_exact_note), AccentOrange)
            }
            /*
            LA RÉFÉRENCE EST CE QUI RATTACHE UN PAIEMENT À UNE DEMANDE.

            Sans elle, le changeur voit arriver de l'argent sans savoir de
            qui, et une demande sans savoir si elle a été payée. Elle est
            donc mise en avant, et l'écran demande explicitement de la
            porter en motif.
            */
            Ligne(stringResource(R.string.change_reference), state.reference, fort = true)
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
            OutlinedButton(
                onClick = { copier(if (achat) state.reference else aCopier) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (achat) R.string.change_copier_reference
                        else R.string.change_copier_adresse
                    )
                )
            }
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
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.Dialpad, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.change_ussd_bouton, p.operateur),
                    fontWeight = FontWeight.Bold
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
            Text(
                code,
                fontSize = 13.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = TextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Note(stringResource(R.string.change_ussd_note), AccentBlue)
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
    ═══════════════════════════════════════════════════════════════════════
    */
    if (achat) {
        Note(stringResource(R.string.change_motif, state.reference), AccentBlue)
    }

    /*
    LA VERIFICATION VIENT AVANT LES CHAMPS, ET C'EST L'ORDRE DU REGARD.

    Sur une vente, la question de l'utilisateur a cet instant n'est pas
    « que dois-je saisir » : c'est « est-ce que mon argent est bien
    parti ». Il vient d'envoyer de la crypto, c'est irreversible, et il
    attend. La reponse doit etre la premiere chose qu'il lit.
    */
    if (!achat) CarteVerification(vm, state)

    Text(
        stringResource(
            if (achat) R.string.change_declarer_titre else R.string.change_vente_coordonnees
        ),
        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary
    )

    /*
    LA REFERENCE MOBILE MONEY N'A DE SENS QUE SUR UN ACHAT.

    Sur une vente, il n'y a eu aucun paiement Mobile Money : c'est le
    changeur qui va en faire un. Demander « la reference du paiement » a
    quelqu'un qui n'a rien paye le laisse chercher dans ses SMS une chose
    qui n'existe pas — et le bouton restait eteint tant qu'il n'avait pas
    invente quelque chose.
    */
    if (achat) {
        OutlinedTextField(
            value = state.referencePaiement,
            onValueChange = vm::onReferencePaiement,
            label = { Text(stringResource(R.string.change_ref_paiement)) },
            /*
            UN EXEMPLE PLUTOT QU'UNE DESCRIPTION. « Le numero de
            transaction que ton operateur t'a envoye par SMS » demande de
            comprendre une phrase ; « MP251008123456 » se reconnait d'un
            coup d'oeil dans le SMS qu'on a sous les yeux.
            */
            placeholder = { Text("Ex. MP251008123456", color = TextSecondary) },
            supportingText = {
                Text(stringResource(R.string.change_ref_paiement_aide), fontSize = 11.sp)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
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
    OutlinedTextField(
        value = state.telephone,
        onValueChange = vm::onTelephone,
        label = {
            Text(
                stringResource(
                    if (achat) R.string.change_telephone else R.string.change_telephone_vente
                )
            )
        },
        placeholder = { Text("Ex. 70 12 34 56", color = TextSecondary) },
        supportingText = {
            Text(
                stringResource(
                    if (achat) R.string.change_telephone_aide
                    else R.string.change_telephone_vente_aide
                ),
                fontSize = 11.sp,
                // Sur une vente, un numero incomplet eteint le bouton : la
                // raison doit se voir a l'endroit ou on la corrige.
                color = if (!achat && state.telephone.isNotBlank() && !state.telephoneValide) {
                    AccentRed
                } else TextSecondary
            )
        },
        isError = !achat && state.telephone.isNotBlank() && !state.telephoneValide,
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
        ),
        modifier = Modifier.fillMaxWidth()
    )

    /*
    ═══════════════════════════════════════════════════════════════════════
    CONFIRMER SON NUMERO PAR SMS — SUR UNE VENTE SEULEMENT
    ═══════════════════════════════════════════════════════════════════════

    Le changeur va envoyer des francs sur ce numéro. Un chiffre de travers
    et l'argent part chez un inconnu, sans recours. Rien n'empêchait ça.

    En envoyant sa référence par SMS au numéro du changeur, le client fait
    lire son numéro PAR L'OPÉRATEUR plutôt que par ce formulaire. Le
    message qui arrive chez le changeur porte alors « ✔ confirmé par SMS »
    sur la ligne du numéro — là où il recopie les huit chiffres.

    ─── C'EST UNE INVITATION, PAS UNE OBLIGATION ────────────────────────

    Le bouton reste actif sans ça, et le changeur décide. Bloquer la vente
    sur un SMS que le client paie, et qui peut ne pas arriver, fermerait
    le service à ceux qui n'ont plus de crédit — pour une protection dont
    ils n'ont peut-être pas besoin s'ils ont tapé juste.

    ─── ET ELLE NE S'AFFICHE QUE SI ELLE PEUT SERVIR ────────────────────

    Sans numéro de changeur réglé, il n'y a nulle part où envoyer. Sans
    référence, il n'y a rien à envoyer. Dans les deux cas on se tait,
    plutôt que d'afficher une consigne qu'on ne peut pas suivre.
    ═══════════════════════════════════════════════════════════════════════
    */
    if (!achat && p.numeroMobileMoney.isNotBlank() && state.reference.isNotBlank()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = AccentBlue.copy(alpha = 0.10f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Sms, null,
                        tint = AccentBlue, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.change_otp_titre),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Text(
                    stringResource(
                        R.string.change_otp_aide,
                        state.reference,
                        p.numeroMobileMoney
                    ),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }

    state.erreur?.let { Text(it, fontSize = 12.sp, color = AccentRed) }

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
        LE BOUTON DIT CE QU'IL ENVOIE. « J'ai paye » est juste pour un
        achat ; sur une vente verifiee, « Transmettre — versement verifie »
        dit a l'utilisateur ce que le changeur va lire, et c'est la
        derniere chose a lui apprendre avant qu'il appuie.
        */
        Text(
            stringResource(
                when {
                    achat -> R.string.change_jai_paye
                    state.verification?.estVert == true -> R.string.change_transmettre_verifie
                    else -> R.string.change_transmettre
                }
            ),
            fontWeight = FontWeight.Bold
        )
    }

    TextButton(onClick = vm::retourAuCalcul, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(stringResource(R.string.change_retour), color = TextSecondary, fontSize = 13.sp)
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
    val couleur = when {
        fini -> AccentOrange
        restant < 2 * 60_000L -> AccentOrange
        else -> TextPrimary
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
                    stringResource(titreVerification(affichage)),
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
                        stringResource(R.string.change_verif_recu),
                        "${crypto(m, state.monnaie)} ${state.monnaie}",
                        icone = Icons.Default.AccountBalanceWallet,
                        vedette = true
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
                        onClick = vm::demarrerVerification,
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
            */
            if (avecSorties) {
                OutlinedTextField(
                    value = state.txidSaisi,
                    onValueChange = vm::onTxid,
                    label = { Text(stringResource(R.string.change_verif_txid)) },
                    supportingText = {
                        Text(stringResource(R.string.change_verif_txid_aide), fontSize = 11.sp)
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
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

private fun affichageVerification(
    state: com.vaultex.ui.viewmodel.ChangeState
): AffichageVerif {
    val v = state.verification
    if (state.rechercheEnCours) return AffichageVerif.RECHERCHE
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

private fun titreVerification(affichage: AffichageVerif): Int = when (affichage) {
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
): String = when (affichage) {
    AffichageVerif.RECHERCHE ->
        stringResource(R.string.change_verif_recherche_detail, state.monnaie)
    AffichageVerif.VERT -> stringResource(R.string.change_verif_trouve_detail)
    AffichageVerif.ABSENT ->
        stringResource(R.string.change_verif_absent_detail, state.essais)
    AffichageVerif.DEJA_SERVI -> stringResource(R.string.change_verif_deja_servi_detail)
    /*
    LA RAISON DU RELAIS EST TRANSMISE TELLE QUELLE quand il en donne une.
    « Le nœud n'a pas répondu » et « aucune adresse réglée pour BTC »
    appellent deux actions différentes — la première se réessaie, la
    seconde est un réglage à poser côté serveur. Les fondre dans
    « vérification impossible » enverrait quelqu'un réessayer indéfiniment
    une opération qui ne peut pas aboutir.
    */
    AffichageVerif.IMPOSSIBLE -> {
        val raison = state.verification?.raison.orEmpty()
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
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (choisi) AccentBlue.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            if (choisi) 1.5.dp else 1.dp,
            if (choisi) AccentBlue else SurfaceColor
        ),
        modifier = modifier.height(46.dp)
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icone, null,
                tint = if (choisi) AccentBlue else TextSecondary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                texte,
                fontSize = 14.sp,
                fontWeight = if (choisi) FontWeight.Bold else FontWeight.Medium,
                color = if (choisi) TextPrimary else TextSecondary
            )
        }
    }
}

/*
═══════════════════════════════════════════════════════════════════════════
LE PAVÉ NUMÉRIQUE, DANS L'ÉCRAN
═══════════════════════════════════════════════════════════════════════════

Pourquoi remplacer le clavier du système, qui marchait :

— IL MANGEAIT L'ÉCRAN. Un clavier Android occupe la moitié basse ; sur un
  écran qui porte deux cartes, le détail et un bouton, il recouvrait
  précisément ce qu'on vient vérifier avant d'appuyer.
— IL PROPOSE DES TOUCHES QUI N'ONT PAS DE SENS ICI : le point, le moins,
  parfois une rangée de suggestions. Chacune est une frappe qui produit
  une saisie que personne ne voulait.
— SES TOUCHES SONT PETITES. Ce pavé-ci se tape d'un pouce, dans la rue,
  sur un montant qu'on ne doit pas se tromper d'écrire.

─── TROIS DÉCISIONS QUI SE VOIENT ────────────────────────────────────────

LA VIRGULE EST GRISÉE EN FRANCS. Le franc CFA n'a pas de centimes. Et ce
n'est pas cosmétique : voir PaveNumerique, une virgule seulement « sans
effet » laisse taper « 5000,50 » et obtenir 500 050.

⌫ EFFACE UN CARACTÈRE, UN APPUI LONG VIDE TOUT. Recommencer un montant est
le geste le plus fréquent après une faute ; le faire en huit appuis quand
un seul suffit use la patience sur l'écran le moins indiqué pour ça.

PAS DE MISE EN FORME PENDANT LA FRAPPE. Le montant se réaffiche tel qu'il
a été tapé. `NumberFormat` français sépare les milliers par une espace
insécable étroite selon la version d'Android, et « 5 000 » redevient alors
5 — un défaut qu'on a déjà payé dans ce projet.
═══════════════════════════════════════════════════════════════════════════
*/
@Composable
private fun PaveDeSaisie(
    saisie: String,
    decimalesMax: Int,
    onSaisie: (String) -> Unit
) {
    val appliquer = { touche: com.vaultex.domain.fiat.ToucheP ->
        onSaisie(com.vaultex.domain.fiat.PaveNumerique.appliquer(saisie, touche, decimalesMax))
    }

    // LARGEUR EXPLICITE : `weight` partage la largeur DISPONIBLE, et une
    // Row qui s'adapte à son contenu n'en a pas à partager. Sans ces
    // `fillMaxWidth`, la rangée dépend d'une contrainte venue de plus haut.
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(1..3, 4..6, 7..9).forEach { rangee ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rangee.forEach { n ->
                    ToucheDuPave(
                        texte = n.toString(),
                        modifier = Modifier.weight(1f)
                    ) { appliquer(com.vaultex.domain.fiat.ToucheP.Chiffre(n)) }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToucheDuPave(
                texte = com.vaultex.domain.fiat.PaveNumerique.VIRGULE.toString(),
                // Grisée, pas absente : une case vide à cet endroit se lit
                // comme un défaut d'affichage, et laisse chercher.
                actif = decimalesMax > 0,
                modifier = Modifier.weight(1f)
            ) { appliquer(com.vaultex.domain.fiat.ToucheP.Virgule) }
            ToucheDuPave(texte = "0", modifier = Modifier.weight(1f)) {
                appliquer(com.vaultex.domain.fiat.ToucheP.Chiffre(0))
            }
            ToucheDuPave(
                icone = Icons.Default.Backspace,
                // Une touche en image n'a pas d'etiquette : sans ca, un
                // lecteur d'ecran annonce « bouton » sur un pave de chiffres.
                description = stringResource(R.string.change_effacer),
                modifier = Modifier.weight(1f),
                onLong = { appliquer(com.vaultex.domain.fiat.ToucheP.Vider) }
            ) { appliquer(com.vaultex.domain.fiat.ToucheP.Effacer) }
        }
    }
}

/** Une touche du pavé. Du texte ou une icône, jamais les deux. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToucheDuPave(
    texte: String? = null,
    icone: androidx.compose.ui.graphics.vector.ImageVector? = null,
    /** Obligatoire sur une touche en image, inutile sur un chiffre. */
    description: String? = null,
    actif: Boolean = true,
    modifier: Modifier = Modifier,
    onLong: (() -> Unit)? = null,
    onClic: () -> Unit
) {
    val couleur = if (actif) TextPrimary else TextSecondary.copy(alpha = 0.3f)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceColor,
        modifier = modifier
            .height(52.dp)
            .then(
                if (actif) {
                    Modifier.combinedClickable(onClick = onClic, onLongClick = onLong)
                } else Modifier
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                icone != null ->
                    Icon(icone, description, tint = couleur, modifier = Modifier.size(22.dp))
                texte != null -> Text(
                    texte,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = couleur
                )
            }
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
    prix: com.vaultex.domain.fiat.PrixFcfa?
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
        stringResource(
            R.string.change_bornes_approx,
            "${crypto(minFcfa / v, monnaie)} $monnaie",
            "${crypto(maxFcfa / v, monnaie)} $monnaie"
        )
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.SwapHoriz, null,
            tint = TextSecondary, modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(texte, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
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
    /** « Tout », qui met le solde entier dans le champ. */
    onTout: (() -> Unit)? = null,
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
     * Vrai sur la carte qu'on remplit, faux sur celle qu'on lit.
     *
     * C'était `onMontant: ((String) -> Unit)?`, du temps où la carte
     * portait elle-même un champ de saisie. Depuis que le pavé est le seul
     * clavier, la carte n'a plus rien à rappeler : elle a seulement besoin
     * de savoir laquelle des deux elle est.
     */
    saisissable: Boolean,
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
    Box(Modifier.fillMaxWidth().heightIn(min = 92.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(libelle, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
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
                    onTout?.let { tout ->
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentBlue.copy(alpha = 0.14f),
                            modifier = Modifier.clickable { tout() }
                        ) {
                            Text(
                                stringResource(R.string.change_tout),
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {

                    if (saisissable) {
                        /*
                        ═════════════════════════════════════════════════════
                        PLUS DE CHAMP DE SAISIE : LE PAVÉ EST LE SEUL CLAVIER
                        ═════════════════════════════════════════════════════

                        C'était un `BasicTextField`, qui ouvrait le clavier du
                        système. Laisser les deux coexister serait pire que
                        n'avoir que l'un : deux claviers pour un seul champ,
                        avec des règles différentes — le système accepte le
                        point, le moins et autant de décimales qu'on veut, là
                        où le pavé les refuse exprès.

                        C'est donc un texte ordinaire. Le trait bleu dit où ça
                        s'écrit, sans promettre un curseur qu'on pourrait
                        déplacer : on tape à la fin, on efface à la fin.
                        ═════════════════════════════════════════════════════
                        */
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                // Un « 0 » en gris pâle montre où taper sans
                                // prétendre être une valeur saisie.
                                montant.ifEmpty { "0" },
                                fontSize = tailleMontant(montant.ifEmpty { "0" }),
                                fontWeight = FontWeight.Bold,
                                color = if (montant.isEmpty()) {
                                    TextSecondary.copy(alpha = 0.4f)
                                } else TextPrimary,
                                maxLines = 1
                            )
                            Spacer(Modifier.width(3.dp))
                            Box(
                                Modifier
                                    .height(tailleMontant(montant.ifEmpty { "0" }).value.dp)
                                    .width(2.dp)
                                    .background(AccentBlue, RoundedCornerShape(1.dp))
                            )
                        }
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
                            color = SurfaceColor,
                            modifier = Modifier.weight(1f).clickable { onRapide(v) }
                        ) {
                            Text(
                                fcfa(v),
                                modifier = Modifier.padding(vertical = 6.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1
                            )
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
    monnaie: String
): List<Double> {
    // PAR MONNAIE : TRON et BNB Chain n'ont pas le même seuil, parce
    // qu'elles n'ont pas les mêmes frais de réseau.
    val min = p.minimumPour(monnaie)
    val max = p.plafondFcfa
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
private fun LigneACopier(libelle: String, valeur: String, onCopier: () -> Unit) {
    if (valeur.length <= VALEUR_COURTE) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Ligne(libelle, valeur, fort = true) }
            IconButton(onClick = onCopier, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.ContentCopy, stringResource(R.string.copy),
                    tint = AccentBlue, modifier = Modifier.size(16.dp)
                )
            }
        }
        return
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(libelle, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            IconButton(onClick = onCopier, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.ContentCopy, stringResource(R.string.copy),
                    tint = AccentBlue, modifier = Modifier.size(16.dp)
                )
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
    vedette: Boolean = false
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
            val teinte = if (vedette) AccentGreen else AccentBlue
            Box(
                Modifier.size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(teinte.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(it, null, tint = teinte, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(libelle, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(
            valeur,
            fontSize = if (vedette) 18.sp else if (fort) 14.sp else 12.sp,
            fontWeight = if (vedette || fort) FontWeight.Bold else FontWeight.Medium,
            color = if (vedette) AccentGreen else TextPrimary
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
    monnaie: String
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
    "minimum" -> stringResource(R.string.change_sous_minimum, fcfa(p.minimumPour(monnaie)))
    "plafond" -> stringResource(R.string.change_sur_plafond, fcfa(p.plafondFcfa))
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
