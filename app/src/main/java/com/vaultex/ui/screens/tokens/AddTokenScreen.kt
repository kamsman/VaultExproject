package com.vaultex.ui.screens.tokens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.vaultex.data.local.dao.TokenDao
import com.vaultex.data.local.entity.TokenEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.vaultex.ui.theme.*

@HiltViewModel
class AddTokenViewModel @Inject constructor(
    private val tokenDao: TokenDao
) : ViewModel() {

    fun addToken(
        contractAddress: String,
        symbol: String,
        name: String,
        decimals: Int,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            tokenDao.insert(
                TokenEntity(
                    contractAddress = contractAddress,
                    blockchain = "ETH",
                    symbol = symbol,
                    name = name,
                    decimals = decimals,
                    iconUrl = null,
                    isCustom = true,
                    isHidden = false
                )
            )
            onSuccess()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTokenScreen(navController: NavHostController) {

    val viewModel: AddTokenViewModel = hiltViewModel()

    var contractAddress by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var decimals by remember { mutableStateOf("18") }
    var error by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajouter un token ERC-20", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgPrimary, BgSecondary)))
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Text(
                "Importer un token personnalisé",
                color = AccentGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {

                    // Blockchain (read-only)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Blockchain", color = TextSecondary, fontSize = 13.sp)
                        Text("Ethereum (ETH)", color = NetworkEth, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    HorizontalDivider(color = BorderColor)

                    TokenField(
                        value = contractAddress,
                        onValueChange = { contractAddress = it; error = "" },
                        label = "Adresse du contrat",
                        placeholder = "0x..."
                    )

                    TokenField(
                        value = symbol,
                        onValueChange = { symbol = it.uppercase(); error = "" },
                        label = "Symbole",
                        placeholder = "ex: USDT"
                    )

                    TokenField(
                        value = name,
                        onValueChange = { name = it; error = "" },
                        label = "Nom du token",
                        placeholder = "ex: Tether USD"
                    )

                    TokenField(
                        value = decimals,
                        onValueChange = { decimals = it; error = "" },
                        label = "Décimales",
                        placeholder = "18",
                        keyboardType = KeyboardType.Number
                    )
                }
            }

            if (error.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(error, color = AccentRed, modifier = Modifier.padding(14.dp), fontSize = 13.sp)
                }
            }

            Button(
                onClick = {
                    when {
                        contractAddress.isBlank() -> error = "Adresse du contrat requise"
                        symbol.isBlank() -> error = "Symbole requis"
                        name.isBlank() -> error = "Nom requis"
                        decimals.toIntOrNull() == null -> error = "Décimales invalides"
                        else -> viewModel.addToken(
                            contractAddress.trim(),
                            symbol.trim(),
                            name.trim(),
                            decimals.toInt()
                        ) { navController.popBackStack() }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
            ) {
                Text("Ajouter le token", color = BgPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun TokenField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextSecondary, fontSize = 12.sp) },
        placeholder = { Text(placeholder, color = TextMuted, fontSize = 13.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentGold,
            unfocusedBorderColor = BorderColor,
            focusedContainerColor = BgPrimary,
            unfocusedContainerColor = BgPrimary,
            cursorColor = AccentGold,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        )
    )
}
