package com.vaultex.ui.screens.send

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vaultex.ui.components.DotsCircleLoader
import com.vaultex.ui.components.ExpandableDetails
import com.vaultex.ui.theme.SplashNavyBottom
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.core.security.BiometricHelper
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentGreen
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.CustomTokenLite
import com.vaultex.ui.viewmodel.SendViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(navController: NavController) {
    val viewModel: SendViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val customTokens by viewModel.customTokens.collectAsState()
    val pendingTxs by viewModel.pendingTxs.collectAsState()

    // P5 : préremplissage depuis un deep link déjà validé (chaîne + adresse).
    // Sinon, pré-sélection de la chaîne depuis la page d'une crypto (#4).
    LaunchedEffect(Unit) {
        // Dépôt de swap (vers l'adresse payin ChangeNOW) : chaîne + adresse + montant.
        val deposit = com.vaultex.core.session.SwapDepositBuffer.consume()
        val target = com.vaultex.core.session.DeepLinkBuffer.consume()
        if (deposit != null) {
            viewModel.setChain(deposit.chain)
            viewModel.setToAddress(deposit.address)
            viewModel.setAmount(deposit.amount)
        } else if (target != null) {
            viewModel.setChain(target.chain)
            viewModel.setToAddress(target.address)
        } else {
            com.vaultex.core.session.TokenSelectionBuffer.consume()?.let { sym ->
                // Chaîne native, USDT, OU token du registre d'échange : selectAsset
                // AUTO-ajoute le token (ERC-20/BEP-20) via son contrat connu s'il
                // n'est pas encore dans le portefeuille, puis le sélectionne.
                viewModel.selectAsset(sym)
            }
        }
    }

    // Adresse renvoyée par le scanner QR
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    LaunchedEffect(savedStateHandle) {
        savedStateHandle?.getStateFlow<String?>(com.vaultex.ui.screens.scanner.SCANNED_ADDRESS_KEY, null)
            ?.collect { scanned ->
                if (!scanned.isNullOrBlank()) {
                    viewModel.setToAddress(scanned)
                    savedStateHandle.remove<String>(com.vaultex.ui.screens.scanner.SCANNED_ADDRESS_KEY)
                }
            }
    }
    val context = LocalContext.current as FragmentActivity
    val biometricHelper = remember { BiometricHelper(context) }
    val haptic = LocalHapticFeedback.current
    var showConfirm by remember { mutableStateOf(false) }
    var showCoinPicker by remember { mutableStateOf(false) }

    // Retour haptique de confirmation quand la transaction part (ou est mise en file)
    LaunchedEffect(state.txHash) {
        if (state.txHash != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(state.queued) {
        if (state.queued) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Regroupement par RÉSEAU (blockchain) : chaque réseau liste toutes ses
    // monnaies (natives + tokens personnalisés ajoutés par contrat).
    val networks = buildSendNetworks(customTokens)
    val selectedNetworkKey = networks.firstOrNull { state.selectedChain in it.coins }?.key
        ?: networks.first().key

    // ─ Données d'affichage « pro » (titre, conversions fiat, récapitulatif) ─
    val coinShort = coinLabel(state.selectedChain)                          // ex: USDT
    val coinTitle = coinTitleOf(state.selectedChain, state.customToken)     // ex: USDT (TRC20)
    val netFull = networkFullLabel(state.selectedChain, state.customToken)  // ex: TRC20 · Tron
    val isNative = state.selectedChain in listOf("BTC", "ETH", "BNB", "SOL", "TRX")

    val price = state.priceSelected
    val amountNum = state.amount.replace(",", ".").toDoubleOrNull() ?: 0.0
    val availNum = state.availableBalance?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
    val feeNum = state.feeNativeAmount ?: 0.0
    val svcFee = state.serviceFeeAmount          // frais de service VaultEx (BTC), même actif

    fun fiat(v: Double): String? =
        if (v > 0.0) com.vaultex.core.util.CurrencyFormat.format(v, state.currency) else null
    val availFiat = if (price > 0.0) fiat(availNum * price) else null
    val amountFiat = if (price > 0.0) fiat(amountNum * price) else null
    val feeFiat = if (state.priceNative > 0.0) fiat(feeNum * state.priceNative) else null
    val svcFeeFiat = if (svcFee > 0.0 && price > 0.0) fiat(svcFee * price) else null
    // Total déduit : montant + frais réseau (si natif, même actif) + frais de
    // service VaultEx (BTC). Pour un token, les frais réseau sont en natif.
    val totalToken = (if (isNative) amountNum + feeNum else amountNum) + svcFee
    val totalFiatValue = totalToken * price
    val totalFiat = if (price > 0.0) fiat(totalFiatValue) else null

    // Frais réseau réel (gas live), calculé par chaîne dans le ViewModel.
    val feeEstimate = state.estimatedFee

    // Hors-ligne : transaction mise en file
    if (state.queued) {
        AlertDialog(
            onDismissRequest = { viewModel.reset(); navController.popBackStack() },
            icon = { Icon(Icons.Default.Schedule, null, tint = AccentBlue) },
            title = { Text(stringResource(R.string.send_queued_title)) },
            text = { Text(stringResource(R.string.send_queued_body), fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { viewModel.reset(); navController.popBackStack() }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    // Données partagées par les écrans Confirmation / Traitement / Succès.
    val detail = SendDetail(
        coinShort = coinShort,
        coinName = coinFullName(state.selectedChain, state.customToken),
        toAddress = state.toAddress,
        netFull = netFull,
        amount = state.amount.ifEmpty { "0" },
        amountFiat = amountFiat,
        feeNative = feeEstimate,
        feeFiat = feeFiat,
        totalToken = formatTokenAmount(totalToken) + " " + coinShort,
        totalFiat = totalFiat,
        isNewRecipient = state.newRecipient
    )

    /*
    ─── APRÈS LA DIFFUSION : « ENVOYÉE », JAMAIS « PATIENTEZ » ─────────────
    L'ancien flux gardait l'utilisateur sur un écran d'attente tant que la
    blockchain n'avait pas confirmé — soit 10 à 30 minutes sur Bitcoin. Rien
    ne le justifiait : la transaction est DÉJÀ partie, elle est irréversible,
    et l'application ne peut rien faire de plus.

    Désormais, dès que le réseau accepte la transaction, l'écran annonce
    « Transaction envoyée » et affiche le statut réel juste en dessous :
    « En attente de confirmation » en ambre, qui bascule en « Confirmée » en
    vert si la confirmation tombe pendant que l'écran est ouvert.

    L'utilisateur part quand il veut. La confirmation le rattrape par une
    notification, et l'historique passe de 🟡 à 🟢 tout seul.
    ───────────────────────────────────────────────────────────────────────
     */
    if (state.txHash != null) {
        val hash = state.txHash!!
        val pending = pendingTxs.firstOrNull { it.hash == hash }
        SendSuccessScreen(
            detail = detail,
            txHash = hash,
            explorerName = explorerName(state.selectedChain, state.customToken),
            explorerUrl = explorerUrl(state.selectedChain, state.customToken, hash),
            // null = plus aucun suivi en cours → la transaction est confirmée.
            confirmed = pending == null || pending.confirmed,
            confirmations = pending?.confirmations ?: 0,
            target = pending?.target ?: 0,
            onShare = { shareReceipt(context, detail, hash) },
            onDone = { viewModel.reset(); navController.popBackStack() }
        )
        return
    }

    // ── ÉCRAN TRAITEMENT (diffusion réseau) ──
    if (state.isLoading) {
        SendProcessingScreen(detail)
        return
    }

    // ── ÉCRAN CONFIRMATION (plein écran) ──
    if (showConfirm) {
        SendConfirmScreen(
            detail = detail,
            onCancel = { showConfirm = false },
            onConfirm = {
                showConfirm = false
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                // m-05 : ré-authentification OBLIGATOIRE avant un envoi.
                val bioStatus = biometricHelper.checkAvailability()
                if (bioStatus == BiometricHelper.BiometricStatus.AVAILABLE ||
                    biometricHelper.canUseDeviceCredential()
                ) {
                    biometricHelper.authenticateStrongOrCredential(
                        title = context.getString(R.string.send_biometric_title),
                        subtitle = context.getString(
                            R.string.send_biometric_subtitle,
                            state.amount, state.selectedChain, state.toAddress.take(12)
                        ),
                        onSuccess = { viewModel.send() },
                        onError = { _, _ -> /* annulé/erreur — reste sur l'écran */ }
                    )
                } else {
                    viewModel.send()
                }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("${stringResource(R.string.send_title)} $coinTitle", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        bottomBar = {
            // « Continuer » + message d'erreur épinglés en bas → toujours visibles
            // sans avoir à scroller (au-dessus de la barre système).
            Column(
                Modifier.background(BgPrimary).fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.error != null) {
                    Surface(shape = RoundedCornerShape(12.dp), color = AccentRed.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, null, tint = AccentRed, modifier = Modifier.size(18.dp))
                            Text(state.error!!, fontSize = 13.sp, color = AccentRed)
                        }
                    }
                }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showConfirm = true
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = state.isAddressValid && state.amount.isNotEmpty() && !state.isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.White)
                ) {
                    if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(stringResource(R.string.continue_btn), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            val coinColor = runCatching { Color(android.graphics.Color.parseColor(coinColorHex(coinShort))) }.getOrDefault(AccentGreen)
            val coinBadge = coinTitle.substringAfter("(", "").removeSuffix(")").ifBlank { null }

            // ── Carte monnaie + solde (touchez pour changer de monnaie) ──
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceColor,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                onClick = {
                    // Revenir à l'écran de sélection de monnaie (réseau + liste).
                    if (!navController.popBackStack(Routes.SEND_SELECT, false)) showCoinPicker = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(coinColor), contentAlignment = Alignment.Center) {
                        Text(coinShort.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        coil.compose.AsyncImage(
                            model = com.vaultex.ui.components.CryptoIcon.url(coinShort),
                            contentDescription = coinShort,
                            modifier = Modifier.size(44.dp).clip(CircleShape)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(coinShort, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                            if (coinBadge != null) {
                                Surface(shape = RoundedCornerShape(5.dp), color = AccentGreen.copy(alpha = 0.14f)) {
                                    Text(coinBadge, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        fontSize = 10.sp, color = AccentGreen, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Icon(Icons.Default.ExpandMore, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                        Text(coinFullName(state.selectedChain, state.customToken), fontSize = 12.sp, color = TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.send_balance_short), fontSize = 11.sp, color = TextSecondary)
                        Text((state.availableBalance ?: "—") + " " + coinShort, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                        availFiat?.let { Text("≈ $it", fontSize = 11.sp, color = TextSecondary) }
                    }
                }
            }

            /*
            LES INTITULÉS DE SECTION SORTENT DES CARTES.

            « Destinataire » et « Montant » étaient de petits textes gris posés
            À L'INTÉRIEUR de leur carte, au-dessus du champ. Rien ne les
            distinguait alors du contenu qu'ils annonçaient.

            Placés au-dessus, sur le fond, en texte plein, ils redeviennent ce
            qu'ils sont : la structure de l'écran. On voit d'un coup d'œil
            qu'il y a deux choses à renseigner.
            */
            Text(
                stringResource(R.string.send_recipient_label),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
            )
            SendCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SendField(
                        value = state.toAddress,
                        onValueChange = { viewModel.setToAddress(it) },
                        placeholder = when (state.selectedChain) {
                            "BTC"          -> stringResource(R.string.send_address_placeholder_btc)
                            "ETH", "BNB"   -> "0x..."
                            "TRX", "USDT"  -> "T..."
                            "SOL"          -> stringResource(R.string.send_address_placeholder_sol)
                            else           -> stringResource(R.string.send_address_placeholder_generic, state.selectedChain)
                        },
                        isError = state.toAddress.isNotEmpty() && !state.isAddressValid,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgTertiary)
                            .clickable { navController.navigate(Routes.SCANNER) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = stringResource(R.string.qr_label),
                            tint = AccentBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                if (state.toAddress.isNotEmpty() && !state.isAddressValid) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.send_invalid_address, state.selectedChain),
                        fontSize = 12.sp,
                        color = AccentRed
                    )
                }
                // ⚠️ « Address poisoning » : l'adresse saisie RESSEMBLE à une
                // adresse connue (carnet / envois passés) sans être identique —
                // signature typique d'une adresse-piège copiée de l'historique.
                if (state.poisonWarning) {
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = AccentRed.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = AccentRed, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.send_poison_warning),
                                fontSize = 12.sp, color = AccentRed,
                                fontWeight = FontWeight.SemiBold, lineHeight = 16.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "📒 " + stringResource(R.string.address_book),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentGreen,
                    modifier = Modifier.clickable { navController.navigate(Routes.ADDRESS_BOOK) }
                )
            }
            /*
            La consigne de vérification sort de la carte et perd son pavé vert.

            Un aplat coloré pleine largeur a le poids d'une alerte. Or ce n'est
            pas une alerte : c'est un rappel permanent, affiché même quand tout
            va bien. À force d'occuper la place d'un avertissement sans en être
            un, il rendait invisibles les VRAIS — adresse invalide, adresse
            ressemblante — qui s'affichent au même endroit, en rouge.
            */
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.send_address_network_note, netFull),
                    fontSize = 12.sp, color = TextSecondary
                )
                // L'avertissement « le destinataire doit accepter le réseau… »
                // a été retiré : le bandeau vert ci-dessus porte déjà la même
                // consigne de vérification du réseau. Deux alertes qui disent
                // la même chose n'en font lire aucune.
            }

            Text(
                stringResource(R.string.amount),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
            )
            SendCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SendField(
                        value = state.amount,
                        onValueChange = { viewModel.setAmount(it) },
                        placeholder = "0.00",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f)
                    )
                    // MAX prend la couleur d'action de l'application. En vert,
                    // il se lisait comme une confirmation — or il n'en est pas
                    // une : c'est un bouton, il doit inviter à être touché.
                    Text(
                        stringResource(R.string.max_label),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentBlue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentBlue.copy(alpha = 0.14f))
                            .clickable { viewModel.onMaxClicked() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    // Trait vertical : la monnaie qui suit n'est pas un bouton,
                    // elle rappelle seulement l'unité du montant saisi.
                    Box(Modifier.height(22.dp).width(1.dp).background(BorderColor))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(coinColor), contentAlignment = Alignment.Center) {
                            coil.compose.AsyncImage(
                                model = com.vaultex.ui.components.CryptoIcon.url(coinShort),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp).clip(CircleShape)
                            )
                        }
                        Text(coinShort, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                    }
                }
                if (amountFiat != null) {
                    Spacer(Modifier.height(6.dp))
                    Text("≈ $amountFiat", fontSize = 12.sp, color = TextSecondary)
                }
            }

            // Avertissement montant sous le minimum réseau
            if (state.dustWarning != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = androidx.compose.ui.graphics.Color(0xFFFFF3CD),
                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFE6AC00)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = androidx.compose.ui.graphics.Color(0xFFB07800), modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.send_dust_warning, state.dustWarning!!),
                            fontSize = 13.sp,
                            color = androidx.compose.ui.graphics.Color(0xFF7A5200)
                        )
                    }
                }
            }

            // « Solde disponible » retiré : la carte du haut porte déjà le solde
            // de la monnaie choisie. Le répéter n'apprend rien et allonge l'écran.
            // Frais réseau (ligne)
            SendInfoRow(
                stringResource(R.string.send_summary_fee),
                feeEstimate.ifEmpty { "…" },
                feeFiat?.let { "≈ $it" }
            )
            /*
            LE TOTAL SORT DE SA CARTE VERTE.

            Un aplat vert pleine largeur dit « tout va bien » ; or cette ligne
            ne rassure pas, elle ENGAGE — c'est le montant qui va réellement
            quitter le portefeuille. Une ligne nue, séparée d'un trait et dont
            seule la valeur est colorée, se lit comme un total et non comme une
            confirmation déjà acquise.
            */
            if (svcFee > 0.0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.send_service_fee), fontSize = 13.sp, color = TextSecondary)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatTokenAmount(svcFee) + " $coinShort", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                        svcFeeFiat?.let { Text("≈ $it", fontSize = 11.sp, color = TextSecondary) }
                    }
                }
            }
            HorizontalDivider(color = BorderColor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.send_summary_total),
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatTokenAmount(totalToken) + " $coinShort", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentGreen)
                    totalFiat?.let { Text("≈ $it", fontSize = 11.sp, color = TextSecondary) }
                }
            }
            if (!isNative) {
                Text(
                    stringResource(R.string.send_summary_fee_note, nativeFeeUnit(state.selectedChain, state.customToken)),
                    fontSize = 11.sp, color = TextSecondary
                )
            }
            // Bloc « Conseil de sécurité » retiré : troisième rappel du même
            // écran demandant de vérifier adresse et réseau. Le bandeau vert
            // sous le destinataire le dit déjà, à l'endroit où ça compte.

            Spacer(Modifier.height(4.dp))
        }

        // Sélecteur de monnaie (feuille du bas). La monnaie se choisit ici —
        // plus de chips dans le formulaire, conformément à la maquette.
        if (showCoinPicker) {
            ModalBottomSheet(onDismissRequest = { showCoinPicker = false }, containerColor = BgPrimary) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(stringResource(R.string.send_choose_coin), fontWeight = FontWeight.Bold,
                        fontSize = 16.sp, color = TextPrimary, modifier = Modifier.padding(bottom = 6.dp))
                    networks.forEach { net ->
                        if (net.coins.isNotEmpty()) {
                            Text(net.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                color = TextSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                            net.coins.forEach { coin ->
                                val c = customTokens.firstOrNull { it.symbol == coin }
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.setChain(coin); showCoinPicker = false }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier.size(32.dp).clip(CircleShape)
                                            .background(runCatching { Color(android.graphics.Color.parseColor(coinColorHex(coinLabel(coin)))) }.getOrDefault(AccentGreen)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        coil.compose.AsyncImage(
                                            model = com.vaultex.ui.components.CryptoIcon.url(coinLabel(coin)),
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(coinTitleOf(coin, c), fontSize = 14.sp, color = TextPrimary,
                                        fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    if (state.selectedChain == coin)
                                        Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SendInfoRow(label: String, value: String, sub: String?) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                sub?.let { Text(it, fontSize = 11.sp, color = TextSecondary) }
            }
        }
    }
}

@Composable
internal fun SendTokenChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) AccentBlue else BgTertiary
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else TextPrimary
        )
    }
}

@Composable
internal fun SendCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
internal fun SendField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceLight)
            .border(
                1.dp,
                if (isError) AccentRed else BorderColor,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        singleLine = true,
        enabled = enabled,
        textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
        cursorBrush = SolidColor(AccentBlue),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 14.sp, color = TextMuted)
                }
                inner()
            }
        }
    )
}

@Composable
private fun ConfirmRow(label: String, value: String, emphasize: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold,
            color = if (emphasize) AccentBlue else TextPrimary
        )
    }
}

private fun shortenAddress(a: String): String =
    if (a.length <= 14) a else "${a.take(8)}…${a.takeLast(6)}"

@Composable
private fun SummaryRow(label: String, value: String, sub: String? = null, emphasize: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            Text(
                value,
                fontSize = if (emphasize) 15.sp else 13.sp,
                fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold,
                color = if (emphasize) AccentBlue else TextPrimary
            )
            if (sub != null) Text(sub, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

/** Titre de la monnaie avec sa norme : « USDT (TRC20) », « SHIB (ERC20) »… */
private fun coinTitleOf(chain: String, custom: CustomTokenLite?): String = when {
    custom != null -> "${custom.symbol} (${if (custom.blockchain == "BNB") "BEP20" else "ERC20"})"
    chain == "USDT" -> "USDT (TRC20)"
    chain == "USDT-ETH" -> "USDT (ERC20)"
    chain == "USDT-BNB" -> "USDT (BEP20)"
    else -> chain
}

/** Réseau complet : « TRC20 · Tron », « ERC20 · Ethereum », « Bitcoin »… */
private fun networkFullLabel(chain: String, custom: CustomTokenLite?): String = when {
    custom != null -> if (custom.blockchain == "BNB") "BEP20 · BNB Chain" else "ERC20 · Ethereum"
    chain == "BTC" -> "Bitcoin"
    chain == "ETH" -> "Ethereum"
    chain == "BNB" -> "BNB Chain"
    chain == "SOL" -> "Solana"
    chain == "TRX" -> "Tron"
    chain == "USDT" -> "TRC20 · Tron"
    chain == "USDT-ETH" -> "ERC20 · Ethereum"
    chain == "USDT-BNB" -> "BEP20 · BNB Chain"
    else -> chain
}

/** Monnaie dans laquelle les frais (gas) sont payés pour un token. */
private fun nativeFeeUnit(chain: String, custom: CustomTokenLite?): String = when {
    custom != null -> if (custom.blockchain == "BNB") "BNB" else "ETH"
    chain == "USDT-ETH" -> "ETH"
    chain == "USDT-BNB" -> "BNB"
    chain == "USDT" -> "TRX"
    else -> chain
}

/** Affiche un montant de token sans zéros superflus (max 8 décimales). */
private fun formatTokenAmount(v: Double): String =
    java.math.BigDecimal.valueOf(v)
        .setScale(8, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

/** Un réseau (blockchain) et toutes les monnaies qui lui sont liées. */
private data class SendNetworkUi(val key: String, val label: String, val coins: List<String>)

/**
 * Construit la liste des réseaux pour l'écran Envoyer. Chaque réseau regroupe
 * ses monnaies natives, ses USDT, et les tokens personnalisés ajoutés par
 * contrat sur cette chaîne (ETH ou BNB).
 */
private fun buildSendNetworks(custom: List<CustomTokenLite>): List<SendNetworkUi> = listOf(
    SendNetworkUi("BTC", "Bitcoin", listOf("BTC")),
    SendNetworkUi(
        "ETH", "Ethereum",
        listOf("ETH", "USDT-ETH") + custom.filter { it.blockchain == "ETH" }.map { it.symbol }
    ),
    SendNetworkUi(
        "BNB", "BNB Chain",
        listOf("BNB", "USDT-BNB") + custom.filter { it.blockchain == "BNB" }.map { it.symbol }
    ),
    SendNetworkUi("SOL", "Solana", listOf("SOL")),
    SendNetworkUi("TRX", "Tron", listOf("TRX", "USDT"))
)

/** Étiquette courte d'une monnaie (le réseau étant déjà affiché au-dessus). */
private fun coinLabel(sym: String): String = when (sym) {
    "USDT-ETH", "USDT-BNB" -> "USDT"
    else -> sym
}

// ══════════════════════════════════════════════════════════════════════
//  Flux d'envoi « pro » : Confirmation → Traitement → Succès (plein écran)
// ══════════════════════════════════════════════════════════════════════

/** Détails d'une transaction d'envoi, partagés par les 3 écrans de statut. */
internal data class SendDetail(
    val coinShort: String,
    val coinName: String,
    val toAddress: String,
    val netFull: String,
    val amount: String,
    val amountFiat: String?,
    val feeNative: String,
    val feeFiat: String?,
    val totalToken: String,
    val totalFiat: String?,
    /** Aucun envoi passé ni contact enregistré vers cette adresse. */
    val isNewRecipient: Boolean = false
)

private fun coinFullName(chain: String, custom: CustomTokenLite?): String = when {
    custom != null -> custom.symbol
    chain == "USDT" || chain == "USDT-ETH" || chain == "USDT-BNB" -> "Tether USD"
    chain == "BTC" -> "Bitcoin"
    chain == "ETH" -> "Ethereum"
    chain == "BNB" -> "BNB"
    chain == "SOL" -> "Solana"
    chain == "TRX" -> "Tron"
    else -> chain
}

private fun coinColorHex(coinShort: String): String = when (coinShort) {
    "USDT" -> "#26A17B"
    "BTC" -> "#F7931A"
    "ETH" -> "#627EEA"
    "BNB" -> "#F0B90B"
    "SOL" -> "#9945FF"
    "TRX" -> "#FF060A"
    else -> "#3B82F6"
}

private fun explorerName(chain: String, custom: CustomTokenLite?): String {
    val evm = custom?.blockchain
    return when {
        evm == "BNB" || chain == "BNB" || chain == "USDT-BNB" -> "BscScan"
        evm == "ETH" || chain == "ETH" || chain == "USDT-ETH" -> "Etherscan"
        chain == "BTC" -> "Blockstream"
        chain == "SOL" -> "Solscan"
        chain == "TRX" || chain == "USDT" -> "Tronscan"
        else -> "Explorer"
    }
}

private fun explorerUrl(chain: String, custom: CustomTokenLite?, hash: String): String {
    val evm = custom?.blockchain
    val base = when {
        evm == "BNB" || chain == "BNB" || chain == "USDT-BNB" -> "https://bscscan.com/tx/"
        evm == "ETH" || chain == "ETH" || chain == "USDT-ETH" -> "https://etherscan.io/tx/"
        chain == "BTC" -> "https://blockstream.info/tx/"
        chain == "SOL" -> "https://solscan.io/tx/"
        chain == "TRX" || chain == "USDT" -> "https://tronscan.org/#/transaction/"
        else -> "https://etherscan.io/tx/"
    }
    return base + hash
}

private fun shareReceipt(context: android.content.Context, d: SendDetail, hash: String) {
    val text = buildString {
        appendLine("VaultEx — Reçu de transaction")
        appendLine("Montant : ${d.amount} ${d.coinShort}")
        appendLine("Destinataire : ${d.toAddress}")
        appendLine("Réseau : ${d.netFull}")
        appendLine("Frais : ${d.feeNative}")
        appendLine("Hash : $hash")
    }
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
    }, null))
}

private fun shorten(a: String): String =
    if (a.length <= 18) a else "${a.take(10)}…${a.takeLast(6)}"

/** Carte récap (icône monnaie + montant, Destinataire, Réseau, Frais, [Total]). */
@Composable
private fun SendDetailCard(detail: SendDetail, showTotal: Boolean, onCopyAddress: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(4.dp)) {
            // Ligne monnaie + montant
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(runCatching { Color(android.graphics.Color.parseColor(coinColorHex(detail.coinShort))) }.getOrDefault(AccentBlue)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(detail.coinShort.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    coil.compose.AsyncImage(
                        model = com.vaultex.ui.components.CryptoIcon.url(detail.coinShort),
                        contentDescription = detail.coinShort,
                        modifier = Modifier.size(42.dp).clip(CircleShape)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(detail.coinShort, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    Text(detail.coinName, fontSize = 12.sp, color = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${detail.amount} ${detail.coinShort}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    detail.amountFiat?.let { Text(it, fontSize = 11.sp, color = TextSecondary) }
                }
            }
            HorizontalDivider(color = BorderColor)
            DetailLine(Icons.Default.Send, stringResource(R.string.send_recipient_label), shorten(detail.toAddress),
                onCopy = onCopyAddress)
            HorizontalDivider(color = BorderColor)
            DetailLine(Icons.Default.Hub, stringResource(R.string.send_summary_network), detail.netFull)
            HorizontalDivider(color = BorderColor)
            DetailLine(Icons.Default.CreditCard, stringResource(R.string.send_summary_fee), detail.feeNative.ifEmpty { "…" }, sub = detail.feeFiat)
            if (showTotal) {
                HorizontalDivider(color = BorderColor)
                DetailLine(Icons.Default.AccountBalanceWallet, stringResource(R.string.send_summary_total), detail.totalToken, sub = detail.totalFiat, emphasize = true)
            }
        }
    }
}

@Composable
private fun DetailLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    sub: String? = null,
    emphasize: Boolean = false,
    onCopy: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(BgTertiary), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(value, fontSize = 14.sp, fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold, color = if (emphasize) AccentBlue else TextPrimary)
            sub?.let { Text(it, fontSize = 11.sp, color = TextSecondary) }
        }
        if (onCopy != null) {
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Default.ContentCopy, null, tint = AccentBlue,
                modifier = Modifier.size(20.dp).clickable { onCopy() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendStatusScaffold(title: String, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
private fun StatusHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    ringColor: Color, title: String, subtitle: String, titleColor: Color,
    animated: Boolean = false
) {
    Box(Modifier.padding(top = 8.dp).size(132.dp), contentAlignment = Alignment.Center) {
        if (animated) {
            // Anneau pointillé fixe + arc qui tourne en continu.
            val transition = rememberInfiniteTransition(label = "ring")
            val angle by transition.animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(animation = tween(1300, easing = LinearEasing)),
                label = "angle"
            )
            Canvas(Modifier.size(132.dp)) {
                val stroke = 5.dp.toPx()
                val inset = stroke * 1.5f
                drawCircle(
                    color = ringColor.copy(alpha = 0.22f),
                    radius = size.minDimension / 2 - inset / 2,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 12f))
                    )
                )
                rotate(angle) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f, sweepAngle = 85f, useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }
            }
        } else {
            Box(Modifier.size(120.dp).clip(CircleShape).background(ringColor.copy(alpha = 0.12f)))
        }
        Box(Modifier.size(88.dp).clip(CircleShape).background(ringColor), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(46.dp))
        }
    }
    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = titleColor)
    Text(subtitle, fontSize = 14.sp, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

private fun composeColor(hex: String, fallback: Long = 0xFF3B82F6): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(fallback))

/** Cercle d'icône (gris, ou vert si mis en avant). */
@Composable
private fun ConfirmIconCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, highlight: Boolean) {
    Box(
        Modifier.size(38.dp).clip(CircleShape)
            .background(if (highlight) AccentGreen.copy(alpha = 0.15f) else BgTertiary),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (highlight) AccentGreen else TextSecondary, modifier = Modifier.size(18.dp))
    }
}

/** Carte « label en haut, valeur en dessous (à gauche) », option copie à droite. */
@Composable
private fun ConfirmRowLeft(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String, value: String, onCopy: (() -> Unit)? = null, trailingBadge: String? = null
) {
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ConfirmIconCircle(icon, false)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, color = TextSecondary)
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
            if (trailingBadge != null) {
                Surface(shape = RoundedCornerShape(5.dp), color = AccentGreen.copy(alpha = 0.14f)) {
                    Text(trailingBadge, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        fontSize = 10.sp, color = AccentGreen, fontWeight = FontWeight.SemiBold)
                }
            }
            if (onCopy != null) {
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Default.ContentCopy, null, tint = AccentBlue, modifier = Modifier.size(20.dp).clickable { onCopy() })
            }
        }
    }
}

/** Carte « label à gauche, valeur + sous-valeur à droite », option vert. */
@Composable
private fun ConfirmRowRight(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String, value: String, sub: String? = null, highlight: Boolean = false
) {
    Surface(shape = RoundedCornerShape(14.dp),
        color = if (highlight) AccentGreen.copy(alpha = 0.10f) else SurfaceColor,
        border = if (highlight) null else androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ConfirmIconCircle(icon, highlight)
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (highlight) AccentGreen else TextPrimary)
                sub?.let { Text(it, fontSize = 11.sp, color = TextSecondary) }
            }
        }
    }
}

/**
 * Adresse du destinataire à la confirmation — affichée ENTIÈRE.
 *
 * Le raccourci « 10 premiers … 6 derniers » qui était utilisé ici est
 * précisément ce que l'« address poisoning » exploite : l'attaquant génère par
 * force brute une adresse dont le DÉBUT et la FIN sont identiques à celle
 * attendue, puis la glisse dans l'historique ou le presse-papiers. Un affichage
 * tronqué montre alors exactement la partie que l'attaquant maîtrise et cache
 * le milieu, seul endroit où la substitution se voit.
 *
 * On affiche donc l'adresse complète, en chasse fixe et découpée en groupes de
 * quatre caractères — un format que l'œil peut réellement comparer, contrairement
 * à une longue chaîne continue.
 */
@Composable
private fun RecipientAddressCard(address: String, isNew: Boolean, onCopy: () -> Unit) {
    // Adresse affichée TELLE QUELLE, d'un seul tenant. Le découpage en blocs
    // de 4 essayé précédemment rendait l'adresse illisible et faussait même la
    // lecture (« 0xf1 7f18 » pour une adresse qui commence par 0xf17f) —
    // retiré à la demande du test réel. La chasse fixe suffit : elle garde les
    // caractères alignés et comparables, l'anti-poisoning reste assuré par
    // l'affichage COMPLET (pas tronqué) + l'alerte sosie + l'alerte premier envoi.
    Surface(
        shape = RoundedCornerShape(14.dp), color = SurfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConfirmIconCircle(Icons.Default.Person, false)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.send_recipient_label),
                    fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ContentCopy, null, tint = AccentBlue,
                    modifier = Modifier.size(20.dp).clickable { onCopy() }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                address,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = TextPrimary
            )
            // Premier envoi vers cette adresse : c'est le seul moment où une
            // substitution (presse-papiers détourné, adresse empoisonnée) peut
            // passer inaperçue — aucun historique ne permet de la contredire.
            if (isNew) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, null, tint = Color(0xFFE6AC00), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.send_new_recipient_warning),
                        fontSize = 11.sp, lineHeight = 15.sp, color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
internal fun SendConfirmScreen(detail: SendDetail, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val badge = if (detail.netFull.contains("·")) detail.netFull.substringBefore("·").trim() else null
    SendStatusScaffold(stringResource(R.string.send_confirm_title)) {
        // Carte monnaie + montant (verte, mise en avant)
        Surface(shape = RoundedCornerShape(14.dp), color = AccentGreen.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(composeColor(coinColorHex(detail.coinShort))), contentAlignment = Alignment.Center) {
                    coil.compose.AsyncImage(model = com.vaultex.ui.components.CryptoIcon.url(detail.coinShort),
                        contentDescription = detail.coinShort, modifier = Modifier.size(42.dp).clip(CircleShape))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(detail.coinShort, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        if (badge != null) Surface(shape = RoundedCornerShape(5.dp), color = AccentGreen.copy(alpha = 0.14f)) {
                            Text(badge, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp), fontSize = 10.sp, color = AccentGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(detail.coinName, fontSize = 12.sp, color = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${detail.amount} ${detail.coinShort}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    detail.amountFiat?.let { Text("≈ $it", fontSize = 11.sp, color = TextSecondary) }
                }
            }
        }
        RecipientAddressCard(
            address = detail.toAddress,
            isNew = detail.isNewRecipient,
            onCopy = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(detail.toAddress)) }
        )
        /*
        HIÉRARCHIE DE L'ÉCRAN. L'utilisateur décide sur trois choses : combien
        part, vers qui, et combien au total lui sera débité. Ces trois-là
        restent visibles SANS défilement — le reste (réseau, détail des frais,
        délai) sert à vérifier, pas à décider, et se range derrière
        « Voir détails ».

        Le défilement est l'ennemi ici : il repousse le bouton de confirmation
        hors de l'écran et noie l'essentiel dans le secondaire.
         */
        ConfirmRowRight(
            Icons.Default.AccountBalanceWallet,
            stringResource(R.string.send_confirm_total_deducted),
            detail.totalToken, detail.totalFiat?.let { "≈ $it" }, highlight = true
        )
        ExpandableDetails(
            accent = AccentBlue,
            labelColor = TextSecondary,
            // Résumé visible même replié : le montant des frais est la seule
            // information secondaire qui puisse faire renoncer à l'envoi.
            summary = if (detail.feeNative.isEmpty()) null
                else stringResource(R.string.send_summary_fee) + " : " + detail.feeNative
        ) {
            ConfirmRowLeft(Icons.Default.Send, stringResource(R.string.send_summary_network), detail.netFull, trailingBadge = badge)
            Spacer(Modifier.height(8.dp))
            ConfirmRowRight(Icons.Default.CreditCard, stringResource(R.string.amount), "${detail.amount} ${detail.coinShort}", detail.amountFiat?.let { "≈ $it" })
            Spacer(Modifier.height(8.dp))
            ConfirmRowRight(Icons.Default.AccountBalanceWallet, stringResource(R.string.send_summary_fee), detail.feeNative.ifEmpty { "…" }, detail.feeFiat?.let { "≈ $it" })
            Spacer(Modifier.height(8.dp))
            ConfirmRowRight(Icons.Default.Schedule, stringResource(R.string.send_confirm_eta_label), stringResource(R.string.send_confirm_eta_value), stringResource(R.string.send_confirm_eta_sub))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, BorderColor)
            ) { Text(stringResource(R.string.cancel), color = TextPrimary, fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) { Text(stringResource(R.string.send_confirm_cta), color = Color.White, fontWeight = FontWeight.Bold) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VerifiedUser, null, tint = AccentGreen, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.send_confirm_secured), fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
internal fun SendProcessingScreen(detail: SendDetail) {
    SendStatusScaffold(stringResource(R.string.send_processing_appbar, detail.coinShort)) {
        StatusHeader(
            Icons.Default.HourglassEmpty, AccentBlue,
            stringResource(R.string.send_processing_title),
            stringResource(R.string.send_processing_subtitle),
            TextPrimary,
            animated = true
        )
        SendDetailCard(detail, showTotal = true)
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = AccentBlue, trackColor = BgTertiary
        )
        Text(stringResource(R.string.send_processing_broadcasting), fontSize = 13.sp, color = TextSecondary)
        Surface(shape = RoundedCornerShape(12.dp), color = AccentBlue.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(stringResource(R.string.send_processing_dont_close_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(stringResource(R.string.send_processing_dont_close_body), fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

/*
─── LIGNES DES ÉCRANS DE STATUT (maquette) ──────────────────────────────────
Une seule carte, des lignes toutes bâties pareil : pastille ronde à gauche,
libellé, valeur alignée à droite avec son équivalent en devise juste dessous.
L'œil descend une seule colonne de valeurs au lieu de sauter d'un encadré à
l'autre — c'est ce qui rend ces écrans lisibles d'un coup.
─────────────────────────────────────────────────────────────────────────────
 */
@Composable
private fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    sub: String? = null,
    onCopy: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(BgTertiary),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            sub?.let { Text(it, fontSize = 12.sp, color = TextSecondary) }
        }
        if (onCopy != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.ContentCopy, null, tint = AccentBlue,
                modifier = Modifier.size(18.dp).clickable { onCopy() }
            )
        }
    }
}

/** Carte des écrans de statut : fond uni, coins arrondis, lignes séparées. */
@Composable
private fun StatusCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}


@Composable
internal fun SendSuccessScreen(
    detail: SendDetail,
    txHash: String,
    explorerName: String,
    explorerUrl: String,
    confirmed: Boolean,
    confirmations: Int,
    target: Int,
    onShare: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val orange = Color(0xFFF59E0B)
    val sentAt = remember {
        java.text.SimpleDateFormat("d MMM yyyy • HH:mm", com.vaultex.core.session.LocaleManager.appLocale()).format(java.util.Date())
    }
    SendStatusScaffold(stringResource(R.string.send_processing_appbar, detail.coinShort)) {
        // Le titre reste « envoyée » dans les deux cas : c'est un fait acquis
        // dès que le réseau a accepté la transaction. Seul le STATUT évolue.
        StatusHeader(
            Icons.Default.CheckCircle, AccentGreen,
            stringResource(R.string.send_success_title),
            stringResource(R.string.send_success_subtitle),
            AccentGreen
        )
        // Pastille de statut : ambre tant que la blockchain n'a pas tranché,
        // verte ensuite. Elle bascule toute seule si la confirmation tombe
        // pendant que l'écran est ouvert.
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = (if (confirmed) AccentGreen else orange).copy(alpha = 0.14f)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (confirmed) Icons.Default.CheckCircle else Icons.Default.Schedule,
                    null,
                    tint = if (confirmed) AccentGreen else orange,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (confirmed) stringResource(R.string.send_status_confirmed)
                    else if (target > 0) stringResource(R.string.send_status_awaiting_count, confirmations, target)
                    else stringResource(R.string.send_status_awaiting),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (confirmed) AccentGreen else orange
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        /*
        L'encadre « statut : confirmee » a ete retire : le grand rond vert et
        le titre le disent deja. Restent deux cartes — la transaction, puis sa
        trace (date et identifiant).
         */
        StatusCard {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(composeColor(coinColorHex(detail.coinShort))),
                    contentAlignment = Alignment.Center
                ) {
                    coil.compose.AsyncImage(
                        model = com.vaultex.ui.components.CryptoIcon.url(detail.coinShort),
                        contentDescription = detail.coinShort,
                        modifier = Modifier.size(38.dp).clip(CircleShape)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(detail.coinShort, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    Text(detail.coinName, fontSize = 12.sp, color = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${detail.amount} ${detail.coinShort}", fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, color = TextPrimary)
                    detail.amountFiat?.let { Text(it, fontSize = 12.sp, color = TextSecondary) }
                }
            }
            HorizontalDivider(color = BorderColor)
            StatusRow(
                Icons.Default.Send, stringResource(R.string.send_recipient_label),
                shorten(detail.toAddress),
                onCopy = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(detail.toAddress)) }
            )
            HorizontalDivider(color = BorderColor)
            StatusRow(
                Icons.Default.Hub, stringResource(R.string.send_summary_network),
                detail.netFull
            )
            HorizontalDivider(color = BorderColor)
            StatusRow(
                Icons.Default.AccountBalanceWallet, stringResource(R.string.send_summary_fee),
                detail.feeNative.ifEmpty { "…" }, detail.feeFiat
            )
        }
        StatusCard {
            StatusRow(
                Icons.Default.Schedule, stringResource(R.string.send_success_date), sentAt
            )
            HorizontalDivider(color = BorderColor)
            StatusRow(
                Icons.Default.Receipt, stringResource(R.string.send_success_txid),
                shorten(txHash),
                onCopy = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(txHash)) }
            )
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(explorerUrl))) } },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentBlue),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
        ) {
            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.send_success_view_on, explorerName), fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
        ) {
            Text(stringResource(R.string.send_success_done), color = Color.White,
                fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        // Rassure sur le fait qu'on peut partir : c'est l'app qui reviendra.
        if (!confirmed) {
            Text(
                stringResource(R.string.send_status_leave_hint),
                fontSize = 11.sp, color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
