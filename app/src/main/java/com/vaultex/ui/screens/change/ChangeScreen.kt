package com.vaultex.ui.screens.change

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
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

                else -> EtapeCalcul(vm, state)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── Temps 1 : le calcul ────────────────────────────────────────────

@Composable
private fun ColumnScope.EtapeCalcul(vm: ChangeViewModel, state: com.vaultex.ui.viewmodel.ChangeState) {
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

    if (p.monnaies.size > 1) {
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
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
        ),
        modifier = Modifier.fillMaxWidth()
    )

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
                Ligne(stringResource(R.string.change_taux, state.monnaie), "${fcfa(unitaire)} FCFA")
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
                    if (fraisReels != null && fraisReels >= 1.0)
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
                        it
                    )
                }
                recoit?.let {
                    HorizontalDivider(color = BgPrimary)
                    Ligne(stringResource(R.string.change_recevra), it, fort = true)
                }
                Ligne(stringResource(R.string.change_delai), "${p.delaiMinutes} min")
            }
        }
    }

    Note(
        stringResource(R.string.change_avertissement, p.nomChangeur.ifBlank { "un changeur" }),
        AccentOrange
    )

    val blocage = state.blocage()
    blocage?.takeIf { state.saisie.isNotBlank() || it == "ferme" || it == "pas_de_rachat" }?.let {
        Text(messageBlocage(it, p), fontSize = 12.sp, color = AccentRed)
    }

    Button(
        onClick = vm::confirmer,
        enabled = blocage == null,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text(stringResource(R.string.change_continuer), fontWeight = FontWeight.Bold)
    }
}

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
            Ligne(
                if (achat) "${stringResource(R.string.change_numero)} (${p.operateur})"
                else stringResource(R.string.change_adresse),
                aCopier, fort = true
            )
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
            OutlinedButton(
                onClick = { copier(aCopier) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.copy))
            }
        }
    }

    Note(stringResource(R.string.change_motif, state.reference), AccentBlue)

    /*
    LA PHRASE LA PLUS IMPORTANTE DE L'ÉCRAN.

    L'utilisateur envoie en premier. Il doit le savoir avant de payer, pas
    après — et savoir aussi que le changeur ne débloquera rien tant qu'il
    n'aura pas vu l'argent sur son propre compte, ce qui prend le temps que
    ça prend.
    */
    Note(stringResource(R.string.change_envoie_en_premier, p.delaiMinutes), AccentOrange)

    Text(
        stringResource(R.string.change_declarer_titre),
        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary
    )

    OutlinedTextField(
        value = state.referencePaiement,
        onValueChange = vm::onReferencePaiement,
        label = { Text(stringResource(R.string.change_ref_paiement)) },
        supportingText = { Text(stringResource(R.string.change_ref_paiement_aide), fontSize = 11.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = state.telephone,
        onValueChange = vm::onTelephone,
        label = { Text(stringResource(R.string.change_telephone)) },
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

@Composable
private fun Ligne(libelle: String, valeur: String, fort: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(libelle, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(
            valeur,
            fontSize = if (fort) 14.sp else 12.sp,
            fontWeight = if (fort) FontWeight.Bold else FontWeight.Medium,
            color = TextPrimary
        )
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
                if (couleur == AccentOrange) Icons.Default.Warning else Icons.Default.Info,
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
