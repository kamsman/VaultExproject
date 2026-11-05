package com.vaultex.ui.screens.actus

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.vaultex.ui.viewmodel.ActusViewModel

/*
═══════════════════════════════════════════════════════════════════════════
LES ACTUALITÉS S'OUVRENT DANS LE NAVIGATEUR, PAS DANS L'APPLICATION
═══════════════════════════════════════════════════════════════════════════

Une page web affichée DANS un portefeuille est la porte par laquelle les
gens se font vider : le site demande une signature, l'utilisateur voit
« Confirmer » et un texte qu'il ne comprend pas, et il approuve.

Ici il n'y a aucune raison de prendre ce risque. Un article se lit, il ne
signe rien. Le navigateur du téléphone le fait très bien, avec sa barre
d'adresse — donc avec le nom du site visible, ce qu'une page intégrée
cache par construction.

C'est aussi cohérent avec ce qu'on écrit à nos utilisateurs : « ta phrase
ne se tape jamais sur un site web ». Une application qui ouvre elle-même
des sites rend cette phrase plus difficile à tenir.
═══════════════════════════════════════════════════════════════════════════
*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActusScreen(navController: NavHostController) {
    val viewModel: ActusViewModel = hiltViewModel()
    val etat by viewModel.etat.collectAsState()
    val contexte = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.actus_titre),
                        fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.charger() }) {
                        Icon(
                            Icons.Default.Refresh,
                            stringResource(R.string.actus_rafraichir),
                            tint = AccentBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        when {
            /*
            LE CHARGEMENT NE S'AFFICHE QUE S'IL N'Y A RIEN À MONTRER.

            Un rafraîchissement sur une liste déjà pleine remplacerait les
            articles par un rond qui tourne — on perdrait ce qu'on était
            en train de lire pour attendre la même chose.
            */
            etat.chargement && etat.articles.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            }
            etat.articles.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Icon(
                            Icons.Default.Newspaper, null,
                            tint = TextSecondary, modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        /*
                        « PAS PU LIRE », ET NON « PAS D'ACTUALITÉ ».

                        Les deux donnent une liste vide et ne veulent pas
                        dire la même chose : l'une parle du monde, l'autre
                        du réseau. Dire la seconde quand c'est la première
                        ferait croire qu'il ne se passe rien.
                        */
                        Text(
                            stringResource(R.string.actus_vide),
                            color = TextSecondary, fontSize = 14.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { viewModel.charger() }) {
                            Text(stringResource(R.string.actus_rafraichir))
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    /*
                    L'ÉCHEC S'AFFICHE À CÔTÉ DES ARTICLES, PAS À LEUR PLACE.

                    C'est la contrepartie de la règle du ViewModel : un
                    rafraîchissement raté garde la liste. Sans ce bandeau,
                    l'utilisateur ne verrait RIEN se passer en appuyant sur
                    Rafraîchir, et conclurait que le bouton est mort.
                    */
                    if (etat.erreur) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(R.string.actus_vide),
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp, vertical = 8.dp
                                    )
                                )
                            }
                        }
                    }
                    items(etat.articles) { article ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceColor,
                            border = BorderStroke(1.dp, BorderColor),
                            modifier = Modifier.fillMaxWidth().clickable {
                                /*
                                UN LIEN QUI N'OUVRE RIEN NE DOIT PAS FAIRE
                                TOMBER L'ÉCRAN. Un téléphone sans
                                navigateur, c'est rare mais ça existe — et
                                une liste d'articles qui plante au premier
                                appui serait pire que pas de liste.
                                */
                                runCatching {
                                    contexte.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(article.lien))
                                    )
                                }
                            }
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                val teinte = if (article.finance) AccentBlue else P
                                Box(
                                    Modifier.size(38.dp).clip(CircleShape)
                                        .background(teinte.copy(alpha = 0.13f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (article.finance) Icons.Default.ShowChart
                                        else Icons.Default.Newspaper,
                                        null, tint = teinte, modifier = Modifier.size(17.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        article.titre,
                                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                        color = TextPrimary, lineHeight = 19.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        article.source,
                                        fontSize = 11.sp, color = TextSecondary
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.OpenInNew, null,
                                    tint = TextSecondary, modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val P = Color(0xFF7C5CFC)
