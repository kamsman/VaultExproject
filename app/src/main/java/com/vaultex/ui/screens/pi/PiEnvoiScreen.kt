package com.vaultex.ui.screens.pi

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
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
import com.vaultex.core.crypto.PiXdr
import com.vaultex.domain.pi.ResultatEnvoiPi
// Imports nommes : com.vaultex.ui.theme.Surface est une COULEUR, celui de
// material3 est un composant. Meme alias que PiScreen et SwapScreen.
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentGreen
import com.vaultex.ui.theme.AccentOrange
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.viewmodel.PiEnvoiViewModel
import com.vaultex.ui.viewmodel.TypeMemoPi

/*
═══════════════════════════════════════════════════════════════════════════
ENVOYER DES PI
═══════════════════════════════════════════════════════════════════════════

Trois champs que les autres monnaies n'ont pas, et trois règles du réseau
Pi qui les imposent. L'écran les dit AVANT la confirmation, parce que
chacune, découverte au refus, coûte des frais brûlés.

LE DISPONIBLE N'EST PAS LE SOLDE. Le réseau Pi oblige à laisser une
réserve sur le compte. L'écran affiche les deux chiffres côte à côte, et
« Max » remplit le disponible — jamais le solde.

LE MÉMO EST FACULTATIF, SAUF EN BOURSE. Un dépôt chez OKX ou Gate sans
mémo est perdu : c'est le mémo qui dit à qui créditer. L'application ne
peut pas savoir si la destination en exige un, donc elle ne devine pas —
mais elle le dit, clairement, au moment où il faut le saisir.

UNE ADRESSE NEUVE DOIT ÊTRE CRÉÉE. L'écran interroge la chaîne dès que
l'adresse est complète, et annonce le montant minimum avant la saisie du
montant.
═══════════════════════════════════════════════════════════════════════════
*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiEnvoiScreen(navController: NavController) {
    val vm: PiEnvoiViewModel = hiltViewModel()
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.pi_envoi_titre),
                        fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(com.vaultex.ui.navigation.Routes.SCANNER) }) {
                        Icon(Icons.Default.QrCodeScanner, null, tint = AccentBlue)
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

            // ─── Solde et disponible ──────────────────────────────────
            val cap = state.capacite
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.chargement && cap == null) {
                        Text(
                            stringResource(R.string.pi_envoi_lecture_solde),
                            fontSize = 13.sp, color = TextSecondary
                        )
                    } else if (cap == null) {
                        Text(
                            stringResource(R.string.pi_envoi_solde_illisible),
                            fontSize = 13.sp, color = AccentOrange
                        )
                    } else {
                        Text(
                            stringResource(
                                R.string.pi_envoi_solde,
                                PiXdr.texteDepuisStroops(cap.soldeStroops)
                            ),
                            fontSize = 13.sp, color = TextSecondary
                        )
                        Text(
                            stringResource(
                                R.string.pi_envoi_disponible,
                                PiXdr.texteDepuisStroops(cap.disponibleStroops)
                            ),
                            fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                        )
                        /*
                        LA RÉSERVE EST EXPLIQUÉE, PAS SEULEMENT SOUSTRAITE.

                        Sans cette ligne, l'écart entre le solde et le
                        disponible ressemble à des frais cachés prélevés par
                        VaultEx. C'est le réseau Pi qui l'impose, l'argent
                        reste sur le compte, et le dire supprime le soupçon.
                        */
                        Text(
                            stringResource(
                                R.string.pi_envoi_reserve,
                                PiXdr.texteDepuisStroops(cap.reserveStroops),
                                PiXdr.texteDepuisStroops(cap.fraisStroops)
                            ),
                            fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp
                        )
                    }
                }
            }

            // ─── Destination ──────────────────────────────────────────
            OutlinedTextField(
                value = state.destination,
                onValueChange = vm::onDestination,
                label = { Text(stringResource(R.string.pi_envoi_destination)) },
                placeholder = { Text("G…", color = TextSecondary) },
                singleLine = false,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            when (state.destinationNeuve) {
                true -> NoteInfo(
                    stringResource(R.string.pi_envoi_compte_neuf),
                    AccentOrange
                )
                false -> NoteInfo(
                    stringResource(R.string.pi_envoi_compte_existant),
                    AccentGreen
                )
                null -> {
                    // Ni l'un ni l'autre : adresse incomplète, ou chaîne
                    // muette. On n'affirme rien plutôt que de deviner.
                }
            }

            // ─── Montant ──────────────────────────────────────────────
            OutlinedTextField(
                value = state.montant,
                onValueChange = vm::onMontant,
                label = { Text(stringResource(R.string.pi_envoi_montant) + " (PI)") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                ),
                trailingIcon = {
                    TextButton(onClick = vm::onMax) {
                        Text(stringResource(R.string.max_label), color = AccentBlue, fontSize = 13.sp)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // ─── Mémo ─────────────────────────────────────────────────
            Text(
                stringResource(R.string.pi_envoi_memo_titre),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
            )
            NoteInfo(stringResource(R.string.pi_envoi_memo_explication), AccentBlue)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoixMemo(stringResource(R.string.pi_envoi_memo_aucun), state.typeMemo == TypeMemoPi.AUCUN) {
                    vm.onTypeMemo(TypeMemoPi.AUCUN)
                }
                ChoixMemo(stringResource(R.string.pi_envoi_memo_texte), state.typeMemo == TypeMemoPi.TEXTE) {
                    vm.onTypeMemo(TypeMemoPi.TEXTE)
                }
                ChoixMemo(stringResource(R.string.pi_envoi_memo_id), state.typeMemo == TypeMemoPi.IDENTIFIANT) {
                    vm.onTypeMemo(TypeMemoPi.IDENTIFIANT)
                }
            }

            if (state.typeMemo != TypeMemoPi.AUCUN) {
                val octets = state.memo.toByteArray(Charsets.UTF_8).size
                val tropLong = state.typeMemo == TypeMemoPi.TEXTE &&
                    octets > PiXdr.MEMO_TEXTE_MAX_OCTETS
                OutlinedTextField(
                    value = state.memo,
                    onValueChange = vm::onMemo,
                    label = { Text(stringResource(R.string.pi_envoi_memo)) },
                    isError = tropLong,
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = if (state.typeMemo == TypeMemoPi.IDENTIFIANT)
                            androidx.compose.ui.text.input.KeyboardType.Number
                        else androidx.compose.ui.text.input.KeyboardType.Text
                    ),
                    supportingText = {
                        if (state.typeMemo == TypeMemoPi.TEXTE) {
                            /*
                            ON COMPTE EN OCTETS, ET ON LE MONTRE.

                            Le réseau Pi limite le mémo à 28 OCTETS, pas à
                            28 lettres : « é » en vaut deux. Afficher un
                            compte de caractères laisserait quelqu'un écrire
                            un mémo refusé sans comprendre pourquoi.
                            */
                            Text(
                                "$octets / ${PiXdr.MEMO_TEXTE_MAX_OCTETS}",
                                color = if (tropLong) AccentRed else TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(2.dp))

            Button(
                onClick = vm::envoyer,
                enabled = vm.pretAEnvoyer(),
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
                Text(
                    stringResource(
                        if (state.envoiEnCours) R.string.pi_envoi_en_cours
                        else R.string.pi_envoi_bouton
                    ),
                    fontWeight = FontWeight.Bold
                )
            }

            state.resultat?.let { Resultat(it) { vm.effacerResultat() } }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChoixMemo(libelle: String, choisi: Boolean, onClick: () -> Unit) {
    FilterChip(selected = choisi, onClick = onClick, label = { Text(libelle, fontSize = 12.sp) })
}

@Composable
private fun NoteInfo(texte: String, couleur: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = couleur.copy(alpha = 0.07f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Icon(
                Icons.Default.Info, null,
                tint = couleur.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(texte, fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp)
        }
    }
}

/**
 * Le verdict.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * TROIS ISSUES, ET LA TROISIÈME N'EST PAS UN ÉCHEC
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Un portefeuille qui n'affiche que « réussi » ou « échoué » mentira un
 * jour, parce qu'il existe un troisième état bien réel : on ne sait pas
 * encore. Une diffusion partie dont la réponse s'est perdue est dans cet
 * état, et c'est le cas le plus fréquent sur un réseau mobile.
 *
 * Le présenter comme un échec est la pire des deux erreurs possibles :
 * l'utilisateur renverrait, et paierait deux fois. L'écran dit donc
 * exactement ce qui est vrai — « on vérifie, ne renvoie pas » — et
 * PiEnvoiUseCase tranchera dès que le réseau répondra.
 * ═══════════════════════════════════════════════════════════════════════
 */
@Composable
private fun Resultat(resultat: ResultatEnvoiPi, onFermer: () -> Unit) {
    val (couleur, icone, texte) = when (resultat) {
        is ResultatEnvoiPi.Reussi -> Triple(
            AccentGreen, Icons.Default.CheckCircle,
            stringResource(
                if (resultat.compteCree) R.string.pi_envoi_reussi_compte_cree
                else R.string.pi_envoi_reussi
            ) + "\n" + resultat.empreinte.take(16) + "…"
        )
        is ResultatEnvoiPi.Indetermine -> Triple(
            AccentOrange, Icons.Default.HourglassEmpty, resultat.raison
        )
        is ResultatEnvoiPi.Refuse -> Triple(
            AccentRed, Icons.Default.ErrorOutline, resultat.raison
        )
        is ResultatEnvoiPi.Invalide -> Triple(
            AccentOrange, Icons.Default.ErrorOutline, resultat.raison
        )
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = couleur.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icone, null, tint = couleur, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(texte, fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp)
            }
            /*
            « Indéterminé » n'offre PAS de bouton pour fermer.

            Fermer le message laisserait un formulaire rempli et un bouton
            d'envoi actif, sur un envoi dont le sort est inconnu. C'est
            précisément la situation où l'on paie deux fois. Le message
            reste jusqu'à ce que la réconciliation tranche.
            */
            if (resultat !is ResultatEnvoiPi.Indetermine) {
                TextButton(onClick = onFermer, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.pi_envoi_compris), color = couleur)
                }
            }
        }
    }
}
