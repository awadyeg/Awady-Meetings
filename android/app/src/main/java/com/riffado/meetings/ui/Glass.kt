package com.riffado.meetings.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Liquid Glass building blocks.
 *
 * Content that should be visible *through* floating chrome is marked with `Modifier.hazeSource(state)`;
 * floating surfaces use [glass], which renders a real backdrop blur (RenderEffect, Android 12+) of that
 * content, tinted and edged with a specular highlight. Below Android 12 Haze draws [AppleColors.glassFallback]
 * instead, a near-opaque frosted fill, so text on glass stays readable.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

enum class GlassStyle { Regular, Thin, Clear }

@Composable
fun glassHazeStyle(style: GlassStyle = GlassStyle.Regular): HazeStyle {
    val c = Apple.colors
    val tint = when (style) {
        GlassStyle.Regular -> c.glassTint
        GlassStyle.Thin -> c.glassTint.copy(alpha = c.glassTint.alpha * 0.6f)
        GlassStyle.Clear -> c.glassTint.copy(alpha = c.glassTint.alpha * 0.25f)
    }
    return HazeStyle(
        backgroundColor = c.background,
        tints = listOf(HazeTint(tint)),
        blurRadius = if (style == GlassStyle.Clear) 12.dp else 24.dp,
        noiseFactor = 0.04f,
        fallbackTint = HazeTint(c.glassFallback),
    )
}

/**
 * Frosted glass background: soft shadow, backdrop blur (or fallback), specular rim.
 * Apply *before* padding so the effect covers the whole shape.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    style: GlassStyle = GlassStyle.Regular,
    elevation: Dp = 12.dp,
    state: HazeState? = LocalHazeState.current,
): Modifier {
    val c = Apple.colors
    val haze = glassHazeStyle(style)
    val shadowColor = if (c.isDark) Color.Black else Color(0xFF1C1C3C)
    return this
        .then(
            if (elevation > 0.dp) {
                Modifier.shadow(elevation, shape, clip = false, ambientColor = shadowColor.copy(alpha = 0.18f), spotColor = shadowColor.copy(alpha = 0.22f))
            } else Modifier,
        )
        .clip(shape)
        .then(
            if (state != null) Modifier.hazeEffect(state = state, style = haze)
            else Modifier.background(c.glassFallback),
        )
        .specularEdge(shape)
}

/** In-content glass card (no backdrop blur, for performance in long lists), with the same rim treatment. */
@Composable
fun Modifier.glassCard(shape: Shape): Modifier {
    val c = Apple.colors
    val shadowColor = if (c.isDark) Color.Black else Color(0xFF1C1C3C)
    return this
        .shadow(if (c.isDark) 0.dp else 6.dp, shape, clip = false, ambientColor = shadowColor.copy(alpha = 0.08f), spotColor = shadowColor.copy(alpha = 0.10f))
        .clip(shape)
        .background(c.cardGlass)
        .specularEdge(shape, strength = 0.8f)
}

/**
 * Liquid Glass "specular" rim: a bright top-left edge fading to a faint lower edge,
 * plus a soft inner sheen across the top third of the surface.
 */
@Composable
fun Modifier.specularEdge(shape: Shape, strength: Float = 1f): Modifier {
    val c = Apple.colors
    val top = if (c.isDark) 0.38f else 0.95f
    val bottom = if (c.isDark) 0.08f else 0.35f
    val sheen = if (c.isDark) 0.06f else 0.22f
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val rim = Brush.linearGradient(
            0f to c.glassStroke.copy(alpha = top * strength),
            0.45f to c.glassStroke.copy(alpha = bottom * 0.5f * strength),
            1f to c.glassStroke.copy(alpha = bottom * strength),
            start = Offset.Zero,
            end = Offset(size.width * 0.35f, size.height),
        )
        val glow = Brush.verticalGradient(
            0f to Color.White.copy(alpha = sheen * strength),
            0.4f to Color.Transparent,
            startY = 0f,
            endY = size.height,
        )
        val strokeWidth = 1.dp.toPx()
        onDrawWithContent {
            drawContent()
            drawOutline(outline, glow)
            drawOutline(outline, rim, style = Stroke(width = strokeWidth))
        }
    }
}

/** Shrinks slightly with a spring while pressed, like iOS controls. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.94f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Round glass button holding an SF-Symbol-like icon. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = Apple.colors.label,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    Box(
        modifier
            .size(size)
            .pressScale(source)
            .glass(CircleShape, elevation = 6.dp)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button) { haptics.tap(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(iconSize))
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    style: GlassStyle = GlassStyle.Regular,
    elevation: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.glass(shape, style, elevation), content = content)
}

/**
 * Soft, colourful ambient backdrop (in the spirit of iOS 26 wallpapers) so that glass has something
 * to refract. Very low contrast so it never competes with content.
 */
@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier, accent: Color = Apple.colors.tint) {
    val c = Apple.colors
    val a = if (c.isDark) 0.22f else 0.16f
    Canvas(modifier.fillMaxSize().background(c.background)) {
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = a), Color.Transparent), center = Offset(size.width * 0.1f, size.height * 0.02f), radius = size.width * 0.9f),
            radius = size.width * 0.9f, center = Offset(size.width * 0.1f, size.height * 0.02f),
        )
        drawCircle(
            Brush.radialGradient(listOf(c.purple.copy(alpha = a * 0.8f), Color.Transparent), center = Offset(size.width * 0.95f, size.height * 0.18f), radius = size.width * 0.7f),
            radius = size.width * 0.7f, center = Offset(size.width * 0.95f, size.height * 0.18f),
        )
        drawCircle(
            Brush.radialGradient(listOf(c.teal.copy(alpha = a * 0.6f), Color.Transparent), center = Offset(size.width * 0.3f, size.height * 0.85f), radius = size.width * 0.8f),
            radius = size.width * 0.8f, center = Offset(size.width * 0.3f, size.height * 0.85f),
        )
    }
}

/** Thin wrapper over the platform haptic engine with iOS-like semantic names. */
class Haptics(private val view: View) {
    fun tap() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun selection() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.TEXT_HANDLE_MOVE else HapticFeedbackConstants.CLOCK_TICK,
        )
    }

    fun confirm() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
        )
    }

    fun heavy() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun reject() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
        )
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
