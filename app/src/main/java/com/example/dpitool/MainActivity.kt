package com.example.dpitool

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private val BG = 0xFF0B0F14.toInt()
    private val CARD = 0xFF151B23.toInt()
    private val ACCENT = 0xFFFFD400.toInt()
    private val MUTED = 0xFF8A94A3.toInt()
    private val GOOD = 0xFF3DDC84.toInt()
    private val BAD = 0xFFFF5A5F.toInt()

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tvCurrent: TextView
    private lateinit var tvPhysical: TextView
    private lateinit var tvStatus: TextView
    private lateinit var input: EditText

    private fun dp(i: Int) = (i * resources.displayMetrics.density).toInt()

    private fun box(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // Jalankan perintah shell lewat Shizuku (newProcess private di API 13 -> reflection)
    private fun shell(cmd: String): String {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText() +
                p.errorStream.bufferedReader().readText()
        p.waitFor()
        return out.trim()
    }

    private fun ready(): Boolean {
        if (!Shizuku.pingBinder()) { toast("Jalankan Shizuku dulu"); return false }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(0); return false
        }
        return true
    }

    private fun refresh() {
        val ok = try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }
        if (!ok) {
            tvStatus.text = "● Shizuku belum aktif / belum diizinkan"
            tvStatus.setTextColor(BAD)
            return
        }
        val out = shell("wm density")
        val phys = Regex("Physical density: (\\d+)").find(out)?.groupValues?.get(1)
        val over = Regex("Override density: (\\d+)").find(out)?.groupValues?.get(1)
        tvCurrent.text = over ?: phys ?: "?"
        tvPhysical.text = "Bawaan HP: ${phys ?: "?"}"
        tvStatus.text = "● Shizuku terhubung"
        tvStatus.setTextColor(GOOD)
    }

    private fun applyDpi() {
        val v = input.text.toString().toIntOrNull()
        if (v == null || v < 200 || v > 800) { toast("Masukkan DPI 200 - 800"); return }
        if (!ready()) return
        shell("wm density $v")
        refresh()
        val revert = Runnable { shell("wm density reset"); refresh() }
        handler.postDelayed(revert, 10_000)
        AlertDialog.Builder(this)
            .setTitle("Pakai DPI ini?")
            .setMessage("Otomatis kembali dalam 10 detik kalau tidak dikonfirmasi.")
            .setCancelable(false)
            .setPositiveButton("Pakai") { _, _ -> handler.removeCallbacks(revert) }
            .setNegativeButton("Kembalikan") { _, _ -> handler.removeCallbacks(revert); revert.run() }
            .show()
    }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = t; textSize = size; setTextColor(color); gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(40), dp(20), dp(24))
        }

        // Header
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_duck)
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(96))
        })
        root.addView(text("Faisal Stabilizer", 26f, Color.WHITE, true).apply {
            setPadding(0, dp(12), 0, 0)
        })
        root.addView(text("DPI Changer", 14f, ACCENT))

        // Kartu DPI saat ini
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = box(CARD, 20, 0xFF263040.toInt())
            setPadding(dp(16), dp(20), dp(16), dp(20))
        }
        card.addView(text("DPI SAAT INI", 12f, MUTED))
        tvCurrent = text("--", 64f, ACCENT, true)
        card.addView(tvCurrent)
        tvPhysical = text("Bawaan HP: --", 13f, MUTED)
        card.addView(tvPhysical)
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })

        tvStatus = text("● ...", 13f, MUTED).apply { setPadding(0, dp(12), 0, dp(12)) }
        root.addView(tvStatus)

        // Input
        input = EditText(this).apply {
            hint = "Masukkan DPI (200 - 800)"
            setHintTextColor(MUTED); setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.CENTER; textSize = 18f
            background = box(CARD, 14, 0xFF263040.toInt())
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        root.addView(input, LinearLayout.LayoutParams(-1, -2))

        // Pilihan cepat
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(320, 360, 400, 440).forEach { v ->
            val chip = text("$v", 15f, ACCENT, true).apply {
                background = box(0xFF1E2630.toInt(), 12)
                setPadding(0, dp(12), 0, dp(12))
                setOnClickListener { input.setText(v.toString()) }
            }
            chips.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
        root.addView(chips, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        // Tombol
        val apply = text("TERAPKAN DPI", 16f, Color.BLACK, true).apply {
            background = box(ACCENT, 16)
            setPadding(0, dp(16), 0, dp(16))
            setOnClickListener { applyDpi() }
        }
        root.addView(apply, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })

        val reset = text("KEMBALIKAN KE BAWAAN", 14f, ACCENT, true).apply {
            background = box(Color.TRANSPARENT, 16, ACCENT)
            setPadding(0, dp(14), 0, dp(14))
            setOnClickListener { if (ready()) { shell("wm density reset"); refresh() } }
        }
        root.addView(reset, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        root.addView(text("Tips: turunkan DPI sedikit demi sedikit, jangan lebih dari ±30% dari bawaan.", 12f, MUTED).apply {
            setPadding(0, dp(20), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            addView(root)
        })

        Shizuku.addRequestPermissionResultListener { _, _ -> runOnUiThread { refresh() } }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }
}
