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
import androidx.compose.ui.text.style.TextOverflow
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
import com.vaultex.ui.viewmodel.Actualite
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
                    /*
                    ═══════════════════════════════════════════════════
                    UN ARTICLE EN AVANT, PUIS LES AUTRES
                    ═══════════════════════════════════════════════════

                    Une liste plate de seize lignes identiques n'offre
                    aucun point d'entrée : l'œil commence en haut faute
                    de mieux, pas parce que quelque chose l'y invite.

                    Le premier article est le plus récent de la source
                    la plus récente — l'entrelacement du relais le place
                    là — donc le mettre en avant ne ment pas sur son
                    importance. C'est bien le dernier paru.

                    SANS IMAGE, et c'est un choix économique avant
                    d'être esthétique : une illustration de flux RSS
                    pèse 50 à 200 Ko, sur le forfait mobile de
                    quelqu'un à Ouagadougou, pour une photo d'agence
                    qui n'apprend rien.
                    */
                    val premier = etat.articles.first()
                    val reste = etat.articles.drop(1)

                    item { TitreSection(stringResource(R.string.actus_une)) }
                    item { CarteUne(premier) { ouvrir(contexte, premier.lien) } }

                    if (reste.isNotEmpty()) {
                        item { TitreSection(stringResource(R.string.actus_dernieres)) }
                        items(reste) { article ->
                            CarteArticle(article) { ouvrir(contexte, article.lien) }
                        }
                    }
                }
            }
        }
    }
}

private val P = Color(0xFF7C5CFC)

/** Violet pour la crypto, bleu pour la finance — comme la pastille ronde. */
@Composable
private fun teinteDe(article: Actualite) = if (article.finance) AccentBlue else P

/**
 * Ouvre l'article dans le navigateur du téléphone.
 *
 * UN LIEN QUI N'OUVRE RIEN NE DOIT PAS FAIRE TOMBER L'ÉCRAN. Un téléphone
 * sans navigateur, c'est rare mais ça existe — et une liste d'articles qui
 * plante au premier appui serait pire que pas de liste.
 *
 * Et c'est bien le navigateur, pas une WebView : voir l'en-tête du fichier.
 */
private fun ouvrir(contexte: android.content.Context, lien: String) {
    runCatching {
        contexte.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lien)))
    }
}

@Composable
private fun TitreSection(texte: String) {
    Text(
        texte,
        fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

/*
LA PASTILLE DE CATÉGORIE — DEUX VALEURS, PARCE QU'ON N'EN A QUE DEUX.

Le thème colorait déjà le rond de l'icône. Écrit en toutes lettres, il se
lit sans décoder une icône : un graphique boursier et un journal ne se
distinguent pas d'un coup d'œil à dix-sept pixels.

PAS DE TROISIÈME CATÉGORIE INVENTÉE. Une maquette proposait « Blockchain »,
« Économie » et « Tech » en plus : des filtres qu'on toucherait pour ne
rien trouver, puisque le relais ne range les sources que sous `crypto` ou
`finance`.
*/
@Composable
private fun Pastille(article: Actualite, grande: Boolean = false) {
    val teinte = teinteDe(article)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = teinte.copy(alpha = 0.15f)
    ) {
        Text(
            stringResource(
                if (article.finance) R.string.actus_theme_finance
                else R.string.actus_theme_crypto
            ),
            fontSize = if (grande) 11.sp else 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = teinte,
            modifier = Modifier.padding(
                horizontal = if (grande) 8.dp else 6.dp,
                vertical = if (grande) 4.dp else 2.dp
            )
        )
    }
}

/**
 * L'article mis en avant.
 *
 * Le titre y tient sur TROIS lignes au lieu de deux : c'est la contrepartie
 * de la mise en avant, et elle ne coûte qu'une ligne puisqu'il n'y en a
 * qu'un. Les autres restent à deux, où la régularité des hauteurs compte
 * plus que la fin d'un titre.
 */
@Composable
private fun CarteUne(article: Actualite, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        border = BorderStroke(1.dp, teinteDe(article).copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pastille(article, grande = true)
                Spacer(Modifier.weight(1f))
                ageArticle(article.quand)?.let {
                    Text(it, fontSize = 11.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                article.titre,
                fontWeight = FontWeight.Bold, fontSize = 17.sp,
                color = TextPrimary, lineHeight = 23.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    article.source,
                    fontSize = 12.sp, color = TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.OpenInNew, null,
                    tint = TextSecondary, modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun CarteArticle(article: Actualite, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        border = BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            val teinte = teinteDe(article)
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
                /*
                DEUX LIGNES AU PLUS.

                Mesure sur appareil : des titres libres prenaient trois
                lignes, et SEPT articles tenaient a l'ecran sur les seize
                envoyes. A deux lignes on en voit une dizaine, et toutes
                les cartes ont la meme hauteur — une liste reguliere se
                parcourt des yeux, une liste en escalier se lit.

                Ce qu'on perd est reel : la fin d'un titre long. C'est
                acceptable parce qu'un titre d'actualite met son sujet au
                debut, et que l'article complet est a un appui.
                */
                Text(
                    article.titre,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = TextPrimary, lineHeight = 19.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                /*
                LA CATEGORIE, LA SOURCE ET L'AGE SUR UNE SEULE LIGNE.

                Aucune hauteur ajoutee : la pastille remplace l'espace
                qu'occupait deja la ligne de source. L'age manquait, et
                c'etait le manque le plus visible — « DIRECT — fausses
                alertes a la bombe » ne dit pas s'il date de dix minutes
                ou de mardi.

                La donnee etait DEJA LA : le relais envoie `quand`, le
                modele le range dans `Actualite.quand`, et l'ecran la
                jetait.
                */
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pastille(article)
                    Spacer(Modifier.width(6.dp))
                    val age = ageArticle(article.quand)
                    Text(
                        if (age == null) article.source
                        else "${article.source} · $age",
                        fontSize = 11.sp, color = TextSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.OpenInNew, null,
                tint = TextSecondary, modifier = Modifier.size(15.dp)
            )
        }
    }
}

/*
═══════════════════════════════════════════════════════════════════════════
L'ÂGE D'UN ARTICLE — ÉCRIT À LA MAIN, ET CE N'EST PAS UN CAPRICE
═══════════════════════════════════════════════════════════════════════════

Android propose `DateUtils.getRelativeTimeSpanString`, qui rend « il y a
2 heures » tout fait et traduit. On ne s'en sert pas, pour une raison
précise à cette application.

VaultEx porte sa PROPRE langue, choisie dans les réglages et appliquée par
`LocaleManager.wrap` sur le Context de l'Activity. `DateUtils` va chercher
ses mots dans les ressources du SYSTÈME, pas dans celles de l'application.
Un utilisateur dont le téléphone est en anglais mais qui a mis VaultEx en
français lirait « 2 hours ago » au milieu d'un écran français — et en
arabe, le mélange serait pire encore, la ligne changeant de sens d'écriture
en son milieu.

Nos quatre chaînes passent par `stringResource`, donc par le Context de la
composition, donc par la langue de l'application. Quatre lignes de
traduction contre une incohérence qu'on n'aurait vue qu'en production.

─── LE TEMPS NE S'ÉCOULE PAS TOUT SEUL ICI ─────────────────────────────

`System.currentTimeMillis()` est lu à la composition : l'âge ne vieillit
pas sous les yeux de quelqu'un qui laisse l'écran ouvert. C'est voulu. Le
rafraîchir à la seconde demanderait une horloge qui recompose la liste
entière, pour corriger « il y a 2 h » en « il y a 2 h » dans l'immense
majorité des cas. Un retour sur l'écran, ou le bouton Rafraîchir, remet
tout à jour.
═══════════════════════════════════════════════════════════════════════════
*/
@Composable
private fun ageArticle(quand: Long): String? {
    if (quand <= 0L) return null

    val ecart = System.currentTimeMillis() - quand

    /*
    UNE DATE DANS LE FUTUR : ON SE TAIT PLUTÔT QUE DE MENTIR.

    Ça arrive — un éditeur qui publie avec un fuseau mal réglé date son
    article d'une heure d'avance. « il y a -45 min » serait absurde, et
    « dans 45 min » ferait croire à une actualité qui n'existe pas encore.

    Sous dix minutes d'écart on arrondit à « à l'instant », parce qu'à
    cette échelle c'est vrai et c'est utile : ce sont les articles les plus
    récents, donc ceux du haut de l'écran.
    */
    if (ecart < -10 * 60_000L) return null

    val minutes = (if (ecart < 0) 0L else ecart) / 60_000L
    return when {
        minutes < 1 -> stringResource(R.string.actus_age_instant)
        minutes < 60 -> stringResource(R.string.actus_age_min, minutes.toInt())
        minutes < 60 * 24 -> stringResource(R.string.actus_age_h, (minutes / 60).toInt())
        else -> stringResource(R.string.actus_age_j, (minutes / (60 * 24)).toInt())
    }
}
