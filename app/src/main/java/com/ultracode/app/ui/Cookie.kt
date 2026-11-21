package com.ultracode.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Material You "cookie" shape: a circle with soft scalloped edges. */
class CookieShape(
    private val lobes: Int = 9,
    private val depth: Float = 0.075f,
    private val rotationDeg: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = min(cx, cy)
        val rot = rotationDeg / 180f * PI.toFloat() - PI.toFloat() / 2f
        val path = Path()
        val steps = 144
        for (i in 0..steps) {
            val t = 2f * PI.toFloat() * i / steps
            val rr = r * (1f - depth) + r * depth * cos(lobes * t)
            val x = cx + rr * cos(t + rot)
            val y = cy + rr * sin(t + rot)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

val Cookie4 = CookieShape(lobes = 4, depth = 0.11f, rotationDeg = 45f)
val Cookie6 = CookieShape(lobes = 6, depth = 0.09f)
val Cookie9 = CookieShape(lobes = 9, depth = 0.075f)
val Cookie12 = CookieShape(lobes = 12, depth = 0.05f)

/** A cookie-shaped button that spins a little when pressed. */
@Composable
fun CookieButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    lobes: Int = 9,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = contentColorFor(container),
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val rotation by animateFloatAsState(
        targetValue = if (pressed) 40f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "cookieRotation",
    )
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "cookieScale")
    val depth = when { lobes <= 4 -> 0.11f; lobes <= 6 -> 0.09f; lobes >= 12 -> 0.05f; else -> 0.075f }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CookieShape(lobes, depth, rotation),
        color = if (enabled) container else container.copy(alpha = 0.4f),
        contentColor = contentColor,
        interactionSource = source,
        modifier = modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Box(contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
        }
    }
}

/** Static cookie badge (avatar). */
@Composable
fun CookieBadge(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    lobes: Int = 9,
    container: Color = MaterialTheme.colorScheme.tertiaryContainer,
    content: @Composable () -> Unit,
) {
    Surface(shape = CookieShape(lobes), color = container, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
