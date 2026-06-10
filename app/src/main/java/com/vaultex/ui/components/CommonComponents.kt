package com.vaultex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultex.R
import com.vaultex.core.crypto.Blockchain
import com.vaultex.ui.theme.*

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentGold,
            contentColor = BgPrimary,
            disabledContainerColor = AccentGold.copy(alpha = 0.3f)
        )
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentGold),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGold)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .border(0.5.dp, BorderColor, RoundedCornerShape(20.dp))
                .padding(20.dp),
            content = content
        )
    }
}

@Composable
fun NetworkBadge(blockchain: Blockchain, modifier: Modifier = Modifier) {
    val color = when (blockchain) {
        Blockchain.BITCOIN -> NetworkBtc
        Blockchain.ETHEREUM -> NetworkEth
        Blockchain.BNB_CHAIN -> NetworkBnb
        Blockchain.SOLANA -> NetworkSol
        Blockchain.TRON -> NetworkTrx
    }
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            blockchain.ticker,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun TokenIcon(
    symbol: String,
    blockchain: Blockchain,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    val networkColor = when (blockchain) {
        Blockchain.BITCOIN -> NetworkBtc
        Blockchain.ETHEREUM -> NetworkEth
        Blockchain.BNB_CHAIN -> NetworkBnb
        Blockchain.SOLANA -> NetworkSol
        Blockchain.TRON -> NetworkTrx
    }
    Box(
        modifier = Modifier
            .size(size)
            .background(
                Brush.linearGradient(listOf(networkColor, networkColor.copy(alpha = 0.6f))),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol.take(2).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.35f).sp
        )
    }
}

@Composable
fun BalanceDisplay(
    amount: String,
    currency: String = "USD",
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.total_balance),
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                amount,
                color = TextPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(6.dp))
            Text(
                currency,
                color = AccentGold,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
}

@Composable
fun GoldDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, AccentGold.copy(alpha = 0.5f), Color.Transparent)
                )
            )
    )
}
