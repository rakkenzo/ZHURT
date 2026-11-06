package kz.zhurt.messenger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

enum class Place(val label: String) { KZ("Қазақстан"), UZ("Oʻzbekiston"), TM("Türkmenistan"), KG("Кыргызстан") }

private val ink = Color(0xFF0D1712)
private val line = Color(0xFFE8C468).copy(alpha = 0.07f)

@Composable
fun Ornament(place: Place, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(ink)
            val step = 148f
            var y = 0f
            var row = 0
            while (y < size.height + step) {
                var x = if (row % 2 == 0) 0f else step / 2
                while (x < size.width + step) {
                    when (place) {
                        Place.KZ -> horn(Offset(x, y))
                        Place.UZ -> star(Offset(x, y))
                        Place.TM -> gul(Offset(x, y))
                        Place.KG -> knot(Offset(x, y))
                    }
                    x += step
                }
                y += step
                row++
            }
        }
        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.horn(c: Offset) {
    val p = Path()
    var a = 0.4f
    p.moveTo(c.x + 18f, c.y)
    for (i in 0..28) {
        val t = i / 4.2f
        p.lineTo(c.x + cos(t) * a, c.y + sin(t) * a)
        a += 1.6f
    }
    drawPath(p, line, style = Stroke(1.4f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.star(c: Offset) {
    val p = Path()
    for (i in 0 until 8) {
        val t = i * 3.14159f / 4f
        val r = if (i % 2 == 0) 22f else 10f
        val pt = Offset(c.x + cos(t) * r, c.y + sin(t) * r)
        if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
    }
    p.close()
    drawPath(p, line, style = Stroke(1.3f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.gul(c: Offset) {
    drawCircle(line, 16f, c, style = Stroke(1.3f))
    drawCircle(line, 7f, c, style = Stroke(1.1f))
    for (i in 0 until 8) {
        val t = i * 3.14159f / 4f
        drawCircle(line, 3.5f, Offset(c.x + cos(t) * 16f, c.y + sin(t) * 16f), style = Stroke(1f))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.knot(c: Offset) {
    drawLine(line, Offset(c.x - 16, c.y), Offset(c.x + 16, c.y), 1.2f)
    drawLine(line, Offset(c.x, c.y - 16), Offset(c.x, c.y + 16), 1.2f)
    drawCircle(line, 8f, c, style = Stroke(1.2f))
}
