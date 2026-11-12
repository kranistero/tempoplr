package app.tempo.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

val Ink = Color(0xFF070B13)
val Mint = Color(0xFF5EEAD4)
val Indigo = Color(0xFF6366F1)

private val Scheme = darkColorScheme(
    primary = Mint, onPrimary = Color(0xFF04201C), secondary = Color(0xFF7C9CFF),
    background = Ink, surface = Color(0xFF0E1524), surfaceVariant = Color(0xFF1A2236),
    surfaceContainer = Color(0xFF141B2D), surfaceContainerHigh = Color(0xFF182036),
    onBackground = Color.White, onSurface = Color.White,
    onSurfaceVariant = Color.White.copy(alpha = .68f)
)

@Composable
fun TempoTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Scheme, content = content)

@Composable
fun Art(url: String?, modifier: Modifier, corner: Dp = 14.dp) {
    Box(
        modifier.clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(Color(0xFF3B4A63), Color(0xFF1B2333))))
    ) {
        if (url != null) AsyncImage(
            model = url, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        ) else Icon(
            Icons.Rounded.MusicNote, null,
            Modifier.align(Alignment.Center), tint = Color.White.copy(alpha = .85f)
        )
    }
}

/** 2x2 collage of covers (single cover when fewer than 4 distinct ones). */
@Composable
fun Mosaic(urls: List<String?>, modifier: Modifier, corner: Dp = 16.dp) {
    val u = urls.filterNotNull().distinct()
    if (u.size < 4) { Art(u.firstOrNull(), modifier, corner); return }
    Column(modifier.clip(RoundedCornerShape(corner)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (r in 0..1) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (c in 0..1) Art(u[r * 2 + c], Modifier.weight(1f).fillMaxHeight(), 0.dp)
        }
    }
}
