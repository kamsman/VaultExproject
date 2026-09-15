package com.vaultex.ui.screens.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.MarketViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinDetailScreen(navController: NavHostController, coinId: String = "bitcoin") {
    val viewModel: MarketViewModel = hiltViewModel()
    val coin by viewModel.coin.collectAsState()
    val coinLoading by viewModel.coinLoading.collectAsState()
    val coinError by viewModel.coinError.collectAsState()

    // Charge UNIQUEMENT cette pièce (appel léger), pas toute la liste marché.
    LaunchedEffect(coinId) { viewModel.loadCoin(coinId) }

    val chart by viewModel.chart.collectAsState()
    val chartLoading by viewModel.chartLoading.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    // Libellés de la maquette. « Tout » remplace « 3M » : entre un mois et un
    // an, trois mois n'apportait rien qu'on ne lise déjà sur la courbe.
    val periods = listOf("24h", "7j", "1M", "1A", "Tout")
    var selectedPeriod by remember { mutableStateOf("7j") }

    LaunchedEffect(coinId, selectedPeriod, coin) {
        /*
        7 J s'appuie normalement sur le sparkline livré avec la fiche : aucun
        appel réseau supplémentaire. Mais si ce sparkline manque — fiche servie
        par le cache disque allégé, ou CoinGecko qui l'a omis — on se rabattait
        sur RIEN, et le graphique restait vide sans que personne ne tente de le
        charger. On demande alors explicitement market_chart sur 7 jours.
         */
        val hasSparkline = (coin?.sparkline_in_7d?.price?.size ?: 0) >= 2
        if (selectedPeriod != "7j" || !hasSparkline) {
            viewModel.loadChart(coinId, daysForPeriod(selectedPeriod))
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        coin?.name ?: coinId.replaceFirstChar { it.uppercase() },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                actions = {
                    val isFav = coinId in favorites
                    IconButton(onClick = { viewModel.toggleFavorite(coinId) }) {
                        Icon(
                            if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = stringResource(R.string.market_filter_favs),
                            tint = if (isFav) Color(0xFFF5B301) else TextSecondary
                        )
                    }
                    /*
                    L'alerte reste ici, contrairement à la maquette qui ne
                    gardait que l'étoile et le rafraîchissement.

                    « M'alerter sur CETTE monnaie » n'a de sens qu'ici : depuis
                    la cloche générale du Marché, il faut ensuite la
                    rechercher. Une action qui perd son contexte perd son
                    intérêt, et celle-ci est l'une des rares raisons de revenir
                    dans l'application sans y être poussé.
                    */
                    IconButton(onClick = { navController.navigate(Routes.NOTIFICATIONS) }) {
                        Icon(
                            Icons.Default.NotificationsNone,
                            contentDescription = stringResource(R.string.coin_alert),
                            tint = TextSecondary
                        )
                    }
                    IconButton(onClick = {
                        viewModel.loadCoin(coinId)
                        viewModel.loadChart(coinId, daysForPeriod(selectedPeriod))
                    }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.history_refresh),
                            tint = AccentBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgSecondary)
            )
        },
        containerColor = BgSecondary
    ) { padding ->
        val c = coin
        if (c == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (coinError) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.coin_load_error), color = TextMuted, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.loadCoin(coinId) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) { Text(stringResource(R.string.history_refresh)) }
                    }
                } else {
                    CircularProgressIndicator(color = AccentBlue)
                }
            }
            return@Scaffold
        }

        /*
        usdFmt sert aux MONTANTS en dollars — la valeur détenue, le gain.
        Deux décimales y sont justes : personne ne lit son portefeuille au
        millionième.

        Les PRIX, eux, passent par formatMarketUsd(), le formateur de la liste
        du Marché. Il adapte la précision à l'ordre de grandeur, là où deux
        décimales affichaient « $0 » pour toute monnaie sous un demi-centime.
        La fiche contredisait donc la liste dont elle venait, sur la seule
        donnée que l'on ouvre l'écran pour lire.
        */
        val usdFmt = remember {
            NumberFormat.getNumberInstance(com.vaultex.core.session.LocaleManager.appLocale()).apply { maximumFractionDigits = 2 }
        }
        val symbol = c.symbol.uppercase()

        /*
        ═══════════════════════════════════════════════════════════════════
        TROIS CARTES ET QUATRE ACTIONS
        ═══════════════════════════════════════════════════════════════════

        Repris d'une maquette. La fiche était une colonne d'éléments posés
        les uns sous les autres ; elle devient trois blocs qui répondent
        chacun à une question : qu'est-ce que ça vaut, comment ça bouge, et
        combien j'en ai.

        TROIS CHOSES DE LA MAQUETTE N'ONT PAS ÉTÉ REPRISES.

        · « Prix d'achat moyen ». VaultEx ne peut pas le connaître : il lit
          les soldes sur la chaîne, et les fonds arrivent d'ailleurs — on
          reçoit du BTC, on ne l'achète pas ici. Ce chiffre aurait été
          inventé, et c'est sur lui qu'on décide de vendre.

        · L'adresse en clair. Le bouton « Recevoir » est juste en dessous et
          c'est sa place. Ici, elle allongeait la page et exposait une
          adresse sur l'écran qu'on montre le plus volontiers à quelqu'un.

        · L'œil de masquage. Il aurait fallu le brancher sur la visibilité
          des soldes ; un bouton décoratif qui ne fait rien est pire que pas
          de bouton.

        ET UN POURCENTAGE A ÉTÉ CLARIFIÉ. La maquette affichait « −3,28 %
        (24h) » pour la monnaie et « +2,14 % » pour le portefeuille, sans
        dire ce que mesurait le second. Deux nombres impossibles à
        réconcilier côte à côte : la ligne du portefeuille porte maintenant
        un gain EN MONNAIE, explicitement daté.
        */
        val devise by viewModel.devise.collectAsState()
        val prixDevise by viewModel.prixDevise.collectAsState()
        val hausse = c.change24h >= 0
        val couleurVar = if (hausse) AccentGreen else AccentRed

        /** Valeur dans la devise de l'utilisateur, ou null si on ne l'a pas. */
        fun enDevise(quantite: Double): String? =
            prixDevise?.let { com.vaultex.core.util.CurrencyFormat.format(quantite * it, devise) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            /* ─────────── CARTE 1 — identité, prix, trois chiffres ─────────── */
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val logoUrl = c.image
                        if (!logoUrl.isNullOrEmpty()) {
                            coil.compose.AsyncImage(
                                model = logoUrl, contentDescription = symbol,
                                modifier = Modifier.size(44.dp).clip(CircleShape)
                            )
                        } else {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(tokenColor(symbol)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(symbol.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(symbol, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
                            Text(c.name, fontSize = 13.sp, color = TextSecondary)
                        }
                        // « En direct » : la donnée vient d'être relue. Le point
                        // vert dit la fraîcheur mieux qu'un horodatage.
                        if (!coinLoading) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(AccentGreen))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.coin_live),
                                        fontSize = 11.sp, color = AccentGreen, fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        "$" + formatMarketUsd(c.currentPrice),
                        fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                    )
                    /*
                    Le prix dans la devise de l'utilisateur, ET SON LIBELLÉ.

                    Sans libellé, deux montants en FCFA se retrouvaient sur le
                    même écran sans rien pour les distinguer : le prix d'un
                    bitcoin ici, la valeur du solde détenu dans la carte
                    « Mon portefeuille » plus bas. Des ordres de grandeur sans
                    rapport — 49 000 000 FCFA et 350 FCFA — que rien
                    n'expliquait.

                    « le BTC » lève l'ambiguïté en trois caractères, et la
                    contre-valeur reste là où elle sert : personne n'a besoin
                    de posséder du bitcoin pour vouloir savoir ce qu'il coûte
                    en francs.
                    */
                    if (devise != "USD") {
                        enDevise(1.0)?.let {
                            Text(
                                "≈ $it " + stringResource(R.string.coin_per_unit, symbol),
                                fontSize = 14.sp, color = TextSecondary
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (hausse) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            null, tint = couleurVar, modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "%+.2f %% (24h)".format(c.change24h),
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = couleurVar
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = BorderColor)
                    Spacer(Modifier.height(12.dp))

                    /*
                    Trois chiffres, en notation courte.

                    La maquette écrivait « $ 1,530,207,504,529.00 » — treize
                    chiffres et les centimes d'une capitalisation. Personne ne
                    lit ça. compact() existait déjà dans ce fichier et rend
                    « 1,53 T ».
                    */
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ChiffreCle(stringResource(R.string.market_cap), "$" + compact(c.marketCap), Modifier.weight(1f))
                        Box(Modifier.height(34.dp).width(1.dp).background(BorderColor))
                        ChiffreCle(stringResource(R.string.coin_volume_label), "$" + compact(c.volume24h), Modifier.weight(1f))
                        Box(Modifier.height(34.dp).width(1.dp).background(BorderColor))
                        ChiffreCle(stringResource(R.string.coin_supply), compact(c.circulatingSupply) + " " + symbol, Modifier.weight(1f))
                    }
                }
            }

            /* ─────────── CARTE 2 — le graphique ─────────── */
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        titrePeriode(selectedPeriod),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                    )

                    Spacer(Modifier.height(10.dp))

                    // Sélecteur de période, en pilules.
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        periods.forEach { period ->
                            val actif = period == selectedPeriod
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (actif) AccentBlue else Color.Transparent)
                                    .clickable { selectedPeriod = period }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    period,
                                    fontSize = 12.sp,
                                    fontWeight = if (actif) FontWeight.Bold else FontWeight.Normal,
                                    color = if (actif) Color.White else TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    val sparklineF = c.sparkline_in_7d?.price?.map { it.toFloat() } ?: emptyList()
                    val chartPoints = if (selectedPeriod == "7j" && sparklineF.size >= 2) sparklineF else chart
                    Box(
                        Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            chartLoading && chartPoints.isEmpty() ->
                                CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(28.dp))
                            chartPoints.size < 2 ->
                                Text(stringResource(R.string.coin_chart_unavailable), color = TextMuted, fontSize = 13.sp)
                            else ->
                                PriceLineChart(
                                    points = chartPoints,
                                    jours = daysForPeriod(selectedPeriod),
                                    modifier = Modifier.fillMaxSize()
                                )
                        }
                    }
                }
            }

            /* ─────────── CARTE 3 — mon portefeuille ─────────── */
            val holding = remember(symbol) { viewModel.holdingOf(symbol) }
            if (holding != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.coin_my_wallet),
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            trimAmount(holding.amount) + " " + symbol,
                            fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "≈ $" + usdFmt.format(holding.valueUsd),
                            fontSize = 13.sp, color = TextSecondary
                        )

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = BorderColor)
                        Spacer(Modifier.height(12.dp))

                        Row(Modifier.fillMaxWidth()) {
                            /*
                            Un gain EN MONNAIE, et daté.

                            Le pourcentage de la monnaie est déjà en haut de
                            l'écran : le répéter ici n'apprenait rien, et la
                            maquette en affichait un second, différent, sans
                            dire ce qu'il mesurait. Ce que la personne veut
                            savoir, c'est combien ça fait pour ELLE.
                            */
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.coin_gain_24h), fontSize = 12.sp, color = TextSecondary)
                                Spacer(Modifier.height(3.dp))
                                val gainUsd = holding.valueUsd * c.change24h / 100.0
                                val gainAbs = kotlin.math.abs(gainUsd)
                                val signe = if (gainUsd >= 0) "+" else "−"
                                Text(
                                    if (gainAbs > 0.0 && gainAbs < 0.01) "$signe < $0.01"
                                    else "$signe $" + usdFmt.format(gainAbs),
                                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = couleurVar
                                )
                            }
                            if (devise != "USD") {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.coin_value_in, devise),
                                        fontSize = 12.sp, color = TextSecondary
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        enDevise(holding.amount)?.let { "≈ $it" } ?: "—",
                                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            /* ─────────── QUATRE ACTIONS, EN GRILLE 2×2 ─────────── */
            // 3 niveaux réels de support, inchangés :
            //  🟢 Échangeable (registre swap)             → Envoyer + Recevoir + Swap
            //  🔵 Existe sur Ethereum/BSC (hors registre) → Envoyer + Recevoir seuls
            //  ⚪ Ni l'un ni l'autre                       → consultation seule
            val supported = com.vaultex.ui.viewmodel.SwapViewModel.assetForSymbol(symbol)
            val receivable by viewModel.receivableToken.collectAsState()
            val receivableChecking by viewModel.receivableChecking.collectAsState()
            LaunchedEffect(coinId, supported) {
                if (supported == null) viewModel.checkReceivable(coinId)
            }
            val receiveOnlyKey = receivable?.symbol
            val transferable = supported != null || receiveOnlyKey != null
            val bufferKey = supported?.key ?: receiveOnlyKey

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionFiche(
                        Icons.Default.Send, stringResource(R.string.action_send),
                        plein = true, actif = transferable, modifier = Modifier.weight(1f)
                    ) {
                        // C'EST ICI que le jeton entre dans le portefeuille, et
                        // nulle part avant : consulter une fiche ne doit rien y
                        // ajouter.
                        if (supported == null) viewModel.enregistrerPourUsage()
                        bufferKey?.let { com.vaultex.core.session.TokenSelectionBuffer.set(it) }
                        navController.navigate(Routes.SEND)
                    }
                    ActionFiche(
                        Icons.Default.ArrowDownward, stringResource(R.string.action_receive),
                        plein = false, actif = transferable, modifier = Modifier.weight(1f)
                    ) {
                        if (supported == null) viewModel.enregistrerPourUsage()
                        bufferKey?.let { com.vaultex.core.session.TokenSelectionBuffer.set(it) }
                        navController.navigate(Routes.RECEIVE)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionFiche(
                        Icons.Default.SwapHoriz, stringResource(R.string.tab_swap),
                        plein = false, actif = supported != null, modifier = Modifier.weight(1f)
                    ) {
                        supported?.let { com.vaultex.core.session.TokenSelectionBuffer.set(it.key) }
                        navController.navigate(Routes.SWAP)
                    }
                    ActionFiche(
                        Icons.Default.History, stringResource(R.string.tab_history),
                        plein = false, actif = true, modifier = Modifier.weight(1f)
                    ) {
                        navController.navigate(Routes.HISTORY)
                    }
                }
            }

            // Pourquoi certaines actions sont grisées — dit une fois, en petit.
            when {
                supported != null -> {}
                receivableChecking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = AccentBlue, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.coin_checking_network), fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.8f))
                    }
                }
                receiveOnlyKey != null -> {
                    val netLabel = if (receivable?.chainTicker == "BNB") "BEP20 · BNB Chain" else "ERC20 · Ethereum"
                    Text(
                        stringResource(R.string.coin_receive_only, netLabel),
                        fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.8f)
                    )
                }
                else -> Text(
                    stringResource(R.string.coin_view_only),
                    fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.8f)
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Un des trois chiffres clés de l'en-tête (libellé au-dessus, valeur dessous). */
@Composable
private fun ChiffreCle(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
    }
}

/**
 * Bouton d'action de la fiche.
 *
 * [actif] à false grise le bouton SANS le retirer : un bouton qui disparaît
 * laisse croire que l'application est incomplète, alors qu'un bouton grisé
 * dit « pas pour cette monnaie » — et la ligne d'explication juste en dessous
 * dit pourquoi.
 */
@Composable
private fun ActionFiche(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    plein: Boolean,
    actif: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val teinte = if (!actif) TextMuted else if (plein) Color.White else AccentBlue
    Surface(
        onClick = onClick,
        enabled = actif,
        shape = RoundedCornerShape(14.dp),
        color = if (plein && actif) AccentBlue else Color.Transparent,
        border = if (plein && actif) null
            else androidx.compose.foundation.BorderStroke(1.dp, if (actif) AccentBlue.copy(alpha = 0.5f) else BorderColor),
        modifier = modifier.height(52.dp)
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = teinte, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = teinte)
        }
    }
}

/** Mappe la période UI vers le nombre de jours pour CoinGecko market_chart. */
private fun daysForPeriod(period: String): Int = when (period) {
    "24h" -> 1
    "7j" -> 7
    "1M" -> 30
    "1A" -> 365
    "Tout" -> 1825   // cinq ans : au-delà, CoinGecko renvoie l'historique complet
    else -> 7
}

/** Titre du bloc graphique, accordé à la période choisie. */
private fun titrePeriode(period: String): String = when (period) {
    "24h" -> "Évolution 24 heures"
    "7j" -> "Évolution 7 jours"
    "1M" -> "Évolution 1 mois"
    "1A" -> "Évolution 1 an"
    else -> "Évolution complète"
}


private fun trimAmount(v: Double): String =
    java.math.BigDecimal.valueOf(v).setScale(8, java.math.RoundingMode.DOWN)
        .stripTrailingZeros().toPlainString()


/** Courbe de prix dessinée au Canvas (sans dépendance externe). */
/*
═══════════════════════════════════════════════════════════════════════════
LE GRAPHIQUE PREND SES AXES
═══════════════════════════════════════════════════════════════════════════

La courbe était nue : une ligne dans un cadre, sans échelle ni dates. Jolie,
et muette — on voyait que ça montait, sans savoir de combien ni depuis quand.
Les graduations sont ce qui sépare une décoration d'une information.

LES ÉTIQUETTES SONT DES Text, PAS DU DESSIN. Elles pourraient être tracées
dans le Canvas avec un TextMeasurer ; posées autour en composables, elles
suivent gratuitement la police du système, la langue et le thème — et le code
tient sans mesure de texte à la main.

CE QUE LA MAQUETTE AVAIT EN TROP : une bulle « 76 202 » sur le dernier point.
C'est exactement le nombre déjà écrit en grand deux cartes plus haut. Le point
et son trait suffisent à dire « ici, maintenant » ; le chiffre, on l'a déjà lu.
*/
@Composable
private fun PriceLineChart(
    points: List<Float>,
    jours: Int,
    modifier: Modifier = Modifier
) {
    val rising = points.last() >= points.first()
    val lineColor = if (rising) AccentGreen else AccentRed

    val min = points.min()
    val max = points.max()

    // Quatre intervalles, donc cinq niveaux : assez pour situer une valeur,
    // pas assez pour quadriller l'écran.
    val niveaux = 5
    val paliers = remember(min, max) {
        List(niveaux) { i -> max - (max - min) * i / (niveaux - 1f) }
    }

    val dates = remember(jours, points.size) { etiquettesDates(jours) }

    Row(modifier) {
        // ── Échelle des prix, à gauche ──
        Column(
            Modifier.fillMaxHeight().padding(end = 6.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            paliers.forEach { v ->
                Text(compact(v.toDouble()), fontSize = 9.sp, color = TextMuted, maxLines = 1)
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            androidx.compose.foundation.Canvas(Modifier.weight(1f).fillMaxWidth()) {
                val range = (max - min).takeIf { it > 0f } ?: 1f
                val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width
                fun y(v: Float) = size.height - ((v - min) / range) * size.height

                // Lignes de repère, en pointillé : présentes sans concurrencer
                // la courbe.
                val pointille = androidx.compose.ui.graphics.PathEffect
                    .dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()))
                repeat(niveaux) { i ->
                    val py = size.height * i / (niveaux - 1f)
                    drawLine(
                        color = BorderColor.copy(alpha = 0.5f),
                        start = androidx.compose.ui.geometry.Offset(0f, py),
                        end = androidx.compose.ui.geometry.Offset(size.width, py),
                        strokeWidth = 1f,
                        pathEffect = pointille
                    )
                }

                val linePath = androidx.compose.ui.graphics.Path()
                val fillPath = androidx.compose.ui.graphics.Path()
                points.forEachIndexed { i, v ->
                    val px = i * stepX
                    val py = y(v)
                    if (i == 0) {
                        linePath.moveTo(px, py)
                        fillPath.moveTo(px, size.height)
                        fillPath.lineTo(px, py)
                    } else {
                        linePath.lineTo(px, py)
                        fillPath.lineTo(px, py)
                    }
                }
                fillPath.lineTo((points.size - 1) * stepX, size.height)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(lineColor.copy(alpha = 0.22f), lineColor.copy(alpha = 0f))
                    )
                )
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )

                // « Ici, maintenant » : un trait vertical et un point sur la
                // dernière valeur. Sans chiffre — il est déjà en haut de
                // l'écran, en grand.
                val dernierX = (points.size - 1) * stepX
                val dernierY = y(points.last())
                drawLine(
                    color = lineColor.copy(alpha = 0.45f),
                    start = androidx.compose.ui.geometry.Offset(dernierX, dernierY),
                    end = androidx.compose.ui.geometry.Offset(dernierX, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = pointille
                )
                drawCircle(
                    color = lineColor,
                    radius = 3.5.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(dernierX, dernierY)
                )
            }

            // ── Dates, en dessous ──
            Row(
                Modifier.fillMaxWidth().height(18.dp).padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dates.forEach {
                    Text(it, fontSize = 9.sp, color = TextMuted, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Quatre repères de temps répartis sur la période, du plus ancien au plus
 * récent.
 *
 * CoinGecko rend des points régulièrement espacés sans horodatage : on les
 * reconstitue depuis la durée demandée. Sous deux jours on affiche l'heure,
 * au-delà la date — « 14:00 » ne veut rien dire sur un an, « 8 sept. » ne
 * veut rien dire sur une journée.
 */
private fun etiquettesDates(jours: Int): List<String> {
    val repères = 4
    val maintenant = System.currentTimeMillis()
    val motif = if (jours <= 2) "HH:mm" else if (jours <= 365) "d MMM" else "MMM yy"
    val fmt = java.text.SimpleDateFormat(motif, com.vaultex.core.session.LocaleManager.appLocale())
    val duree = jours * 86_400_000L
    return List(repères) { i ->
        val t = maintenant - duree + duree * i / (repères - 1L)
        fmt.format(java.util.Date(t))
    }
}


private fun tokenColor(symbol: String) = when (symbol) {
    "BTC" -> NetworkBtc
    "ETH" -> NetworkEth
    "BNB" -> NetworkBnb
    "SOL" -> NetworkSol
    "TRX" -> NetworkTrx
    else -> Color(0xFF1A6FE8)
}

private fun compact(value: Double): String = when {
    value >= 1e12 -> "%.2fT".format(value / 1e12)
    value >= 1e9 -> "%.0fMd".format(value / 1e9)
    value >= 1e6 -> "%.0fM".format(value / 1e6)
    else -> "%.0f".format(value)
}

