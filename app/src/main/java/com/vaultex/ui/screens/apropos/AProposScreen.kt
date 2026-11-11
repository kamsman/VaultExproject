package com.vaultex.ui.screens.apropos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.AProposViewModel
import com.vaultex.ui.viewmodel.Membre

/*
═══════════════════════════════════════════════════════════════════════════
« À PROPOS » — CE QUE L'APPLICATION SAIT D'ELLE-MÊME, PUIS L'ÉQUIPE
═══════════════════════════════════════════════════════════════════════════

L'ORDRE N'EST PAS DÉCORATIF. Le nom, la version et ce que fait
l'application sont dans l'APK : ils s'affichent sans réseau, tout de
suite, y compris pour quelqu'un qui ouvre cet écran justement parce que
rien d'autre ne marche. L'équipe vient du relais, et c'est un supplément.

Sans réseau, la section de l'équipe disparaît — pas de message d'erreur
sur un écran dont l'essentiel s'affiche très bien.

─── CE QUE CET ÉCRAN N'EST PAS ────────────────────────────────────────

Ce n'est pas un moyen de vérifier avec qui l'on traite. Il a été demandé
d'abord pour ça — « je veux la photo du changeur » — et c'est précisément
ce qu'il ne faut pas en faire : une photo officielle distribuée dans
l'application est une photo qu'un escroc met en photo de profil, et
l'utilisateur qui compare les visages trouve alors que ça correspond.

Ce qui identifie le changeur est le NUMÉRO affiché sur l'écran d'achat au
moment de la commande, et rien d'autre. Si cet écran devait un jour
servir à authentifier quelqu'un, il faudrait le retirer.
═══════════════════════════════════════════════════════════════════════════
*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AProposScreen(navController: NavHostController) {
    val viewModel: AProposViewModel = hiltViewModel()
    val etat by viewModel.etat.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_about),
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
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    Text(
                        stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold, fontSize = 26.sp, color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.app_tagline),
                        fontSize = 13.sp, color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    /*
                    LA VRAIE VERSION, LUE DANS BuildConfig.

                    « v1.0.0 » avait été écrit en dur dans les trois
                    traductions : l'écran Aide affichait le vrai numéro
                    pendant qu'« À propos » en annonçait un autre, ce qui
                    rendait les deux inutilisables pour savoir quelle
                    version tourne vraiment.
                    */
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SurfaceColor,
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Text(
                            stringResource(
                                R.string.settings_version_short,
                                com.vaultex.BuildConfig.VERSION_NAME
                            ),
                            fontSize = 12.sp, color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.apropos_mission),
                    fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (etat.membres.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.apropos_equipe),
                        fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary,
                        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
                    )
                }
                items(etat.membres) { membre -> CarteMembre(membre) }
            }
        }
    }
}

@Composable
private fun CarteMembre(membre: Membre) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(membre)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    membre.nom,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(membre.poste, fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

/*
LES INITIALES SONT DESSOUS, LA PHOTO PAR-DESSUS.

C'est le même montage que les logos de monnaies (voir CryptoIcon) et il
n'a qu'un but : il n'existe aucun état où le rond est vide. Une photo
absente, une adresse injoignable, un réseau coupé, un format que Coil ne
sait pas lire — dans tous ces cas les initiales restent, parce qu'elles
étaient déjà là avant que l'image ne soit demandée.

─── MAIS ELLES DOIVENT S'EFFACER QUAND LA PHOTO ARRIVE ─────────────────

CONSTATÉ SUR L'ÉCRAN, et je ne l'avais pas vu en l'écrivant : les photos
de l'équipe sont DÉTOURÉES. Leur fond est transparent, et les initiales
posées dessous transparaissaient tout autour de la tête — deux lettres
bleues dans les coins du rond, qu'on prend pour un détourage raté.

Le montage était juste tant que les photos étaient opaques. Il devenait
faux à la seconde où elles ont cessé de l'être, et rien dans le code ne
le disait.

`onState` règle les deux à la fois : les initiales tiennent la place
pendant le chargement et en cas d'échec, puis disparaissent à l'instant
où l'image est posée. On n'attend donc toujours pas de connaître l'échec
pour dessiner quelque chose — c'est le succès qu'on attend pour effacer.
*/
@Composable
private fun Avatar(membre: Membre) {
    /*
    `remember(membre.photo)` et non `remember(Unit)` : la liste est
    recyclée par la LazyColumn, et un rond qui change de membre en
    gardant « photo déjà chargée » afficherait un rond vide le temps de
    la nouvelle image.
    */
    var photoPosee by remember(membre.photo) { mutableStateOf(false) }

    Box(
        Modifier.size(46.dp).clip(CircleShape).background(AccentBlue.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        if (!photoPosee) {
            Text(
                initiales(membre.nom),
                fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AccentBlue
            )
        }
        if (membre.photo.isNotEmpty()) {
            coil.compose.AsyncImage(
                model = membre.photo,
                contentDescription = membre.nom,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(CircleShape),
                onState = { etat ->
                    photoPosee = etat is coil.compose.AsyncImagePainter.State.Success
                }
            )
        }
    }
}

/**
 * Deux lettres au plus, prises sur les deux premiers mots.
 *
 * « Rasmane OUEDRAOGO, dit DOLLAR » donne « RO » et non « RD » : on
 * s'arrête aux deux premiers mots, ce qui reste le nom dans tous les cas
 * qu'on a. Un nom d'un seul mot donne une seule lettre, et c'est très
 * bien — mieux qu'une lettre inventée.
 */
private fun initiales(nom: String): String =
    nom.split(' ', '-')
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }
