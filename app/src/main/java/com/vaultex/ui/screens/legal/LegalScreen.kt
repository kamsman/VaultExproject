package com.vaultex.ui.screens.legal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary

/**
 * Affiche un document légal (CGU ou politique de confidentialité) embarqué dans
 * l'app (assets/*.md). Consultable HORS LIGNE : ces textes doivent rester
 * accessibles même sans réseau, notamment avant d'accepter les conditions.
 *
 * Rendu simple : le Markdown est mis en forme à la volée (titres, listes,
 * gras) sans dépendance externe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(navController: NavController, docType: String) {
    val context = LocalContext.current
    val isTerms = docType != "privacy"
    val assetName = if (isTerms) "cgu.md" else "confidentialite.md"

    val content by produceState(initialValue = "") {
        value = runCatching {
            context.assets.open(assetName).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(if (isTerms) R.string.legal_terms_title else R.string.legal_privacy_title),
                        fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary
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
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            if (content.isEmpty()) {
                Text(stringResource(R.string.legal_load_error), color = TextSecondary, fontSize = 13.sp)
            }
            content.lineSequence().forEach { raw ->
                val line = raw.trimEnd()
                when {
                    line.isBlank() -> Spacer(Modifier.height(8.dp))
                    line.startsWith("# ") -> {
                        Text(line.removePrefix("# ").clean(), color = TextPrimary,
                            fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                    }
                    line.startsWith("## ") -> {
                        Spacer(Modifier.height(10.dp))
                        Text(line.removePrefix("## ").clean(), color = AccentBlue,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                    }
                    line.startsWith("### ") -> {
                        Spacer(Modifier.height(6.dp))
                        Text(line.removePrefix("### ").clean(), color = TextPrimary,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    line.startsWith("---") -> {
                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(color = TextSecondary.copy(alpha = 0.2f))
                    }
                    line.startsWith("- ") || line.startsWith("* ") -> Text(
                        "•  " + line.drop(2).clean(),
                        color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp,
                        modifier = Modifier.padding(start = 6.dp, bottom = 3.dp)
                    )
                    // Ligne de tableau Markdown : affichée telle quelle, nettoyée.
                    line.startsWith("|") -> Text(
                        line.trim('|').split("|").joinToString(" · ") { it.trim() }.clean(),
                        color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    else -> Text(
                        line.clean(), color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Retire les marqueurs Markdown restants (gras, italique, séparateurs de tableau). */
private fun String.clean(): String =
    replace("**", "").replace("*", "").replace("`", "")
        .replace(Regex("^-{3,}$"), "").trim()
