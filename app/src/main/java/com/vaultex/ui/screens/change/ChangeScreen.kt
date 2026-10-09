package com.vaultex.ui.screens.change

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShowChart
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

                state.etape == EtapeChange.TRANSMIS -> EtapeTransmis(state.reference) {
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

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.sens == SensChange.ACHAT,
            onClick = { vm.onSens(SensChange.ACHAT) },
            label = { Text(stringResource(R.string.change_acheter)) },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = state.sens == SensChange.VENTE,
            onClick = { vm.onSens(SensChange.VENTE) },
            label = { Text(stringResource(R.string.change_vendre)) },
            modifier = Modifier.weight(1f)
        )
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    LE MONTANT ET LA MONNAIE SUR UNE SEULE LIGNE
    ═══════════════════════════════════════════════════════════════════════

    Les monnaies etaient une rangee de pastilles. Ca tient a deux, ca
    deborde a cinq, et ca pousse le champ de saisie vers le bas — alors que
    c'est lui qu'on vient remplir.

    Une liste deroulante posee DANS le champ ne grandit pas avec le nombre
    de monnaies, et met cote a cote les deux choses qui vont ensemble :
    combien, et de quoi.
    ═══════════════════════════════════════════════════════════════════════
    */
    var listeOuverte by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = state.saisie,
        onValueChange = vm::onSaisie,
        label = {
            Text(
                stringResource(
                    if (state.sens == SensChange.ACHAT) R.string.change_montant_fcfa
                    else R.string.change_montant_crypto,
                    state.monnaie
                )
            )
        },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary
        ),
        /*
        LE LOGO DE LA MONNAIE DANS LE CHAMP.

        Il dit DE QUOI on parle sans ajouter un mot, et il le dit a
        l'endroit ou le regard se pose deja — sur le montant qu'on tape.
        Meme source que partout ailleurs dans l'application, donc meme
        image que sur l'accueil et le Marche.
        */
        leadingIcon = {
            coil.compose.AsyncImage(
                model = com.vaultex.ui.components.CryptoIcon.url(state.monnaie),
                contentDescription = state.monnaie,
                modifier = Modifier.size(28.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
            )
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
        ),
        trailingIcon = if (state.sens == SensChange.VENTE && p.monnaies.size > 1) {
            {
                Box {
                    TextButton(onClick = { listeOuverte = true }) {
                        Text(state.monnaie, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = listeOuverte, onDismissRequest = { listeOuverte = false }) {
                        p.monnaies.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = { vm.onMonnaie(m); listeOuverte = false }
                            )
                        }
                    }
                }
            }
        } else null,
        modifier = Modifier.fillMaxWidth()
    )

    /*
    EN ACHAT, LA MONNAIE NE S'AFFICHE PAS DANS LE CHAMP : on y tape des
    FRANCS. La mettre la ferait croire qu'on saisit des USDT. Elle reste
    donc en pastilles, sous le champ, ou elle designe ce qu'on RECOIT.
    */
    if (state.sens == SensChange.ACHAT && p.monnaies.size > 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            p.monnaies.forEach { m ->
                FilterChip(
                    selected = state.monnaie == m,
                    onClick = { vm.onMonnaie(m) },
                    label = { Text(m, fontSize = 12.sp) }
                )
            }
        }
    }

    /*
    LE DÉTAIL COMPLET, AVANT LE BOUTON.

    C'est l'exigence de départ : rien ne doit se découvrir après. La marge
    est donnée dans les DEUX unités — en francs par dollar parce que c'est
    la langue du marché local, en pourcentage parce que c'est la seule qui
    se compare à un échangeur en ligne.
    */
    val prix = state.prix
    val taux = state.fcfaParDollar
    if (prix != null && taux != null) {
        Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val unitaire = if (state.sens == SensChange.ACHAT) prix.achatFcfa else prix.venteFcfa
                Ligne(
                    stringResource(R.string.change_taux, state.monnaie),
                    "${fcfa(unitaire)} FCFA",
                    icone = Icons.Default.ShowChart
                )
                /*
                ═══════════════════════════════════════════════════════════
                « 25 FCFA/$ » EST UNE UNITE, PAS UN MONTANT
                ═══════════════════════════════════════════════════════════

                Cette ligne annoncait « Frais : 25 FCFA/$ ». Sur cent USDT,
                les frais reels sont deux mille cinq cents francs.

                Quelqu'un qui lit « 25 FCFA » et ne refait pas la
                multiplication se trompe d'un facteur cent — et il ne la
                refait pas, parce qu'une ligne intitulee « Frais » se lit
                comme un montant, pas comme un taux.

                On affiche donc ce que l'operation coute REELLEMENT, en
                francs, des qu'un montant est saisi. Le taux reste a cote,
                entre parentheses, pour qui veut comparer a un echangeur :
                c'est la seule unite comparable, mais ce n'est pas celle
                qu'on lit en premier.

                Sans montant saisi, il n'y a pas de frais a annoncer : on
                montre alors le taux seul, qui est tout ce qu'on sait.
                ═══════════════════════════════════════════════════════════
                */
                val pourcent = TauxFcfa.margeEnPourcent(p.margeFcfaParDollar, taux)
                    ?.let { "%.2f %%".format(it) }
                val fraisReels = state.montantCrypto?.let { c ->
                    val applique = if (state.sens == SensChange.ACHAT) prix.achatFcfa else prix.venteFcfa
                    c * kotlin.math.abs(applique - prix.baseFcfa)
                }
                Ligne(
                    stringResource(R.string.change_marge),
                    icone = Icons.Default.Payments,
                    valeur = if (fraisReels != null && fraisReels >= 1.0)
                        "${fcfa(fraisReels)} FCFA" + (pourcent?.let { " ($it)" } ?: "")
                    else "${fcfa(p.margeFcfaParDollar)} FCFA/$" + (pourcent?.let { " ($it)" } ?: "")
                )
                /*
                CE QU'ON DONNE, PUIS CE QU'ON RECOIT — et les deux changent
                d'unite avec le sens.

                La ligne « Tu paies » affichait des FRANCS dans les deux
                cas. En vente, c'est faux deux fois : l'utilisateur ne paie
                pas des francs, il en recoit — et le meme montant
                apparaissait donc juste au-dessus de « Tu recois », avec le
                meme chiffre et un libelle qui le contredisait.

                Sur un ecran ou l'on s'engage, deux lignes qui se
                contredisent valent moins qu'une seule.
                */
                val achatEnCours = state.sens == SensChange.ACHAT
                val donne = if (achatEnCours) state.montantFcfa?.let { "${fcfa(it)} FCFA" }
                    else state.montantCrypto?.let { "${crypto(it)} ${state.monnaie}" }
                val recoit = if (achatEnCours) state.montantCrypto?.let { "${crypto(it)} ${state.monnaie}" }
                    else state.montantFcfa?.let { "${fcfa(it)} FCFA" }

                donne?.let {
                    Ligne(
                        stringResource(
                            if (achatEnCours) R.string.change_total_fcfa else R.string.change_tu_envoies
                        ),
                        it,
                        icone = Icons.Default.NorthEast
                    )
                }
                /*
                LE SEUL CHIFFRE QUI DECIDE, ET IL DOIT SE VOIR COMME TEL.

                Tout le reste de cette carte sert a le justifier. Personne
                ne compare deux offres sur le taux unitaire : on compare sur
                ce qu'on recoit. Il passe donc en vert et en dix-huit.

                LE DELAI SORT DE CETTE CARTE. Il n'est pas un prix, et il
                etait affiche DEUX FOIS — ici et sur l'ecran de paiement.
                Il ne reste qu'a l'endroit ou l'on attend.
                */
                recoit?.let {
                    HorizontalDivider(color = BgPrimary)
                    Ligne(
                        stringResource(R.string.change_recevra), it,
                        icone = Icons.Default.AccountBalanceWallet, vedette = true
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
        NoteEnDeuxTemps(messageBlocage(it, p), AccentRed)
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
    var tout by remember { mutableStateOf(false) }
    val visibles = if (tout) demandes else demandes.take(APERCU_HISTORIQUE)

    Spacer(Modifier.height(6.dp))
    HorizontalDivider(color = SurfaceColor)

    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.History, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.change_historique_titre),
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
        )
    }

    visibles.forEach { d -> LigneHistorique(d, copier) }

    if (demandes.size > APERCU_HISTORIQUE) {
        TextButton(onClick = { tout = !tout }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(
                if (tout) stringResource(R.string.change_historique_moins)
                else stringResource(R.string.change_historique_tout, demandes.size),
                fontSize = 12.sp, color = AccentBlue
            )
        }
    }

    Note(stringResource(R.string.change_historique_note), AccentBlue)
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

/** Trois lignes avant d'avoir a deplier. Voir [HistoriqueDemandes]. */
private const val APERCU_HISTORIQUE = 3

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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Ligne(
                        if (achat) "${stringResource(R.string.change_numero)} (${p.operateur})"
                        else stringResource(R.string.change_adresse),
                        aCopier, fort = true
                    )
                }
                IconButton(onClick = { copier(aCopier) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.ContentCopy, stringResource(R.string.copy),
                        tint = AccentBlue, modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (achat && p.nomChangeur.isNotBlank()) {
                Ligne(stringResource(R.string.change_beneficiaire), p.nomChangeur)
            }
            Ligne(
                stringResource(R.string.change_montant_exact),
                if (achat) "${fcfa(state.montantFcfa ?: 0.0)} FCFA"
                else "${crypto(state.montantCrypto ?: 0.0)} ${state.monnaie}",
                fort = true
            )
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
            Ligne(stringResource(R.string.change_delai), "~ ${p.delaiMinutes} min")
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
            OutlinedButton(
                onClick = { copier(state.reference) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.change_copier_reference))
            }
        }
    }

    Note(stringResource(R.string.change_motif, state.reference), AccentBlue)

    Text(
        stringResource(R.string.change_declarer_titre),
        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary
    )

    OutlinedTextField(
        value = state.referencePaiement,
        onValueChange = vm::onReferencePaiement,
        label = { Text(stringResource(R.string.change_ref_paiement)) },
        /*
        UN EXEMPLE PLUTOT QU'UNE DESCRIPTION. « Le numero de transaction
        que ton operateur t'a envoye par SMS » demande de comprendre une
        phrase ; « MP251008123456 » se reconnait d'un coup d'oeil dans le
        SMS qu'on a sous les yeux.
        */
        placeholder = { Text("Ex. MP251008123456", color = TextSecondary) },
        supportingText = { Text(stringResource(R.string.change_ref_paiement_aide), fontSize = 11.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = state.telephone,
        onValueChange = vm::onTelephone,
        label = { Text(stringResource(R.string.change_telephone)) },
        placeholder = { Text("Ex. 70 12 34 56", color = TextSecondary) },
        supportingText = { Text(stringResource(R.string.change_telephone_aide), fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
        ),
        modifier = Modifier.fillMaxWidth()
    )

    state.erreur?.let { Text(it, fontSize = 12.sp, color = AccentRed) }

    Button(
        onClick = vm::transmettre,
        enabled = !state.envoiEnCours && state.referencePaiement.isNotBlank(),
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
        Text(stringResource(R.string.change_jai_paye), fontWeight = FontWeight.Bold)
    }

    TextButton(onClick = vm::retourAuCalcul, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(stringResource(R.string.change_retour), color = TextSecondary, fontSize = 13.sp)
    }
}

// ─── Temps 3 : transmis ─────────────────────────────────────────────

@Composable
private fun ColumnScope.EtapeTransmis(reference: String, onRecommencer: () -> Unit) {
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
                stringResource(R.string.change_transmis_detail, reference),
                fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp
            )
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
private fun messageBlocage(code: String, p: com.vaultex.domain.fiat.ParametresChange): String = when (code) {
    "ferme" -> stringResource(R.string.change_ferme)
    "pas_de_rachat" -> stringResource(R.string.change_pas_de_rachat)
    "sans_cours" -> stringResource(R.string.change_sans_cours)
    "minimum" -> stringResource(R.string.change_sous_minimum, fcfa(p.minimumFcfa))
    "plafond" -> stringResource(R.string.change_sur_plafond, fcfa(p.plafondFcfa))
    else -> stringResource(R.string.change_montant_invalide)
}

private fun fcfa(v: Double): String =
    java.text.NumberFormat.getIntegerInstance(java.util.Locale.FRANCE).format(v)

private fun crypto(v: Double): String =
    java.text.NumberFormat.getNumberInstance(java.util.Locale.FRANCE).apply {
        maximumFractionDigits = 8
        minimumFractionDigits = 0
    }.format(v)
