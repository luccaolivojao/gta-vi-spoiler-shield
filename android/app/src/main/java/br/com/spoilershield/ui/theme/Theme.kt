package br.com.spoilershield.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object Neon {
    val Black = Color(0xFF050507)
    val Surface = Color(0xFF0E0E12)
    val Card = Color(0xFF15151B)
    val Red = Color(0xFFFF1744)
    val RedDeep = Color(0xFF8E0A22)
    val Cyan = Color(0xFF00E5FF)
    val Green = Color(0xFF00E676)
    val Amber = Color(0xFFFFB300)
    val Text = Color(0xFFF2F2F5)
    val Muted = Color(0xFF9A9AA8)
}

@Composable
fun ShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Neon.Red,
            onPrimary = Color.White,
            secondary = Neon.Cyan,
            background = Neon.Black,
            surface = Neon.Surface,
            surfaceVariant = Neon.Card,
            onSurface = Neon.Text,
            onBackground = Neon.Text,
            error = Neon.Red,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Neon.Black) { content() }
    }
}

/** Cartão escuro com contorno neon e um leve brilho. */
@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    glow: Color = Neon.Red,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(glow.copy(alpha = 0.10f), Color.Transparent)))
            }
            .border(BorderStroke(1.dp, glow.copy(alpha = 0.55f)), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Neon.Card),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
