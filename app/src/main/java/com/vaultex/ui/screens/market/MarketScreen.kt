package com.vaultex.ui.screens.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
// stickyHeader ne s'importe pas : c'est un membre de LazyListScope, comme
// item(). Seul items(), qui est une fonction d'extension, exige un import.
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.data.remote.dto.CoinGeckoMarketDto
import com.vaultex.ui.components.HistoryListSkeleton
import com.vaultex.ui.components.BottomBarSpace
import com.vaultex.ui.components.BoutonRecherche
import com.vaultex.ui.components.ChampRecherche
import com.vaultex.ui.components.rememberEtatRecherche
import com.vaultex.ui.components.VaultExBottomBar
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentGreen
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.Surface
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.MarketViewModel
import java.text.NumberFormat
import java.util.Locale

private enum class MarketFilter { ALL, SWAPPABLE, GAINERS, LOSERS, FAVORITES }

/**
 * Nombre de lignes restantes qui déclenche le chargement de la page suivante.
 *
 * Déclencher à la toute dernière ligne se voit : la liste s'arrête net, puis
 * repart. Avec dix lignes d'avance, la page suivante est là avant que
 * l'utilisateur n'atteigne le bas.
 */
private const val SEUIL_PREFETCH = 10

/**
 * Bouton rond de l'en-tête — recherche, cloche.
 *
 * Un IconButton nu se perd sur un fond sombre : rien ne dit qu'il se touche.
 * Le disque lui donne une cible visible et une surface de frappe confortable.
 */
@Composable
private fun BoutonRond(
    icone: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icone, description, tint = TextPrimary, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarketScreen(navController: NavHostController) {

    val viewModel: MarketViewModel = hiltViewModel()
    val markets by viewModel.markets.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val global by viewModel.global.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val isStale by viewModel.isStale.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val moreError by viewModel.moreError.collectAsState()
    val reseau by viewModel.reseau.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadMarkets() }

    var filter by remember { mutableStateOf(MarketFilter.ALL) }

    // Recherche repliable partagée avec les autres écrans : même bouton,
    // même champ, même geste.
    val etatRecherche = rememberEtatRecherche()

    /*
    La recherche ne se limite plus à la liste téléchargée.

    Deux sources, dans cet ordre voulu :
      · les monnaies DÉJÀ chargées, filtrées en local — elles s'affichent à
        la frappe, sans attendre le réseau ;
      · les résultats du catalogue CoinGecko (~19 000 monnaies), qui
        arrivent ensuite et complètent la liste.

    C'est ce qui donne une barre à la fois immédiate et complète : avant,
    elle était immédiate mais aveugle au-delà du rang 100, ce qui se lit
    comme « cette monnaie n'existe pas ».
    */
    val requete = etatRecherche.texte.trim()
    val enRecherche = requete.length >= 2

    // Le filtre local s'applique dès le PREMIER caractère : seul l'appel
    // réseau attend deux caractères, pas la réponse visuelle de la liste.
    val locaux = if (requete.isEmpty()) markets else markets.filter {
        it.name.contains(requete, true) || it.symbol.contains(requete, true)
    }

    val source = if (enRecherche) (locaux + searchResults).distinctBy { it.id } else locaux

    val filtered = source
        .filter {
            when (filter) {
                MarketFilter.ALL -> true
                MarketFilter.SWAPPABLE -> it.symbol.uppercase() in com.vaultex.ui.viewmodel.SwapViewModel.SWAPPABLE_SYMBOLS
                MarketFilter.GAINERS -> it.change24h > 0
                MarketFilter.LOSERS -> it.change24h < 0
                MarketFilter.FAVORITES -> it.id in favorites
            }
        }
    val topGainers = markets.filter { it.change24h > 0 }.sortedByDescending { it.change24h }.take(6)

    /*
    ═══════════════════════════════════════════════════════════════════════
    DÉCLENCHEUR DU DÉFILEMENT INFINI
    ═══════════════════════════════════════════════════════════════════════

    La première version posait un élément « sentinelle » en bas de la liste
    et lançait le chargement à sa composition. Constaté sur appareil : la
    liste s'arrêtait à la première page. Le déclenchement dépendait de la
    composition d'un élément placé sous une barre de navigation flottante et
    une marge basse — plusieurs hypothèses de mise en page, dont aucune n'est
    vérifiable depuis le code.

    On observe donc la position de défilement elle-même, qui ne dépend
    d'aucune de ces hypothèses. Et on déclenche SEUIL_PREFETCH lignes AVANT
    la fin plutôt qu'à la fin : la page suivante arrive pendant que
    l'utilisateur descend encore, au lieu de le laisser buter sur un arrêt
    net puis attendre.
    */
    val listState = rememberLazyListState()

    val doitCharger by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val dernierVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            info.totalItemsCount > 0 && dernierVisible >= info.totalItemsCount - SEUIL_PREFETCH
        }
    }

    // Ni pendant une recherche ni sous un filtre : la page suivante viendrait
    // s'ajouter à des résultats auxquels elle n'appartient pas.
    val paginationActive = requete.isEmpty() && filter == MarketFilter.ALL

    LaunchedEffect(doitCharger, paginationActive) {
        if (doitCharger && paginationActive) viewModel.loadMoreMarkets()
    }

    /*
    Une recherche lancée depuis le fond de la liste ramène en haut.

    Conséquence directe de la barre épinglée : on peut désormais chercher
    depuis la ligne 800. Sans ce retour, les résultats s'afficheraient bien
    — mais tout en haut, hors de l'écran, et la liste paraîtrait vide.

    La clé est le seul passage « vide → non vide », pas le texte lui-même :
    on remonte au DÉBUT d'une recherche, jamais à chaque frappe, sinon le
    défilement de l'utilisateur dans ses résultats serait sans cesse annulé.
    */
    LaunchedEffect(requete.isNotEmpty()) {
        if (requete.isNotEmpty()) listState.scrollToItem(0)
    }

    // La barre de navigation est FLOTTANTE : posée par-dessus le contenu, qui
    // défile derrière elle (comme Trust Wallet). Elle n'est donc plus le
    // bottomBar du Scaffold — sinon elle occuperait une place dans la mise en
    // page et resterait « collée » au bord.
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = BgPrimary
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            // Marge basse = hauteur de la barre flottante.
            contentPadding = PaddingValues(bottom = BottomBarSpace)
        ) {
            /*
            ─── En-tête ÉPINGLÉ : titre · recherche · cloche ───

            La recherche était un champ pleine largeur posé au milieu de
            l'écran. Trop encombrant pour une fonction qu'on n'utilise pas en
            permanence, et invisible dès qu'on descendait dans la liste.

            Elle tient maintenant dans un bouton rond à côté de la cloche, et
            ne se déploie qu'au moment où l'on s'en sert — en prenant la place
            du titre, plutôt qu'une ligne de plus. L'en-tête étant collant,
            elle reste atteignable jusqu'au rang 1000.
            */
            stickyHeader {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(BgPrimary)
                        .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (etatRecherche.ouverte) {
                        ChampRecherche(
                            etat = etatRecherche,
                            placeholder = stringResource(R.string.market_search_hint),
                            modifier = Modifier.weight(1f),
                            enChargement = searching,
                            onTexteChange = { viewModel.onSearchQueryChanged(it) }
                        )
                    } else {
                        Text(
                            stringResource(R.string.market_title),
                            fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        BoutonRecherche(etatRecherche)
                        Spacer(Modifier.width(8.dp))
                        BoutonRond(
                            icone = Icons.Default.Notifications,
                            description = stringResource(R.string.notifications)
                        ) { navController.navigate(Routes.NOTIFICATIONS) }
                    }
                }
            }

            // ─── Bandeau hors ligne (données du cache) ───
            if (isStale && markets.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            stringResource(R.string.offline_cached),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // ─── Bandeau global : cap. totale + dominance BTC ───
            global?.let { g ->
                item {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlobalStatCard(
                            title = stringResource(R.string.market_cap_total),
                            value = compactUsd(g.totalMcapUsd),
                            change = g.mcapChange24h,
                            modifier = Modifier.weight(1f)
                        )
                        GlobalStatCard(
                            title = stringResource(R.string.market_btc_dominance),
                            value = String.format(Locale.US, "%.1f%%", g.btcDominance),
                            change = null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }


            // ─── Filtres Tous / Gagnants / Perdants / Favoris ───
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    /*
                    Sélecteur de RÉSEAU, en tête des filtres.

                    Placé avant les autres parce qu'il ne joue pas le même
                    rôle : les pastilles suivantes trient ce qui est déjà
                    chargé, celle-ci change la liste elle-même en la
                    redemandant filtrée par blockchain.
                    */
                    PastilleReseau(reseau) { viewModel.setReseau(it) }
                    FilterPill(stringResource(R.string.market_filter_all), filter == MarketFilter.ALL) { filter = MarketFilter.ALL }
                    FilterPill(stringResource(R.string.market_swappable) + " ⇄", filter == MarketFilter.SWAPPABLE) { filter = MarketFilter.SWAPPABLE }
                    FilterPill(stringResource(R.string.market_filter_gainers) + " ↗", filter == MarketFilter.GAINERS) { filter = MarketFilter.GAINERS }
                    FilterPill(stringResource(R.string.market_filter_losers) + " ↘", filter == MarketFilter.LOSERS) { filter = MarketFilter.LOSERS }
                    FilterPill(stringResource(R.string.market_filter_favs) + " ★", filter == MarketFilter.FAVORITES) { filter = MarketFilter.FAVORITES }
                }
            }

            // ─── Top gagnants (cartes horizontales) ───
            if (topGainers.isNotEmpty() && filter == MarketFilter.ALL && requete.isEmpty()) {
                item {
                    Column(Modifier.padding(top = 12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🚀 " + stringResource(R.string.market_top_gainers), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            topGainers.forEach { dto ->
                                GainerCard(dto) { navController.navigate(Routes.coinDetail(dto.id)) }
                            }
                        }
                    }
                }
            }

            // ─── Liste des cryptos ───
            if (isLoading && markets.isEmpty()) {
                item { HistoryListSkeleton() }
            } else if (reseau.categorie != null && markets.isEmpty()) {
                /*
                Réseau filtré, aucune monnaie.

                Distingué d'une panne de chargement, et ce n'est pas un
                détail : un identifiant de catégorie qui ne correspond à rien
                chez CoinGecko renvoie une liste VIDE en HTTP 200, pas une
                erreur. Le message « impossible de charger » s'afficherait
                alors avec un bouton de réessai qui ne réussirait jamais.

                On nomme donc le réseau en cause et on offre le seul geste
                utile : revenir à la liste complète.
                */
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            stringResource(R.string.market_network_empty, reseau.libelle),
                            color = TextSecondary, fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { viewModel.setReseau(MarketViewModel.RESEAUX.first()) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) { Text(stringResource(R.string.market_network_all)) }
                    }
                }
            } else if (loadError && markets.isEmpty()) {
                // Chargement échoué ET aucun cache (1re ouverture hors ligne /
                // rate-limit CoinGecko) : message + bouton « Actualiser » manuel.
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            stringResource(R.string.coin_load_error),
                            color = TextSecondary, fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { viewModel.loadMarkets() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) { Text(stringResource(R.string.history_refresh)) }
                    }
                }
            } else {
                item {
                    Text(
                        stringResource(
                            if (enRecherche) R.string.market_search_results else R.string.market_all_cryptos
                        ),
                        fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                items(filtered, key = { it.id }) { dto ->
                    CoinRowCard(
                        dto = dto,
                        isFavorite = dto.id in favorites,
                        onToggleFavorite = { viewModel.toggleFavorite(dto.id) },
                        onAlert = { navController.navigate(Routes.NOTIFICATIONS) },
                        onClick = { navController.navigate(Routes.coinDetail(dto.id)) }
                    )
                }

                // Recherche aboutie mais sans correspondance : on le DIT. Une
                // liste qui se vide sans un mot laisse croire à une panne.
                if (requete.isNotEmpty() && !searching && filtered.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            /*
                            « Rien ne correspond » et « la recherche a
                            échoué » disaient la même phrase. L'écran
                            accusait donc le catalogue d'une panne venue du
                            réseau, et rien ne permettait de trancher — c'est
                            ce qui a rendu l'absence de Celo indiagnosticable.
                            */
                            Text(
                                stringResource(
                                    if (searchError) R.string.market_search_error
                                    else R.string.market_search_empty,
                                    requete
                                ),
                                fontSize = 14.sp, color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (searchError) {
                                Button(
                                    onClick = { viewModel.retrySearch() },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                                ) { Text(stringResource(R.string.history_refresh)) }
                            }
                        }
                    }
                }

                /*
                Pied de liste : ce qui se passe en bas, DIT.

                Le chargement d'une page suivante pouvait échouer sans
                laisser la moindre trace — la liste s'arrêtait, c'est tout.
                Un échec se voit maintenant, et se réessaie.
                */
                if (paginationActive && markets.isNotEmpty()) {
                    item {
                        when {
                            loadingMore -> Box(
                                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    Modifier.size(22.dp), color = AccentBlue, strokeWidth = 2.dp
                                )
                            }

                            moreError -> Column(
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    stringResource(R.string.market_more_error),
                                    fontSize = 13.sp, color = TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Button(
                                    onClick = { viewModel.retryLoadMore() },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                                ) { Text(stringResource(R.string.history_refresh)) }
                            }

                            // Combien de monnaies sont réellement chargées.
                            // Utile à l'utilisateur, qui sait où il en est du
                            // classement — et sans équivoque quand il s'agit
                            // de dire si une page a bien été ajoutée.
                            else -> Text(
                                stringResource(R.string.market_loaded_count, markets.size),
                                fontSize = 12.sp, color = TextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
        // Barre FLOTTANTE, posée par-dessus le contenu qui défile derrière.
        VaultExBottomBar(navController, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun GlobalStatCard(title: String, value: String, change: Double?, modifier: Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 12.sp, color = TextSecondary)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
            change?.let {
                Text(
                    (if (it >= 0) "+" else "") + String.format(Locale.US, "%.2f%%", it) + " (24h)",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = if (it >= 0) AccentGreen else AccentRed
                )
            }
        }
    }
}

@Composable
private fun GainerCard(dto: CoinGeckoMarketDto, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier.width(120.dp).clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            coil.compose.AsyncImage(model = dto.image, contentDescription = dto.symbol, modifier = Modifier.size(30.dp).clip(CircleShape))
            Text(dto.symbol.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
            Text("$" + formatMarketUsd(dto.currentPrice), fontSize = 12.sp, color = TextSecondary)
            Text("+" + String.format(Locale.US, "%.2f%%", dto.change24h), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
            Sparkline(dto.sparkline_in_7d?.price ?: emptyList(), AccentGreen, Modifier.fillMaxWidth().height(20.dp))
        }
    }
}

@Composable
private fun CoinRowCard(
    dto: CoinGeckoMarketDto,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onAlert: () -> Unit,
    onClick: () -> Unit
) {
    val isPositive = dto.change24h >= 0
    val changeColor = if (isPositive) AccentGreen else AccentRed

    // Maquette : ligne À PLAT (sans fond de carte), séparée par un fin trait —
    // rendu identique en thème clair et sombre.
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            coil.compose.AsyncImage(
                model = dto.image, contentDescription = dto.symbol,
                modifier = Modifier.size(40.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(dto.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(dto.symbol.uppercase(), fontSize = 11.sp, color = TextSecondary)
                    if (dto.symbol.uppercase() in com.vaultex.ui.viewmodel.SwapViewModel.SWAPPABLE_SYMBOLS) {
                        androidx.compose.material3.Surface(shape = RoundedCornerShape(5.dp), color = AccentGreen.copy(alpha = 0.13f)) {
                            Text(stringResource(R.string.market_swappable), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                color = AccentGreen, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$" + formatMarketUsd(dto.currentPrice), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Sparkline(dto.sparkline_in_7d?.price ?: emptyList(), changeColor, Modifier.size(width = 56.dp, height = 18.dp))
            }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Surface(shape = RoundedCornerShape(8.dp), color = changeColor.copy(alpha = 0.13f)) {
                Text(
                    "${if (isPositive) "+" else ""}${String.format(Locale.US, "%.2f", dto.change24h)}%",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = changeColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.NotificationsNone, stringResource(R.string.alerts_title), tint = TextSecondary,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onAlert)
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                stringResource(R.string.market_filter_favs),
                tint = if (isFavorite) Color(0xFFF5B301) else TextSecondary,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onToggleFavorite)
            )
        }
    }
}

/**
 * Pastille « Réseau ▾ » : ouvre la liste des blockchains.
 *
 * Elle affiche le réseau COURANT plutôt que le mot « Réseau », et se teinte
 * quand un filtre est actif. Une liste soudain plus courte sans que rien ne
 * dise pourquoi est le défaut classique de ce genre de sélecteur : ici, la
 * cause reste écrite à l'écran.
 */
@Composable
private fun PastilleReseau(
    courant: MarketViewModel.Reseau,
    onChoisir: (MarketViewModel.Reseau) -> Unit
) {
    var ouvert by remember { mutableStateOf(false) }
    val actif = courant.categorie != null
    /*
    « Réseau » au repos, le nom du réseau quand un filtre est posé.

    Afficher « Réseau » en permanence coûterait l'information la plus utile :
    savoir sur quoi la liste est filtrée. Afficher « Tous » au repos, à
    l'inverse, ne dit pas de quoi il s'agit — ce mot pourrait porter sur
    n'importe lequel des filtres voisins.

    Le libellé change donc de rôle avec l'état : il NOMME la fonction tant
    qu'elle est inutilisée, il en donne le RÉSULTAT dès qu'elle sert.
    */
    val texte = if (actif) courant.libelle else stringResource(R.string.market_network)
    Box {
        Box(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (actif) AccentBlue else Surface)
                .clickable { ouvert = true }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                "$texte  ▾",
                fontSize = 13.sp,
                fontWeight = if (actif) FontWeight.Bold else FontWeight.Medium,
                color = if (actif) Color.White else TextSecondary
            )
        }
        DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
            MarketViewModel.RESEAUX.forEach { r ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (r.categorie == null) stringResource(R.string.market_network_all) else r.libelle,
                            fontWeight = if (r == courant) FontWeight.Bold else FontWeight.Normal,
                            color = if (r == courant) AccentBlue else TextPrimary
                        )
                    },
                    onClick = { ouvert = false; onChoisir(r) }
                )
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AccentBlue else Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else TextSecondary
        )
    }
}

/** Vraie mini-courbe 7 j (données CoinGecko), sous-échantillonnée pour rester légère. */
@Composable
private fun Sparkline(prices: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (prices.size < 2) return@Canvas
        val pts = if (prices.size > 40) prices.filterIndexed { i, _ -> i % (prices.size / 40) == 0 } else prices
        val min = pts.min(); val max = pts.max()
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val step = size.width / (pts.size - 1)
        val path = Path()
        pts.forEachIndexed { i, p ->
            val x = i * step
            val y = size.height * (1f - ((p - min) / range).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 2f))
    }
}

/** Cap. de marché compacte : 3,52 T$, 70,1 Md$… */
private fun compactUsd(v: Double): String = when {
    v >= 1e12 -> String.format(Locale.US, "$%.2fT", v / 1e12)
    v >= 1e9 -> String.format(Locale.US, "$%.1fB", v / 1e9)
    v >= 1e6 -> String.format(Locale.US, "$%.1fM", v / 1e6)
    else -> String.format(Locale.US, "$%,.0f", v)
}

/* Formateur mis en cache : reconstruire un NumberFormat pour chaque ligne de
   marché, à chaque image du défilement, charge les données de locale à chaque
   fois. Utilisé uniquement depuis la composition (thread UI unique). */
private var marketUsdLocale: Locale? = null
private var marketUsdFormatCache: NumberFormat = NumberFormat.getNumberInstance(Locale.FRENCH)

private fun marketUsdFormat(): NumberFormat {
    val loc = com.vaultex.core.session.LocaleManager.appLocale()
    if (marketUsdLocale != loc) {
        marketUsdFormatCache = NumberFormat.getNumberInstance(loc)
        marketUsdLocale = loc
    }
    return marketUsdFormatCache
}

/**
 * Prix en USD, précision adaptée à l'ordre de grandeur.
 *
 * Quatre décimales sous 1 $ ne suffisent pas : SHIB (~0,00001 $) et PEPE
 * (~0,000001 $) s'affichaient « 0 » dans la liste du Marché. Un prix à zéro
 * fait passer une monnaie parfaitement cotée pour cassée ou sans valeur.
 * Sous 0,01 $ on garde donc quatre chiffres SIGNIFICATIFS.
 */
internal fun formatMarketUsd(value: Double): String {
    val nf = marketUsdFormat()
    nf.minimumFractionDigits = 0
    nf.maximumFractionDigits = when {
        value >= 1000.0 -> 0
        value >= 1.0 -> 2
        value >= 0.01 -> 4
        value > 0.0 -> {
            val leadingZeros = kotlin.math.floor(-kotlin.math.log10(value)).toInt()
            (leadingZeros + 4).coerceIn(4, 12)
        }
        else -> 2
    }
    return nf.format(value)
}
