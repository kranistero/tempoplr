package app.tempo.player

import android.content.Context
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import kotlin.math.min
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}
fun fmtSpeed(v: Float) = String.format(Locale.US, "%.2fx", v)

// ═════════════════════ cover colours + readable text tone ═════════════════════

data class CoverColors(val a: Color, val b: Color, val c: Color)
data class Tone(val on: Color, val dim: Color, val light: Boolean)

val DarkTone = Tone(Color.White, Color.White.copy(alpha = .70f), false)          // white text (dark background)
private val LightTone = Tone(Color(0xFF0E1117), Color(0xFF0E1117).copy(alpha = .62f), true) // dark text (light background)

val DefaultCover = CoverColors(Color(0xFF0E9AA7), Color(0xFF6366F1), Color(0xFF0B1730))
val LocalCover = compositionLocalOf { DefaultCover }
val LocalTone = compositionLocalOf { DarkTone }

private fun lum(c: Color) = ColorUtils.calculateLuminance(c.toArgb())
private fun dist(x: Int, y: Int): Double {
    val dr = android.graphics.Color.red(x) - android.graphics.Color.red(y)
    val dg = android.graphics.Color.green(x) - android.graphics.Color.green(y)
    val db = android.graphics.Color.blue(x) - android.graphics.Color.blue(y)
    return (dr * dr + dg * dg + db * db).toDouble()
}

/** Keeps vivid covers rich, but leaves black / white / grey covers exactly grey. */
private fun tweak(c: Int): Color {
    val hsl = FloatArray(3); ColorUtils.colorToHSL(c, hsl)
    if (hsl[1] > .12f) { hsl[1] = hsl[1].coerceIn(.30f, .85f); hsl[2] = hsl[2].coerceIn(.12f, .88f) }
    return Color(ColorUtils.HSLToColor(hsl))
}

private suspend fun extractColors(ctx: Context, url: String): CoverColors? {
    return try {
        val req = ImageRequest.Builder(ctx).data(url).allowHardware(false).size(160).build()
        val bmp = (ctx.imageLoader.execute(req).drawable as? BitmapDrawable)?.bitmap ?: return null
        val p = Palette.from(bmp).maximumColorCount(24).generate()
        val sw = p.swatches.sortedByDescending { it.population }.take(6)
        if (sw.isEmpty()) return null
        val ai = sw.first().rgb
        val bi = sw.drop(1).maxByOrNull { dist(it.rgb, ai) }?.rgb ?: ai
        val a = tweak(ai); val b = tweak(bi)
        val m = lerp(a, b, .5f)
        val l = lum(m)
        val c = when {
            l < .28f -> lerp(m, Color.Black, .55f)   // dark cover → deep base
            l > .60f -> lerp(m, Color.Black, .06f)   // light cover → stays light
            else -> lerp(m, Color.Black, .28f)
        }
        CoverColors(a, b, c)
    } catch (e: Exception) { null }
}

private fun isLight(c: CoverColors): Boolean = lum(lerp(lerp(c.a, c.b, .5f), c.c, .45f)) > .28f

/** Palette of the current cover + matching text colours, both animated when the song changes. */
@Composable
fun rememberCover(url: String?): Pair<CoverColors, Tone> {
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
    val light = remember(target) { isLight(target) }
    val on by animateColorAsState(if (light) LightTone.on else DarkTone.on, tween(600), label = "on")
    val dim by animateColorAsState(if (light) LightTone.dim else DarkTone.dim, tween(600), label = "dim")
    return CoverColors(a, b, c) to Tone(on, dim, light)
}

// ═════════════════════ backdrop capture (what the glass looks through) ═════════════════════

class Backdrop(val layer: GraphicsLayer) { var coords: LayoutCoordinates? = null }
val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

@Composable
fun rememberBackdrop(): Backdrop {
    val l = rememberGraphicsLayer()
    return remember(l) { Backdrop(l) }
}

/** Marks everything inside as "the stuff behind the glass". */
fun Modifier.recordBackdrop(b: Backdrop): Modifier = this
    .onGloballyPositioned { b.coords = it }
    .drawWithContent {
        b.layer.record { this@drawWithContent.drawContent() }
        drawLayer(b.layer)
    }

// Lens refraction: near the rim the backdrop is bent (content from beyond the edge is pulled in)
// with a slight RGB split, after the backdrop has been blurred.
private const val AGSL = """
uniform shader content;
uniform float2 size;
uniform float2 shapeSize;
uniform float radius;
uniform float thickness;
uniform float strength;

float sdRR(float2 p, float2 hs, float r) {
    float2 q = abs(p) - hs + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

half4 main(float2 fc) {
    float2 p = fc - size * 0.5;
    float2 hs = shapeSize * 0.5;
    float r = min(radius, min(hs.x, hs.y));
    float d = sdRR(p, hs, r);
    float2 g = float2(
        sdRR(p + float2(1.0, 0.0), hs, r) - sdRR(p - float2(1.0, 0.0), hs, r),
        sdRR(p + float2(0.0, 1.0), hs, r) - sdRR(p - float2(0.0, 1.0), hs, r));
    float2 n = normalize(g + float2(0.0001));
    float t = clamp(1.0 + d / thickness, 0.0, 1.0);
    t = t * t * (3.0 - 2.0 * t);
    float2 off = n * (t * strength);
    float2 lo = float2(0.5);
    float2 hi = size - float2(0.5);
    half4 cr = content.eval(clamp(fc + off * 0.90, lo, hi));
    half4 cg = content.eval(clamp(fc + off, lo, hi));
    half4 cb = content.eval(clamp(fc + off * 1.10, lo, hi));
    return half4(cr.r, cg.g, cb.b, 1.0);
}
"""

private class GlassFx {
    var coords: LayoutCoordinates? = null
    private var shader: RuntimeShader? = null
    private var cached: ComposeRenderEffect? = null
    private var k = FloatArray(7) { -1f }

    fun effect(pw: Float, ph: Float, w: Float, h: Float, r: Float, blur: Float, thick: Float, strength: Float): ComposeRenderEffect? {
        val nk = floatArrayOf(pw, ph, w, h, r, blur, thick)
        if (nk.contentEquals(k)) return cached
        k = nk
        cached = try {
            val blurFx = AndroidRenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)
            val s = shader ?: RuntimeShader(AGSL).also { shader = it }
            s.setFloatUniform("size", pw, ph)
            s.setFloatUniform("shapeSize", w, h)
            s.setFloatUniform("radius", r)
            s.setFloatUniform("thickness", thick)
            s.setFloatUniform("strength", strength)
            AndroidRenderEffect.createChainEffect(
                AndroidRenderEffect.createRuntimeShaderEffect(s, "content"), blurFx
            ).asComposeRenderEffect()
        } catch (e: Throwable) {
            try { AndroidRenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP).asComposeRenderEffect() }
            catch (e2: Throwable) { null }
        }
        return cached
    }
}

/**
 * Liquid glass: blurs and refracts the live backdrop behind it, adds a soft tint, a sheen and a specular rim.
 * [smoke] darkens it (used where text must stay white), [selected] makes it brighter.
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(28.dp),
    strength: Float = 1f,
    smoke: Float = 0f,
    selected: Boolean = false,
    blurDp: Float = 14f
): Modifier = composed {
    val backdrop = LocalBackdrop.current
    val tone = LocalTone.current
    val fx = remember { GlassFx() }
    val layer = rememberGraphicsLayer()
    val lightBg = tone.light && smoke == 0f
    val base = if (lightBg) Color.Black.copy(alpha = .05f) else Color.White.copy(alpha = .06f)
    val selTint = if (lightBg) Color.Black.copy(alpha = .10f) else Color.White.copy(alpha = .16f)
    val rim = if (lightBg) listOf(Color.White.copy(alpha = .95f), Color.Black.copy(alpha = .06f), Color.White.copy(alpha = .55f))
    else listOf(Color.White.copy(alpha = .58f), Color.White.copy(alpha = .05f), Color.White.copy(alpha = .24f))

    this
        .onGloballyPositioned { fx.coords = it }
        .clip(shape)
        .drawBehind {
            val b = backdrop
            val gc = fx.coords
            val bc = b?.coords
            if (b != null && gc != null && bc != null && gc.isAttached && bc.isAttached && size.minDimension > 2f) {
                val pad = (blurDp * 2.2f * density).toInt()
                val w = size.width.toInt()
                val h = size.height.toInt()
                val off = gc.positionInRoot() - bc.positionInRoot()
                layer.record(size = IntSize(w + 2 * pad, h + 2 * pad)) {
                    translate(pad - off.x, pad - off.y) { drawLayer(b.layer) }
                }
                layer.topLeft = IntOffset(-pad, -pad)
                val r = (shape as? CornerBasedShape)?.topStart?.toPx(size, this) ?: 28.dp.toPx()
                val thick = min(size.minDimension / 2f, 20.dp.toPx())
                layer.renderEffect = fx.effect(
                    (w + 2 * pad).toFloat(), (h + 2 * pad).toFloat(), w.toFloat(), h.toFloat(),
                    r, blurDp * density, thick, thick * .85f * strength
                )
                drawLayer(layer)
            }
            drawRect(base)
            if (selected) drawRect(selTint)
            if (smoke > 0f) drawRect(Ink.copy(alpha = smoke))
            drawRect(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .12f * strength), Color.Transparent),
                    center = Offset(size.width * .12f, 0f), radius = size.maxDimension * .8f
                )
            )
        }
        .border(1.dp, Brush.linearGradient(rim), shape)
}

// ═════════════════════ background ═════════════════════

/** Cover-coloured glow background (grey for black/white covers) that slowly drifts. */
@Composable
fun AuroraBackground(cover: CoverColors, modifier: Modifier = Modifier, artUrl: String? = null) {
    val inf = rememberInfiniteTransition(label = "aurora")
    val phase by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse), label = "phase"
    )
    Box(modifier.background(cover.c)) {
        if (artUrl != null) AsyncImage(
            model = artUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(80.dp).alpha(.42f)
        )
        Box(
            Modifier.fillMaxSize().drawBehind {
                val w = size.width; val h = size.height; val p = phase
                drawRect(brush = Brush.radialGradient(listOf(cover.a.copy(alpha = .9f), Color.Transparent), Offset(w * (.02f + .14f * p), h * (.06f + .06f * p)), w * 1.15f))
                drawRect(brush = Brush.radialGradient(listOf(cover.b.copy(alpha = .9f), Color.Transparent), Offset(w * (.98f - .14f * p), h * (.20f - .06f * p)), w * 1.15f))
                drawRect(brush = Brush.radialGradient(listOf(lerp(cover.a, cover.b, .5f).copy(alpha = .55f), Color.Transparent), Offset(w * (.3f + .4f * p), h * .95f), w * 1.0f))
            }
        )
    }
}

// ═════════════════════ interaction + building blocks ═════════════════════

/** Springy press animation + click (no ripple, the glass itself reacts). */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) .92f else 1f, spring(dampingRatio = .5f, stiffness = 520f), label = "press")
    return this.graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun ScLogo(height: Dp = 20.dp, modifier: Modifier = Modifier, tint: Color = LocalTone.current.on) =
    Image(painterResource(R.drawable.sc_logo), "SoundCloud", modifier.height(height), colorFilter = ColorFilter.tint(tint))

@Composable
fun GlassCircle(size: Dp = 46.dp, onClick: () -> Unit, selected: Boolean = false, strength: Float = 1f, content: @Composable () -> Unit) =
    Box(
        Modifier.size(size).bounceClick(onClick = onClick).glass(CircleShape, strength, selected = selected),
        contentAlignment = Alignment.Center
    ) { content() }

@Composable
fun GlassPill(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, selected: Boolean = false, content: @Composable RowScope.() -> Unit) =
    Row(
        modifier.alpha(if (enabled) 1f else .4f).bounceClick(enabled, onClick).glass(CircleShape, selected = selected)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp), content = content
    )

@Composable
fun PillText(text: String) = Text(text, fontWeight = FontWeight.SemiBold)

@Composable
fun GlassDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(32.dp)
        val pop = remember { Animatable(0f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = .72f, stiffness = 380f)) }
        CompositionLocalProvider(LocalTone provides DarkTone, LocalContentColor provides Color.White, LocalBackdrop provides null) {
            Column(
                Modifier.graphicsLayer { val sc = .86f + .14f * pop.value; scaleX = sc; scaleY = sc; alpha = pop.value.coerceIn(0f, 1f) }
                    .padding(20.dp).fillMaxWidth().clip(shape)
                    .background(Color(0xFF0B1220).copy(alpha = .86f))
                    .glass(shape).padding(22.dp),
                content = content
            )
        }
    }
}

@Composable
fun glassFieldColors(): TextFieldColors {
    val t = LocalTone.current
    return TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        cursorColor = t.on, focusedTextColor = t.on, unfocusedTextColor = t.on,
        focusedPlaceholderColor = t.dim, unfocusedPlaceholderColor = t.dim,
        focusedLeadingIconColor = t.on, unfocusedLeadingIconColor = t.on,
        focusedTrailingIconColor = t.on, unfocusedTrailingIconColor = t.on
    )
}
