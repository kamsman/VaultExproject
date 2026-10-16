package com.vaultex.ui.screens.assistance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentOrange
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.viewmodel.AssistanceViewModel
import com.vaultex.ui.viewmodel.MessageAssistance

/*
═══════════════════════════════════════════════════════════════════════════
L'ÉCRAN D'ASSISTANCE
═══════════════════════════════════════════════════════════════════════════

Une conversation, et rien d'autre. Pas de catégories à choisir, pas de
formulaire : quelqu'un qui bloque veut écrire sa phrase, pas remplir des
champs.

─── CE QUI EST DIT D'ENTRÉE, ET POURQUOI ────────────────────────────────

Que c'est une assistance automatique, et que le changeur reste joignable.
Laisser croire à un humain serait payé deux fois : par la déception quand
la réponse sonne faux, et par la confiance qu'on ne regagne pas.

Et qu'on ne demandera JAMAIS la phrase secrète. C'est l'arnaque la plus
courante sur ce marché — quelqu'un se fait passer pour le support et
réclame les douze mots. Le dire ici, dans l'écran qui ressemble le plus à
ce qu'un escroc imiterait, est le seul endroit où ça protège vraiment.
═══════════════════════════════════════════════════════════════════════════
*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistanceScreen(navController: NavController) {
    val vm: AssistanceViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val defilement = rememberScrollState()

    // On suit la conversation : une réponse qui arrive hors de l'écran
    // ressemble à une absence de réponse.
    LaunchedEffect(state.messages.size, state.enCours) {
        defilement.animateScrollTo(defilement.maxValue)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.assistance_titre),
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
        /*
        ═══════════════════════════════════════════════════════════════════
        LE CLAVIER NE DOIT PAS RECOUVRIR LA BARRE DE SAISIE
        ═══════════════════════════════════════════════════════════════════

        Vu sur un vrai téléphone : le clavier ouvert, et le champ passé
        dessous. On écrit sans voir ce qu'on écrit — sur l'écran où l'on
        pose une question, c'est-à-dire où l'on formule déjà mal.

        Le manifeste déclare pourtant `adjustResize`, et d'autres écrans
        de cette application emploient `imePadding()`. Celui-ci ne
        l'avait pas : la barre de saisie est fixée en bas d'une colonne
        qui remplit l'écran, donc elle descend avec le clavier.

        `imePadding` réserve la hauteur du clavier ouvert. La conversation
        se comprime, la barre reste visible, et le défilement automatique
        la suit.
        ═══════════════════════════════════════════════════════════════════
        */
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {

            Column(
                Modifier.weight(1f).verticalScroll(defilement)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.messages.isEmpty()) Accueil(vm)

                state.messages.forEach { Bulle(it) }

                if (state.enCours) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AccentBlue
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.assistance_en_cours),
                            fontSize = 12.sp, color = TextSecondary
                        )
                    }
                }

                state.erreur?.let {
                    Text(it, fontSize = 12.sp, color = AccentRed)
                }

                /*
                « PARLER AU CHANGEUR » N'EST PAS UN ÉCHEC.

                C'est la sortie prévue, et elle doit être visible au moment
                où elle sert : quand la réponse automatique n'a pas suffi.
                L'afficher en permanence la banaliserait.
                */
                if (state.passerAuChangeur) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AccentOrange.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(R.string.assistance_humain_titre),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                            )
                            Text(
                                stringResource(R.string.assistance_humain_aide),
                                fontSize = 12.sp, color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            if (state.numeroDemande) DemandeNumero(vm, state.numero, state.numeroValide)
            else Saisie(vm, state.saisie, state.peutEnvoyer)
        }
    }
}

/*
L'ACCUEIL DIT TROIS CHOSES, ET PAS UNE DE PLUS.

Ce que c'est, ce que ça ne fera jamais, et par quoi commencer. Un écran
vide ferait écrire « bonjour » et attendre ; une page d'explications ne
serait pas lue.
*/
@Composable
private fun Accueil(vm: AssistanceViewModel) {
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SupportAgent, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.assistance_accueil_titre),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                )
            }
            Text(
                stringResource(R.string.assistance_accueil_aide),
                fontSize = 12.sp, color = TextSecondary
            )
            Text(
                stringResource(R.string.assistance_jamais_phrase),
                fontSize = 12.sp, color = AccentOrange, fontWeight = FontWeight.SemiBold
            )

            /*
            ═══════════════════════════════════════════════════════════════
            QUATRE QUESTIONS TOUTES FAITES
            ═══════════════════════════════════════════════════════════════

            Un champ vide demande de formuler, et formuler est justement ce
            qu'on fait mal quand on est bloque ou presse. Sur un telephone,
            dans la rue, « c'est quoi le minimum » prend vingt frappes.

            CE SONT CELLES AUXQUELLES LE RELAIS SAIT REPONDRE SANS MODELE,
            et ce n'est pas un hasard : tant qu'il n'y a pas de cle d'API,
            ce sont les seules qui aboutissent. Les proposer, c'est mener
            vers ce qui marche au lieu de laisser decouvrir ce qui ne
            marche pas.

            La derniere — la phrase secrete — n'est pas une question qu'on
            pense a poser. C'est pour ca qu'elle est la : quelqu'un a qui
            l'on vient de reclamer ses douze mots doit pouvoir verifier en
            un geste, avant d'avoir le temps d'hesiter.
            ═══════════════════════════════════════════════════════════════
            */
            Spacer(Modifier.height(2.dp))
            listOf(
                R.string.assistance_ex_minimum,
                R.string.assistance_ex_delai,
                R.string.assistance_ex_reseau,
                R.string.assistance_ex_phrase
            ).forEach { id ->
                val texte = stringResource(id)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentBlue.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth()
                        .clickable { vm.poser(texte) }
                ) {
                    Text(
                        texte,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        color = AccentBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun Bulle(m: MessageAssistance) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.deMoi) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            // Le message de l'utilisateur en bleu, la réponse sur le fond
            // des cartes : la même grammaire que partout ailleurs.
            color = if (m.deMoi) AccentBlue.copy(alpha = 0.18f) else SurfaceColor,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                m.texte,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                fontSize = 13.sp,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun Saisie(vm: AssistanceViewModel, valeur: String, peutEnvoyer: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = valeur,
            onValueChange = vm::onSaisie,
            placeholder = { Text(stringResource(R.string.assistance_placeholder), color = TextSecondary) },
            modifier = Modifier.weight(1f),
            maxLines = 4
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = vm::envoyer, enabled = peutEnvoyer) {
            Icon(
                Icons.Default.Send,
                stringResource(R.string.assistance_envoyer),
                tint = if (peutEnvoyer) AccentBlue else TextSecondary
            )
        }
    }
}

/*
═══════════════════════════════════════════════════════════════════════════
LA DEMANDE DE NUMÉRO, AU TROISIÈME ÉCHANGE
═══════════════════════════════════════════════════════════════════════════

Elle remplace la barre de saisie au lieu de s'empiler au-dessus. Deux
raisons : on ne peut pas la rater, et elle n'ampute pas la conversation
qu'on vient de lire.

ELLE DIT À QUOI LE NUMÉRO SERT, et c'est la seule chose qui fait répondre.
« Pour mieux vous servir » ne fait répondre personne ; « pour que le
changeur puisse te rappeler si ta question doit lui être transmise » est
une raison vérifiable.

ET « CONTINUER SANS » RESTE. Un blocage dur serait payé par ceux qui
n'osent pas donner leur numéro — souvent les mêmes qui ont le plus besoin
d'aide.
═══════════════════════════════════════════════════════════════════════════
*/
@Composable
private fun DemandeNumero(vm: AssistanceViewModel, numero: String, valide: Boolean) {
    Surface(color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.assistance_numero_titre),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary
            )
            Text(
                stringResource(R.string.assistance_numero_aide),
                fontSize = 12.sp, color = TextSecondary
            )
            OutlinedTextField(
                value = numero,
                onValueChange = vm::onNumero,
                placeholder = { Text("Ex. 70 12 34 56", color = TextSecondary) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = vm::ignorerNumero, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.assistance_numero_sans), color = TextSecondary)
                }
                Button(
                    onClick = vm::validerNumero,
                    enabled = valide,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.assistance_numero_valider))
                }
            }
        }
    }
}
