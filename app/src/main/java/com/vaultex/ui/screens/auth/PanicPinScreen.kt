package com.vaultex.ui.screens.auth

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.PanicPinViewModel

private val P = Color(0xFF7C5CFC)
private val Danger = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanicPinScreen(navController: NavHostController) {
    val viewModel: PanicPinViewModel = hiltViewModel()
    val saved by viewModel.saved.collectAsState()
    val context = LocalContext.current

    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var check1 by remember { mutableStateOf(false) }
    var check2 by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf(false) }

    // Succès → toast + retour
    LaunchedEffect(saved) {
        if (saved == true) {
            android.widget.Toast.makeText(context, R.string.panic2_saved, android.widget.Toast.LENGTH_LONG).show()
            navController.popBackStack()
        }
    }

    val canActivate = check1 && check2 && pin.length == 6 && confirmPin.length == 6

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.panic_pin_title), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = P)
                    }
                },
                actions = {
                    Icon(Icons.Default.Shield, null, tint = P, modifier = Modifier.padding(end = 14.dp).size(20.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── En-tête : bouclier rouge + titre + description ───
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceColor, border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(Danger.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.WarningAmber, null, tint = Danger, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.panic_pin_title), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.panic_description).replace("\n", " "),
                            fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp
                        )
                    }
                }
            }

            // ─── Action irréversible (carte rouge) ───
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Danger.copy(alpha = 0.07f),
                border = BorderStroke(1.dp, Danger.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, null, tint = Danger, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.panic2_irr_title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Danger)
                        Spacer(Modifier.height(3.dp))
                        Text(stringResource(R.string.panic2_irr_body), fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(Danger.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Delete, null, tint = Danger, modifier = Modifier.size(22.dp))
                    }
                }
            }

            // ─── Avant d'activer, confirmez (2 cases OBLIGATOIRES) ───
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceColor, border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, tint = P, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.panic2_before), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                    }
                    Spacer(Modifier.height(6.dp))
                    ConfirmCheck(stringResource(R.string.panic2_check1), check1) { check1 = it }
                    HorizontalDivider(color = SurfaceLight)
                    ConfirmCheck(stringResource(R.string.panic2_check2), check2) { check2 = it }
                }
            }

            // ─── Définir / Confirmer le PIN ───
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceColor, border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.panic2_define), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = P)
                    PanicPinField(pin) { pin = it.filter { c -> c.isDigit() }.take(6); mismatch = false }
                    Text(stringResource(R.string.panic2_confirm), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = P)
                    PanicPinField(confirmPin) { confirmPin = it.filter { c -> c.isDigit() }.take(6); mismatch = false }
                    if (mismatch) {
                        Text(stringResource(R.string.panic_mismatch), fontSize = 12.sp, color = Danger)
                    }
                    if (saved == false) {
                        Text(stringResource(R.string.error_generic), fontSize = 12.sp, color = Danger)
                    }
                }
            }

            // ─── Bouton dégradé rouge → violet ───
            Box(
                Modifier.fillMaxWidth().height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (canActivate) Brush.horizontalGradient(listOf(Danger, Color(0xFFC2417B), P))
                        else Brush.horizontalGradient(listOf(Danger.copy(alpha = 0.35f), P.copy(alpha = 0.35f)))
                    )
                    .clickable(enabled = canActivate) {
                        if (pin == confirmPin) viewModel.savePin(pin) else mismatch = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.panic2_activate), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                }
            }

            // ─── Comment ça fonctionne ? ───
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceColor, border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = P, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.panic2_how), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                    }
                    Spacer(Modifier.height(8.dp))
                    HowRow(Icons.Default.Lock, stringResource(R.string.panic2_how1))
                    HorizontalDivider(color = SurfaceLight)
                    HowRow(Icons.Default.Delete, stringResource(R.string.panic2_how2))
                    HorizontalDivider(color = SurfaceLight)
                    HowRow(Icons.Default.Logout, stringResource(R.string.panic2_how3))
                    HorizontalDivider(color = SurfaceLight)
                    HowRow(Icons.Default.Shield, stringResource(R.string.panic2_how4))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ConfirmCheck(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked, onCheckedChange = onChecked,
            colors = CheckboxDefaults.colors(checkedColor = P, uncheckedColor = TextSecondary)
        )
        Text(label, fontSize = 13.sp, color = TextPrimary)
    }
}

/** Champ PIN masqué (points) avec œil afficher/masquer. */
@Composable
private fun PanicPinField(value: String, onValueChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(12.dp), color = BgTertiary, border = BorderStroke(1.dp, P.copy(alpha = 0.35f)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, tint = P, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = TextStyle(fontSize = 16.sp, letterSpacing = 6.sp, color = TextPrimary),
                cursorBrush = SolidColor(P),
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                null, tint = TextSecondary,
                modifier = Modifier.size(19.dp).clip(CircleShape).clickable { visible = !visible }
            )
        }
    }
}

@Composable
private fun HowRow(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
    }
}
