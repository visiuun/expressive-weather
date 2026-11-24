@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.weather.ui.game

import android.content.Context
import androidx.compose.ui.res.stringResource
import dev.vis.weather.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Drop Dash: a secret mini-game. You're a raindrop falling through the sky. Drag to steer,
 * soak up droplets to grow and score, dodge sun flares (they shrink you) and lightning (game over).
 */
object DropDash {
    val codes = setOf("drop dash", "dropdash", "raindrop", "drizzle")
    fun best(context: Context) = context.getSharedPreferences("game", Context.MODE_PRIVATE).getInt("best", 0)
    fun saveBest(context: Context, v: Int) = context.getSharedPreferences("game", Context.MODE_PRIVATE).edit().putInt("best", v).apply()
}

private enum class Kind { DROPLET, SUN, BOLT }

private class Thing(var x: Float, var y: Float, var kind: Kind, var r: Float, var spin: Float, var alive: Boolean = true)

@Composable
fun DropDashScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    BackHandler(onBack = onExit)
    var running by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(DropDash.best(context)) }
    var frame by remember { mutableLongStateOf(0L) }
    var playerX by remember { mutableFloatStateOf(0.5f) }
    var drop by remember { mutableFloatStateOf(1f) }
    val things = remember { ArrayList<Thing>(64) }
    val stars = remember { List(60) { Offset(Random.nextFloat(), Random.nextFloat()) } }
    var speed by remember { mutableFloatStateOf(0.25f) }
    var scroll by remember { mutableFloatStateOf(0f) }
    var flash by remember { mutableFloatStateOf(0f) }

    fun reset() {
        things.clear(); score = 0; drop = 1f; speed = 0.25f; playerX = 0.5f; over = false; running = true
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        var spawn = 0f
        while (running) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
            speed = min(0.85f, speed + dt * 0.012f)
            scroll += dt * speed
            flash = max(0f, flash - dt * 3f)
            spawn -= dt
            if (spawn <= 0f) {
                spawn = 0.32f / (speed / 0.25f)
                val roll = Random.nextFloat()
                val kind = when {
                    roll < 0.62f -> Kind.DROPLET
                    roll < 0.86f -> Kind.SUN
                    else -> Kind.BOLT
                }
                things += Thing(Random.nextFloat() * 0.9f + 0.05f, 1.1f, kind, if (kind == Kind.DROPLET) 0.018f else 0.04f, Random.nextFloat() * 360f)
            }
            val px = playerX
            val py = 0.22f
            val pr = 0.03f * drop
            for (t in things) {
                if (!t.alive) continue
                t.y -= dt * speed * (if (t.kind == Kind.BOLT) 1.35f else 1f)
                t.spin += dt * 90f
                if (t.y < -0.1f) t.alive = false
                val dx = t.x - px
                val dy = (t.y - py) * 1.8f
                if (dx * dx + dy * dy < (pr + t.r) * (pr + t.r)) {
                    t.alive = false
                    when (t.kind) {
                        Kind.DROPLET -> { score += 1 + (drop * 2).toInt(); drop = min(2.4f, drop + 0.06f); haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
                        Kind.SUN -> { drop -= 0.35f; flash = 0.6f; haptics.performHapticFeedback(HapticFeedbackType.Reject); if (drop < 0.55f) { running = false; over = true } }
                        Kind.BOLT -> { flash = 1f; haptics.performHapticFeedback(HapticFeedbackType.LongPress); running = false; over = true }
                    }
                }
            }
            things.removeAll { !it.alive }
            if (over && score > best) { best = score; DropDash.saveBest(context, score) }
            frame = now
        }
    }

    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) { detectDragGestures { change, drag -> playerX = (playerX + drag.x / this.size.width * 1.1f).coerceIn(0.04f, 0.96f); change.consume() } }
                .pointerInput(Unit) { detectTapGestures { o -> playerX = (o.x / this.size.width).coerceIn(0.04f, 0.96f) } },
        ) {
            @Suppress("UNUSED_EXPRESSION") frame
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1730), Color(0xFF1B3A6B), Color(0xFF3C6FB0))))
            stars.forEach { s ->
                val y = ((s.y + scroll * 0.3f) % 1f)
                drawCircle(Color.White.copy(alpha = 0.35f), 1.5.dp.toPx(), Offset(s.x * size.width, (1f - y) * size.height))
            }
            things.forEach { t -> drawThing(t) }
            drawDrop(Offset(playerX * size.width, 0.22f * size.height), 0.03f * size.width * drop, cs.primary)
            if (flash > 0f) drawRect(Color.White.copy(alpha = flash * 0.6f))
        }
        Row(Modifier.statusBarsPadding().padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.s_game_exit), tint = Color.White) }
            Spacer(Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.35f)) {
                Text(stringResource(R.string.s_game_score_best, score, best), color = Color.White, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        if (!running) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (over) stringResource(R.string.s_game_over) else stringResource(R.string.s_game_title), style = MaterialTheme.typography.displaySmallEmphasized, color = Color.White)
                Text(
                    if (over) {
                        if (score >= best && score > 0) stringResource(R.string.s_game_score_new_best, score) else stringResource(R.string.s_game_score, score)
                    } else stringResource(R.string.s_game_intro),
                    style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Button(onClick = { reset() }) { Text(if (over) stringResource(R.string.s_game_play_again) else stringResource(R.string.s_game_start)) }
            }
        }
    }
}


private fun DrawScope.drawDrop(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r * 2.1f)
        cubicTo(c.x + r * 0.4f, c.y - r * 1.2f, c.x + r, c.y - r * 0.4f, c.x + r, c.y + r * 0.2f)
        cubicTo(c.x + r, c.y + r * 0.8f, c.x + r * 0.55f, c.y + r * 1.15f, c.x, c.y + r * 1.15f)
        cubicTo(c.x - r * 0.55f, c.y + r * 1.15f, c.x - r, c.y + r * 0.8f, c.x - r, c.y + r * 0.2f)
        cubicTo(c.x - r, c.y - r * 0.4f, c.x - r * 0.4f, c.y - r * 1.2f, c.x, c.y - r * 2.1f)
        close()
    }
    // Falling: the point faces up the screen.
    rotate(180f, c) {
        drawPath(p, Brush.verticalGradient(listOf(Color(0xFFBDE3FF), color), startY = c.y - r * 2f, endY = c.y + r))
        drawCircle(Color.White.copy(alpha = 0.7f), r * 0.22f, Offset(c.x - r * 0.35f, c.y + r * 0.1f))
    }
}

private fun DrawScope.drawThing(t: Thing) {
    val c = Offset(t.x * size.width, (1f - t.y) * size.height)
    val r = t.r * size.width
    when (t.kind) {
        Kind.DROPLET -> drawDrop(c, r, Color(0xFF6FB6FF))
        Kind.SUN -> rotate(t.spin, c) {
            for (i in 0 until 8) {
                val a = i * PI.toFloat() / 4f
                drawLine(Color(0xFFFFC94D), Offset(c.x + cos(a) * r * 0.9f, c.y + sin(a) * r * 0.9f), Offset(c.x + cos(a) * r * 1.5f, c.y + sin(a) * r * 1.5f), strokeWidth = r * 0.22f)
            }
            drawCircle(Color(0xFFFFB020), r * 0.75f, c)
            drawCircle(Color(0xFFFFE08A), r * 0.75f, c, style = Stroke(r * 0.12f))
        }
        Kind.BOLT -> {
            val p = Path().apply {
                moveTo(c.x + r * 0.2f, c.y - r * 1.4f)
                lineTo(c.x - r * 0.6f, c.y + r * 0.15f)
                lineTo(c.x - r * 0.05f, c.y + r * 0.15f)
                lineTo(c.x - r * 0.35f, c.y + r * 1.4f)
                lineTo(c.x + r * 0.65f, c.y - r * 0.25f)
                lineTo(c.x + r * 0.05f, c.y - r * 0.25f)
                close()
            }
            drawPath(p, Color(0xFFFFEB3B))
            drawPath(p, Color.White, style = Stroke(r * 0.08f))
        }
    }
}
