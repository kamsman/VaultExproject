package com.vaultex.ui.screens.pi

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.domain.pi.SoldePi
// Imports nommes, et non un joker : com.vaultex.ui.theme.Surface est une
// COULEUR, androidx.compose.material3.Surface est un composant. Les deux
// jokers rendraient `Surface` ambigu — le reste du depot resout cela par le
// meme alias (voir SwapScreen).
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentOrange
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.viewmodel.PiViewModel

/*
═══════════════════════════════════════════════════════════════════════════
PI — ADRESSE ET SOLDE
═══════════════════════════════════════════════════════════════════════════

Cet écran ne sait RIEN envoyer. Il affiche l'adresse Pi du portefeuille, son
solde, et dit la seule chose qui compte pour s'en servir.

─── POURQUOI UN ÉCRAN À PART, ET NON UNE MONNAIE DE PLUS ────────────────

Ajouter Pi au registre des actifs le ferait apparaître dans le sélecteur
d'échange, où il n'a rien à faire : aucun échangeur ne l'accepte, et la
chaîne Pi ne porte aucun actif à coter. Une monnaie proposée à l'échange et
systématiquement refusée apprend à l'utilisateur que l'application est
cassée.

─── CE QU'IL FAUT DIRE, ET QUE PERSONNE N'AIME ENTENDRE ─────────────────

Cette adresse est NEUVE. Les Pi déjà minés vivent sous la phrase du Pi
Wallet, qui est une autre phrase : ils n'apparaîtront pas ici tant qu'on ne
les y aura pas envoyés. Taire ce point produirait exactement la plainte
qu'on veut éviter — « VaultEx ne voit pas mes Pi ».
═══════════════════════════════════════════════════════════════════════════
*/
/** Montant en francs, sans décimale : le franc CFA n'en a pas. */
private fun fcfa(v: Double): String =
    java.text.NumberFormat.getNumberInstance(
        com.vaultex.core.session.LocaleManager.appLocale()
    ).apply { maximumFractionDigits = 0 }.format(v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiScreen(navController: NavController) {
    val viewModel: PiViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val copier = com.vaultex.ui.components.rememberCopieAvecVibration()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.pi_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            val adresse = state.adresse
            if (adresse == null) {
                if (state.chargement) CircularProgressIndicator()
                else Text(
                    stringResource(R.string.pi_adresse_indisponible),
                    color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center
                )
            } else {

                // ─── Solde ───
                Text(
                    when (val s = state.solde) {
                        is SoldePi.Connu -> "${s.montant} Pi"
                        SoldePi.JamaisCredite -> "0 Pi"
                        SoldePi.Inconnu -> "—"
                        null -> "—"
                    },
                    fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                )
                /*
                LA CONTREVALEUR, ET CE QU'ELLE N'EST PAS.

                Le Pi s'échange à des prix sensiblement différents selon la
                place — OKX, Gate et MEXC n'affichent pas le même. Ce chiffre
                est une MOYENNE de marché : l'écrire sans le dire laisserait
                croire qu'on obtiendra exactement cela en vendant, et la
                déception serait pour le jour de la vente.

                Le prix unitaire s'affiche même à solde nul : savoir ce que
                vaut un Pi est utile avant d'en recevoir.
                */
                state.prixXof?.let { prix ->
                    val montant = (state.solde as? SoldePi.Connu)?.montant ?: 0.0
                    Text(
                        stringResource(
                            R.string.pi_contrevaleur,
                            fcfa(montant * prix),
                            fcfa(prix)
                        ),
                        fontSize = 12.sp, color = TextSecondary, textAlign = TextAlign.Center
                    )
                }
                if (state.solde == SoldePi.Inconnu) {
                    Text(
                        stringResource(R.string.pi_solde_inconnu),
                        fontSize = 12.sp, color = AccentOrange, textAlign = TextAlign.Center
                    )
                }

                // ─── QR ───
                val qr = remember(adresse) {
                    com.vaultex.ui.screens.receive.generateQr(adresse, 520)
                }
                qr?.let {
                    Surface(shape = RoundedCornerShape(16.dp), color = androidx.compose.ui.graphics.Color.White) {
                        Image(
                            it.asImageBitmap(), stringResource(R.string.pi_title),
                            modifier = Modifier.padding(12.dp).size(200.dp)
                        )
                    }
                }

                // ─── Adresse ───
                Surface(
                    shape = RoundedCornerShape(14.dp), color = SurfaceColor,
                    modifier = Modifier.fillMaxWidth().clickable { copier(adresse) }
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            adresse, fontSize = 12.sp, color = TextPrimary,
                            modifier = Modifier.weight(1f),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Default.ContentCopy, stringResource(R.string.copy),
                            tint = AccentBlue, modifier = Modifier.size(18.dp))
                    }
                }

                /*
                L'AVERTISSEMENT EST LA RAISON D'ÊTRE DE CET ÉCRAN.

                Sans lui, quelqu'un ouvre « Pi », voit zéro, et conclut que
                l'application est en panne — alors qu'elle dit la vérité sur une
                adresse qui n'a jamais rien reçu.
                */
                Surface(shape = RoundedCornerShape(12.dp), color = AccentBlue.copy(alpha = 0.10f)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.pi_adresse_neuve),
                            fontSize = 12.sp, color = TextPrimary, lineHeight = 17.sp
                        )
                    }
                }

                /*
                LE COMPTE N'EXISTE PAS ENCORE SUR LA CHAÎNE.

                Cet écran sait déjà le dire — SoldePi.JamaisCredite n'est pas
                la même chose qu'un solde nul — mais il n'en tirait aucune
                conséquence pratique.

                Or elle est lourde : mesuré sur OKX, la plateforme refuse
                cette adresse à la saisie avec « Adresse incorrecte », alors
                que sa propre adresse de dépôt passe dans le même champ. La
                seule différence entre les deux est l'existence du compte.

                Quelqu'un qui ne le sait pas conclut que VaultEx est cassé —
                c'est la seule conclusion disponible quand un courtier vous
                dit que votre adresse est incorrecte.
                */
                if (state.solde == SoldePi.JamaisCredite) {
                    Surface(shape = RoundedCornerShape(12.dp), color = AccentOrange.copy(alpha = 0.12f)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Info, null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.pi_compte_a_creer, "1"),
                                fontSize = 12.sp, color = TextPrimary, lineHeight = 17.sp
                            )
                        }
                    }
                }

                /*
                DE QUOI REPARTIR : CET ÉCRAN NE DOIT PAS ÊTRE UN CUL-DE-SAC.

                Il montre une adresse et un solde. Quelqu'un qui vient d'y
                recevoir ses premiers Pi veut ensuite les envoyer, et il est
                ICI — pas dans la liste « Envoyer ». Sans ce bouton, il
                faudrait ressortir, trouver l'accueil, toucher la ligne PI,
                puis Envoyer. Quatre gestes pour une suite évidente.
                */
                Button(
                    onClick = { navController.navigate(com.vaultex.ui.navigation.Routes.PI_ENVOI) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.pi_envoi_bouton), fontWeight = FontWeight.Bold)
                }

                Text(
                    stringResource(R.string.pi_pas_d_echange),
                    fontSize = 11.sp, color = TextSecondary, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
