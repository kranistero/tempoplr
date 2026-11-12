package app.tempo.player

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}
fun fmtSpeed(v: Float) = String.format(Locale.US, "%.2fx", v)

// ───────────────────────── cover colour scheme ─────────────────────────

data class CoverColors(val a: Color, val b: Color, val c: Color, val accent: Color)

val DefaultCover = CoverColors(Color(0xFF0E9AA7), Color(0xFF6366F1), Color(0xFF0B1730), Mint)
val LocalCover = compositionLocalOf { DefaultCover }

private fun hueOf(c: Int): Float { val h = FloatArray(3); android.graphics.Color.colorToHSV(c, h); return h[0] }
private fun hueDist(x: Float, y: Float): Float { val d = abs(x - y) % 360f; return min(d, 360f - d) }

private fun boost(c: Int): Int {
    val hsl = FloatArray(3); ColorUtils.colorToHSL(c, hsl)
    hsl[1] = hsl[1].coerceIn(.35f, .9f); hsl[2] = hsl[2].coerceIn(.28f, .58f)
    return ColorUtils.HSLToColor(hsl)
}
private fun darken(c: Int): Int {
    val hsl = FloatArray(3); ColorUtils.colorToHSL(c, hsl)
    hsl[2] = hsl[2].coerceAtMost(.14f)
    return ColorUtils.HSLToColor(hsl)
}

private suspend fun extractColors(ctx: Context, url: String): CoverColors? {
    return try {
        val req = ImageRequest.Builder(ctx).data(url).allowHardware(false).size(160).build()
        val bmp = (ctx.imageLoader.execute(req).drawable as? BitmapDrawable)?.bitmap ?: return null
        val p = Palette.from(bmp).maximumColorCount(24).generate()
        val dom = p.getDominantColor(0xFF1E6F8A.toInt())
        val vib = p.getVibrantColor(dom)
        val vibHue = hueOf(vib)
        val second = p.swatches.sortedByDescending { it.population }.take(6)
            .maxByOrNull { hueDist(hueOf(it.rgb), vibHue) + it.hsl[1] * 80f }?.rgb
            ?: p.getLightVibrantColor(vib)
        val dark = darken(p.getDarkMutedColor(p.getDarkVibrantColor(0xFF0B1730.toInt())))
        CoverColors(
            Color(boost(vib)), Color(boost(second)), Color(dark),
            Color(ColorUtils.blendARGB(second, 0xFFFFFFFF.toInt(), .5f))
        )
    } catch (e: Exception) { null }
}

/** Palette of the current cover, animated when the song changes. */
@Composable
fun rememberCoverColors(url: String?): CoverColors {
    val ctx = LocalContext.current
    var target by remember { mutableStateOf(DefaultCover) }
    LaunchedEffect(url) {
        target = if (url == null) DefaultCover
        else withContext(Dispatchers.IO) { extractColors(ctx, url) } ?: DefaultCover
    }
    val spec = tween<Color>(900)
    val a by animateColorAsState(target.a, spec, label = "ca")
    val b by animateColorAsState(target.b, spec, label = "cb")
    val c by animateColorAsState(target.c, spec, label = "cc")
    val acc by animateColorAsState(target.accent, spec, label = "cd")
    return CoverColors(a, b, c, acc)
}

// ───────────────────────── glass look ─────────────────────────

/** Translucent frosted-glass surface: tinted fill, soft top-left sheen and a bright specular rim. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(28.dp), strength: Float = 1f): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(listOf(Color.White.copy(alpha = .20f * strength), Color.White.copy(alpha = .06f * strength))),
        shape
    )
    .drawBehind {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = .20f * strength), Color.Transparent),
                center = Offset(size.width * .12f, 0f), radius = size.maxDimension * .85f
            )
        )
    }
    .border(
        1.dp,
        Brush.linearGradient(listOf(Color.White.copy(alpha = .70f), Color.White.copy(alpha = .06f), Color.White.copy(alpha = .30f))),
        shape
    )

/** Blurred cover + cover-coloured glows, like a lit-up background. */
@Composable
fun AuroraBackground(cover: CoverColors, modifier: Modifier = Modifier, artUrl: String? = null, intensity: Float = 1f) {
    Box(modifier.background(Ink)) {
        if (artUrl != null) AsyncImage(
            model = artUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(80.dp).alpha(.5f)
        )
        Box(
            Modifier.fillMaxSize().drawBehind {
                val w = size.width; val h = size.height
                drawRect(brush = Brush.radialGradient(listOf(cover.a.copy(alpha = .85f * intensity), Color.Transparent), Offset(0f, h * .08f), w * 1.15f))
                drawRect(brush = Brush.radialGradient(listOf(cover.b.copy(alpha = .85f * intensity), Color.Transparent), Offset(w, h * .16f), w * 1.15f))
                drawRect(brush = Brush.radialGradient(listOf(cover.c.copy(alpha = .95f * intensity), Color.Transparent), Offset(w * .5f, h), w * 1.4f))
            }
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(colorStops = arrayOf(0f to Color.Transparent, .55f to Color.Transparent, 1f to Ink.copy(alpha = .88f)))
            )
        )
    }
}

// ───────────────────────── small building blocks ─────────────────────────

@Composable
fun ScLogo(height: Dp = 20.dp, modifier: Modifier = Modifier, alpha: Float = 1f) =
    Image(painterResource(R.drawable.sc_logo), "SoundCloud", modifier.height(height), alpha = alpha)

@Composable
fun GlassPill(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    Row(
        modifier.glass(CircleShape).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp), content = content
    )

@Composable
fun SolidPill(text: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) =
    Row(
        Modifier.clip(CircleShape).background(Color.White.copy(alpha = if (enabled) .95f else .3f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = Color.Black)
        Text(text, color = Color.Black, fontWeight = FontWeight.SemiBold)
    }

@Composable
fun GlassCircle(size: Dp = 46.dp, onClick: () -> Unit, content: @Composable () -> Unit) =
    Box(Modifier.size(size).glass(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }

@Composable
fun GlassDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(32.dp)
        Column(
            Modifier.padding(20.dp).fillMaxWidth().clip(shape)
                .background(Color(0xFF0B1220).copy(alpha = .84f))
                .glass(shape).padding(22.dp),
            content = content
        )
    }
}

@Composable
fun glassFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
    cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    focusedPlaceholderColor = Color.White.copy(alpha = .5f), unfocusedPlaceholderColor = Color.White.copy(alpha = .5f),
    focusedLeadingIconColor = Color.White.copy(alpha = .8f), unfocusedLeadingIconColor = Color.White.copy(alpha = .8f),
    focusedTrailingIconColor = Color.White.copy(alpha = .8f), unfocusedTrailingIconColor = Color.White.copy(alpha = .8f)
)
