package com.x8rgb.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.x8rgb.domain.model.RgbColor
import kotlin.math.roundToInt

/** HSV picker -> RgbColor. Hue bar + S/V panel + RGB sliders. */
@Composable
fun HsvColorPicker(
    initial: RgbColor,
    onColorChange: (RgbColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (h0, s0, v0) = rgbToHsv(initial.r, initial.g, initial.b)
    var hue by remember { mutableFloatStateOf(h0) }
    var sat by remember { mutableFloatStateOf(s0) }
    var value by remember { mutableFloatStateOf(v0) }

    fun emit() {
        val (r, g, b) = hsvToRgb(hue, sat, value)
        onColorChange(RgbColor(r, g, b))
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val preview = hsvToRgb(hue, sat, value)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(preview.first, preview.second, preview.third))
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "R=${preview.first}  G=${preview.second}  B=${preview.third}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        val hueColor = hsvToColor(hue, 1f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(hue) {
                    detectTapGestures { offset ->
                        sat = (offset.x / size.width).coerceIn(0f, 1f)
                        value = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }
                }
                .pointerInput(hue) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        sat = (change.position.x / size.width).coerceIn(0f, 1f)
                        value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val tx = sat * size.width
                val ty = (1f - value) * size.height
                drawCircle(Color.White, radius = 10f, center = Offset(tx, ty))
                drawCircle(Color.Black, radius = 10f, center = Offset(tx, ty), style = Stroke(width = 2f))
            }
        }

        Text("Hue", style = MaterialTheme.typography.labelMedium)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        hue = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                        emit()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                        emit()
                    }
                }
        ) {
            val colors = List(7) { i -> hsvToColor(i * 60f, 1f, 1f) }
            drawRect(Brush.horizontalGradient(colors))
            val hx = (hue / 360f) * size.width
            drawCircle(Color.White, radius = 12f, center = Offset(hx, size.height / 2))
            drawCircle(Color.Black, radius = 12f, center = Offset(hx, size.height / 2), style = Stroke(width = 2f))
        }

        val (r, g, b) = hsvToRgb(hue, sat, value)
        SliderRow("R", r / 255f) { f ->
            val nr = (f * 255).roundToInt()
            val (nh, ns, nv) = rgbToHsv(nr, g, b)
            hue = nh; sat = ns; value = nv; emit()
        }
        SliderRow("G", g / 255f) { f ->
            val ng = (f * 255).roundToInt()
            val (nh, ns, nv) = rgbToHsv(r, ng, b)
            hue = nh; sat = ns; value = nv; emit()
        }
        SliderRow("B", b / 255f) { f ->
            val nb = (f * 255).roundToInt()
            val (nh, ns, nv) = rgbToHsv(r, g, nb)
            hue = nh; sat = ns; value = nv; emit()
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(20.dp), style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, modifier = Modifier.weight(1f))
    }
}

private fun rgbToHsv(r: Int, g: Int, b: Int): Triple<Float, Float, Float> {
    val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
    val max = maxOf(rf, gf, bf); val min = minOf(rf, gf, bf)
    val d = max - min
    val h = when {
        d == 0f -> 0f
        max == rf -> 60f * (((gf - bf) / d) % 6f)
        max == gf -> 60f * (((bf - rf) / d) + 2f)
        else -> 60f * (((rf - gf) / d) + 4f)
    }.let { if (it < 0) it + 360f else it }
    val s = if (max == 0f) 0f else d / max
    return Triple(h, s, max)
}

private fun hsvToRgb(h: Float, s: Float, v: Float): Triple<Int, Int, Int> {
    val c = v * s
    val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
    val m = v - c
    val (rp, gp, bp) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Triple(
        ((rp + m) * 255).roundToInt().coerceIn(0, 255),
        ((gp + m) * 255).roundToInt().coerceIn(0, 255),
        ((bp + m) * 255).roundToInt().coerceIn(0, 255),
    )
}

private fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val (r, g, b) = hsvToRgb(h, s, v)
    return Color(r, g, b)
}
