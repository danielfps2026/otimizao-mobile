package com.otimizao.mobile

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

enum class Scope { GLOBAL, SECURE, SYSTEM }

data class Item(
    val key: String,
    val scope: Scope,
    val label: String,
    val hint: String,
    val performance: String? = null,
    val saver: String? = null,
    val readOnly: Boolean = false
)

class MainActivity : Activity() {

    private lateinit var root: LinearLayout

    private val items = listOf(
        Item("window_animation_scale", Scope.GLOBAL, "Animação de janela", "1.0 = normal, 0 = desligada", "0.5", "0"),
        Item("transition_animation_scale", Scope.GLOBAL, "Animação de transição", "1.0 = normal, 0 = desligada", "0.5", "0"),
        Item("animator_duration_scale", Scope.GLOBAL, "Duração do animador", "1.0 = normal, 0 = desligada", "0.5", "0"),
        Item("low_power", Scope.GLOBAL, "Economia de bateria", "1 = ligada, 0 = desligada", "0", "1"),
        Item("stay_on_while_plugged_in", Scope.GLOBAL, "Manter tela ligada carregando", "0 = não, 7 = sempre", "0", "0"),
        Item("screen_off_timeout", Scope.SYSTEM, "Tempo até apagar a tela (ms)", "ex.: 30000 = 30 s", "60000", "15000"),
        Item("font_scale", Scope.SYSTEM, "Tamanho da fonte", "1.0 = normal, 1.3 = grande"),
        Item("screen_brightness", Scope.SYSTEM, "Brilho (0 a 255)", "valor manual do brilho"),
        Item("accelerometer_rotation", Scope.SYSTEM, "Rotação automática", "1 = ligada, 0 = desligada"),
        Item("adb_enabled", Scope.GLOBAL, "Depuração USB", "somente leitura", readOnly = true),
        Item("adb_wifi_enabled", Scope.GLOBAL, "Depuração sem fio", "somente leitura", readOnly = true),
        Item("development_settings_enabled", Scope.GLOBAL, "Opções do desenvolvedor", "somente leitura", readOnly = true)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sv = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(32))
        }
        sv.addView(root)
        setContentView(sv)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // ---------- Acesso às configurações ----------

    private fun get(scope: Scope, key: String): String? = try {
        when (scope) {
            Scope.GLOBAL -> Settings.Global.getString(contentResolver, key)
            Scope.SECURE -> Settings.Secure.getString(contentResolver, key)
            Scope.SYSTEM -> Settings.System.getString(contentResolver, key)
        }
    } catch (e: Exception) {
        null
    }

    private fun put(scope: Scope, key: String, value: String?): Boolean = try {
        when (scope) {
            Scope.GLOBAL -> Settings.Global.putString(contentResolver, key, value)
            Scope.SECURE -> Settings.Secure.putString(contentResolver, key, value)
            Scope.SYSTEM -> Settings.System.putString(contentResolver, key, value)
        }
    } catch (e: Exception) {
        false
    }

    private fun hasSecurePermission(): Boolean =
        checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    private fun canWrite(scope: Scope): Boolean =
        if (scope == Scope.SYSTEM) Settings.System.canWrite(this) || hasSecurePermission()
        else hasSecurePermission()

    // ---------- Backup / perfis ----------

    private val prefs get() = getSharedPreferences("backup", Context.MODE_PRIVATE)

    private fun saveBackupIfMissing() {
        if (prefs.getBoolean("saved", false)) return
        val e = prefs.edit()
        for (it in items) {
            if (it.readOnly) continue
            val v = get(it.scope, it.key)
            e.putBoolean("n_" + it.key, v == null)
            if (v != null) e.putString("v_" + it.key, v)
        }
        e.putBoolean("saved", true).apply()
    }

    private fun applyProfile(name: String, pick: (Item) -> String?) {
        saveBackupIfMissing()
        var ok = 0
        var fail = 0
        for (it in items) {
            val value = pick(it) ?: continue
            if (canWrite(it.scope) && put(it.scope, it.key, value)) ok++ else fail++
        }
        toast("$name: $ok aplicadas, $fail não aplicadas")
        render()
    }

    private fun restore() {
        if (!prefs.getBoolean("saved", false)) {
            toast("Nenhum backup salvo ainda.")
            return
        }
        var ok = 0
        var fail = 0
        for (it in items) {
            if (it.readOnly) continue
            val wasNull = prefs.getBoolean("n_" + it.key, true)
            val v = if (wasNull) null else prefs.getString("v_" + it.key, null)
            if (wasNull) continue
            if (canWrite(it.scope) && put(it.scope, it.key, v)) ok++ else fail++
        }
        toast("Restaurado: $ok valores, $fail falharam")
        render()
    }

    // ---------- Interface ----------

    private fun render() {
        root.removeAllViews()

        text("Otimizão Mobile", 26f, true)
        text("Versão 1.0", 13f, false, Color.GRAY)
        space()

        section("Seu aparelho")
        text("${Build.MANUFACTURER} ${Build.MODEL}")
        text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        val note = when {
            Build.MANUFACTURER.equals("xiaomi", true) || Build.MANUFACTURER.equals("redmi", true) || Build.MANUFACTURER.equals("poco", true) ->
                "Xiaomi/Redmi/POCO: ative também \"Depuração USB (Configurações de segurança)\" nas opções do desenvolvedor."
            Build.MANUFACTURER.equals("samsung", true) ->
                "Samsung: se o comando ADB falhar, desative o \"Auto Blocker\" e tente de novo."
            Build.VERSION.SDK_INT >= 34 ->
                "Android 14+: alguns itens podem ser restritos pelo fabricante."
            else -> null
        }
        if (note != null) text(note, 13f, false, Color.parseColor("#B26A00"))
        space()

        section("Permissões")
        val sec = hasSecurePermission()
        text(
            if (sec) "✅ WRITE_SECURE_SETTINGS concedida" else "❌ WRITE_SECURE_SETTINGS ainda não concedida",
            15f, true
        )
        if (!sec) {
            text("Conecte o celular ao computador com a depuração USB ativa e rode uma vez:", 13f)
            val cmd = "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
            text(cmd, 12f, false, Color.parseColor("#1B5E20")).apply { setTypeface(Typeface.MONOSPACE) }
            button("Copiar comando") {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("adb", cmd))
                toast("Comando copiado")
            }
        }
        val sys = Settings.System.canWrite(this)
        text(
            if (sys) "✅ Alterar configurações do sistema: permitido" else "❌ Alterar configurações do sistema: não permitido",
            15f, true
        )
        if (!sys) {
            button("Permitir modificar configurações do sistema") {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
                )
            }
        }
        button("Abrir opções do desenvolvedor") {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            } catch (e: Exception) {
                toast("Não foi possível abrir neste aparelho")
            }
        }
        space()

        section("Perfis de otimização")
        text("Um backup dos seus valores originais é salvo antes do primeiro perfil aplicado.", 13f, false, Color.GRAY)
        button("⚡ Desempenho (animações rápidas, sem economia)") { applyProfile("Desempenho") { it.performance } }
        button("🔋 Economia (animações off, bateria, tela 15 s)") { applyProfile("Economia") { it.saver } }
        button("↩ Restaurar valores originais") { restore() }
        space()

        section("Configurações detectadas neste aparelho")
        for (it in items) {
            val value = get(it.scope, it.key)
            val status = when {
                value == null -> "⚪ Não disponível neste aparelho"
                it.readOnly -> "🔒 Somente leitura"
                canWrite(it.scope) -> "✅ Compatível (toque para editar)"
                else -> "🟡 Compatível, falta permissão"
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(Color.parseColor("#F2F2F2"))
                val lp = LinearLayout.LayoutParams(-1, -2)
                lp.setMargins(0, dp(4), 0, dp(4))
                layoutParams = lp
            }
            row.addView(tv(it.label, 15f, true))
            row.addView(tv("${it.key} = ${value ?: "—"}", 12f, false, Color.DKGRAY))
            row.addView(tv(status, 12f))
            if (value != null && !it.readOnly && canWrite(it.scope)) {
                row.setOnClickListener { _ -> edit(it) }
            }
            root.addView(row)
        }
    }

    private fun edit(item: Item) {
        val input = EditText(this)
        input.setText(get(item.scope, item.key) ?: "")
        AlertDialog.Builder(this)
            .setTitle(item.label)
            .setMessage("${item.key}\n${item.hint}")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                saveBackupIfMissing()
                val ok = put(item.scope, item.key, input.text.toString().trim())
                toast(if (ok) "Salvo" else "O sistema recusou a alteração")
                render()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------- Helpers de UI ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(s: String, size: Float = 14f, bold: Boolean = false, color: Int = Color.BLACK) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(2))
        }

    private fun text(s: String, size: Float = 14f, bold: Boolean = false, color: Int = Color.BLACK): TextView =
        tv(s, size, bold, color).also { root.addView(it) }

    private fun section(s: String) {
        text(s, 18f, true, Color.parseColor("#0D47A1"))
    }

    private fun space() {
        root.addView(android.view.View(this), LinearLayout.LayoutParams(-1, dp(14)))
    }

    private fun button(label: String, onClick: () -> Unit) {
        root.addView(Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        })
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
