package com.vaultex.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.core.session.NotifItem
import com.vaultex.ui.components.CryptoIcon
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.viewmodel.NotificationCenterViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(navController: NavHostController) {
    val viewModel: NotificationCenterViewModel = hiltViewModel()
    val items by viewModel.items.collectAsState()

    // À l'ouverture, on marque tout comme lu (la pastille disparaît).
    LaunchedEffect(Unit) {
        // Relire AVANT de marquer comme lu : sans cela, on marquerait une
        // liste perimee et l'arrivee recente serait perdue.
        viewModel.refresh()
        viewModel.markAllRead()
    }

    Scaffold(
        containerColor = BgPrimary,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Notifications", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Retour", tint = AccentBlue)
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clear() }) {
                            Text("Effacer", color = AccentBlue, fontSize = 13.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.NotificationsNone, null, tint = TextSecondary, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("Aucune notification", color = TextSecondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    /*
                    UNE NOTIFICATION MÈNE À CE DONT ELLE PARLE.

                    La liste était inerte : on lisait « CAKE en hausse » ou
                    « Vous avez reçu 0,01 SOL », et il fallait ressortir,
                    retrouver l'onglet Marché ou l'Historique, et chercher la
                    monnaie à la main. Une notification qui ne mène nulle part
                    oblige à refaire le chemin qu'elle vient de signaler.

                    Le sujet d'une notification, c'est toujours une MONNAIE :
                    celle qui monte, celle qu'on a reçue, celle qu'on a
                    envoyée. Sa fiche porte le cours, le solde détenu et
                    l'accès à l'historique — c'est la bonne destination dans
                    les trois cas.

                    Faute de monnaie identifiable — une annonce de
                    l'application, par exemple — la ligne reste inerte plutôt
                    que d'ouvrir quelque chose au hasard.
                    */
                    /*
                    UN MOUVEMENT DE FONDS MÈNE À SA TRANSACTION, LE RESTE À
                    LA MONNAIE.

                    Quand la notification porte un hash — un envoi, une
                    réception — c'est de CETTE opération qu'elle parle, et
                    l'écran de détail en donne le montant exact, le
                    destinataire, les frais et le lien vers l'explorateur.
                    Ouvrir la fiche de la monnaie obligerait à la retrouver
                    dans l'historique.

                    Sans hash — alerte de prix, annonce — le sujet reste la
                    monnaie, et sa fiche est la bonne destination.
                    */
                    val coinId = item.symbol
                        ?.let { com.vaultex.core.market.CoinIds.BY_SYMBOL[it.uppercase()] }
                    val destination = when {
                        !item.hash.isNullOrBlank() -> Routes.historyDetail(item.hash!!)
                        coinId != null -> Routes.coinDetail(coinId)
                        else -> null
                    }
                    NotifRow(item, destination?.let { { navController.navigate(it) } })
                }
            }
        }
    }
}

@Composable
private fun NotifRow(item: NotifItem, onClick: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        modifier = Modifier.fillMaxWidth().then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        )
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            // Logo crypto si dispo, sinon pastille bleue.
            Box(Modifier.size(40.dp).clip(CircleShape).background(AccentBlue.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                if (item.symbol != null) {
                    coil.compose.AsyncImage(
                        model = CryptoIcon.url(item.symbol),
                        contentDescription = item.symbol,
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                    )
                } else {
                    Icon(Icons.Default.NotificationsNone, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                Text(item.body, fontSize = 12.sp, color = TextSecondary)
                /*
                LA DATE SEULE, PUISQUE L'HEURE EST À DROITE.

                La maquette porte « 12:13 » en haut à droite ET
                « 25/09/2026 12:13 » en bas. Le même instant, écrit deux fois
                à trois centimètres d'écart : l'œil s'arrête dessus pour
                vérifier qu'il s'agit bien du même, et n'apprend rien.

                L'heure reste là où la maquette la place — c'est ce qu'on
                cherche sur une notification récente. La ligne du bas ne
                garde que la date, qui elle n'est écrite nulle part ailleurs.
                */
                Text(formatDate(item.timestamp), fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.7f))
            }
            /*
            LA PASTILLE « NON LU » DISPARAÎT.

            Elle ne survivait de toute façon pas à la seconde qui suit :
            l'écran marque tout comme lu dès son ouverture, donc elle ne se
            voyait qu'au premier rendu. Un point qui s'éteint sous les yeux
            n'informe de rien, et il occupait la place du chevron — le seul
            signe qui dise que la ligne mène quelque part.
            */
            /*
            HEURE EN HAUT, CHEVRON EN DESSOUS.

            L'heure est ce qu'on cherche d'abord sur une notification :
            « c'est de quand ? ». La placer à droite, alignée sur le titre,
            la rend lisible sans traverser le texte.

            Le chevron dessous dit que la ligne mène quelque part — et il
            n'apparaît que lorsque c'est vrai. Une notification sans sujet
            identifiable n'en porte pas, plutôt que de promettre une
            destination qui n'existe pas.
            */
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatHeure(item.timestamp),
                    fontSize = 12.sp,
                    color = TextSecondary.copy(alpha = 0.8f)
                )
                if (onClick != null) {
                    Spacer(Modifier.height(6.dp))
                    Icon(
                        Icons.Default.ChevronRight, null,
                        tint = TextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatHeure(ts: Long): String =
    SimpleDateFormat("HH:mm", com.vaultex.core.session.LocaleManager.appLocale()).format(Date(ts))

private fun formatDate(ts: Long): String =
    SimpleDateFormat("dd/MM/yyyy", com.vaultex.core.session.LocaleManager.appLocale()).format(Date(ts))
