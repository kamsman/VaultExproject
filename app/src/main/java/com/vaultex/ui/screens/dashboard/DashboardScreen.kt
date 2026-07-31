package com.vaultex.ui.screens.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.vaultex.R
import kotlinx.coroutines.launch
import com.vaultex.ui.components.BottomBarSpace
import com.vaultex.ui.components.VaultExBottomBar
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.PortfolioViewModel
import com.vaultex.ui.viewmodel.TokenBalance
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DashboardScreen(navController: NavHostController) {
    val viewModel: PortfolioViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val balanceHidden by viewModel.balanceHidden.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val visibleAssets by viewModel.visibleAssets.collectAsState()
    val pendingSymbols by viewModel.pendingSymbols.collectAsState()
    val unreadNotifs by viewModel.unreadNotifs.collectAsState()
    val recentTxs by viewModel.recentTxs.collectAsState()

    // P5 : un deep link de paiement valide redirige vers l'écran d'envoi
    LaunchedEffect(Unit) {
        if (com.vaultex.core.session.DeepLinkBuffer.hasPending()) {
            navController.navigate(Routes.SEND)
        }
    }

    /*
    ─── FLUIDITÉ DU DÉFILEMENT ───────────────────────────────────────────
    Tout ce qui suit vivait AUPARAVANT à l'intérieur des blocs item{} de la
    LazyColumn. Or LazyColumn détruit un item dès qu'il sort de l'écran et le
    recrée dès qu'il revient : à chaque aller-retour on ré-enregistrait un
    callback réseau (appel système), on relisait les préférences et on
    relançait le minuteur du carrousel — d'où les à-coups au défilement.
    Hoisté ici, tout cela est calculé une fois et reste stable.
    ──────────────────────────────────────────────────────────────────────
     */
    val offline = com.vaultex.core.session.NetworkMonitor.observeOffline()
    val bannerContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { TelegramBannerState.init(bannerContext) }
    // Statut PAR WALLET, relu à chaque retour sur le Dashboard (voir ON_RESUME
    // plus bas) : si l'utilisateur confirme sa sauvegarde puis revient, le
    // bandeau disparaît sans redémarrage.
    var phraseBackedUp by remember { mutableStateOf(viewModel.isPhraseBackedUp()) }

    // Rafraîchissement auto des soldes au RETOUR sur le Dashboard (après un
    // envoi, ou retour de l'app au premier plan) — sans spinner.
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                phraseBackedUp = viewModel.isPhraseBackedUp()
                viewModel.refreshSilently()
                // Après un envoi : rafale courte (toutes les 5 s) pour afficher
                // le solde dès la confirmation, sans attendre le poll de 45 s.
                if (com.vaultex.core.session.BalanceRefreshSignal.consumePending()) {
                    scope.launch {
                        repeat(5) {
                            kotlinx.coroutines.delay(5_000)
                            viewModel.refreshSilently()
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /*
    Rafraîchissement IMMÉDIAT quand un dépôt vient d'être détecté.

    Sans cette écoute, l'écran ne se mettait à jour qu'à son propre cycle de
    45 s : la notification pouvait donc arriver jusqu'à trois quarts de minute
    AVANT que les fonds ne s'affichent. L'utilisateur ouvrait l'application
    après avoir été prévenu et n'y voyait rien — le pire ressenti possible sur
    un portefeuille. Et quand le cycle tombait d'abord, c'était l'inverse : la
    notification annonçait un montant déjà visible.

    Les deux événements sont maintenant liés : le détecteur prévient, l'écran
    rafraîchit dans la seconde.
     */
    LaunchedEffect(Unit) {
        com.vaultex.core.session.BalanceRefreshSignal.events.collect {
            viewModel.refreshSilently()
        }
    }

    // Polling discret toutes les 45 s tant que le Dashboard est affiché
    // (pour voir une réception arriver sans action de l'utilisateur).
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(45_000)
            viewModel.refreshSilently()
        }
    }

    // ─── Carrousel de bandeaux : construit ICI (hors LazyColumn) pour que le
    // minuteur de rotation et l'index survivent au défilement. ───
    var depositDismissed by FirstDepositBannerState.dismissed
    val telegramHidden by TelegramBannerState.hidden
    var backupDismissed by BackupReminderBannerState.dismissed
    val hasFunds = state.totalBalanceUsd > 0.01
    val bannerSlots = buildList<@Composable () -> Unit> {
        // S'affiche UNIQUEMENT si le wallet est vide (solde 0). Fermeture
        // SESSION au ✕ (revient au prochain lancement) ; disparaît pour de
        // bon dès le premier fonds reçu.
        if (!hasFunds && !depositDismissed) add {
            DashboardBanner(
                accent = Color(0xFF16A34A),   // vert : recevoir des fonds (positif)
                icon = Icons.Default.AccountBalanceWallet,
                title = stringResource(R.string.dashboard_first_deposit_title),
                body = stringResource(R.string.dashboard_first_deposit_body),
                ctaLabel = stringResource(R.string.dashboard_first_deposit_cta),
                ctaIcon = Icons.Default.ArrowDownward,
                onDismiss = { depositDismissed = true },
                onCtaClick = { navController.navigate(Routes.RECEIVE) }
            )
        }
        // PHILOSOPHIE B : le rappel de sauvegarde n'apparaît QUE si le wallet
        // a des fonds (quelque chose à protéger). Wallet vide → priorité au
        // dépôt ; wallet financé → priorité à la sauvegarde. Jamais 3
        // bannières en même temps. Le ✕ n'est qu'un « pas maintenant » : le
        // rappel ne s'éteint vraiment qu'une fois la phrase révélée.
        if (hasFunds && !phraseBackedUp && !backupDismissed) add {
            DashboardBanner(
                accent = Color(0xFFF59E0B),   // ambre : rappel de sécurité (seed)
                icon = Icons.Default.Shield,
                title = stringResource(R.string.dashboard_backup_title),
                body = stringResource(R.string.dashboard_backup_body),
                ctaLabel = stringResource(R.string.dashboard_backup_cta),
                ctaIcon = Icons.Default.Shield,
                onDismiss = { backupDismissed = true },
                onCtaClick = { navController.navigate(Routes.BACKUP) }
            )
        }
        // Rappel marketing NON définitif : réapparaît ~1×/semaine (fenêtre de
        // 7 jours après le dernier ✕ ou clic « Rejoindre »).
        if (!telegramHidden) add {
            DashboardBanner(
                accent = Color(0xFF229ED9),   // bleu Telegram (communauté)
                icon = Icons.Default.Chat,
                title = stringResource(R.string.dashboard_telegram_title),
                body = stringResource(R.string.dashboard_telegram_body),
                ctaLabel = stringResource(R.string.dashboard_telegram_cta),
                ctaIcon = Icons.Default.Send,
                onDismiss = { TelegramBannerState.dismiss(bannerContext) },
                onCtaClick = {
                    bannerContext.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(TELEGRAM_COMMUNITY_URL)
                        )
                    )
                    // Cliquer « Rejoindre » = engagé → on ne relance plus
                    // (fermeture DURABLE, comme le ✕).
                    TelegramBannerState.dismiss(bannerContext)
                }
            )
        }
    }
    var bannerIndex by remember { mutableStateOf(0) }
    LaunchedEffect(bannerSlots.size) {
        while (bannerSlots.size > 1) {
            kotlinx.coroutines.delay(6_000L)
            bannerIndex = (bannerIndex + 1) % bannerSlots.size
        }
    }

    // Listes dérivées : mémorisées pour ne pas re-filtrer/re-trier à chaque
    // image pendant le défilement.
    val funded = remember(state.tokens) { state.tokens.filter { it.valueUsd > 0.0 } }
    val majors = remember(state.tokens) {
        listOf("BTC", "ETH", "SOL", "BNB").mapNotNull { sym -> state.tokens.firstOrNull { it.symbol == sym } }
    }
    // Règle : une monnaie s'affiche si elle est ACTIVÉE (Gérer les actifs) OU
    // si elle a un solde > 0 (on ne masque jamais de fonds). Les tokens
    // personnalisés (ajoutés par contrat) restent toujours affichés.
    val visibleTokens = remember(state.tokens, visibleAssets) {
        state.tokens
            .filter { it.valueUsd > 0.0 || it.symbol in visibleAssets || it.isCustom }
            .sortedByDescending { it.valueUsd }
    }

    // La barre de navigation est FLOTTANTE : posée par-dessus le contenu, qui
    // défile derrière elle (comme Trust Wallet). Elle n'est donc plus le
    // bottomBar du Scaffold — sinon elle occuperait une place dans la mise en
    // page et resterait « collée » au bord.
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = BgPrimary
    ) { padding ->
        // Pull-to-refresh (#6) : UNIQUEMENT déclenché par l'utilisateur (tirer
        // l'écran). L'indicateur ne s'affiche pas pour le chargement auto.
        var manualRefresh by remember { mutableStateOf(false) }
        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) manualRefresh = false
        }
        val pullState = rememberPullRefreshState(
            refreshing = manualRefresh,
            onRefresh = { manualRefresh = true; viewModel.refresh() }
        )
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .pullRefresh(pullState)
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Marge basse = hauteur de la barre flottante : le dernier élément
            // reste atteignable au lieu de disparaître dessous.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = BottomBarSpace),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ─── Bandeau HORS CONNEXION : se met à jour EN DIRECT (pas un
            // simple message d'erreur après un appel raté) ; le user sait
            // immédiatement pourquoi rien ne se rafraîchit. ───
            item(key = "offline") {
                if (offline) {
                    // Simple mention DISCRÈTE (pas de fond ni de couleur d'alerte) :
                    // informe sans dramatiser — les données en cache restent
                    // affichées normalement.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudOff, null, tint = TextMuted, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.dashboard_offline),
                            color = TextMuted, fontSize = 11.sp
                        )
                    }
                }
            }

            // ─── Carrousel de bandeaux (défilement auto, 6 s par bandeau).
            // Les bandeaux et le minuteur sont construits DANS LE CORPS de
            // l'écran (voir plus haut) : ici on ne fait que les afficher, si
            // bien que le défilement ne relance plus la rotation.
            item(key = "banners") {
                if (bannerSlots.isNotEmpty() && !state.isLoading) {
                    Crossfade(
                        targetState = bannerIndex.coerceIn(0, bannerSlots.lastIndex),
                        label = "dashboard_banner"
                    ) { i -> bannerSlots[i]() }
                }
            }

            // ─── Rangée d'icônes fine : scan · cloche · réglages ───
            // On garde ces accès rapides mais SANS le grand en-tête « Bonjour »,
            // pour que la carte de solde reste tout en haut.
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo de marque (logo_tex.png) à gauche de la barre du haut.
                    com.vaultex.ui.components.VaultexWordmark(height = 26.dp)
                    Spacer(Modifier.weight(1f))
                    HeaderSquareButton(Icons.Default.QrCodeScanner) { navController.navigate(Routes.SCANNER) }
                    BadgedBox(
                        badge = {
                            if (unreadNotifs > 0) Badge(containerColor = Color(0xFFE53935), contentColor = Color.White) {
                                Text(if (unreadNotifs > 99) "99+" else unreadNotifs.toString(), fontSize = 10.sp)
                            }
                        }
                    ) {
                        HeaderSquareButton(Icons.Default.Notifications) { navController.navigate(Routes.NOTIFICATION_CENTER) }
                    }
                }
            }

            // ─── Carte solde (dégradé bleu, USD + ≈ XOF) ───
            item(key = "balance") {
                BalanceCard(
                    currency = currency,
                    usd = state.totalBalanceUsd,
                    eur = state.totalBalanceEur,
                    xof = state.totalBalanceXof,
                    changePercent = state.totalChangePercent,
                    // Barre de chargement UNIQUEMENT au tout premier chargement
                    // (aucune donnée en cache) ; sinon affichage direct + refresh
                    // silencieux en arrière-plan (comme Trust Wallet).
                    isLoading = state.isLoading && state.tokens.isEmpty(),
                    hidden = balanceHidden,
                    onToggleHidden = { viewModel.toggleBalanceVisibility() }
                )
                Spacer(Modifier.height(6.dp))
                com.vaultex.ui.components.LastUpdatedLabel(
                    lastUpdated = state.lastUpdated,
                    isFromCache = state.isFromCache,
                    modifier = Modifier.padding(start = 4.dp)
                )
                state.error?.let { err ->
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                // Trait léger sous le bloc de solde (maquette) : délimite la
                // section sans réintroduire de fond de carte.
                HorizontalDivider(
                    color = SurfaceLight,
                    thickness = 1.dp,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }

            // ─── Donut de répartition (#10) : seulement si >= 2 actifs financés ───
            if (funded.size >= 2) {
                item(key = "donut") { PortfolioDonutCard(funded) }
            }

            // ─── 3 tuiles d'action (modèle, sans MoMo) ───
            item(key = "actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(stringResource(R.string.action_send), Icons.Default.ArrowUpward,
                        AccentRed, Modifier.weight(1f)) { navController.navigate(Routes.SEND_SELECT) }
                    ActionTile(stringResource(R.string.action_receive), Icons.Default.ArrowDownward,
                        AccentGreen, Modifier.weight(1f)) { navController.navigate(Routes.RECEIVE) }
                    ActionTile(stringResource(R.string.tab_swap), Icons.Default.SwapHoriz,
                        AccentBlue, Modifier.weight(1f)) { navController.navigate(Routes.SWAP) }
                }
            }

            // ─── Aperçu du marché (cartes horizontales, modèle) ───
            if (majors.isNotEmpty()) {
                item(key = "market_overview") {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.dash_market_overview), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                            Text(
                                stringResource(R.string.see_all) + " →",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue,
                                modifier = Modifier.clickable { navController.navigate(Routes.MARKET) }
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            majors.forEach { t -> MarketMiniCard(t) { navController.navigate(Routes.tokenDetail(t.symbol)) } }
                        }
                    }
                }
            }

            // ─── Mes actifs ───
            item(key = "assets") {
                SectionCard(
                    title = stringResource(R.string.my_assets),
                    linkLabel = stringResource(R.string.see_all),
                    onLinkClick = { navController.navigate(Routes.PORTFOLIO) }
                ) {
                    if (visibleTokens.isEmpty() && !state.isLoading) {
                        Text(
                            stringResource(R.string.dashboard_no_assets),
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    visibleTokens.forEachIndexed { index, token ->
                        AssetRow(token, balanceHidden, currency, token.symbol in pendingSymbols) {
                            navController.navigate(Routes.tokenDetail(token.symbol))
                        }
                        if (index < visibleTokens.lastIndex) {
                            HorizontalDivider(color = SurfaceLight, thickness = 1.dp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "+ " + stringResource(R.string.manage_assets_action),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentBlue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(Routes.MANAGE_ASSETS) }
                            .padding(vertical = 8.dp)
                    )
                }
            }

            // ─── Activité récente (3 dernières transactions réelles) ───
            item(key = "recent") {
                SectionCard(
                    title = stringResource(R.string.recent_title),
                    linkLabel = stringResource(R.string.see_all),
                    onLinkClick = { navController.navigate(Routes.HISTORY) }
                ) {
                    if (recentTxs.isEmpty()) {
                        Text(
                            stringResource(R.string.recent_empty),
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        recentTxs.forEachIndexed { index, tx ->
                            RecentTxRow(tx) { navController.navigate(Routes.historyDetail(tx.hash)) }
                            if (index < recentTxs.lastIndex) {
                                HorizontalDivider(color = SurfaceLight, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }
        PullRefreshIndicator(
            refreshing = manualRefresh,
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        }
    }
        // Barre FLOTTANTE, posée par-dessus le contenu qui défile derrière.
        VaultExBottomBar(navController, Modifier.align(Alignment.BottomCenter))
    }
}

private const val DASHBOARD_BANNER_PREFS = "vaultex_dashboard_banners"

/** Fermeture SESSION du bandeau « premier dépôt » (mémoire process) : un ✕ le
 *  retire pour la session, mais il REVIENT à chaque ouverture tant que le
 *  wallet est vide — sinon un wallet vide fermé une fois n'aurait plus AUCUN
 *  rappel et l'écran resterait vide (défaut signalé). Disparaît pour de bon
 *  dès qu'un premier fonds arrive (géré côté condition d'affichage). */
private object FirstDepositBannerState {
    val dismissed = mutableStateOf(false)
}

/** Fermeture SESSION du rappel de sauvegarde — sécurité des fonds : revient à
 *  chaque ouverture tant que la phrase n'a pas été VRAIMENT révélée sur
 *  l'écran Sauvegarde (le ✕ n'est qu'un « pas maintenant »). */
private object BackupReminderBannerState {
    val dismissed = mutableStateOf(false)
}

/** Lien d'invitation du groupe Telegram COMMUNAUTÉ (public) — distinct du
 *  groupe admin privé « Vaultex Administration » utilisé pour le monitoring. */
private const val TELEGRAM_COMMUNITY_URL = "https://t.me/+TAgIGCHKMKpjZGI0"

/** Bandeau Telegram : rappel marketing NON définitif — il réapparaît ~1 fois
 *  par semaine. On mémorise l'INSTANT du dernier refus (✕ ou clic « Rejoindre »)
 *  et on le re-masque tant qu'on est dans la fenêtre de 7 jours. */
private object TelegramBannerState {
    private const val KEY_LAST_DISMISS = "telegram_last_dismiss"
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    private var initialized = false
    /** true = masqué POUR L'INSTANT (dans la fenêtre de 7 jours). */
    val hidden = mutableStateOf(false)

    fun init(context: android.content.Context) {
        if (initialized) return
        initialized = true
        val last = context.getSharedPreferences(DASHBOARD_BANNER_PREFS, android.content.Context.MODE_PRIVATE)
            .getLong(KEY_LAST_DISMISS, 0L)
        hidden.value = last > 0L && (System.currentTimeMillis() - last < WEEK_MS)
    }

    fun dismiss(context: android.content.Context) {
        hidden.value = true
        context.getSharedPreferences(DASHBOARD_BANNER_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_DISMISS, System.currentTimeMillis()).apply()
    }
}

/** Bandeau générique du carrousel Dashboard : icône + titre + texte + bouton
 *  d'action + fermeture. Même style pour « premier dépôt » et « Telegram ». */
@Composable
private fun DashboardBanner(
    accent: Color,
    icon: ImageVector,
    title: String,
    body: String,
    ctaLabel: String,
    ctaIcon: ImageVector,
    onDismiss: () -> Unit,
    onCtaClick: () -> Unit
) {
    // Même MODÈLE pour les 3 bandeaux (taille, forme, disposition) ; seule la
    // couleur d'accent change (fond teinté + icône + bouton) pour les
    // distinguer d'un coup d'œil. Bouton toujours plein, texte blanc.
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    // minLines = maxLines : titre et corps occupent TOUJOURS le
                    // même nombre de lignes quel que soit le texte (ou la langue)
                    // → tous les bandeaux ont la MÊME hauteur, donc l'écran ne
                    // « saute » plus au changement de slide (crossfade).
                    Text(
                        title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        minLines = 2, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        body, color = TextSecondary, fontSize = 12.sp, lineHeight = 15.sp,
                        minLines = 2, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onCtaClick,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color.White
                )
            ) {
                Icon(ctaIcon, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(ctaLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BalanceCard(
    currency: String,
    usd: Double,
    eur: Double,
    xof: Double,
    changePercent: Double,
    isLoading: Boolean,
    hidden: Boolean,
    onToggleHidden: () -> Unit
) {
    val primaryAmount = when (currency) { "EUR" -> eur; "XOF" -> xof; else -> usd }
    val primaryText = com.vaultex.core.util.CurrencyFormat.format(primaryAmount, currency)
    // Référence secondaire : USD si on n'est pas en USD, sinon FCFA.
    val secondaryText =
        if (currency == "USD") com.vaultex.core.util.CurrencyFormat.format(xof, "XOF")
        else com.vaultex.core.util.CurrencyFormat.format(usd, "USD")
    val masked = "••••••"

    // Maquette : bloc SANS fond (à plat sur l'écran), identique en clair et en
    // sombre — les couleurs de texte viennent du thème et s'adaptent seules.
    Box(modifier = Modifier.fillMaxWidth()) {
        // Courbe violette décorative à droite (maquette) — pas d'historique de
        // solde disponible localement, la forme est stable (déterministe).
        Canvas(
            Modifier.align(Alignment.CenterEnd)
                .fillMaxWidth(0.52f)
                .height(120.dp)
                .padding(end = 6.dp)
        ) {
            val rnd = kotlin.random.Random(42)
            val n = 26
            val pts = (0..n).map { i ->
                val trend = 0.85f - 0.55f * (i / n.toFloat())            // montée vers la droite
                (trend + (rnd.nextFloat() - 0.5f) * 0.22f).coerceIn(0.08f, 0.95f)
            }
            val step = size.width / n
            val line = Path()
            pts.forEachIndexed { i, v ->
                val x = i * step; val y = v * size.height
                if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            // Halo dégradé sous la courbe
            val fill = Path().apply {
                addPath(line)
                lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(Color(0xFF9B6CFF).copy(alpha = 0.30f), Color.Transparent)))
            drawPath(line, Color(0xFFA855F7), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.total_balance),
                    color = TextPrimary,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(if (hidden) R.string.balance_show else R.string.balance_hide),
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp).clip(CircleShape).clickable(onClick = onToggleHidden)
                )
            }
            Spacer(Modifier.height(8.dp))
            /*
            EN COURS DE CHARGEMENT : « — », JAMAIS « 0 ».

            L'appelant ne met isLoading à vrai que lorsqu'il n'y a RIEN à
            afficher (aucun actif connu). Dans ce cas, écrire « 0 » n'est pas
            une information neutre : sur un portefeuille, ça se lit « ton
            argent a disparu ». C'est exactement ce qui se produisait juste
            après un changement de wallet — les caches du précédent venaient
            d'être purgés, et l'accueil annonçait 0 en gros et en gras pendant
            que le réseau répondait.

            La barre de progression sous le montant ne suffisait pas : personne
            ne regarde un trait fin quand un chiffre énorme dit zéro.
             */
            val placeholder = "—"
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    when {
                        isLoading -> placeholder
                        hidden -> masked
                        else -> primaryText
                    },
                    color = TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
                Text(currency, color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 5.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    isLoading -> placeholder
                    hidden -> masked
                    else -> "= $secondaryText"
                },
                color = TextSecondary,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(10.dp))
            val positive = changePercent >= 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (positive) Color(0xFF86EFAC) else Color(0xFFFBD1D1)
                ) {
                    Text(
                        "%+.1f%%".format(changePercent),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = if (positive) Color(0xFF14532D) else Color(0xFFB42318),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.balance_today), color = TextSecondary, fontSize = 13.sp)
            }
            if (isLoading) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AccentBlue,
                    trackColor = AccentBlue.copy(alpha = 0.20f)
                )
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextPrimary, fontSize = 11.sp)
    }
}

@Composable
private fun SectionCard(
    title: String,
    linkLabel: String,
    onLinkClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    // Maquette : section À PLAT (sans fond de carte), identique clair/sombre —
    // les lignes sont séparées par de fins séparateurs.
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            Text(
                "$linkLabel →",
                color = AccentBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onLinkClick)
            )
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun AssetRow(token: TokenBalance, hidden: Boolean, currency: String, isPending: Boolean = false, onClick: () -> Unit) {
    val valueAmount = when (currency) {
        "EUR" -> token.valueEur
        "XOF" -> token.valueXof
        else -> token.valueUsd
    }
    val valueText = com.vaultex.core.util.CurrencyFormat.format(valueAmount, currency)
    val tokenColor = try {
        Color(android.graphics.Color.parseColor(token.colorHex))
    } catch (_: Exception) { AccentBlue }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(tokenColor),
            contentAlignment = Alignment.Center
        ) {
            // Lettres en repli ; le logo réel par-dessus s'il se charge.
            Text(
                token.symbol.take(2),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            coil.compose.AsyncImage(
                model = com.vaultex.ui.components.CryptoIcon.urlFor(token.symbol, token.contractAddress, token.blockchain.ticker),
                contentDescription = token.symbol,
                modifier = Modifier.size(40.dp).clip(CircleShape)
            )
            // Badge « ! » : transaction sortante en attente de confirmation.
            if (isPending) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(15.dp).clip(CircleShape)
                        .background(Color(0xFFF59E0B))
                        .border(1.5.dp, BgPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        // Prix unitaire de marché. Repli sur valeur/quantité si le prix stocké
        // est 0 (ex. instantané en cache d'une ancienne version) → plus de « 00 ».
        val storedPrice = when (currency) {
            "EUR" -> token.priceEur
            "XOF" -> token.priceXof
            else -> token.priceUsd
        }
        val unitPrice = if (storedPrice > 0.0) storedPrice else {
            val qty = token.amountFormatted.substringBefore(" ").replace(" ", "").replace(",", ".").toDoubleOrNull() ?: 0.0
            if (qty > 0.0) valueAmount / qty else 0.0
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(token.symbol, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                // Badge réseau (#9) : montre clairement sur quel réseau est le coin
                Surface(shape = RoundedCornerShape(4.dp), color = tokenColor.copy(alpha = 0.12f)) {
                    Text(
                        networkLabel(token.symbol),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        fontSize = 9.sp,
                        color = tokenColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                // Badge « En attente » VISIBLE (au lieu du petit « ! ») : une
                // transaction sortante attend sa confirmation on-chain.
                if (isPending) {
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFF59E0B).copy(alpha = 0.16f)) {
                        Row(
                            Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.Schedule, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(10.dp))
                            Text(stringResource(R.string.tx_pending), fontSize = 9.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.asset_price_fmt, com.vaultex.core.util.CurrencyFormat.formatPrice(unitPrice, currency)),
                fontSize = 11.sp, color = TextSecondary
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (hidden) "••••" else valueText,
                fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary
            )
            val changeColor = if (token.changePercent24h >= 0) AccentGreen else AccentRed
            Text("%+.1f%%".format(token.changePercent24h), fontSize = 12.sp, color = changeColor)
        }
    }
}

/** Réseau lisible d'un coin (#9) — clarifie sur quelle chaîne il circule. */
private fun networkLabel(symbol: String): String = when (symbol) {
    "USDT" -> "TRC20"
    "USDT-ETH" -> "ERC20"
    "USDT-BNB" -> "BEP20"
    "BTC" -> "Bitcoin"
    "ETH" -> "Ethereum"
    "BNB" -> "BNB Chain"
    "SOL" -> "Solana"
    "TRX" -> "Tron"
    else -> symbol.substringBefore("-")
}

/*
Formateurs MIS EN CACHE. Construire un NumberFormat ou un SimpleDateFormat
charge les données de locale : le faire pour chaque ligne d'actif et chaque
transaction, à chaque recomposition du défilement, coûtait plusieurs
millisecondes par image. Ils ne sont utilisés que depuis la composition
(thread UI unique), donc une instance partagée est sûre.
 */
private val AssetPriceFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.FRANCE)
private val RecentDateFormat = java.text.SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)

private fun formatAssetPrice(value: Double): String {
    AssetPriceFormat.maximumFractionDigits = if (value < 1.0) 4 else 2
    return AssetPriceFormat.format(value)
}

@Composable
private fun PortfolioDonutCard(tokens: List<TokenBalance>) {
    val total = tokens.sumOf { it.valueUsd }
    val fallbackColor = AccentBlue   // lu dans le contexte @Composable
    fun colorOf(hex: String): Color = try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) { fallbackColor }

    // Maquette : bloc À PLAT, sans fond de carte (clair et sombre).
    Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.portfolio_repartition),
                fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.vaultex.ui.components.DonutChart(
                    slices = tokens.map { colorOf(it.colorHex) to it.valueUsd.toFloat() },
                    modifier = Modifier.size(120.dp)
                )
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tokens.forEach { token ->
                        val pct = if (total > 0.0) token.valueUsd / total * 100 else 0.0
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                Modifier.size(10.dp).clip(CircleShape).background(colorOf(token.colorHex))
                            )
                            Text(
                                token.symbol,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            Text(
                                "%.0f%%".format(pct),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
    }
}

/* ───────────────────── Composants du modèle (en-tête / marché / activité) ───────────────────── */

/** Bouton carré arrondi (scan · cloche · réglages). */
@Composable
private fun HeaderSquareButton(icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = com.vaultex.ui.theme.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(19.dp))
        }
    }
}

/** Action (maquette) : tuile carrée arrondie BORDÉE, icône monochrome + libellé. */
@Composable
private fun ActionTile(label: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = com.vaultex.ui.theme.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(26.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        }
    }
}

/** Carte marché compacte : logo + symbole, prix $, variation 24 h + mini-courbe. */
@Composable
private fun MarketMiniCard(token: TokenBalance, onClick: () -> Unit) {
    val up = token.changePercent24h >= 0
    val trendColor = if (up) AccentGreen else AccentRed
    // Maquette (mise à jour) : carte à fond léger, comme les tuiles d'action.
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = com.vaultex.ui.theme.Surface, modifier = Modifier.width(148.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                coil.compose.AsyncImage(
                    model = com.vaultex.ui.components.CryptoIcon.url(token.symbol),
                    contentDescription = token.symbol,
                    modifier = Modifier.size(20.dp).clip(CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(token.symbol, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
            }
            Text(
                "$" + String.format(Locale.US, "%,.2f", token.priceUsd),
                fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary
            )
            Text(
                (if (up) "▲ " else "▼ ") + String.format(Locale.US, "%.1f", kotlin.math.abs(token.changePercent24h)) + "%",
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = trendColor
            )
            // Mini-courbe DÉCORATIVE (déterministe par symbole) — pas de données
            // historiques ici ; la vraie courbe est sur l'écran Marché.
            Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                val rnd = kotlin.random.Random(token.symbol.hashCode())
                val n = 10
                var prev = androidx.compose.ui.geometry.Offset(0f, size.height * (0.3f + rnd.nextFloat() * 0.4f))
                for (i in 1..n) {
                    val next = androidx.compose.ui.geometry.Offset(
                        size.width * i / n,
                        size.height * (0.15f + rnd.nextFloat() * 0.7f)
                    )
                    drawLine(trendColor, prev, next, strokeWidth = 2f)
                    prev = next
                }
            }
        }
    }
}

/** Ligne d'activité récente : icône par type + titre + adresse courte + montant/date. */
@Composable
private fun RecentTxRow(tx: com.vaultex.data.local.entity.TransactionEntity, onClick: () -> Unit) {
    val (icon, tint, sign) = when (tx.type) {
        "received" -> Triple(Icons.Default.ArrowDownward, AccentGreen, "+")
        "sent" -> Triple(Icons.Default.ArrowUpward, AccentRed, "-")
        else -> Triple(Icons.Default.SwapHoriz, Color(0xFF7C5CFC), "")
    }
    val title = when (tx.type) {
        "received" -> stringResource(R.string.received) + " " + tx.tokenSymbol
        "sent" -> stringResource(R.string.sent) + " " + tx.tokenSymbol
        else -> "Swap " + tx.tokenSymbol
    }
    val ref = when (tx.type) {
        "received" -> tx.fromAddress
        "sent" -> tx.toAddress
        else -> tx.hash
    }
    val short = if (ref.length > 12) ref.take(6) + "…" + ref.takeLast(4) else ref
    val date = RecentDateFormat.format(java.util.Date(tx.timestamp))
    val amountSym = if (tx.type == "swap") tx.tokenSymbol.substringBefore("→") else tx.tokenSymbol

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(short, fontSize = 11.sp, color = TextSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$sign${tx.amount} $amountSym", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tint)
            Text(date, fontSize = 11.sp, color = TextSecondary)
        }
    }
}
