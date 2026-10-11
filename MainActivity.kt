package com.nagi.hustle

import android.animation.ValueAnimator
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

/** One row of the rankings. */
class Row(val id: Int, val name: String, val cash: Long, val rep: Int, val area: Int, val energy: Int, val food: Int,
          val busyMs: Long, val busyTotal: Long, val busyLabel: String) {
    fun worth(): Long = cash + rep * 200L
}

class MainActivity : AppCompatActivity() {

    private var server: GameServer? = null
    private var client: GameClient? = null
    private var finder: RoomFinder? = null
    private lateinit var root: FrameLayout
    private val ui = Handler(Looper.getMainLooper())

    private var screen = ""
    private var isHost = false
    private var myName = ""
    private var myId = -1
    private var lastState: JSONObject? = null

    // menu
    private var roomsBox: LinearLayout? = null
    private var nameField: EditText? = null

    // lobby
    private var lobbyText: TextView? = null
    private var startBtn: Button? = null

    // game
    private var scene: SceneView? = null
    private var tvMoney: TextView? = null
    private var tvTime: TextView? = null
    private var tvArea: TextView? = null
    private var tvEnergy: TextView? = null
    private var tvFood: TextView? = null
    private var tvRep: TextView? = null
    private var barEnergy: BarView? = null
    private var barFood: BarView? = null
    private var barRep: BarView? = null
    private var tvEvent: TextView? = null
    private var tvRent: TextView? = null
    private var tvMsg: TextView? = null
    private var tvBusy: TextView? = null
    private var barBusy: BarView? = null
    private var btnWork: Button? = null
    private var btnEat: Button? = null
    private var btnRest: Button? = null
    private var btnTravel: Button? = null
    private var busyRemain = 0L
    private var busyTotal = 0L
    private var busyStamp = 0L
    private var myRow: Row? = null
    private var curDay = 1
    private var ticking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this)
        root.setBackgroundColor(Colors.INK)
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (screen) {
                    "game" -> confirmLeave()
                    "lobby", "end" -> showMenu()
                    else -> finish()
                }
            }
        })
        showMenu()
    }

    override fun onDestroy() {
        stopNet()
        super.onDestroy()
    }

    // ---------- small helpers ----------

    private fun tv(text: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun btn(text: String, accent: Boolean, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 15f
        setTextColor(if (accent) Colors.INK else Colors.PAPER)
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(if (accent) Colors.ACCENT else Colors.CARD2)
        }
        setOnClickListener { onClick() }
    }

    private fun vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun field(hint: String, number: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint
        setHintTextColor(Colors.MUTED)
        setTextColor(Colors.PAPER)
        textSize = 16f
        setSingleLine(true)
        if (number) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(Colors.CARD)
        }
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun lp(top: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.topMargin = dp(top)
        return p
    }

    private fun setScreen(v: View) {
        root.removeAllViews()
        root.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun stopNet() {
        ticking = false
        finder?.stop()
        finder = null
        client?.close()
        client = null
        server?.stop()
        server = null
        isHost = false
    }

    private fun money(v: Long) = naira(v)

    // ---------- menu ----------

    private fun showMenu() {
        stopNet()
        screen = "menu"
        lastState = null
        val col = vbox()
        col.setPadding(dp(20), dp(28), dp(20), dp(24))

        col.addView(tv("EKO HUSTLE", 30f, Colors.PAPER, true))
        col.addView(tv("A Lagos life game for up to $MAX_PLAYERS players on the same Wi-Fi.", 14f, Colors.MUTED), lp(4))

        val prefs = getSharedPreferences("hustle", MODE_PRIVATE)
        val nf = field("Your name")
        nf.setText(prefs.getString("name", "") ?: "")
        nameField = nf
        col.addView(nf, lp(22))

        col.addView(btn("Host a game", true) { startHost() }, lp(14))

        col.addView(tv("Games nearby", 16f, Colors.PAPER, true), lp(24))
        val box = vbox()
        roomsBox = box
        box.addView(tv("Looking for hosts on this Wi-Fi...", 13f, Colors.MUTED))
        col.addView(box, lp(8))

        col.addView(tv("Or type the host's address", 13f, Colors.MUTED), lp(18))
        val ipf = field("For example 192.168.43.1", true)
        col.addView(ipf, lp(6))
        col.addView(btn("Join with address", false) {
            val ip = ipf.text.toString().trim()
            if (ip.isEmpty()) toast("Type the address shown on the host's screen.") else joinRoom(ip)
        }, lp(8))

        col.addView(
            tv(
                "How to play: everyone joins the same Wi-Fi (or the host's hotspot). One person taps Host a game, " +
                    "the others pick that room, and the host starts when everyone is in. Work, eat, rest and pay your rent. " +
                    "The richest, most respected player after $TOTAL_DAYS days wins.",
                13f, Colors.MUTED
            ), lp(22)
        )

        val sv = ScrollView(this)
        sv.addView(col)
        setScreen(sv)

        val f = RoomFinder { list -> ui.post { showRooms(list) } }
        finder = f
        f.start()
    }

    private fun showRooms(list: List<RoomFinder.Room>) {
        val box = roomsBox ?: return
        if (screen != "menu") return
        box.removeAllViews()
        if (list.isEmpty()) {
            box.addView(tv("No games found yet. Ask the host to open one, and make sure you are on the same Wi-Fi.", 13f, Colors.MUTED))
            return
        }
        for (room in list) {
            box.addView(btn("Join ${room.name}'s game  (${room.count}/$MAX_PLAYERS)", false) { joinRoom(room.ip) }, lp(6))
        }
    }

    private fun nameValue(): String? {
        val n = nameField?.text?.toString()?.trim() ?: ""
        if (n.isEmpty()) {
            toast("Type your name first.")
            return null
        }
        getSharedPreferences("hustle", MODE_PRIVATE).edit().putString("name", n).apply()
        return n
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    }

    // ---------- connecting ----------

    private fun startHost() {
        val name = nameValue() ?: return
        finder?.stop()
        finder = null
        val s = GameServer(name)
        thread(isDaemon = true) {
            try {
                s.start()
                ui.post {
                    server = s
                    isHost = true
                    connect("127.0.0.1", name)
                }
            } catch (e: Exception) {
                ui.post { toast("Could not open a room: ${e.message}") }
            }
        }
    }

    private fun joinRoom(ip: String) {
        val name = nameValue() ?: return
        finder?.stop()
        finder = null
        isHost = false
        connect(ip, name)
    }

    private fun connect(ip: String, name: String) {
        myName = name
        val cl = GameClient()
        client = cl
        cl.onState = { o -> ui.post { onState(o) } }
        cl.onMsg = { m -> ui.post { onMessage(m) } }
        cl.onClosed = { why ->
            ui.post {
                toast(why)
                showMenu()
            }
        }
        showLobby()
        cl.connect(ip, name)
    }

    private fun onMessage(m: String) {
        if (screen == "game") tvMsg?.text = m else toast(m)
    }

    private fun onState(o: JSONObject) {
        lastState = o
        myId = o.optInt("you", -1)
        when (o.optString("phase")) {
            "lobby" -> {
                if (screen != "lobby") showLobby()
                updateLobby(o)
            }
            "playing" -> {
                if (screen != "game") showGame()
                updateGame(o)
            }
            "ended" -> {
                if (screen != "end") showEnd()
                updateEnd(o)
            }
        }
    }

    private fun rows(o: JSONObject): List<Row> {
        val list = ArrayList<Row>()
        val arr: JSONArray = o.optJSONArray("players") ?: return list
        for (i in 0 until arr.length()) {
            val b = arr.getJSONObject(i)
            list.add(
                Row(
                    b.getInt("id"), b.getString("n"), b.getLong("c"), b.getInt("r"), b.getInt("a"),
                    b.getInt("e"), b.getInt("f"), b.getLong("b"), b.getLong("bt"), b.optString("bl")
                )
            )
        }
        return list
    }

    // ---------- lobby ----------

    private fun showLobby() {
        screen = "lobby"
        val col = vbox()
        col.setPadding(dp(20), dp(28), dp(20), dp(24))
        col.addView(tv("Waiting room", 26f, Colors.PAPER, true))
        val t = tv("Connecting...", 15f, Colors.PAPER)
        lobbyText = t
        col.addView(t, lp(16))

        if (isHost) {
            val ips = localIps()
            val addr = if (ips.isEmpty()) "Connect this phone to Wi-Fi or turn on your hotspot." else
                "Friends can also join by typing: " + ips.joinToString("  or  ")
            col.addView(tv(addr, 13f, Colors.MUTED), lp(14))
            val b = btn("Start the game", true) { server?.startGame() }
            startBtn = b
            col.addView(b, lp(20))
        } else {
            col.addView(tv("The host will start the game when everyone is in.", 13f, Colors.MUTED), lp(14))
        }
        col.addView(btn("Leave", false) { showMenu() }, lp(14))
        val sv = ScrollView(this)
        sv.addView(col)
        setScreen(sv)
    }

    private fun updateLobby(o: JSONObject) {
        val list = rows(o)
        val sb = StringBuilder()
        sb.append("Players (${list.size}/$MAX_PLAYERS):\n")
        for (r in list) {
            sb.append("• ").append(r.name)
            if (r.id == myId) sb.append("  (you)")
            sb.append("\n")
        }
        lobbyText?.text = sb.toString()
    }

    // ---------- the game screen ----------

    private fun showGame() {
        screen = "game"
        val col = vbox()

        val sc = SceneView(this)
        scene = sc
        col.addView(sc, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250)))

        val body = vbox()
        body.setPadding(dp(16), dp(10), dp(16), dp(16))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        val m = tv("", 26f, Colors.GOLD, true)
        tvMoney = m
        top.addView(m, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val tt = tv("", 14f, Colors.PAPER)
        tt.gravity = Gravity.END
        tvTime = tt
        top.addView(tt, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        body.addView(top, lp())

        val ar = tv("", 13f, Colors.MUTED)
        tvArea = ar
        body.addView(ar, lp(2))

        val en = tv("", 13f, Colors.PAPER)
        tvEnergy = en
        body.addView(en, lp(10))
        val be = BarView(this, Colors.GREEN)
        barEnergy = be
        body.addView(be, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)).apply { topMargin = dp(3) })

        val fo = tv("", 13f, Colors.PAPER)
        tvFood = fo
        body.addView(fo, lp(8))
        val bf = BarView(this, Colors.ACCENT)
        barFood = bf
        body.addView(bf, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)).apply { topMargin = dp(3) })

        val rp = tv("", 13f, Colors.PAPER)
        tvRep = rp
        body.addView(rp, lp(8))
        val br = BarView(this, Colors.BLUE)
        barRep = br
        body.addView(br, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)).apply { topMargin = dp(3) })

        val rent = tv("", 13f, Colors.MUTED)
        tvRent = rent
        body.addView(rent, lp(10))

        val ev = tv("", 13f, Colors.INK)
        ev.setPadding(dp(12), dp(8), dp(12), dp(8))
        ev.background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(Colors.GOLD)
        }
        ev.visibility = View.GONE
        tvEvent = ev
        body.addView(ev, lp(10))

        val msg = tv("Welcome to Lagos. Make some money.", 14f, Colors.ACCENT)
        tvMsg = msg
        body.addView(msg, lp(10))

        val bt = tv("", 13f, Colors.MUTED)
        tvBusy = bt
        body.addView(bt, lp(10))
        val bb = BarView(this, Colors.GOLD)
        barBusy = bb
        body.addView(bb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)).apply { topMargin = dp(3) })

        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        val w = btn("Work", true) { doWork() }
        btnWork = w
        val e = btn("Eat", false) { act("eat", -1, 1.0) }
        btnEat = e
        row1.addView(w, weighted())
        row1.addView(e, weighted())
        body.addView(row1, lp(14))

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        val rs = btn("Rest", false) { act("rest", -1, 1.0) }
        btnRest = rs
        val tr = btn("Travel", false) { chooseTravel() }
        btnTravel = tr
        row2.addView(rs, weighted())
        row2.addView(tr, weighted())
        body.addView(row2, lp(0))

        val row3 = LinearLayout(this)
        row3.orientation = LinearLayout.HORIZONTAL
        row3.addView(btn("Rankings", false) { showRankings() }, weighted())
        row3.addView(btn("Leave game", false) { confirmLeave() }, weighted())
        body.addView(row3, lp(0))

        col.addView(body)
        val sv = ScrollView(this)
        sv.addView(col)
        setScreen(sv)

        ticking = true
        tickBusy()
    }

    private fun weighted(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(0, dp(52), 1f)
        p.setMargins(dp(4), dp(4), dp(4), dp(4))
        return p
    }

    private fun updateGame(o: JSONObject) {
        val list = rows(o)
        val me = list.firstOrNull { it.id == myId } ?: return
        myRow = me
        val day = o.optInt("day", 1)
        curDay = day
        val hour = o.optDouble("hour", 8.0)
        val hh = hour.toInt()
        val mm = ((hour - hh) * 60).toInt() / 10 * 10

        tvMoney?.text = money(me.cash)
        tvTime?.text = "Day $day of ${o.optInt("total", TOTAL_DAYS)}\n" + String.format("%02d:%02d", hh, mm)
        val a = AREAS[me.area]
        tvArea?.text = "${a.name}: ${a.blurb}"
        tvEnergy?.text = "Energy ${me.energy}"
        tvFood?.text = "Food ${me.food}"
        tvRep?.text = "Reputation ${me.rep}"
        barEnergy?.frac = me.energy / 100f
        barFood?.frac = me.food / 100f
        barRep?.frac = me.rep / 100f
        val rentIn = o.optInt("rentIn", 0)
        tvRent?.text = "Rent ${money(o.optLong("rent", RENT))} is due " + (if (rentIn <= 0) "today" else "in $rentIn days") +
            ". Your job here: ${a.jobName}."

        val evTitle = o.optString("ev", "")
        val evBox = tvEvent
        if (evBox != null) {
            if (evTitle.isEmpty()) {
                evBox.visibility = View.GONE
            } else {
                evBox.visibility = View.VISIBLE
                evBox.text = "⚠ $evTitle: ${o.optString("evText", "")}"
            }
        }

        btnWork?.text = "Work: ${a.jobName}"
        btnEat?.text = "Eat (about ${money(a.foodCost.toLong())})"

        busyRemain = me.busyMs
        busyTotal = me.busyTotal
        busyStamp = SystemClock.elapsedRealtime()
        if (me.busyMs > 0) tvBusy?.text = me.busyLabel

        val sc = scene
        if (sc != null) {
            sc.hour = hour.toFloat()
            sc.area = me.area
            sc.rain = o.optBoolean("rain", false)
            sc.dark = o.optBoolean("dark", false)
            sc.meId = me.id
            sc.meName = me.name
            sc.busy = me.busyMs > 0
            sc.others = list.filter { it.id != myId && it.area == me.area }.map { SceneView.Other(it.id, it.name) }
        }
    }

    /** Smooths the busy bar between the host's updates, and locks the buttons while you are busy. */
    private fun tickBusy() {
        if (!ticking) return
        val elapsed = SystemClock.elapsedRealtime() - busyStamp
        val left = (busyRemain - elapsed).coerceAtLeast(0L)
        val isBusy = left > 0
        barBusy?.frac = if (busyTotal > 0 && isBusy) 1f - left.toFloat() / busyTotal else 0f
        if (!isBusy) tvBusy?.text = "Ready"
        scene?.busy = isBusy
        for (b in listOf(btnWork, btnEat, btnRest, btnTravel)) {
            b?.isEnabled = !isBusy
            b?.alpha = if (isBusy) 0.5f else 1f
        }
        ui.postDelayed({ tickBusy() }, 100)
    }

    private fun act(a: String, to: Int, bonus: Double) {
        val o = JSONObject().put("t", "act").put("a", a)
        if (to >= 0) o.put("to", to)
        o.put("bonus", bonus)
        client?.send(o)
        // lock the buttons right away so a double tap does nothing
        busyRemain = 1500
        busyTotal = 1500
        busyStamp = SystemClock.elapsedRealtime()
    }

    private fun doWork() {
        val me = myRow ?: return
        val a = AREAS[me.area]
        val box = vbox()
        box.setPadding(dp(20), dp(16), dp(20), dp(8))
        box.addView(tv("Shift: ${a.jobName}", 18f, Colors.PAPER, true))
        box.addView(tv("Tap STOP when the marker is in the green zone. A better hit means better pay.", 13f, Colors.MUTED), lp(6))
        val timing = TimingView(this)
        box.addView(timing, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(16) })

        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = (950 - curDay * 12).coerceAtLeast(480).toLong()
        anim.repeatMode = ValueAnimator.REVERSE
        anim.repeatCount = ValueAnimator.INFINITE
        anim.addUpdateListener {
            timing.pos = it.animatedValue as Float
            timing.invalidate()
        }

        val dlg = AlertDialog.Builder(this).setView(box).setCancelable(false).create()
        box.addView(btn("STOP", true) {
            anim.cancel()
            val s = timing.score()
            dlg.dismiss()
            tvMsg?.text = if (s >= 1.5) "Perfect timing!" else if (s >= 1.2) "Good work." else "A bit slow."
            act("work", -1, s)
        }, lp(16))
        dlg.show()
        anim.start()
    }

    private fun chooseTravel() {
        val me = myRow ?: return
        val options = ArrayList<Area>()
        for (a in AREAS) if (a.id != me.area) options.add(a)
        val labels = options.map { "${it.name}: ${it.blurb}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Where to? Fares start at ₦1,000 and rise with distance.")
            .setItems(labels) { _, which -> act("travel", options[which].id, 1.0) }
            .setNegativeButton("Stay here", null)
            .show()
    }

    private fun rankingText(o: JSONObject): String {
        val list = rows(o).sortedByDescending { it.worth() }
        val sb = StringBuilder()
        var i = 1
        for (r in list) {
            sb.append("$i. ").append(r.name)
            if (r.id == myId) sb.append(" (you)")
            sb.append("\n    ").append(money(r.cash)).append("  |  reputation ").append(r.rep)
            sb.append("  |  ").append(AREAS[r.area].name).append("\n")
            i++
        }
        return sb.toString()
    }

    private fun showRankings() {
        val o = lastState ?: return
        AlertDialog.Builder(this)
            .setTitle("Rankings (money + reputation)")
            .setMessage(rankingText(o))
            .setPositiveButton("Close", null)
            .show()
    }

    private fun confirmLeave() {
        AlertDialog.Builder(this)
            .setTitle("Leave the game?")
            .setMessage(if (isHost) "You are the host. Leaving ends the game for everyone." else "You can rejoin with the same name.")
            .setPositiveButton("Leave") { _, _ -> showMenu() }
            .setNegativeButton("Stay", null)
            .show()
    }

    // ---------- the end ----------

    private var endText: TextView? = null

    private fun showEnd() {
        screen = "end"
        ticking = false
        val col = vbox()
        col.setPadding(dp(20), dp(28), dp(20), dp(24))
        col.addView(tv("Game over", 28f, Colors.PAPER, true))
        val t = tv("", 15f, Colors.PAPER)
        endText = t
        col.addView(t, lp(16))
        col.addView(btn("Back to menu", true) { showMenu() }, lp(20))
        val sv = ScrollView(this)
        sv.addView(col)
        setScreen(sv)
    }

    private fun updateEnd(o: JSONObject) {
        val list = rows(o).sortedByDescending { it.worth() }
        val winner = list.firstOrNull()
        val head = if (winner == null) "" else "Winner: ${winner.name}\n\n"
        endText?.text = head + rankingText(o)
    }
}
