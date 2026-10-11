package com.nagi.hustle

import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

const val GAME_PORT = 47002
const val DISCOVERY_PORT = 47001
const val MAX_PLAYERS = 20
const val TOTAL_DAYS = 40
const val RENT_EVERY = 7
const val RENT = 45000L
const val DAY_MS = 30000L

class Player(val id: Int, var name: String) {
    var cash = 15000L
    var energy = 100
    var food = 80
    var rep = 50
    var area = 1
    var busyUntil = 0L
    var busyTotal = 0L
    var busyLabel = ""
    var online = true

    fun worth(): Long = cash + rep * 200L
}

class Conn(val socket: Socket, private val out: PrintWriter) {
    var player: Player? = null

    /** Returns false if the connection is broken. */
    fun send(s: String): Boolean {
        synchronized(out) {
            out.println(s)
            out.flush()
            return !out.checkError()
        }
    }
}

/** Addresses used to shout "I am a room" to everyone on the Wi-Fi. */
fun broadcastAddresses(): List<InetAddress> {
    val list = ArrayList<InetAddress>()
    try {
        list.add(InetAddress.getByName("255.255.255.255"))
    } catch (e: Exception) {
    }
    try {
        val ifs = NetworkInterface.getNetworkInterfaces()
        while (ifs != null && ifs.hasMoreElements()) {
            val ni = ifs.nextElement()
            if (!ni.isUp || ni.isLoopback) continue
            for (ia in ni.interfaceAddresses) {
                val b = ia.broadcast
                if (b != null) list.add(b)
            }
        }
    } catch (e: Exception) {
    }
    return list
}

/** This phone's own addresses on the local network, to show on the host screen. */
fun localIps(): List<String> {
    val list = ArrayList<String>()
    try {
        val ifs = NetworkInterface.getNetworkInterfaces()
        while (ifs != null && ifs.hasMoreElements()) {
            val ni = ifs.nextElement()
            if (!ni.isUp || ni.isLoopback) continue
            for (ia in ni.interfaceAddresses) {
                val a = ia.address
                if (a is java.net.Inet4Address) {
                    val s = a.hostAddress
                    if (s != null) list.add(s)
                }
            }
        }
    } catch (e: Exception) {
    }
    return list
}

/**
 * The host phone runs this. It keeps the one true copy of the game and every phone, including the
 * host's own, talks to it over the Wi-Fi.
 */
class GameServer(private val hostName: String) {

    private val lock = Any()
    private val players = ArrayList<Player>()
    private val conns = ArrayList<Conn>()
    private var nextId = 0

    @Volatile
    private var running = false
    private var serverSocket: ServerSocket? = null
    private var phase = "lobby"
    private var hours = 7.0
    private var lastDay = 1
    private var event: CityEvent? = null
    private var eventEndDay = 0
    private val rnd = Random(System.nanoTime())

    fun start() {
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(GAME_PORT))
        serverSocket = ss
        running = true

        thread(isDaemon = true) {
            while (running) {
                try {
                    val s = ss.accept()
                    thread(isDaemon = true) { handle(s) }
                } catch (e: Exception) {
                    if (!running) break
                }
            }
        }

        thread(isDaemon = true) {
            var last = SystemClock.elapsedRealtime()
            while (running) {
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    break
                }
                val now = SystemClock.elapsedRealtime()
                val dt = now - last
                last = now
                synchronized(lock) {
                    if (phase == "playing") advance(dt)
                }
            }
        }

        thread(isDaemon = true) {
            while (running) {
                try {
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    break
                }
                broadcast()
            }
        }

        thread(isDaemon = true) {
            var sock: DatagramSocket? = null
            try {
                sock = DatagramSocket()
                sock.broadcast = true
                while (running) {
                    var count = 0
                    var ph = ""
                    synchronized(lock) {
                        count = players.size
                        ph = phase
                    }
                    if (ph == "lobby") {
                        val msg = "EKOHUSTLE|$hostName|$GAME_PORT|$count".toByteArray()
                        for (addr in broadcastAddresses()) {
                            try {
                                sock.send(DatagramPacket(msg, msg.size, addr, DISCOVERY_PORT))
                            } catch (e: Exception) {
                            }
                        }
                    }
                    Thread.sleep(1000)
                }
            } catch (e: Exception) {
            } finally {
                try {
                    sock?.close()
                } catch (e: Exception) {
                }
            }
        }
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
        }
        synchronized(lock) {
            for (c in conns) {
                try {
                    c.socket.close()
                } catch (e: Exception) {
                }
            }
            conns.clear()
        }
    }

    fun startGame() {
        synchronized(lock) {
            if (phase != "lobby" || players.isEmpty()) return
            phase = "playing"
            hours = 7.0
            lastDay = 1
            announce("Day 1 begins. Work, eat, rest, and pay your rent. Good luck!")
        }
    }

    // ---------- connections ----------

    private fun handle(s: Socket) {
        var conn: Conn? = null
        try {
            s.tcpNoDelay = true
            val c = Conn(s, PrintWriter(s.getOutputStream(), true))
            conn = c
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            while (running) {
                val line = reader.readLine() ?: break
                val o = JSONObject(line)
                when (o.optString("t")) {
                    "join" -> join(c, o.optString("name", "Player"))
                    "act" -> {
                        val p = c.player
                        if (p != null) act(p, o)
                    }
                }
            }
        } catch (e: Exception) {
            // connection ended
        } finally {
            val c = conn
            if (c != null) drop(c)
            try {
                s.close()
            } catch (e: Exception) {
            }
        }
    }

    private fun join(c: Conn, rawName: String) {
        synchronized(lock) {
            if (c.player != null) return
            val clean = rawName.trim().take(14).ifEmpty { "Player" }

            if (phase != "lobby") {
                // Someone whose Wi-Fi dropped can come back with the same name.
                val back = players.firstOrNull { !it.online && it.name.equals(clean, ignoreCase = true) }
                if (back == null) {
                    c.send("{\"t\":\"full\"}")
                    return
                }
                back.online = true
                c.player = back
                conns.add(c)
                return
            }
            if (players.size >= MAX_PLAYERS) {
                c.send("{\"t\":\"full\"}")
                return
            }
            var name = clean
            var n = 2
            while (players.any { it.name.equals(name, ignoreCase = true) }) {
                name = "$clean$n"
                n++
            }
            val p = Player(nextId++, name)
            c.player = p
            players.add(p)
            conns.add(c)
        }
    }

    private fun drop(c: Conn) {
        synchronized(lock) {
            conns.remove(c)
            val p = c.player
            if (p != null) {
                if (phase == "lobby") players.remove(p) else p.online = false
            }
        }
    }

    private fun tell(p: Player, text: String) {
        val s = JSONObject().put("t", "msg").put("m", text).toString()
        for (c in conns) {
            if (c.player === p) c.send(s)
        }
    }

    private fun announce(text: String) {
        val s = JSONObject().put("t", "msg").put("m", text).toString()
        for (c in conns) c.send(s)
    }

    // ---------- the game ----------

    private fun busy(p: Player, ms: Long, label: String) {
        p.busyUntil = SystemClock.elapsedRealtime() + ms
        p.busyTotal = ms
        p.busyLabel = label
    }

    private fun act(p: Player, o: JSONObject) {
        synchronized(lock) {
            if (phase != "playing") return
            val now = SystemClock.elapsedRealtime()
            if (now < p.busyUntil) return
            val a = AREAS[p.area]
            val ev = event

            when (o.optString("a")) {
                "work" -> {
                    if (ev != null && ev.blocked == p.area) {
                        tell(p, "You cannot work in ${a.name} right now: ${ev.title}.")
                        return
                    }
                    if (p.energy < a.energyCost) {
                        tell(p, "Too tired to work. Rest first.")
                        return
                    }
                    val bonus = o.optDouble("bonus", 1.0).coerceIn(0.7, 1.5)
                    var mult = bonus * (if (ev != null) ev.pay[p.area] else 1.0)
                    if (p.food < 20) mult *= 0.8
                    var pay = (a.pay * mult).toLong()
                    p.energy -= a.energyCost
                    p.rep += if (bonus >= 1.4) 2 else 1
                    var extra = ""
                    val roll = rnd.nextInt(100)
                    if (roll < 10) {
                        pay += 1500L
                        extra = " A customer tipped you ${naira(1500L)}."
                    } else if (roll < 18) {
                        val loss = min(3000L, p.cash + pay)
                        pay -= loss
                        extra = " A pickpocket got ${naira(loss)}."
                    }
                    p.cash += pay
                    busy(p, 7000L, "Working as ${a.jobName}")
                    tell(p, "Shift done. You earned ${naira(pay)}.$extra")
                }
                "eat" -> {
                    val mult = if (ev != null) ev.food else 1.0
                    val cost = (a.foodCost * mult).toLong()
                    if (p.cash < cost) {
                        tell(p, "You cannot afford food here (${naira(cost)}).")
                        return
                    }
                    p.cash -= cost
                    p.food = min(100, p.food + 45)
                    busy(p, 3000L, "Eating")
                    tell(p, "You ate for ${naira(cost)}.")
                }
                "rest" -> {
                    p.energy = min(100, p.energy + 55)
                    busy(p, 5000L, "Resting")
                    tell(p, "You rested and feel better.")
                }
                "travel" -> {
                    val to = o.optInt("to", -1)
                    if (to < 0 || to >= AREAS.size || to == p.area) return
                    val fm = if (ev != null) ev.travel else 1.0
                    val tm = if (ev != null) ev.travelTime else 1.0
                    val fare = ((500 + 500 * abs(to - p.area)) * fm).toLong()
                    if (p.cash < fare) {
                        tell(p, "You cannot afford the fare (${naira(fare)}).")
                        return
                    }
                    p.cash -= fare
                    p.area = to
                    busy(p, (3000 * tm).toLong(), "Travelling to ${AREAS[to].name}")
                    tell(p, "Fare paid: ${naira(fare)}.")
                }
            }
        }
    }

    private fun advance(dtMs: Long) {
        hours += 24.0 * dtMs / DAY_MS
        val day = (hours / 24.0).toInt() + 1
        while (lastDay < day && phase == "playing") {
            lastDay++
            newDay(lastDay)
        }
    }

    private fun newDay(day: Int) {
        if (day > TOTAL_DAYS) {
            phase = "ended"
            announce("The game is over! See who built the best life.")
            return
        }
        val old = event
        if (old != null && day >= eventEndDay) {
            announce("${old.title} is over.")
            event = null
        }
        if (event == null && day >= 2 && rnd.nextInt(100) < 40) {
            val e = EVENTS[rnd.nextInt(EVENTS.size)]
            event = e
            eventEndDay = day + e.days
            announce("${e.title}: ${e.text}")
        }
        for (p in players) {
            p.food = max(0, p.food - 32)
            p.energy = max(0, p.energy - 6)
            if (p.food == 0) {
                p.energy = max(0, p.energy - 15)
                p.rep = max(0, p.rep - 3)
                tell(p, "You are starving! Eat something.")
            }
            if (day % RENT_EVERY == 0) {
                if (p.cash >= RENT) {
                    p.cash -= RENT
                    tell(p, "Rent paid: ${naira(RENT)}.")
                } else {
                    p.cash = 0
                    p.rep = max(0, p.rep - 10)
                    tell(p, "You could not pay your rent in full. The landlord is angry. Reputation -10.")
                }
            }
        }
    }

    private fun stateJson(): JSONObject {
        val o = JSONObject()
        val day = min(TOTAL_DAYS, (hours / 24.0).toInt() + 1)
        o.put("t", "state")
        o.put("phase", phase)
        o.put("day", day)
        o.put("hour", hours % 24.0)
        o.put("total", TOTAL_DAYS)
        o.put("host", hostName)
        o.put("rent", RENT)
        o.put("rentIn", ((day / RENT_EVERY) + 1) * RENT_EVERY - day)
        val ev = event
        if (ev != null) {
            o.put("ev", ev.title)
            o.put("evText", ev.text)
            o.put("rain", ev.rain)
            o.put("dark", ev.dark)
        }
        val now = SystemClock.elapsedRealtime()
        val arr = JSONArray()
        for (p in players) {
            val b = JSONObject()
            b.put("id", p.id)
            b.put("n", p.name)
            b.put("c", p.cash)
            b.put("e", p.energy)
            b.put("f", p.food)
            b.put("r", p.rep)
            b.put("a", p.area)
            b.put("b", max(0L, p.busyUntil - now))
            b.put("bt", p.busyTotal)
            b.put("bl", p.busyLabel)
            b.put("on", p.online)
            arr.put(b)
        }
        o.put("players", arr)
        return o
    }

    private fun broadcast() {
        synchronized(lock) {
            if (conns.isEmpty()) return
            val o = stateJson()
            val dead = ArrayList<Conn>()
            for (c in conns) {
                val p = c.player ?: continue
                o.put("you", p.id)
                if (!c.send(o.toString())) dead.add(c)
            }
            for (c in dead) {
                conns.remove(c)
                val p = c.player
                if (p != null) {
                    if (phase == "lobby") players.remove(p) else p.online = false
                }
                try {
                    c.socket.close()
                } catch (e: Exception) {
                }
            }
        }
    }
}
