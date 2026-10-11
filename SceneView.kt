package com.nagi.hustle

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val SKINS = intArrayOf(0xFF6B4423.toInt(), 0xFF8D5524.toInt(), 0xFF5A3A22.toInt(), 0xFFA0673A.toInt(), 0xFF3E2A1C.toInt())
private val SHIRTS = intArrayOf(
    0xFFE4572E.toInt(), 0xFF29A19C.toInt(), 0xFFF3A712.toInt(), 0xFF4D7EA8.toInt(),
    0xFFA663CC.toInt(), 0xFF59A96A.toInt(), 0xFFD7263D.toInt(), 0xFFEFEFEF.toInt()
)
private val PANTS = intArrayOf(0xFF22303C.toInt(), 0xFF3B3B3B.toInt(), 0xFF2F4858.toInt(), 0xFF5B4B3A.toInt())

private fun lerp(a: Int, b: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    val ar = (a shr 16) and 255
    val ag = (a shr 8) and 255
    val ab = a and 255
    val br = (b shr 16) and 255
    val bg = (b shr 8) and 255
    val bb = b and 255
    val r = (ar + (br - ar) * k).toInt()
    val g = (ag + (bg - ag) * k).toInt()
    val bl = (ab + (bb - ab) * k).toInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
}

/** The picture of the city: sky, buildings, street, vehicles, and the people in the area. */
class SceneView(context: Context) : View(context) {

    class Other(val id: Int, val name: String)

    var hour = 8f
    var area = 1
    var rain = false
    var dark = false
    var busy = false
    var meId = 0
    var meName = ""
    var others: List<Other> = emptyList()

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val path = Path()
    private val t0 = System.nanoTime()
    private val starX = FloatArray(45)
    private val starY = FloatArray(45)

    init {
        val rnd = Random(11)
        for (i in starX.indices) {
            starX[i] = rnd.nextFloat()
            starY[i] = rnd.nextFloat() * 0.5f
        }
    }

    private fun clock(): Float = ((System.nanoTime() - t0) / 1.0e9).toFloat()

    private fun daylight(): Float {
        val x = (hour - 6f) / 12f
        if (x <= 0f || x >= 1f) return 0f
        val s = sin(x * PI).toFloat()
        return min(1f, max(0f, s) * 1.5f)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val tm = clock()
        val day = daylight()
        drawSky(c, w, h, day, tm)
        drawSkyline(c, w, h, day, tm)
        drawStreet(c, w, h, day, tm)
        drawPeople(c, w, h, tm)
        if (rain) drawRain(c, w, h, tm)
        if (dark) {
            p.shader = null
            p.color = 0x55000014
            c.drawRect(0f, 0f, w, h, p)
        }
        postInvalidateOnAnimation()
    }

    // ---------- sky ----------

    private fun drawSky(c: Canvas, w: Float, h: Float, day: Float, tm: Float) {
        val top = lerp(0xFF070B1E.toInt(), 0xFF4FA3E8.toInt(), day)
        val horizonDay = lerp(0xFFFF9A62.toInt(), 0xFFBFE3FA.toInt(), min(1f, day * 1.6f))
        val horizon = lerp(0xFF1B2547.toInt(), horizonDay, min(1f, day * 3f))
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.64f, top, horizon, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h * 0.66f, p)
        p.shader = null

        if (day < 0.35f) {
            val a = ((1f - day / 0.35f) * 255f).toInt()
            for (i in starX.indices) {
                p.color = (a shl 24) or 0xFFFFFF
                val tw = 0.6f + 0.4f * sin(tm * 2f + i)
                c.drawCircle(starX[i] * w, starY[i] * h, 1.2f + tw, p)
            }
        }

        val isDay = hour >= 6f && hour < 18f
        val t = if (isDay) (hour - 6f) / 12f else ((hour - 18f + 24f) % 24f) / 12f
        val cx = w * (0.08f + 0.84f * t)
        val cy = h * 0.5f - sin(t * PI).toFloat() * h * 0.38f
        if (isDay) {
            p.color = 0x33FFD36B
            c.drawCircle(cx, cy, h * 0.13f, p)
            p.color = 0x55FFD36B
            c.drawCircle(cx, cy, h * 0.09f, p)
            p.color = 0xFFFFE08A.toInt()
            c.drawCircle(cx, cy, h * 0.06f, p)
        } else {
            p.color = 0x22DDE8FF
            c.drawCircle(cx, cy, h * 0.09f, p)
            p.color = 0xFFEFF3FF.toInt()
            c.drawCircle(cx, cy, h * 0.045f, p)
            p.color = lerp(0xFF0B1330.toInt(), 0xFF1B2547.toInt(), 0.3f)
            c.drawCircle(cx + h * 0.02f, cy - h * 0.012f, h * 0.04f, p)
        }

        val cloud = lerp(0xFF3A4468.toInt(), 0xFFFFFFFF.toInt(), day)
        val alpha = if (rain) 235 else 190
        for (i in 0 until 4) {
            val span = w + 260f
            val x = ((i * 0.31f * w + tm * (6f + i * 3f)) % span) - 130f
            val y = h * (0.1f + 0.09f * i)
            p.color = (alpha shl 24) or (cloud and 0xFFFFFF)
            r.set(x, y, x + 110f, y + 26f)
            c.drawOval(r, p)
            r.set(x + 28f, y - 14f, x + 90f, y + 18f)
            c.drawOval(r, p)
            r.set(x + 60f, y - 4f, x + 140f, y + 24f)
            c.drawOval(r, p)
        }
    }

    // ---------- buildings, one look for each area ----------

    private fun drawSkyline(c: Canvas, w: Float, h: Float, day: Float, tm: Float) {
        val base = h * 0.64f
        val night = 1f - day
        val rnd = Random(100L + area)

        // far hills of buildings, softer and darker
        p.color = lerp(0xFF1A2236.toInt(), 0xFF9DB0C6.toInt(), day)
        var fx = -10f
        val frnd = Random(7L + area)
        while (fx < w) {
            val bw = w * (0.05f + frnd.nextFloat() * 0.07f)
            val bh = h * (0.12f + frnd.nextFloat() * 0.2f)
            c.drawRect(fx, base - bh, fx + bw, base, p)
            fx += bw + 2f
        }

        var x = -6f
        while (x < w) {
            val bw = w * (0.08f + rnd.nextFloat() * 0.07f)
            val bh: Float
            val body: Int
            when (area) {
                0 -> {
                    bh = h * (0.14f + rnd.nextFloat() * 0.12f)
                    val cols = intArrayOf(0xFFB4856A.toInt(), 0xFFC9A27E.toInt(), 0xFF9C6B52.toInt(), 0xFFD8B98F.toInt())
                    body = cols[rnd.nextInt(cols.size)]
                }
                1 -> {
                    bh = h * (0.17f + rnd.nextFloat() * 0.15f)
                    val cols = intArrayOf(0xFFD7D2C8.toInt(), 0xFFC4BFB3.toInt(), 0xFFE5DFD2.toInt(), 0xFFB9B2A4.toInt())
                    body = cols[rnd.nextInt(cols.size)]
                }
                2 -> {
                    bh = h * (0.26f + rnd.nextFloat() * 0.24f)
                    val cols = intArrayOf(0xFF6B7F99.toInt(), 0xFF8296AD.toInt(), 0xFF5A6B82.toInt(), 0xFF93A5BA.toInt())
                    body = cols[rnd.nextInt(cols.size)]
                }
                else -> {
                    bh = h * (0.32f + rnd.nextFloat() * 0.3f)
                    val cols = intArrayOf(0xFF3C6E9E.toInt(), 0xFF4A82B4.toInt(), 0xFF2F5C88.toInt(), 0xFF5B95C4.toInt())
                    body = cols[rnd.nextInt(cols.size)]
                }
            }
            val shade = lerp(0xFF0E1322.toInt(), body, 0.35f + 0.65f * day)
            if (area == 3) {
                p.shader = LinearGradient(x, base - bh, x + bw, base, lerp(0xFF16233B.toInt(), 0xFFA9D6F5.toInt(), day), shade, Shader.TileMode.CLAMP)
                c.drawRect(x, base - bh, x + bw, base, p)
                p.shader = null
            } else {
                p.color = shade
                c.drawRect(x, base - bh, x + bw, base, p)
                p.color = lerp(0xFF0A0E1A.toInt(), 0xFF6E5A4A.toInt(), 0.4f + 0.6f * day)
                c.drawRect(x - 2f, base - bh - 5f, x + bw + 2f, base - bh, p)
            }

            // windows
            val cols = max(2, (bw / 20f).toInt())
            val rows = max(2, (bh / 26f).toInt() - 1)
            val ww = bw / (cols * 1.9f)
            val wh = 11f
            for (rr in 0 until rows) {
                for (cc in 0 until cols) {
                    val wx = x + bw * 0.12f + cc * (bw * 0.76f / cols)
                    val wy = base - bh + 14f + rr * 24f
                    if (wy + wh > base - 8f) continue
                    val on = rnd.nextFloat()
                    val lit = if (dark) (if (area == 3) on < 0.5f else on < 0.08f) else on < 0.55f
                    p.color = if (night > 0.45f && lit) 0xFFFFDC7A.toInt()
                    else lerp(0xFF121828.toInt(), 0xFFB9D7EE.toInt(), day * 0.9f)
                    c.drawRect(wx, wy, wx + ww, wy + wh, p)
                }
            }
            x += bw + 3f
        }

        when (area) {
            0 -> drawMarket(c, w, h, base, day)
            1 -> drawCampus(c, w, h, base, day)
            2 -> drawBridgeAndPlane(c, w, h, base, day, tm)
            else -> drawPalms(c, w, h, base, day, tm)
        }
    }

    private fun sign(c: Canvas, text: String, cx: Float, cy: Float, h: Float, bg: Int) {
        val ts = h * 0.05f
        p.textSize = ts
        p.isFakeBoldText = true
        val tw = p.measureText(text)
        p.color = bg
        r.set(cx - tw / 2 - ts * 0.6f, cy - ts * 1.1f, cx + tw / 2 + ts * 0.6f, cy + ts * 0.5f)
        c.drawRoundRect(r, ts * 0.3f, ts * 0.3f, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawText(text, cx - tw / 2, cy, p)
        p.isFakeBoldText = false
    }

    private fun drawMarket(c: Canvas, w: Float, h: Float, base: Float, day: Float) {
        val colors = intArrayOf(0xFFE63946.toInt(), 0xFF2A9D8F.toInt(), 0xFFF4A261.toInt(), 0xFF457B9D.toInt(), 0xFFE9C46A.toInt())
        var x = w * 0.03f
        var i = 0
        while (x < w - 30f) {
            val uw = w * 0.11f
            val top = base - h * 0.13f
            p.color = lerp(0xFF2A1D14.toInt(), 0xFF7A5A3C.toInt(), 0.3f + 0.7f * day)
            c.drawRect(x + uw * 0.1f, top + h * 0.05f, x + uw * 0.9f, base, p)
            p.color = lerp(0xFF0F0F18.toInt(), colors[i % colors.size], 0.35f + 0.65f * day)
            path.reset()
            path.moveTo(x - 4f, top + h * 0.05f)
            path.lineTo(x + uw / 2, top)
            path.lineTo(x + uw + 4f, top + h * 0.05f)
            path.close()
            c.drawPath(path, p)
            p.color = lerp(0xFF101018.toInt(), 0xFFFFD166.toInt(), 0.2f + 0.8f * day)
            c.drawRect(x + uw * 0.18f, base - h * 0.05f, x + uw * 0.4f, base - h * 0.02f, p)
            c.drawRect(x + uw * 0.55f, base - h * 0.05f, x + uw * 0.8f, base - h * 0.02f, p)
            x += uw + w * 0.025f
            i++
        }
        sign(c, "OSHODI MARKET", w * 0.5f, h * 0.2f, h, 0xCC8A3B12.toInt())
    }

    private fun drawCampus(c: Canvas, w: Float, h: Float, base: Float, day: Float) {
        var x = w * 0.06f
        while (x < w) {
            p.color = lerp(0xFF2B1B10.toInt(), 0xFF6B4A2B.toInt(), 0.4f + 0.6f * day)
            c.drawRect(x - 3f, base - h * 0.1f, x + 3f, base, p)
            p.color = lerp(0xFF0C2214.toInt(), 0xFF2F8F46.toInt(), 0.35f + 0.65f * day)
            c.drawCircle(x, base - h * 0.14f, h * 0.075f, p)
            c.drawCircle(x - h * 0.05f, base - h * 0.1f, h * 0.055f, p)
            c.drawCircle(x + h * 0.05f, base - h * 0.1f, h * 0.055f, p)
            x += w * 0.2f
        }
        sign(c, "YABA TECH HUB", w * 0.5f, h * 0.22f, h, 0xCC1D4E89.toInt())
    }

    private fun drawBridgeAndPlane(c: Canvas, w: Float, h: Float, base: Float, day: Float, tm: Float) {
        val deck = h * 0.47f
        p.color = lerp(0xFF161B26.toInt(), 0xFF7C8796.toInt(), 0.3f + 0.7f * day)
        c.drawRect(0f, deck, w, deck + h * 0.035f, p)
        c.drawRect(w * 0.12f, deck, w * 0.12f + 10f, base, p)
        c.drawRect(w * 0.88f, deck, w * 0.88f + 10f, base, p)
        p.color = lerp(0xFF0F131C.toInt(), 0xFF5B6573.toInt(), 0.3f + 0.7f * day)
        c.drawRect(0f, deck - h * 0.03f, w, deck, p)

        val ax = ((tm * 22f) % (w + 200f)) - 100f
        val ay = h * 0.14f
        p.color = 0xFFF2F4F8.toInt()
        r.set(ax, ay, ax + 46f, ay + 9f)
        c.drawOval(r, p)
        path.reset()
        path.moveTo(ax + 14f, ay + 4f)
        path.lineTo(ax + 28f, ay - 12f)
        path.lineTo(ax + 34f, ay + 4f)
        path.close()
        c.drawPath(path, p)
        if (day < 0.5f) {
            p.color = 0xFFFF4D4D.toInt()
            c.drawCircle(ax + 46f, ay + 4f, 2.5f, p)
        }
    }

    private fun drawPalms(c: Canvas, w: Float, h: Float, base: Float, day: Float, tm: Float) {
        var x = w * 0.1f
        var i = 0
        while (x < w) {
            val th = h * (0.2f + 0.04f * (i % 3))
            p.color = lerp(0xFF1E140C.toInt(), 0xFF8A6A48.toInt(), 0.3f + 0.7f * day)
            path.reset()
            path.moveTo(x - 5f, base)
            path.quadTo(x + 6f, base - th * 0.5f, x, base - th)
            path.lineTo(x + 5f, base - th)
            path.quadTo(x + 14f, base - th * 0.5f, x + 5f, base)
            path.close()
            c.drawPath(path, p)
            p.color = lerp(0xFF0A2412.toInt(), 0xFF2E9B4D.toInt(), 0.35f + 0.65f * day)
            val sway = sin(tm * 1.5f + i) * 4f
            for (k in 0 until 6) {
                val ang = (k * 60f - 90f) * (PI.toFloat() / 180f)
                val ex = x + 2f + kotlin.math.cos(ang) * h * 0.11f + sway
                val ey = base - th + sin(ang) * h * 0.06f + h * 0.03f
                path.reset()
                path.moveTo(x + 2f, base - th)
                path.quadTo((x + ex) / 2, min(base - th, ey) - 8f, ex, ey)
                path.quadTo((x + ex) / 2, min(base - th, ey) + 2f, x + 2f, base - th + 3f)
                path.close()
                c.drawPath(path, p)
            }
            x += w * 0.27f
            i++
        }
        sign(c, "LEKKI", w * 0.5f, h * 0.2f, h, 0xCC0B6E4F.toInt())
    }

    // ---------- street and traffic ----------

    private fun drawStreet(c: Canvas, w: Float, h: Float, day: Float, tm: Float) {
        val walkTop = h * 0.64f
        val roadTop = h * 0.72f
        val roadBottom = h * 0.95f
        p.color = lerp(0xFF2B2F36.toInt(), 0xFFA39F96.toInt(), day)
        c.drawRect(0f, walkTop, w, roadTop, p)
        p.color = lerp(0xFF14171C.toInt(), 0xFF50555E.toInt(), day)
        c.drawRect(0f, roadTop, w, roadBottom, p)
        p.color = lerp(0xFF202329.toInt(), 0xFF8C8880.toInt(), day)
        c.drawRect(0f, roadBottom, w, h, p)
        p.color = lerp(0xFF0D0F13.toInt(), 0xFF3A3E45.toInt(), day)
        c.drawRect(0f, roadTop, w, roadTop + 3f, p)

        // moving lane line
        p.color = 0xFFFFD166.toInt()
        val off = (tm * 70f) % 90f
        var lx = -90f + off
        val ly = (roadTop + roadBottom) / 2
        while (lx < w) {
            c.drawRect(lx, ly - 2f, lx + 46f, ly + 2f, p)
            lx += 90f
        }

        val lane1 = h * 0.8f
        val lane2 = h * 0.925f
        val span = w + 400f
        val slow = if (rain) 0.7f else 1f

        // top lane, heading right
        vehicle(c, 0, ((tm * 55f * slow + 0f) % span) - 200f, lane1, h, true, day)
        vehicle(c, 1, ((tm * 80f * slow + span * 0.5f) % span) - 200f, lane1, h, true, day)
        // bottom lane, heading left
        vehicle(c, 2, w + 200f - ((tm * 62f * slow + span * 0.2f) % span), lane2, h, false, day)
        vehicle(c, 3, w + 200f - ((tm * 90f * slow + span * 0.7f) % span), lane2, h, false, day)
    }

    private fun vehicle(c: Canvas, kind: Int, x: Float, y: Float, h: Float, right: Boolean, day: Float) {
        c.save()
        val s = h * 0.62f
        if (!right) c.scale(-1f, 1f, x + s / 2, y)
        when ((kind + area) % 4) {
            0, 3 -> danfo(c, x, y, s * 0.62f, day)
            1 -> okada(c, x, y, s * 0.3f, day, kind)
            else -> car(c, x, y, s * 0.46f, day, kind)
        }
        c.restore()
    }

    private fun wheel(c: Canvas, x: Float, y: Float, rad: Float) {
        p.color = 0xFF0B0B0F.toInt()
        c.drawCircle(x, y, rad, p)
        p.color = 0xFFB8BCC4.toInt()
        c.drawCircle(x, y, rad * 0.45f, p)
    }

    private fun headlight(c: Canvas, x: Float, y: Float, len: Float, day: Float) {
        if (day > 0.5f) return
        p.color = 0x33FFF2B0
        path.reset()
        path.moveTo(x, y - len * 0.06f)
        path.lineTo(x + len, y - len * 0.2f)
        path.lineTo(x + len, y + len * 0.1f)
        path.close()
        c.drawPath(path, p)
    }

    private fun danfo(c: Canvas, x: Float, y: Float, bw: Float, day: Float) {
        val bh = bw * 0.46f
        p.color = 0xFFFFC400.toInt()
        r.set(x, y - bh, x + bw, y - bh * 0.1f)
        c.drawRoundRect(r, bh * 0.14f, bh * 0.14f, p)
        p.color = 0xFF111111.toInt()
        c.drawRect(x, y - bh * 0.5f, x + bw, y - bh * 0.34f, p)
        p.color = lerp(0xFF2C3E5A.toInt(), 0xFF9ED0F0.toInt(), day)
        val wn = 4
        for (i in 0 until wn) {
            val wx = x + bw * (0.07f + i * 0.2f)
            c.drawRect(wx, y - bh * 0.9f, wx + bw * 0.15f, y - bh * 0.56f, p)
        }
        p.color = lerp(0xFF2C3E5A.toInt(), 0xFF9ED0F0.toInt(), day)
        c.drawRect(x + bw * 0.88f, y - bh * 0.9f, x + bw * 0.98f, y - bh * 0.56f, p)
        p.color = 0xFFFFF4B8.toInt()
        c.drawRect(x + bw * 0.96f, y - bh * 0.32f, x + bw, y - bh * 0.2f, p)
        wheel(c, x + bw * 0.2f, y - bh * 0.1f, bh * 0.17f)
        wheel(c, x + bw * 0.78f, y - bh * 0.1f, bh * 0.17f)
        headlight(c, x + bw, y - bh * 0.26f, bw * 0.8f, day)
    }

    private fun car(c: Canvas, x: Float, y: Float, bw: Float, day: Float, seed: Int) {
        val cols = intArrayOf(0xFFD7263D.toInt(), 0xFFE9ECEF.toInt(), 0xFF3A86FF.toInt(), 0xFF2B2D42.toInt())
        val bh = bw * 0.32f
        p.color = cols[seed % cols.size]
        r.set(x, y - bh * 1.05f, x + bw, y - bh * 0.1f)
        c.drawRoundRect(r, bh * 0.3f, bh * 0.3f, p)
        path.reset()
        path.moveTo(x + bw * 0.2f, y - bh * 1.05f)
        path.lineTo(x + bw * 0.32f, y - bh * 1.7f)
        path.lineTo(x + bw * 0.7f, y - bh * 1.7f)
        path.lineTo(x + bw * 0.85f, y - bh * 1.05f)
        path.close()
        c.drawPath(path, p)
        p.color = lerp(0xFF2C3E5A.toInt(), 0xFF9ED0F0.toInt(), day)
        path.reset()
        path.moveTo(x + bw * 0.27f, y - bh * 1.08f)
        path.lineTo(x + bw * 0.36f, y - bh * 1.6f)
        path.lineTo(x + bw * 0.68f, y - bh * 1.6f)
        path.lineTo(x + bw * 0.78f, y - bh * 1.08f)
        path.close()
        c.drawPath(path, p)
        p.color = 0xFFFFF4B8.toInt()
        c.drawRect(x + bw * 0.94f, y - bh * 0.8f, x + bw, y - bh * 0.55f, p)
        wheel(c, x + bw * 0.22f, y - bh * 0.1f, bh * 0.32f)
        wheel(c, x + bw * 0.78f, y - bh * 0.1f, bh * 0.32f)
        headlight(c, x + bw, y - bh * 0.7f, bw * 0.9f, day)
    }

    private fun okada(c: Canvas, x: Float, y: Float, s: Float, day: Float, seed: Int) {
        val rad = s * 0.42f
        wheel(c, x + s * 0.2f, y - rad, rad)
        wheel(c, x + s * 1.2f, y - rad, rad)
        p.color = 0xFFCC2936.toInt()
        c.drawRect(x + s * 0.3f, y - rad * 1.9f, x + s * 1.1f, y - rad * 1.5f, p)
        p.color = 0xFF222222.toInt()
        c.drawRect(x + s * 0.15f, y - rad * 2.15f, x + s * 0.75f, y - rad * 1.9f, p)
        // rider
        p.color = SHIRTS[(seed + 2) % SHIRTS.size]
        c.drawRoundRect(x + s * 0.35f, y - rad * 3.6f, x + s * 0.75f, y - rad * 2.1f, 6f, 6f, p)
        p.color = SKINS[seed % SKINS.size]
        c.drawCircle(x + s * 0.62f, y - rad * 3.95f, rad * 0.42f, p)
        p.color = 0xFFFFC400.toInt()
        c.drawArc(x + s * 0.62f - rad * 0.5f, y - rad * 4.45f, x + s * 0.62f + rad * 0.5f, y - rad * 3.5f, 180f, 180f, true, p)
        p.color = SHIRTS[(seed + 2) % SHIRTS.size]
        c.drawRect(x + s * 0.72f, y - rad * 3.2f, x + s * 1.15f, y - rad * 2.95f, p)
        headlight(c, x + s * 1.5f, y - rad * 1.3f, s * 1.4f, day)
    }

    // ---------- people ----------

    private fun drawPeople(c: Canvas, w: Float, h: Float, tm: Float) {
        val foot = h * 0.715f
        // other players in the same area, small and spread along the pavement
        val shown = min(others.size, 19)
        for (i in 0 until shown) {
            val o = others[i]
            val frac = ((i * 0.618034f) + 0.13f) % 1f
            val x = w * (0.06f + 0.88f * frac)
            val ht = h * (0.2f + 0.02f * (o.id % 3))
            val bob = sin(tm * 2f + o.id) * ht * 0.006f
            person(c, x, foot, ht, o.id, bob)
            if (shown <= 8) tag(c, o.name, x, foot - ht * 1.12f, h * 0.04f, false)
        }
        if (others.size > 8) {
            p.textSize = h * 0.042f
            p.color = 0xFFFFFFFF.toInt()
            val s = "${others.size} other players here"
            c.drawText(s, w - p.measureText(s) - 8f, h * 0.06f, p)
        }

        // you, bigger and in front
        val ht = h * 0.34f
        val bob = if (busy) sin(tm * 12f) * ht * 0.02f else sin(tm * 2f) * ht * 0.004f
        person(c, w * 0.5f, h * 0.745f, ht, meId, bob)
        tag(c, meName, w * 0.5f, h * 0.745f - ht * 1.13f, h * 0.048f, true)
    }

    private fun tag(c: Canvas, text: String, cx: Float, y: Float, ts: Float, me: Boolean) {
        p.textSize = ts
        p.isFakeBoldText = me
        val tw = p.measureText(text)
        p.color = if (me) 0xFFFF5B2E.toInt() else 0x99000000.toInt()
        r.set(cx - tw / 2 - ts * 0.5f, y - ts * 1.05f, cx + tw / 2 + ts * 0.5f, y + ts * 0.35f)
        c.drawRoundRect(r, ts * 0.6f, ts * 0.6f, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawText(text, cx - tw / 2, y, p)
        p.isFakeBoldText = false
    }

    private fun person(c: Canvas, x: Float, foot: Float, ht: Float, id: Int, bob: Float) {
        val skin = SKINS[abs(id) % SKINS.size]
        val shirt = SHIRTS[abs(id * 3 + 1) % SHIRTS.size]
        val pants = PANTS[abs(id) % PANTS.size]
        val y = foot - abs(bob)
        val head = ht * 0.12f
        val legH = ht * 0.42f
        val bodyH = ht * 0.34f

        p.color = 0x44000000
        r.set(x - ht * 0.2f, foot - ht * 0.02f, x + ht * 0.2f, foot + ht * 0.025f)
        c.drawOval(r, p)

        p.color = pants
        c.drawRect(x - ht * 0.1f, y - legH, x - ht * 0.01f, y - ht * 0.03f, p)
        c.drawRect(x + ht * 0.01f, y - legH, x + ht * 0.1f, y - ht * 0.03f, p)
        p.color = 0xFFF2F2F2.toInt()
        c.drawRect(x - ht * 0.11f, y - ht * 0.035f, x - ht * 0.0f, y, p)
        c.drawRect(x + ht * 0.0f, y - ht * 0.035f, x + ht * 0.11f, y, p)

        p.color = skin
        c.drawRect(x - ht * 0.2f, y - legH - bodyH * 0.9f, x - ht * 0.14f, y - legH - bodyH * 0.25f, p)
        c.drawRect(x + ht * 0.14f, y - legH - bodyH * 0.9f, x + ht * 0.2f, y - legH - bodyH * 0.25f, p)

        p.color = shirt
        r.set(x - ht * 0.16f, y - legH - bodyH, x + ht * 0.16f, y - legH + ht * 0.02f)
        c.drawRoundRect(r, ht * 0.05f, ht * 0.05f, p)

        p.color = skin
        c.drawRect(x - ht * 0.03f, y - legH - bodyH - ht * 0.03f, x + ht * 0.03f, y - legH - bodyH + ht * 0.02f, p)
        val hy = y - legH - bodyH - head * 0.95f
        c.drawCircle(x, hy, head, p)
        p.color = 0xFF161616.toInt()
        r.set(x - head, hy - head, x + head, hy + head)
        c.drawArc(r, 180f, 180f, true, p)
        p.color = 0xFF161616.toInt()
        c.drawCircle(x - head * 0.35f, hy + head * 0.05f, head * 0.1f, p)
        c.drawCircle(x + head * 0.35f, hy + head * 0.05f, head * 0.1f, p)
    }

    // ---------- weather ----------

    private fun drawRain(c: Canvas, w: Float, h: Float, tm: Float) {
        p.color = 0x33203050
        c.drawRect(0f, 0f, w, h, p)
        p.color = 0x99C8DCFF.toInt()
        p.strokeWidth = 2f
        for (i in 0 until 70) {
            val x = ((i * 97) % 1000) / 1000f * w + (tm * 40f) % 20f
            val y = (((i * 53) % 1000) / 1000f * h + tm * 520f) % (h + 30f) - 15f
            c.drawLine(x, y, x - 6f, y + 18f, p)
        }
    }
}

/** A rounded progress bar. */
class BarView(context: Context, private val color: Int) : View(context) {
    var frac = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = Colors.CARD2
        r.set(0f, 0f, w, h)
        c.drawRoundRect(r, h / 2, h / 2, p)
        if (frac > 0f) {
            p.color = color
            r.set(0f, 0f, max(h, w * frac), h)
            c.drawRoundRect(r, h / 2, h / 2, p)
        }
    }
}

/** The tap-at-the-right-moment bar used when you work a shift. */
class TimingView(context: Context) : View(context) {
    var pos = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    fun score(): Double {
        val d = abs(pos - 0.5f)
        return if (d <= 0.1f) 1.5 else if (d <= 0.22f) 1.2 else 0.85
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = Colors.CARD2
        r.set(0f, h * 0.25f, w, h * 0.75f)
        c.drawRoundRect(r, h * 0.25f, h * 0.25f, p)
        p.color = 0xFF8A6D1F.toInt()
        r.set(w * 0.28f, h * 0.25f, w * 0.72f, h * 0.75f)
        c.drawRect(r, p)
        p.color = Colors.GREEN
        r.set(w * 0.4f, h * 0.25f, w * 0.6f, h * 0.75f)
        c.drawRect(r, p)
        p.color = Colors.PAPER
        val x = pos * w
        r.set(x - 6f, 0f, x + 6f, h)
        c.drawRoundRect(r, 6f, 6f, p)
    }
}
