package br.com.spoilershield.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.spoilershield.ui.theme.Neon

/** Escudo com cadeado desenhado em Canvas (sem imagens de terceiros). */
@Composable
fun ShieldMark(size: Dp, color: Color = Neon.Red, pulse: Boolean = true, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(
        initialValue = if (pulse) 0.55f else 1f, targetValue = 1f, label = "alpha",
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
    )
    Canvas(modifier.size(size).alpha(a)) {
        val k = this.size.width / 24f
        val shield = Path().apply {
            moveTo(12f * k, 2f * k); lineTo(4f * k, 5f * k); lineTo(4f * k, 11f * k)
            cubicTo(4f * k, 16f * k, 7.4f * k, 20.7f * k, 12f * k, 22f * k)
            cubicTo(16.6f * k, 20.7f * k, 20f * k, 16f * k, 20f * k, 11f * k)
            lineTo(20f * k, 5f * k); close()
        }
        drawPath(shield, color.copy(alpha = 0.18f))
        drawPath(shield, color, style = Stroke(width = 1.4f * k))
        drawRect(color, Offset(8.6f * k, 11.2f * k), Size(6.8f * k, 5.4f * k))
        drawArc(color, 180f, 180f, false, Offset(9.6f * k, 8.2f * k), Size(4.8f * k, 5.6f * k), style = Stroke(1.1f * k))
    }
}

@Composable
fun NeonButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Neon.Red, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) { Text(text, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Neon.Cyan) {
    OutlinedButton(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = Neon.Red, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.6.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Neon.Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = modifier)
}

@Composable
fun DigitTile(value: Long, label: String) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp),
    ) {
        Text(value.toString().padStart(2, '0'), color = Neon.Text, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Text(label, color = Neon.Muted, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun StatusDot(ok: Boolean) {
    val c = if (ok) Neon.Green else Neon.Amber
    Canvas(Modifier.size(10.dp)) { drawCircle(c) }
}

@Composable
fun RowSpaced(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}
