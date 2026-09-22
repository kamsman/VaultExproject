package com.vaultex.ui.screens.swap

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.ui.components.CryptoIcon
import com.vaultex.ui.components.ExpandableDetails
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.NetworkBnb
import com.vaultex.ui.theme.NetworkBtc
import com.vaultex.ui.theme.NetworkEth
import com.vaultex.ui.theme.NetworkSol
import com.vaultex.ui.theme.NetworkTrx
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.components.BottomBarSpace
import com.vaultex.ui.components.VaultExBottomBar
import com.vaultex.ui.viewmodel.SwapViewModel

/* ───────────────────────── Palette violet (accents fixes) ─────────────────────────
 * Les fonds / textes suivent le thème ACTUEL de l'app (clair ou sombre) via les
 * accesseurs @Composable ci-dessous ; seuls le violet et le vert restent constants. */
private val SwapPurple = Color(0xFF7C5CFC)
private val SwapGreen = Color(0xFF22C55E)
// Perte de valeur : ambre au-delà de 10 %, rouge au-delà de 25 %. Deux teintes
// et non une, parce que « des frais élevés » et « la moitié part en frais » ne
// demandent pas la même réaction.
private val SwapOrange = Color(0xFFF59E0B)
private val SwapRed = Color(0xFFEF4444)

private val swapBg: Color        @Composable get() = BgPrimary
private val swapCard: Color      @Composable get() = SurfaceColor
private val swapCardAlt: Color   @Composable get() = SurfaceLight
private val swapBorder: Color    @Composable get() = BorderColor
private val swapText: Color      @Composable get() = TextPrimary
private val swapTextDim: Color   @Composable get() = TextSecondary
private val swapTextFaint: Color @Composable get() = TextMuted
private val swapPurpleDim: Color @Composable get() = SwapPurple.copy(alpha = 0.16f)
private val swapErrBg: Color     @Composable get() = AccentRed.copy(alpha = 0.12f)

private fun tokenColor(token: String): Color = when (token.uppercase()) {
    "BTC" -> NetworkBtc
    "ETH" -> NetworkEth
    "BNB" -> NetworkBnb
    "SOL" -> NetworkSol
    "TRX", "USDT" -> NetworkTrx
    else -> SwapPurple
}

/* Affichage d'un actif : tout vient du registre du ViewModel (12 actifs). */
private fun swapBaseOf(key: String): String = SwapViewModel.assetOf(key).base
private fun swapNetworkBadge(key: String): String = SwapViewModel.assetOf(key).badge
private fun swapNetworkLong(key: String): String = SwapViewModel.assetOf(key).network

/** Rang d'avancement d'un statut ChangeNOW (pour la frise). */
/**
 * Étape atteinte, de 0 à 5.
 *
 * Normalise par précaution : le statut arrive déjà en minuscules du
 * ViewModel, mais cette fonction décide seule de ce que l'écran montre.
 * Qu'elle dépende de la casse d'une chaîne venue d'un service extérieur
 * serait une fragilité de trop pour ce qu'elle coûte à éviter.
 */
private fun statusRank(status: String?): Int = when (status?.trim()?.lowercase()) {
    "creating", "depositing" -> 0
    "waiting" -> 1
    "confirming" -> 2
    "exchanging" -> 3
    "sending" -> 4
    "finished" -> 5
    else -> 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapScreen(navController: NavHostController) {
    val viewModel: SwapViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current as androidx.fragment.app.FragmentActivity
    val biometricHelper = remember { com.vaultex.core.security.BiometricHelper(context) }

    // "form" | "confirm" — le suivi (state.swapInProgress) prend la priorité.
    var screen by remember { mutableStateOf("form") }

    val tokens = SwapViewModel.SWAP_ASSETS.map { it.key }

    // Pré-sélection « De » depuis la page d'une crypto.
    LaunchedEffect(Unit) {
        com.vaultex.core.session.TokenSelectionBuffer.consume()?.let { sym ->
            // preselect… et non setFromToken : la fiche du Marché parle de
            // Tether en général, pas de la chaîne qui porte les fonds.
            if (tokens.any { it.equals(sym, ignoreCase = true) }) viewModel.preselectFromToken(sym)
        }
        /*
        Échange de déblocage venu de l'écran d'envoi : les trois champs sont
        déjà décidés là-bas, source comprise — setFromToken tel quel, sans
        passer par preselect… qui irait chercher « la variante détenue » et
        pourrait choisir une autre chaîne que celle qui a été évaluée.

        Rien n'est confirmé pour autant : l'écran affiche le minimum, le coût
        et le taux, et c'est l'utilisateur qui valide.
        */
        com.vaultex.core.session.DeblocageFraisBuffer.consume()?.let { p ->
            if (tokens.any { it.equals(p.de, ignoreCase = true) } &&
                tokens.any { it.equals(p.vers, ignoreCase = true) }
            ) {
                viewModel.setFromToken(p.de)
                viewModel.setToToken(p.vers)
                viewModel.setFromAmount(p.montant)
            }
        }
    }

    val confirmAndExecute = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val bio = biometricHelper.checkAvailability()
        if (bio == com.vaultex.core.security.BiometricHelper.BiometricStatus.AVAILABLE ||
            biometricHelper.canUseDeviceCredential()
        ) {
            biometricHelper.authenticateStrongOrCredential(
                title = "Confirmer le swap",
                subtitle = "${state.fromAmount} ${swapBaseOf(state.fromToken)} → ${swapBaseOf(state.toToken)}",
                onSuccess = { viewModel.executeSwap() },
                onError = { _, _ -> }
            )
        } else viewModel.executeSwap()
    }

    when {
        state.swapInProgress -> SwapTrackingScreen(
            state = state,
            onClose = { viewModel.resetSwap(); screen = "form" },
            onHistory = { viewModel.resetSwap(); screen = "form"; navController.navigate(Routes.HISTORY) },
            onAccueil = {
                viewModel.resetSwap()
                screen = "form"
                // Même forme que la barre du bas : on revient à l'accueil
                // sans empiler une seconde copie de l'écran.
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(Routes.DASHBOARD) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            nomFournisseur = viewModel.nomFournisseur
        )
        screen == "confirm" -> SwapConfirmScreen(
            state = state,
            onBack = { screen = "form" },
            onConfirm = confirmAndExecute,
            commissionPourcent = viewModel.commissionPourcent,
            nomFournisseur = viewModel.nomFournisseur
        )
        else -> SwapFormScreen(
            navController = navController,
            state = state,
            tokens = tokens,
            balanceInfo = viewModel::balanceInfo,
            onFromToken = viewModel::setFromToken,
            onToToken = viewModel::setToToken,
            onAmount = viewModel::setFromAmount,
            onFraction = viewModel::onFractionClicked,
            onInvert = viewModel::swapTokens,
            onContinue = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                screen = "confirm"
            },
            minimumPaire = viewModel.minimumLisible(),
            commissionPourcent = viewModel.commissionPourcent,
            nomFournisseur = viewModel.nomFournisseur
        )
    }
}

/* ════════════════════════════ 1) FORMULAIRE ════════════════════════════ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwapFormScreen(
    navController: NavHostController,
    state: com.vaultex.ui.viewmodel.SwapState,
    tokens: List<String>,
    balanceInfo: (String) -> Pair<Double, Double>,
    onFromToken: (String) -> Unit,
    onToToken: (String) -> Unit,
    onAmount: (String) -> Unit,
    onFraction: (Double) -> Unit,
    onInvert: () -> Unit,
    onContinue: () -> Unit,
    /** Minimum de la paire, mis en forme par le ViewModel. */
    minimumPaire: String?,
    /** Commission réellement appliquée par le fournisseur en service. */
    commissionPourcent: Double,
    /** Nom de l'échangeur — ChangeNOW ou SimpleSwap. */
    nomFournisseur: String
) {
    val fromAmt = state.fromAmount.toDoubleOrNull() ?: 0.0
    val toAmt = state.toAmount.toDoubleOrNull() ?: 0.0
    val fromFiat = if (state.fromPriceUsd > 0.0 && fromAmt > 0.0) "≈ " + String.format(java.util.Locale.US, "%,.2f", fromAmt * state.fromPriceUsd) + " $" else null
    val toFiat = if (state.toPriceUsd > 0.0 && toAmt > 0.0) "≈ " + String.format(java.util.Locale.US, "%,.2f", toAmt * state.toPriceUsd) + " $" else null
    // Affiche le vrai solde : si trop petit pour 4 décimales (ex. 0,00003 BNB
    // arrondi à « 0 »), on montre jusqu'à 8 décimales pour ne pas mentir.
    val balTxt = if (state.fromBalance > 0.0) {
        val bd4 = java.math.BigDecimal.valueOf(state.fromBalance).setScale(4, java.math.RoundingMode.DOWN).stripTrailingZeros()
        if (bd4.signum() == 0)
            java.math.BigDecimal.valueOf(state.fromBalance).setScale(8, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString()
        else bd4.toPlainString()
    } else "0"

    /*
    ─── SOLDE INSUFFISANT : DIT TOUT DE SUITE, PAS À LA FIN ───

    Rien ne vérifiait le solde dans cet écran. SwapUseCase.validate ne connaît
    que trois refus — montant nul, même monnaie, sous le minimum — et pas
    celui-là. On pouvait donc taper 10 BTC avec 0,0001 BTC, passer la
    confirmation, poser son doigt sur le capteur, et n'apprendre qu'au dépôt
    que les fonds n'y étaient pas.

    La comparaison se fait ici, à la frappe : le bouton se désactive et la
    raison s'affiche juste au-dessus de lui.

    Le libellé est celui de l'écran d'envoi (send_err_insufficient_balance,
    « Solde insuffisant. Disponible : … ») : il est déjà traduit en français,
    anglais et arabe, et dit exactement la même chose. Un nouveau texte aurait
    voulu dire trois traductions de plus pour la même phrase.
    */
    val soldeInsuffisant = fromAmt > 0.0 && fromAmt > state.fromBalance
    val messageBloquant = when {
        soldeInsuffisant -> stringResource(
            com.vaultex.R.string.send_err_insufficient_balance,
            "$balTxt ${swapBaseOf(state.fromToken)}"
        )
        else -> state.error
    }

    /*
    LA BARRE DE NAVIGATION EST CELLE DE TOUTE L'APPLICATION.

    L'écran en portait un doublon local — quatre onglets au lieu de cinq, et
    posé dans le bottomBar du Scaffold SANS marge de barre système. Sa moitié
    basse passait donc sous les boutons du téléphone, et « Accueil », tout à
    gauche, était le plus exposé : le geste partait au système au lieu de
    l'application.

    VaultExBottomBar gère ses propres marges et sert déjà l'Accueil, le
    Marché et l'Historique. Un seul composant, un seul comportement.
    */
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = swapBg,
        topBar = {
            /*
            statusBarsPadding() : PRÉPARATION À L'EDGE-TO-EDGE.

            Cette barre de titre est un Box, pas un TopAppBar de Material —
            elle ne reçoit donc AUCUNE marge système automatique. Tant que la
            fenêtre exclut la barre d'état, cette marge vaut zéro et ne change
            rien. Le jour où l'application ciblera Android 16, où le contenu
            passe obligatoirement SOUS la barre d'état, elle empêchera la
            flèche de retour et le titre de se retrouver sous l'horloge.

            Les 43 autres écrans utilisent Scaffold + TopAppBar et sont servis
            par Material : c'est le seul de l'application à devoir le faire
            lui-même.
            */
            Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                BoxIconButton(Icons.Default.ArrowBack, "Retour", Modifier.align(Alignment.CenterStart)) { navController.popBackStack() }
                // Titre seul : « Échangez vos cryptos » redisait ce que le mot
                // « Swap » et l'écran entier disent déjà.
                Text(
                    "Swap",
                    fontWeight = FontWeight.Bold, fontSize = 20.sp, color = swapText,
                    modifier = Modifier.align(Alignment.Center)
                )
                BoxIconButton(Icons.Default.History, "Historique", Modifier.align(Alignment.CenterEnd)) { navController.navigate(Routes.HISTORY) }
            }
        },
        bottomBar = {
            // La barre de navigation FLOTTE par-dessus le contenu : sans cette
            // marge, elle recouvrirait « Continuer », le seul bouton de
            // l'écran.
            Column(Modifier.background(swapBg).padding(bottom = BottomBarSpace)) {
                /*
                LE MESSAGE D'ERREUR EST ÉPINGLÉ AU BOUTON, PAS EN BAS DU SCROLL.

                Il vivait à la fin de la colonne défilante, sous les deux cartes
                de monnaie et la carte de détails — donc hors écran. On voyait
                « Continuer » grisé sans jamais voir pourquoi, et le seul moyen
                de l'apprendre était de faire défiler vers le bas alors que rien
                n'indiquait qu'il y avait quelque chose à y lire.

                Collé au bouton qu'il explique, il est toujours visible.
                L'écran d'envoi fait déjà exactement cela.
                */
                if (messageBloquant != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = swapErrBg,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Text(
                            messageBloquant,
                            fontSize = 13.sp, color = AccentRed,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(54.dp),
                    enabled = state.fromAmount.isNotEmpty() && state.toAmount.isNotEmpty() &&
                        state.fromToken != state.toToken && !state.isLoading && !soldeInsuffisant,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SwapPurple,
                        disabledContainerColor = SwapPurple.copy(alpha = 0.35f),
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.6f)
                    )
                ) {
                    if (state.isLoading)
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Continuer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    ) { padding ->
        /*
        ─── LE FORMULAIRE TIENT DANS CE QUI RESTE ───

        Vu sur photo : la carte de détails coupée en deux, « Meilleur taux ·
        ChangeNOW » tranché au milieu. Un écran ne se lit pas comme ça — une
        carte à moitié visible passe pour un bug d'affichage, pas pour une
        invitation à faire défiler.

        Ce qui mange la hauteur ici, ce n'est pas le contenu, c'est le bas :
        la barre de navigation flotte, donc le bouton « Continuer » doit
        réserver sa hauteur ENTIÈRE au-dessus d'elle (BottomBarSpace), et le
        message d'erreur vient s'ajouter par-dessus. Sur un téléphone courant
        cela laisse environ 380 dp au formulaire, qui en demandait 495.

        Une centaine de points ont donc été repris sur les marges et les
        hauteurs plancher — pas sur le contenu, qui est intact. Le défilement
        reste en filet de sécurité pour les petits écrans et les polices
        agrandies.
        */
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // ─── Vous envoyez ───
            SwapCoinCard(
                label = "Vous envoyez",
                rightLabel = "Solde : $balTxt ${swapBaseOf(state.fromToken)}",
                token = state.fromToken, tokens = tokens, balanceInfo = balanceInfo, onTokenSelect = onFromToken,
                amount = state.fromAmount, editable = true, onAmountChange = onAmount,
                fiat = fromFiat, onFraction = onFraction, highlight = true,
                minimum = minimumPaire
            )

            // Inversion (le bouton fait un demi-tour à chaque clic → preuve visuelle de l'échange)
            var invertSpin by remember { mutableStateOf(0f) }
            val spinAngle by animateFloatAsState(
                targetValue = invertSpin,
                animationSpec = tween(durationMillis = 420),
                label = "swapInvertSpin"
            )
            Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                Surface(
                    onClick = { invertSpin += 180f; onInvert() },
                    shape = CircleShape, color = Color.Transparent,
                    border = BorderStroke(1.dp, swapBorder), modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SwapVert, "Inverser", tint = SwapPurple,
                            modifier = Modifier.size(22.dp).rotate(spinAngle))
                    }
                }
            }

            // ─── Vous recevez ───
            SwapCoinCard(
                label = "Vous recevez",
                rightLabel = null,
                token = state.toToken, tokens = tokens, balanceInfo = balanceInfo, onTokenSelect = onToToken,
                amount = state.toAmount, editable = false, onAmountChange = {},
                fiat = toFiat, onFraction = null, highlight = false
            )

            Spacer(Modifier.height(6.dp))

            /*
            ═══════════════════════════════════════════════════════════════
            LES FONDS DISPARAISSENT, LES CONTOURS RESTENT
            ═══════════════════════════════════════════════════════════════

            Demandé sur maquette, comme pour l'écran d'envoi : « j'ai
            simplement supprimé les fonds ».

            Chaque carte, chaque pastille de part, chaque bouton rond portait
            un aplat ET un contour. Deux façons de dire la même frontière : le
            trait suffit, et l'écran respire au lieu d'empiler des rectangles
            plus clairs sur un fond sombre.

            Le disque violet de l'icône reste, lui : ce n'est pas le fond
            d'une zone, c'est la forme de l'icône.

            ─── DEUX CARTES AU LIEU D'UNE ───

            Les frais et le fournisseur partageaient une carte, séparés d'un
            trait. Sans aplat, ce trait interne devenait la seule frontière
            entre deux sujets sans rapport — le coût de l'échange d'un côté,
            qui l'exécute de l'autre. Deux cartes disent la même chose avec la
            même grammaire que le reste de l'écran, où une carte porte une
            chose. C'est aussi ce que montre la maquette.

            La valeur perdue garde sa carte à elle. C'est un avertissement,
            pas un détail de confort, et il ne doit pas se lire comme une
            ligne de plus dans un récapitulatif.
            */
            val taux = tauxLisible(swapBaseOf(state.fromToken), swapBaseOf(state.toToken), fromAmt, toAmt)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, swapBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(swapPurpleDim),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.TrendingUp, null, tint = SwapPurple, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        /*
                        Tant qu'aucun montant n'est saisi, il n'y a pas de
                        taux — et la ligne affichait alors un « — » en gras,
                        à la place réservée à la donnée principale. Un tiret
                        mis en avant n'informe de rien : il occupe la place
                        d'un chiffre qui n'existe pas encore.

                        La ligne disparaît donc jusqu'à ce qu'il y ait
                        quelque chose à lire, et « Frais inclus · 2 – 5 min »
                        se centre seule. C'est ce que montre la maquette.
                        */
                        if (taux != null) {
                            Text(
                                taux,
                                fontWeight = FontWeight.Bold, fontSize = 16.sp, color = swapText
                            )
                        }
                        // « Frais inclus » juste au-dessus d'une ligne « Frais
                        // estimés » se contredisait : on comprenait soit que
                        // les frais étaient offerts, soit qu'il y en avait
                        // deux. Le taux est estimé, les frais sont dedans, et
                        // c'est la ligne des frais qui le dit.
                        Text(
                            "Taux estimé  ·  2 – 5 min",
                            fontSize = 12.sp, color = swapTextDim
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = swapTextFaint, modifier = Modifier.size(18.dp))
                }
            }

            /*
            ═══════════════════════════════════════════════════════════════
            UN COÛT S'ACCEPTE, UNE PERTE SE SUBIT
            ═══════════════════════════════════════════════════════════════

            Cette ligne s'intitulait « Valeur perdue » et n'apparaissait
            qu'au-dessus de 10 %. Deux défauts, et le second est le pire.

            « Valeur perdue » ne dit ni qui perd, ni au profit de qui. Sur
            un écran qui appartient à VaultEx, « ≈ 26 % » se lit donc
            spontanément « VaultEx me prend 26 % » — alors que la
            commission est de 1,5 % et que l'essentiel est constitué des
            FRAIS DE RÉSEAU du fournisseur. Ces frais sont FIXES : sortir
            de l'ETH coûte la même somme qu'on retire 3 $ ou 3 000 $. Sur
            3 $, cela pèse un quart ; sur 300 $, quelques millièmes. Le
            chiffre ne décrit pas une ponction, il décrit un petit montant
            — encore faut-il le dire, sinon il accuse.

            NE PARAÎTRE QUE QUAND ÇA VA MAL, C'EST ÊTRE UNE ALARME. Sous
            10 %, la ligne disparaissait ; sa seule présence signalait donc
            un problème, et personne n'apprenait jamais à quoi ressemble un
            échange ordinaire. Affichée en permanence — 2 % en sobre, 26 %
            en rouge — elle donne l'échelle, et le 26 % se lit enfin pour
            ce qu'il est : une anomalie de MONTANT, pas de probité.

            Le cacher n'a jamais été une option : quelqu'un qui envoie 3 $
            et en reçoit 2,20 $ le découvrira de toute façon. Un coût
            annoncé est un coût ; un coût découvert est une arnaque.
            */
            coutEchange(state, fromAmt, toAmt)?.let { cout ->
                val teinte = when {
                    cout.pourcent >= SEUIL_COUT_ELEVE -> SwapRed
                    cout.pourcent >= SEUIL_COUT_NOTABLE -> SwapOrange
                    else -> swapTextDim
                }
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, if (cout.pourcent >= SEUIL_COUT_NOTABLE) teinte.copy(alpha = 0.5f) else swapBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    /*
                    UNE SOMME, RIEN D'AUTRE.

                    Ni pourcentage, ni phrase d'explication : la ligne tient
                    en « Frais estimés — ≈ 0,51 $ ». Un taux se lit comme un
                    tarif, donc comme celui de VaultEx, alors que l'essentiel
                    part en frais de réseau ; et deux lignes de justification
                    sous un chiffre attirent l'attention sur lui au lieu de le
                    banaliser. Une somme posée sans commentaire se lit comme
                    ce qu'elle est : le prix de l'opération.

                    La proportion reste calculée et continue de TEINTER la
                    ligne — sobre, ambre au-delà de 10 %, rouge au-delà de
                    25 %. Le signal demeure, sans le chiffre qui accuse.

                    Le détail appartient désormais à l'écran de confirmation,
                    qui dispose de la place pour le dire correctement.
                    */
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = teinte, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Frais estimés", fontSize = 13.sp, color = swapTextDim)
                        Spacer(Modifier.weight(1f))
                        Text(
                            sommeUsd(cout.usd),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = teinte
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, swapBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                /*
                « MEILLEUR TAUX » ANNONÇAIT UNE COMPARAISON QUI N'A PAS LIEU.

                Un seul échangeur répond : SwapModule en choisit un selon
                `swap.provider`, et aucun devis concurrent n'est demandé.
                Rien, dans le code, ne permettait donc d'affirmer que ce taux
                est le meilleur — et la coche verte donnait à cette affirmation
                l'apparence d'une vérification.

                Sur un écran qui déplace de l'argent, un label invérifiable est
                le pire endroit où se montrer approximatif : c'est exactement
                ce qu'un utilisateur échaudé ira relire. « Échange via » dit ce
                qui est vrai, et n'ôte rien — savoir QUI exécute l'opération
                est l'information utile.

                Le jour où plusieurs devis seront réellement comparés, la
                mention se rétablira d'elle-même, et elle sera vraie.
                */
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Échange via", fontSize = 13.sp, color = swapTextDim)
                    Spacer(Modifier.width(8.dp))
                    // Le nom vient du fournisseur en service : l'écrire en dur
                    // mentirait dès la bascule vers SimpleSwap.
                    Text(nomFournisseur, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SwapPurple)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = swapTextFaint, modifier = Modifier.size(18.dp))
                }
            }

            // (L'erreur n'est plus ici : elle est épinglée au bouton « Continuer ».)
            Spacer(Modifier.height(4.dp))
        }
    }
        VaultExBottomBar(navController, Modifier.align(Alignment.BottomCenter))
    }
}

/* ════════════════════════════ 2) CONFIRMATION ════════════════════════════ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwapConfirmScreen(
    state: com.vaultex.ui.viewmodel.SwapState,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    /** Commission réellement appliquée par le fournisseur en service. */
    commissionPourcent: Double,
    /** Nom de l'échangeur — ChangeNOW ou SimpleSwap. */
    nomFournisseur: String
) {
    val fromAmt = state.fromAmount.toDoubleOrNull() ?: 0.0
    val toAmt = state.toAmount.toDoubleOrNull() ?: 0.0
    val fromFiat = if (state.fromPriceUsd > 0.0 && fromAmt > 0.0) "≈ " + String.format(java.util.Locale.US, "%,.2f", fromAmt * state.fromPriceUsd) + " $" else ""
    val toFiat = if (state.toPriceUsd > 0.0 && toAmt > 0.0) "≈ " + String.format(java.util.Locale.US, "%,.2f", toAmt * state.toPriceUsd) + " $" else ""

    Scaffold(
        containerColor = swapBg,
        topBar = {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                BoxIconButton(Icons.Default.ArrowBack, "Retour", Modifier.align(Alignment.CenterStart), onClick = onBack)
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Swap", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = swapText)
                    Text("Vérifiez et confirmez", fontSize = 12.sp, color = swapTextDim)
                }
            }
        },
        bottomBar = {
            /*
            navigationBarsPadding() : le slot bottomBar ne reçoit AUCUNE marge
            système, contrairement au contenu du Scaffold. Sans elle, « Confirmer
            le swap » passe sous les boutons du téléphone depuis le passage à
            Android 16 — et c'est le bouton qui engage les fonds.
            */
            Column(
                Modifier
                    .background(swapBg)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                /*
                CONFIRMER EXIGE UN DEVIS.

                Le bouton n'était désactivé que pendant le chargement. Quand le
                fournisseur refusait de coter — « Devis indisponible.
                Unauthorized », constaté à l'écran — la page affichait
                « Vous recevez — BNB » et laissait pourtant confirmer.

                Or c'est le pire moment pour laisser passer un appui : on
                engage des fonds vers une opération dont personne n'a dit ce
                qu'elle rendrait. L'écran de saisie posait déjà cette condition
                (toAmount non vide) ; celui de confirmation, qui est pourtant
                le dernier rempart, ne la reprenait pas.
                */
                val devisValide = state.toAmount.isNotEmpty() &&
                    (state.toAmount.toDoubleOrNull() ?: 0.0) > 0.0
                // Même raison qu'à la saisie : la raison du refus se lit à côté
                // du bouton refusé, pas au bas d'un défilement.
                if (state.error != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = swapErrBg,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text(
                            state.error!!,
                            fontSize = 13.sp, color = AccentRed,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = !state.isLoading && devisValide,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SwapPurple, contentColor = Color.White,
                        disabledContainerColor = SwapPurple.copy(alpha = 0.35f)
                    )
                ) {
                    if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Confirmer le swap", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                /*
                LE FAUX COMPTE À REBOURS EST RETIRÉ.

                « Le taux sera valable pendant 00:29 » était une chaîne écrite
                EN DUR. Aucun minuteur derrière, aucun état : elle affichait
                29 secondes en permanence, y compris — vu sur capture — juste
                sous « Devis indisponible », donc en annonçant la validité d'un
                taux qui n'existait pas.

                Sur un écran où l'on engage des fonds, une phrase inventée sur
                la durée d'un engagement n'est pas un détail d'habillage.

                Rien ne la remplace : la note verte au-dessus dit déjà que le
                montant reçu est estimé, ce qui est vrai et suffit. Un vrai
                compte à rebours supposerait de connaître l'expiration du devis
                — le fournisseur la donne, mais on ne la lit pas encore.
                */
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Vérifiez les détails", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = swapText,
                modifier = Modifier.padding(top = 4.dp))

            // Carte récap envoi/réception
            Surface(shape = RoundedCornerShape(16.dp), color = swapCard, border = BorderStroke(1.dp, swapBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    ConfirmAmountRow("Vous envoyez", state.fromToken, "${state.fromAmount} ${swapBaseOf(state.fromToken)}", fromFiat)
                    Box(Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp)) {
                        Icon(Icons.Default.SwapVert, null, tint = swapTextFaint, modifier = Modifier.size(20.dp))
                    }
                    ConfirmAmountRow("Vous recevez", state.toToken, "${state.toAmount.ifEmpty { "—" }} ${swapBaseOf(state.toToken)}", toFiat)
                }
            }

            /*
            Même principe que la confirmation d'envoi : le TAUX est la seule
            donnée sur laquelle l'utilisateur décide vraiment — c'est elle qui
            dit ce qu'il obtient. Fournisseur, frais, réseau et délai servent à
            vérifier. Ils passent donc derrière « Voir détails », pour que le
            bouton de confirmation reste atteignable sans faire défiler.
             */
            // Le « — » reste ICI : c'est une ligne étiquetée « Taux : — », où
            // le tiret dit « pas encore de valeur ». Sur le formulaire, la
            // même chaîne était le titre en gras de la carte — ce n'est pas
            // la même chose.
            val rate = (if (fromAmt > 0.0 && toAmt > 0.0)
                tauxLisible(swapBaseOf(state.fromToken), swapBaseOf(state.toToken), fromAmt, toAmt)
            else null) ?: "—"
            /*
            LA COMMISSION AFFICHÉE EST CELLE QUI EST PRÉLEVÉE.

            Cette ligne annonçait 1,5 % calculés depuis une constante du code,
            alors que RIEN n'était prélevé : applyFee() n'était appelée que
            par les tests, et le corps envoyé à ChangeNOW n'avait aucun champ
            de commission. L'application annonçait donc un frais qu'elle ne
            prenait pas — une déclaration inexacte sur un produit financier,
            et l'un des points que la Play Console examine.

            La commission vit désormais sur la CLÉ du fournisseur : 0,4 % chez
            ChangeNOW, réglable de 0,4 à 5 % chez SimpleSwap. Le code ne
            prélève rien — il lit ce que le fournisseur applique et le dit.

            Le pourcentage accompagne le montant : « 0,075 USDT (1,5 %) ». Un
            montant seul ne permet pas de comparer, et c'est précisément ce
            qu'un utilisateur veut faire avec un frais.
            */
            val feeTxt = if (fromAmt > 0.0 && commissionPourcent > 0.0)
                "${montantLisible(fromAmt * commissionPourcent / 100.0)} ${swapBaseOf(state.fromToken)}" +
                    " (%.2f %%)".format(commissionPourcent)
            else "—"

            Surface(shape = RoundedCornerShape(16.dp), color = swapCard, border = BorderStroke(1.dp, swapBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    ConfirmRow("Taux", rate)
                    ExpandableDetails(
                        accent = SwapPurple,
                        labelColor = swapTextDim,
                        summary = "Frais inclus : $feeTxt"
                    ) {
                        Divider(color = swapBorder)
                        ConfirmRow("Fournisseur", nomFournisseur, chevron = true)
                        Divider(color = swapBorder)
                        ConfirmRow("Frais (inclus)", feeTxt)
                        Divider(color = swapBorder)
                        ConfirmRow("Réseau", "${swapNetworkLong(state.fromToken)} → ${swapNetworkLong(state.toToken)}")
                        Divider(color = swapBorder)
                        ConfirmRow("Délai estimé", "2 - 5 min", valueColor = SwapPurple)
                    }
                }
            }

            /*
            DEUX ENCARTS RETIRÉS DE CET ÉCRAN.

            Le bandeau vert annonçait « vous recevrez au moins X ». Le vert
            est la couleur du succès : l'employer pour une RÉSERVE sur le
            montant à recevoir envoyait deux messages contraires en même
            temps. Et l'écran dit déjà « Taux estimé » ; le répéter ici, en
            couleur, appuyait sur l'incertitude au moment précis où l'on
            demande de confirmer.

            « Soyez attentif au réseau » mettait en garde contre une erreur
            impossible à commettre : le dépôt est envoyé PAR L'APPLICATION,
            rien n'est à copier ni à coller — la phrase le disait elle-même.
            Un avertissement sans danger correspondant apprend à ignorer les
            avertissements, y compris ceux de l'écran d'envoi, où une adresse
            sur la mauvaise chaîne perd réellement les fonds.

            Le réseau reste indiqué, sans dramatisation, dans la ligne
            « Réseau » du détail juste au-dessus.
            */

            // (L'erreur n'est plus ici : elle est épinglée à « Confirmer ».)
            Spacer(Modifier.height(4.dp))
        }
    }
}

/* ════════════════════════════ 3) SUIVI / SUCCÈS ════════════════════════════ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwapTrackingScreen(
    state: com.vaultex.ui.viewmodel.SwapState,
    onClose: () -> Unit,
    onHistory: () -> Unit,
    /** Retour à l'accueil, automatique après le décompte ou sur demande. */
    onAccueil: () -> Unit,
    /** Nom de l'échangeur en service — ChangeNOW ou SimpleSwap. */
    nomFournisseur: String
) {
    val copier = com.vaultex.ui.components.rememberCopieAvecVibration()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    // Même précaution que statusRank : ces deux booléens commandent le vert,
    // le message de succès et le retour à l'accueil.
    val etat = state.swapStatus?.trim()?.lowercase()
    val finished = etat == "finished"
    val failed = etat in listOf("failed", "refunded", "expired")
    val rank = statusRank(state.swapStatus)

    LaunchedEffect(finished) { if (finished) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

    /*
    ═══════════════════════════════════════════════════════════════════════
    RETOUR AUTOMATIQUE À L'ACCUEIL — VISIBLE, ET ANNULABLE
    ═══════════════════════════════════════════════════════════════════════

    Le dépôt parti, il n'y a plus rien à faire ici : l'échange se termine
    seul, le worker le suit, et l'accueil affiche désormais « Échange en
    cours ». Rester planté devant une roue qui tourne n'apporte rien.

    MAIS ON NE FAIT PAS DISPARAÎTRE UN ÉCRAN SOUS LES YEUX DE QUELQU'UN. Cet
    écran porte l'identifiant de l'échange — la seule donnée qu'on ne peut
    pas reconstituer, celle qu'il faut pour réclamer auprès du fournisseur.
    Le lui retirer pendant qu'il la lit ou la copie serait le genre de geste
    qu'on ne pardonne pas sur un écran qui déplace de l'argent.

    D'où un décompte ÉCRIT, et un appui qui l'arrête définitivement. Celui
    qui veut partir n'attend pas ; celui qui veut lire n'est pas chassé.
    */
    /*
    ═══════════════════════════════════════════════════════════════════════
    CET ÉCRAN EST UN PASSAGE, PAS UNE SALLE D'ATTENTE
    ═══════════════════════════════════════════════════════════════════════

    Un échange dure deux à cinq minutes. Retenir quelqu'un devant une roue
    pendant ce temps n'apporte rien : il n'a aucune décision à prendre, et
    rien de ce qu'il ferait ne changerait quoi que ce soit.

    On repart donc dès que le dépôt est DIFFUSÉ — pas avant : jusque-là, la
    transaction peut encore échouer, et son message d'erreur doit s'afficher
    sur un écran que l'utilisateur regarde encore. Le hash du dépôt est la
    preuve matérielle de cette diffusion.

    LA SUITE SE LIT AILLEURS, ET C'EST CE QUI REND LE DÉPART ACCEPTABLE.
    L'accueil porte la ligne « Échange en cours » jusqu'à l'aboutissement, et
    une notification annonce la fin même application fermée. On ne perd donc
    pas l'information en partant : on la retrouve là où elle dérange moins.

    DEUXIÈME MOMENT, POUR CEUX QUI SONT RESTÉS. Si l'échange aboutit alors
    que l'écran est encore ouvert — décompte annulé, retour volontaire — le
    compteur repart à cinq une fois tout au vert. Ceux-là voient la frise
    aller jusqu'au bout et la confirmation s'afficher.

    UN ÉCHEC NE RENVOIE NULLE PART. C'est le seul cas où l'écran porte une
    information qu'on ne retrouvera pas ailleurs, et où l'utilisateur a une
    décision à prendre.
    */
    val depotEnvoye = state.depositTxHash != null
    val phase = when {
        failed -> null
        finished -> "fini"
        depotEnvoye -> "depot"
        else -> null
    }
    var decompteAnnule by remember { mutableStateOf(false) }
    var secondes by remember(phase) { mutableStateOf(5) }
    val decompteActif = phase != null && !decompteAnnule

    LaunchedEffect(phase, decompteAnnule) {
        if (phase == null || decompteAnnule) return@LaunchedEffect
        while (secondes > 0) {
            kotlinx.coroutines.delay(1000)
            secondes--
        }
        onAccueil()
    }

    val payoutAddr = "votre portefeuille"

    Scaffold(
        containerColor = swapBg,
        topBar = {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                BoxIconButton(Icons.Default.ArrowBack, "Fermer", Modifier.align(Alignment.CenterStart), onClick = onClose)
                Text(
                    if (finished) "Swap terminé !" else if (failed) "Swap échoué" else "Swap en cours",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp, color = swapText,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        },
        bottomBar = {
            // Même raison qu'au-dessus : le bottomBar ne reçoit pas de marge
            // système, il doit la poser lui-même.
            Column(
                Modifier
                    .background(swapBg)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (decompteActif) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, swapBorder),
                        modifier = Modifier.fillMaxWidth().clickable { decompteAnnule = true }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, null, tint = SwapGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                (if (phase == "fini") "Échange terminé" else "Dépôt envoyé") +
                                    "  ·  retour à l'accueil dans $secondes s",
                                fontSize = 12.sp, color = swapTextDim, modifier = Modifier.weight(1f)
                            )
                            Text(
                                "Rester ici",
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SwapPurple
                            )
                        }
                    }
                }
                // Bouton PRINCIPAL « Nouveau swap » (remplace « Partager le reçu ») :
                // toujours actif — pendant le traitement, l'échange se termine tout
                // seul en arrière-plan, l'utilisateur n'a pas à attendre.
                Button(
                    onClick = onClose,   // resetSwap → formulaire vierge
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SwapPurple, contentColor = Color.White)
                ) { Text("Nouveau swap", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                OutlinedButton(
                    onClick = onHistory,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, swapBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = swapText)
                ) { Text("Voir dans l'historique", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            }
        }
    ) { padding ->
        Column(
            /*
            ═══════════════════════════════════════════════════════════════
            TOUT TIENT SUR UN ÉCRAN
            ═══════════════════════════════════════════════════════════════

            Constaté sur appareil : il fallait faire défiler pour voir ce
            qu'on recevait et l'identifiant de l'échange. Sur un écran qui
            dit « votre argent est parti », ce qui se trouve sous la ligne
            de flottaison n'existe pas : on referme avant d'y arriver.

            Trois coupes, aucune information perdue :
            · la carte « Vous recevez » répétait le montant déjà écrit en
              haut ; seul l'identifiant était nouveau, il rejoint la frise ;
            · le pavé d'attente disait en quatre lignes ce qui en prend
              deux ;
            · les espacements passent de 14 à 10, la frise de 16 à 10.

            Le défilement reste en place : une langue plus verbeuse, un
            grand corps de texte ou un petit écran doivent pouvoir
            déborder sans rien masquer.
            */
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            // Deux logos + flèche
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                TokenBadgeWithCheck(state.fromToken, finished)
                Icon(Icons.Default.ChevronRight, null, tint = swapTextFaint, modifier = Modifier.size(26.dp))
                TokenBadgeWithCheck(state.toToken, finished)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.fromAmount} ${swapBaseOf(state.fromToken)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = swapText)
                    Text(swapNetworkBadge(state.fromToken), fontSize = 11.sp, color = swapTextDim)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("≈ ${state.toAmount.ifEmpty { "—" }} ${swapBaseOf(state.toToken)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = swapText)
                    Text(swapNetworkBadge(state.toToken), fontSize = 11.sp, color = swapTextDim)
                }
            }

            // Grand cercle d'état
            Box(
                Modifier.size(72.dp).clip(CircleShape)
                    .background(if (finished) SwapGreen else if (failed) AccentRed else swapPurpleDim),
                contentAlignment = Alignment.Center
            ) {
                when {
                    finished -> Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    failed -> Text("!", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    else -> CircularProgressIndicator(color = SwapPurple, strokeWidth = 4.dp, modifier = Modifier.size(40.dp))
                }
            }

            /*
            LE SUCCÈS N'ÉTAIT NULLE PART ÉCRIT.

            L'écran passait bien au vert — grande coche, frise cochée, badge
            « Terminé » — mais pas une phrase ne disait ce qui venait de se
            passer. Des symboles verts se lisent vite, et se lisent MAL : rien
            n'y distinguait « les fonds sont arrivés » de « la demande a été
            acceptée ».

            Sur la dernière image que l'on voit d'une opération qui déplace de
            l'argent, l'ambiguïté n'a pas sa place.

            La phrase affirme les deux choses qu'on veut savoir : l'échange a
            réussi, et les fonds ont bougé. Elle n'est affichée que sur un
            statut « finished » du fournisseur, qui signifie exactement cela.
            */
            if (finished) {
                Surface(shape = RoundedCornerShape(12.dp), color = swapPurpleDim, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = SwapPurple, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Succès de l'échange. Tous les fonds ont été transférés.",
                            fontSize = 12.sp, color = swapText, lineHeight = 16.sp
                        )
                    }
                }
            }

            // Message « pas besoin d'attendre » pendant le traitement (2–5 min).
            if (!finished && !failed) {
                Surface(shape = RoundedCornerShape(12.dp), color = swapPurpleDim, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = SwapPurple, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            // La promesse « pas besoin d'attendre » n'était pas
                            // tenue : le suivi vivait dans le ViewModel de cet
                            // écran et mourait en le quittant. SwapTrackingWorker
                            // le reprend désormais depuis la base, donc on peut
                            // annoncer la notification sans mentir.
                            // Quatre lignes pour dire deux choses : c'est parti,
                            // tu peux fermer. Le reste — la durée, la
                            // notification — tient dans la même phrase.
                            "Dépôt envoyé ✓ · 2 à 5 min. Tu peux fermer l'app : une notification t'avertira.",
                            fontSize = 12.sp, color = swapText, lineHeight = 16.sp
                        )
                    }
                }
            }

            // Détails / frise
            Surface(shape = RoundedCornerShape(16.dp), color = swapCard, border = BorderStroke(1.dp, swapBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Détails de la transaction", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = swapText, modifier = Modifier.weight(1f))
                        val statusTxt = if (finished) "Réussie" else if (failed) "Échouée" else "En cours"
                        val statusCol = if (finished) SwapGreen else if (failed) AccentRed else SwapPurple
                        Surface(shape = RoundedCornerShape(6.dp), color = statusCol.copy(alpha = 0.16f)) {
                            Text(statusTxt, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 11.sp, color = statusCol, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    TimelineStep("Transaction créée", null, done = rank >= 0 || finished, active = false, last = false)
                    TimelineStep("Confirmations réseau ${swapNetworkBadge(state.fromToken)}", null, done = rank > 2 || finished, active = rank in 1..2 && !finished, last = false)
                    // « Par ChangeNOW » était écrit en dur : l'écran nommait le
                    // mauvais échangeur dès que SimpleSwap répondait.
                    TimelineStep("Échange effectué", "Par $nomFournisseur", done = rank > 3 || finished, active = rank == 3 && !finished, last = false)
                    TimelineStep("Envoi des ${swapBaseOf(state.toToken)}", swapNetworkBadge(state.toToken), done = rank > 4 || finished, active = rank == 4 && !finished, last = false)
                    TimelineStep("Terminé", null, done = finished, active = false, last = true)

                    /*
                    L'IDENTIFIANT RESTE, LA CARTE QUI L'ENTOURAIT PART.

                    « Vous recevez ≈ 0,001015 ETH » répétait mot pour mot la
                    ligne déjà affichée sous les logos. Seul l'identifiant
                    était neuf — et c'est le seul élément de cet écran qu'on
                    ne peut pas reconstituer : sans lui, impossible de
                    retrouver l'échange auprès du fournisseur si quelque
                    chose se passe mal. Il descend donc au pied de la frise,
                    toujours copiable d'un doigt.
                    */
                    state.swapId?.let { id ->
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { copier(id) }
                        ) {
                            Text("Reçu sur $payoutAddr · ID ${id.take(12)}…",
                                fontSize = 11.sp, color = swapTextDim, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ContentCopy, "Copier", tint = SwapPurple, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            if (failed && state.error != null) {
                Surface(shape = RoundedCornerShape(10.dp), color = swapErrBg, modifier = Modifier.fillMaxWidth()) {
                    Text(state.error!!, fontSize = 13.sp, color = AccentRed, modifier = Modifier.padding(12.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/* ───────────────────────── Composants partagés ───────────────────────── */

@Composable
private fun BoxIconButton(icon: ImageVector, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Sans fond : le contour seul dessine le bouton (voir le bloc « LES FONDS
    // DISPARAISSENT » plus bas).
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = Color.Transparent, border = BorderStroke(1.dp, swapBorder), modifier = modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, desc, tint = swapText, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun TokenLogo(token: String, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(tokenColor(token)), contentAlignment = Alignment.Center) {
        Text(token.take(2).uppercase(), color = Color.White, fontSize = (size / 4).sp, fontWeight = FontWeight.Bold)
        coil.compose.AsyncImage(model = CryptoIcon.url(token), contentDescription = token, modifier = Modifier.size(size.dp).clip(CircleShape))
    }
}

@Composable
private fun TokenBadgeWithCheck(token: String, finished: Boolean) {
    Box(contentAlignment = Alignment.BottomEnd) {
        TokenLogo(token, 56)
        if (finished) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(swapBg).padding(2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(SwapGreen), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

@Composable
private fun TimelineStep(title: String, subtitle: String?, done: Boolean, active: Boolean, last: Boolean) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(22.dp).clip(CircleShape)
                    .background(if (done) SwapGreen else if (active) SwapPurple else swapCardAlt),
                contentAlignment = Alignment.Center
            ) {
                when {
                    done -> Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    active -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                    else -> Box(Modifier.size(6.dp).clip(CircleShape).background(swapTextFaint))
                }
            }
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(if (done) SwapGreen else swapBorder))
        }
        Spacer(Modifier.width(12.dp))
        // 16 dp entre chaque étape faisaient déborder la frise sous la ligne
        // de flottaison ; 10 dp la laissent parfaitement lisible.
        Column(Modifier.padding(bottom = if (last) 0.dp else 10.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (done || active) swapText else swapTextDim)
            subtitle?.let { Text(it, fontSize = 11.sp, color = swapTextDim) }
        }
    }
}

@Composable
private fun ConfirmAmountRow(label: String, token: String, amount: String, fiat: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TokenLogo(token, 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = swapTextDim)
            Text(amount, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = swapText)
        }
        if (fiat.isNotEmpty()) Text(fiat, fontSize = 12.sp, color = swapTextDim)
    }
}

@Composable
private fun ConfirmRow(label: String, value: String, valueColor: Color = swapText, chevron: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = swapTextDim)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor, textAlign = TextAlign.End)
        if (chevron) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ChevronRight, null, tint = swapTextFaint, modifier = Modifier.size(16.dp)) }
    }
}

/**
 * Pastille « 25% », « 50% », « MAX » au-dessus du champ de montant.
 *
 * MAX ressort davantage : c'est le seul des trois qui engage la totalité du
 * solde, et le seul dont on veuille pouvoir dire qu'on l'a touché exprès.
 */
@Composable
private fun PartSolde(libelle: String, fort: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        // Aplat remplacé par un contour. MAX garde son avance par un trait
        // plus franc et son texte en gras, plutôt que par un fond plus dense.
        color = Color.Transparent,
        border = BorderStroke(1.dp, SwapPurple.copy(alpha = if (fort) 0.8f else 0.45f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            libelle,
            fontSize = 12.sp,
            fontWeight = if (fort) FontWeight.Bold else FontWeight.SemiBold,
            color = SwapPurple,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** Carte « Vous envoyez / Vous recevez » (logo + sélecteur + montant + ≈ $). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwapCoinCard(
    label: String,
    rightLabel: String?,
    token: String,
    tokens: List<String>,
    balanceInfo: (String) -> Pair<Double, Double>,
    onTokenSelect: (String) -> Unit,
    amount: String,
    editable: Boolean,
    onAmountChange: (String) -> Unit,
    fiat: String?,
    onFraction: ((Double) -> Unit)?,
    highlight: Boolean,
    /**
     * Minimum de la paire, déjà mis en forme — ou null s'il est inconnu.
     *
     * Ne concerne que la carte du HAUT : c'est le montant qu'on saisit qui
     * doit atteindre le seuil, pas celui qu'on reçoit.
     */
    minimum: String? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        // Sans fond : le contour porte seul la carte. C'est lui qui distingue
        // déjà « Vous envoyez » (violet, plus épais) de « Vous recevez ».
        color = Color.Transparent,
        border = BorderStroke(if (highlight) 1.5.dp else 1.dp, if (highlight) SwapPurple else swapBorder),
        /*
        Hauteur minimale COMMUNE aux deux cartes.

        Seule celle du haut porte les pastilles de part : sans plancher, elle
        dépasserait l'autre d'une trentaine de points, et deux cartes voisines
        de hauteurs différentes se lisent comme un défaut d'alignement.

        Ramenée de 152 à 126 dp : c'est la hauteur naturelle de la carte du
        haut une fois les marges resserrées, donc rien n'est comprimé — le
        plancher se contente de suivre. Les deux cartes gagnent 52 dp à elles
        deux, l'essentiel de ce qu'il fallait reprendre pour que la carte de
        détails ne soit plus tranchée en bas d'écran.
        */
        modifier = Modifier.fillMaxWidth().heightIn(min = 126.dp)
    ) {
        /*
        CARTE PLUS HAUTE, et le solde SUR SA PROPRE LIGNE.

        Le libellé, le solde et les trois pastilles se partageaient une seule
        ligne. Sur « Solde : 0.0000311 ETH » cela passait déjà tout juste ; sur
        un solde à plusieurs chiffres — 243 939,9182633 SHIB — le texte se
        serait écrasé contre les pastilles, voire tronqué.

        Le solde reste donc en haut avec le libellé, où il se lit, et les
        pastilles descendent sur une ligne à elles, alignées à droite. Elles y
        gagnent aussi une cible de frappe plus confortable.
        */
        Column(
            Modifier.fillMaxHeight().padding(13.dp),
            // Centré : l'espace en trop de la carte du bas se répartit au lieu
            // de tomber d'un bloc sous le montant.
            verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 12.sp, color = swapTextDim)
                Spacer(Modifier.weight(1f))
                rightLabel?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = swapTextDim,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            /*
            MAX seul obligeait à saisir le montant à la main dès qu'on ne
            voulait pas tout échanger — c'est-à-dire presque toujours, vider
            un solde n'étant pas le geste courant.

            Ordre croissant : on lit une progression, et le geste le plus
            engageant reste au bout.
            */
            if (onFraction != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PartSolde("25%") { onFraction(0.25) }
                    Spacer(Modifier.width(8.dp))
                    PartSolde("50%") { onFraction(0.50) }
                    Spacer(Modifier.width(8.dp))
                    PartSolde("MAX", fort = true) { onFraction(1.0) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                var showPicker by remember { mutableStateOf(false) }
                Surface(shape = RoundedCornerShape(12.dp), color = swapCardAlt,
                    modifier = Modifier.clickable { showPicker = true }) {
                    Row(Modifier.padding(start = 5.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TokenLogo(token, 30)
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(swapBaseOf(token), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = swapText)
                                Icon(Icons.Default.ArrowDropDown, null, tint = swapTextDim, modifier = Modifier.size(18.dp))
                            }
                            Surface(shape = RoundedCornerShape(4.dp), color = SwapPurple.copy(alpha = 0.16f)) {
                                Text(swapNetworkBadge(token), modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    fontSize = 9.sp, color = SwapPurple, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                if (showPicker) {
                    TokenPickerSheet(
                        tokens = tokens,
                        current = token,
                        balanceInfo = balanceInfo,
                        onSelect = { onTokenSelect(it); showPicker = false },
                        onDismiss = { showPicker = false }
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    if (editable) {
                        BasicTextField(
                            value = amount,
                            onValueChange = onAmountChange,
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = swapText, textAlign = TextAlign.End),
                            cursorBrush = SolidColor(SwapPurple),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.widthIn(min = 60.dp),
                            decorationBox = { inner -> Box(contentAlignment = Alignment.CenterEnd) { if (amount.isEmpty()) Text("0", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = swapTextFaint); inner() } }
                        )
                    } else {
                        Text(amount.ifEmpty { "0" }, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = swapText)
                    }
                    fiat?.let { Text(it, fontSize = 12.sp, color = swapTextDim) }
                    /*
                    LE MINIMUM SE LIT AVANT DE TAPER.

                    Il n'était demandé qu'au clic sur MAX et à la création de
                    l'échange : on le découvrait donc en se faisant refuser,
                    après avoir choisi les monnaies, saisi un montant et
                    touché « Continuer ».

                    Mesuré sur le vrai service, USDT-TRC20 → BTC exige 17,30
                    USDT — environ 10 400 FCFA. Quelqu'un qui détient 2 000
                    FCFA n'avait aucune chance, et rien ne le lui disait.

                    Rien n'est bloqué pour autant : le fournisseur refusera
                    ce qu'il refuse, et la ligne « Coût de l'échange » dit déjà ce
                    que l'opération coûte en proportion. C'est à l'utilisateur
                    de décider — c'est son argent.
                    */
                    minimum?.let {
                        Text(
                            stringResource(com.vaultex.R.string.send_min_hint, it),
                            fontSize = 11.sp, color = swapTextFaint
                        )
                    }
                }
            }
        }
    }
}

/** Ligne de détail (icône + libellé à gauche, valeur à droite). */
@Composable
private fun SwapDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = swapText,
    chevron: Boolean = false,
    showIcon: Boolean = true
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showIcon) { Icon(icon, null, tint = swapTextDim, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)) }
        Text(label, fontSize = 13.sp, color = swapTextDim)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
        if (chevron) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ChevronRight, null, tint = swapTextFaint, modifier = Modifier.size(16.dp)) }
    }
}

/* ─────────────── Feuille « Choisir une crypto » (maquette) ─────────────── */

/** Nom complet d'un actif (ligne du sélecteur). */
private fun swapFullName(key: String): String = when (SwapViewModel.assetOf(key).base.uppercase()) {
    "BTC" -> "Bitcoin"; "ETH" -> "Ethereum"; "BNB" -> "BNB Chain"; "SOL" -> "Solana"
    "TRX" -> "Tron"; "USDT" -> "Tether USD"; "USDC" -> "USD Coin"; "DAI" -> "Dai Stablecoin"
    "LINK" -> "Chainlink"; "SHIB" -> "Shiba Inu"; "PEPE" -> "Pepe"; "UNI" -> "Uniswap"
    "AAVE" -> "Aave"; "WBTC" -> "Wrapped Bitcoin"; "CAKE" -> "PancakeSwap"
    else -> SwapViewModel.assetOf(key).base
}

/** Montant du sélecteur : 8 décimales pour les natifs, 2 pour les gros soldes. */
private fun pickerAmount(v: Double): String =
    if (v <= 0.0) "0.00"
    else java.math.BigDecimal.valueOf(v).setScale(if (v >= 1.0) 2 else 8, java.math.RoundingMode.DOWN)
        .stripTrailingZeros().toPlainString()

private fun pickerXof(v: Double): String =
    java.text.NumberFormat.getNumberInstance(com.vaultex.core.session.LocaleManager.appLocale()).format(v.toLong())

/** Couleur du badge réseau (TRC20 violet, ERC20 bleu, BEP20 jaune). */
private fun badgeColor(badge: String): Color = when (badge.uppercase()) {
    "TRC20" -> SwapPurple
    "ERC20" -> Color(0xFF3B82F6)
    "BEP20" -> Color(0xFFF5B301)
    else -> Color(0xFF3B82F6)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenPickerSheet(
    tokens: List<String>,
    current: String,
    balanceInfo: (String) -> Pair<Double, Double>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = tokens.filter { key ->
        val a = SwapViewModel.assetOf(key)
        query.isBlank() || a.base.contains(query, true) ||
            swapFullName(key).contains(query, true) || a.badge.contains(query, true)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = swapBg) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            // Titre + fermer
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(com.vaultex.R.string.swap_pick_crypto),
                    fontWeight = FontWeight.Bold, fontSize = 18.sp, color = swapText, modifier = Modifier.weight(1f))
                Surface(onClick = onDismiss, shape = CircleShape, color = swapCardAlt, modifier = Modifier.size(34.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, stringResource(com.vaultex.R.string.close), tint = swapText, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Recherche
            Surface(shape = RoundedCornerShape(14.dp), color = swapCard,
                border = BorderStroke(1.dp, swapBorder), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = swapTextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = swapText),
                        cursorBrush = SolidColor(SwapPurple),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text(stringResource(com.vaultex.R.string.swap_search_crypto), fontSize = 14.sp, color = swapTextFaint)
                            inner()
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // Liste des actifs
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered.size) { i ->
                    val key = filtered[i]
                    val a = SwapViewModel.assetOf(key)
                    val selected = key.equals(current, ignoreCase = true)
                    val isTokenBadge = a.badge.uppercase() in listOf("TRC20", "ERC20", "BEP20")
                    val (bal, xof) = balanceInfo(key)
                    Surface(
                        onClick = { onSelect(key) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) SwapPurple.copy(alpha = 0.10f) else swapCard,
                        border = BorderStroke(if (selected) 1.2.dp else 1.dp, if (selected) SwapPurple else swapBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TokenLogo(key, 32)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.base, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = swapText)
                                Text(swapFullName(key), fontSize = 11.sp, color = swapTextDim)
                            }
                            // Solde + ≈ XOF (comme la maquette)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    pickerAmount(bal),
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (bal > 0.0) swapText else swapTextDim
                                )
                                Text("≈ " + pickerXof(xof) + " XOF", fontSize = 10.sp, color = swapTextDim)
                            }
                            Spacer(Modifier.width(8.dp))
                            if (isTokenBadge) {
                                val bc = badgeColor(a.badge)
                                Surface(shape = RoundedCornerShape(6.dp), color = bc.copy(alpha = 0.14f)) {
                                    Text(a.badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = bc,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            if (selected) {
                                Box(Modifier.size(19.dp).clip(CircleShape).background(SwapPurple), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
                                }
                            } else {
                                Icon(Icons.Default.ChevronRight, null, tint = swapTextFaint, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Bandeau « Transactions sécurisées »
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SwapPurple.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, SwapPurple.copy(alpha = 0.30f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(SwapPurple.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Shield, null, tint = SwapPurple, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(stringResource(com.vaultex.R.string.swap_secure_title), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = swapText)
                        Text(stringResource(com.vaultex.R.string.swap_secure_desc), fontSize = 10.sp, color = swapTextDim, lineHeight = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

/**
 * Taux de change, LISIBLE quel que soit l'écart entre les deux monnaies.
 *
 * Le format à huit décimales affichait « 1 SHIB ≈ 0 ETH ». Ce n'était pas
 * faux au sens strict — le vrai taux vaut 0,0000000011 — mais un taux affiché
 * à zéro dans un écran d'échange dit exactement la mauvaise chose : que l'on
 * ne recevra rien.
 *
 * Sous un dix-millième, on INVERSE donc le sens de lecture : « 1 ETH ≈
 * 917 412 254 SHIB » porte la même information et se lit. C'est d'ailleurs
 * ainsi qu'on la formule spontanément entre une monnaie chère et une monnaie
 * de faible valeur unitaire.
 */
private fun tauxLisible(deTexte: String, versTexte: String, deMontant: Double, versMontant: Double): String? {
    // null, et non « — » : l'appelant fait alors disparaître la ligne au lieu
    // d'afficher un tiret en gras à la place de la donnée principale.
    if (deMontant <= 0.0 || versMontant <= 0.0) return null
    val taux = versMontant / deMontant
    if (taux >= 0.0001) {
        val v = String.format(java.util.Locale.US, "%.8f", taux).trimEnd('0').trimEnd('.')
        return "1 $deTexte ≈ $v $versTexte"
    }
    val inverse = deMontant / versMontant
    val v = String.format(java.util.Locale.US, "%,.0f", inverse)
    return "1 $versTexte ≈ $v $deTexte"
}

/** Ce que l'échange coûte : la somme, et sa part du montant envoyé. */
private data class CoutEchange(val usd: Double, val pourcent: Int)

/**
 * Écart entre la valeur envoyée et la valeur reçue — ou null si l'on ne peut
 * pas le calculer (montant vide, prix indisponible).
 *
 * Sur un petit montant, les frais de réseau du fournisseur peuvent dépasser
 * la moitié de la somme : l'écran affichait « ≈ 1,32 $ » d'un côté et
 * « ≈ 0,66 $ » de l'autre, sans un mot. Les deux chiffres étaient exacts et
 * la conclusion — la moitié part en frais — restait à la charge de qui
 * penserait à les comparer.
 *
 * LA SOMME EST AFFICHÉE, LA PROPORTION EST TEINTÉE. Un pourcentage se lit
 * comme un tarif, donc comme celui de VaultEx, alors que l'essentiel part en
 * frais de réseau ; c'est la somme que l'utilisateur compare à ce que lui
 * prendrait un changeur. La proportion reste calculée et décide de la couleur
 * de la ligne — elle signale sans chiffrer une accusation.
 *
 * Plus aucun seuil d'affichage : le chiffre est rendu dès qu'il existe. Une
 * ligne qui ne paraît qu'au-dessus de 10 % est une alarme, pas une mesure —
 * on ne peut pas juger « 26 % » sans avoir jamais vu à quoi ressemble un
 * échange ordinaire.
 *
 * Jamais négatif : les prix des deux monnaies viennent d'un instantané du
 * portefeuille, et un léger décalage entre les deux suffirait à annoncer un
 * gain là où il n'y en a pas.
 */
private fun coutEchange(state: com.vaultex.ui.viewmodel.SwapState, deMontant: Double, versMontant: Double): CoutEchange? {
    if (deMontant <= 0.0 || versMontant <= 0.0) return null
    if (state.fromPriceUsd <= 0.0 || state.toPriceUsd <= 0.0) return null
    val envoye = deMontant * state.fromPriceUsd
    val recu = versMontant * state.toPriceUsd
    if (envoye <= 0.0) return null
    return CoutEchange(
        usd = (envoye - recu).coerceAtLeast(0.0),
        pourcent = ((1.0 - recu / envoye) * 100.0).toInt().coerceAtLeast(0)
    )
}

/**
 * Somme en dollars, au format des deux lignes « ≈ 4,37 $ » de l'écran.
 *
 * En dessous d'un centime, « 0,00 $ » ferait croire à la gratuité : on écrit
 * « < 0,01 $ », qui est à la fois vrai et modeste.
 */
private fun sommeUsd(v: Double): String =
    if (v < 0.01) "< 0,01 $"
    else "≈ " + String.format(java.util.Locale.US, "%,.2f", v) + " $"

/** Au-delà, le coût mérite une couleur et un mot d'explication. */
private const val SEUIL_COUT_NOTABLE = 10

/** Au-delà, il mérite du rouge : l'échange coûte le quart de la somme. */
private const val SEUIL_COUT_ELEVE = 25

/**
 * Petit montant de jeton, LISIBLE — jamais arrondi à zéro.
 *
 * Le format à quatre décimales affichait « Frais inclus : 0 ETH » sur un swap
 * de 0,0007 ETH, dont les frais valent 0,0000105. Zéro était faux, et faux
 * dans le sens qui arrange : annoncer qu'on ne prélève rien quand on prélève
 * quelque chose.
 *
 * On garde donc assez de décimales pour que le premier chiffre significatif
 * apparaisse, plafonné à douze — au-delà, on lit du bruit.
 */
private fun montantLisible(valeur: Double): String {
    if (valeur <= 0.0) return "0"
    val decimales = when {
        valeur >= 1.0 -> 4
        else -> (kotlin.math.floor(-kotlin.math.log10(valeur)).toInt() + 4).coerceIn(4, 12)
    }
    return String.format(java.util.Locale.US, "%.${decimales}f", valeur)
        .trimEnd('0').trimEnd('.')
}
