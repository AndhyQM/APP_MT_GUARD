package com.tuapp.mt_guard

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

class ConfigModuleActivity : AppCompatActivity() {

    private lateinit var dotConexion: View
    private lateinit var tvEstadoConexion: TextView

    private lateinit var tvVolumen: TextView
    private lateinit var seekVolumen: SeekBar

    private lateinit var tvTimeout: TextView
    private lateinit var seekTimeout: SeekBar

    private lateinit var tvPulso: TextView
    private lateinit var seekPulso: SeekBar

    private lateinit var tvArrTimeout: TextView
    private lateinit var seekArrTimeout: SeekBar

    private lateinit var tvPostCrank: TextView
    private lateinit var seekPostCrank: SeekBar

    private lateinit var tvContactoOff: TextView
    private lateinit var seekContactoOff: SeekBar

    private lateinit var tvDebounce: TextView
    private lateinit var seekDebounce: SeekBar

    private lateinit var switchPuerta: SwitchCompat
    private lateinit var switchArranque: SwitchCompat
    private lateinit var switchBeacon: SwitchCompat

    private lateinit var etNombre: EditText
    private lateinit var btnGuardarNombre: Button
    private lateinit var btnReiniciar: Button

    private lateinit var bleManager: BleManager

    private var conectado = false

    /*
     * true mientras se aplican a la UI los valores que mandó el
     * módulo, para que los listeners no reenvíen esos mismos
     * valores de vuelta.
     */
    private var cargandoConfig = false

    /*
     * RANGOS — deben coincidir con los del firmware (main.c):
     *   vol 0-30 | tmo 1-60 min | pul 100-5000 ms
     *   art 200-3000 ms | pck 500-10000 ms
     *   cof 200-10000 ms | dbn 1-10
     *
     * Los SeekBar de Android siempre arrancan en 0, así que cada
     * parámetro se mapea: valor = MIN + progreso * PASO.
     */
    private data class Rango(val min: Int, val max: Int, val paso: Int) {
        val steps: Int get() = (max - min) / paso
        fun aValor(progress: Int) = min + progress * paso
        fun aProgreso(valor: Int) =
            ((valor.coerceIn(min, max) - min) / paso)
    }

    private val rangoTmo   = Rango(1, 60, 1)          // minutos
    private val rangoPul   = Rango(100, 5000, 100)    // ms
    private val rangoArt   = Rango(200, 3000, 100)    // ms
    private val rangoPck   = Rango(500, 10000, 250)   // ms
    private val rangoCof   = Rango(200, 10000, 100)   // ms
    private val rangoDbn   = Rango(1, 10, 1)          // muestras

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config_module)

        enlazarVistas()
        configurarBleManager()

        // Si no está conectado, bloquear y avisar
        if (!bleManager.isConnected) {
            bloquearPantalla()
            return
        }

        conectado = true
        actualizarEstadoConexion()
        configurarVolumen()
        configurarTiempos()
        configurarSwitches()
        configurarDispositivo()

        // Pide la config actual: {"cmd":"GET"}. El firmware responde
        // {"tipo":"config",...} y la UI se llena con los valores REALES.
        bleManager.requestConfig()
    }

    private fun enlazarVistas() {
        dotConexion = findViewById(R.id.dotConexion)
        tvEstadoConexion = findViewById(R.id.tvEstadoConexion)

        tvVolumen = findViewById(R.id.tvVolumen)
        seekVolumen = findViewById(R.id.seekVolumen)

        tvTimeout = findViewById(R.id.tvTimeout)
        seekTimeout = findViewById(R.id.seekTimeout)

        tvPulso = findViewById(R.id.tvPulso)
        seekPulso = findViewById(R.id.seekPulso)

        tvArrTimeout = findViewById(R.id.tvArrTimeout)
        seekArrTimeout = findViewById(R.id.seekArrTimeout)

        tvPostCrank = findViewById(R.id.tvPostCrank)
        seekPostCrank = findViewById(R.id.seekPostCrank)

        tvContactoOff = findViewById(R.id.tvContactoOff)
        seekContactoOff = findViewById(R.id.seekContactoOff)

        tvDebounce = findViewById(R.id.tvDebounce)
        seekDebounce = findViewById(R.id.seekDebounce)

        switchPuerta = findViewById(R.id.switchPuerta)
        switchArranque = findViewById(R.id.switchArranque)
        switchBeacon = findViewById(R.id.switchBeacon)

        etNombre = findViewById(R.id.etNombre)
        btnGuardarNombre = findViewById(R.id.btnGuardarNombre)
        btnReiniciar = findViewById(R.id.btnReiniciar)

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun configurarBleManager() {
        bleManager = BleManager(
            context = this,
            onConnectionChange = { connected ->
                runOnUiThread {
                    conectado = connected
                    actualizarEstadoConexion()

                    if (!connected) {
                        bloquearPantalla()
                    }
                }
            },
            onAuthenticated = {},
            onData = { data ->
                runOnUiThread {
                    procesarRespuestaModulo(data)
                }
            },
            onError = { message ->
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // ═══════════════════════════════════════════════
    // RESPUESTAS DEL MÓDULO (APP_TX)
    //
    //   {"tipo":"config","vol":30,"tmo":5,"pul":1000,"art":500,
    //    "pck":3000,"cof":1500,"dbn":3,"pta":1,"arq":1,"bcn":1,
    //    "nom":"MT GUARD 01"}
    //   {"ok":true}                → config guardada en NVS
    //   {"ok":false,"err":"..."}   → rechazado
    // ═══════════════════════════════════════════════

    private fun procesarRespuestaModulo(data: String) {
        val json = try {
            JSONObject(data)
        } catch (_: Exception) {
            return
        }

        // Config completa del módulo → poblar toda la UI
        if (json.optString("tipo") == "config") {
            cargandoConfig = true

            val vol = json.optInt("vol", 30)
            seekVolumen.progress = vol
            tvVolumen.text = "$vol"

            aplicarSeek(seekTimeout, tvTimeout, rangoTmo,
                json.optInt("tmo", 5), " min")

            aplicarSeek(seekPulso, tvPulso, rangoPul,
                json.optInt("pul", 1000), " ms")

            aplicarSeek(seekArrTimeout, tvArrTimeout, rangoArt,
                json.optInt("art", 500), " ms")

            aplicarSeek(seekPostCrank, tvPostCrank, rangoPck,
                json.optInt("pck", 3000), " ms")

            aplicarSeek(seekContactoOff, tvContactoOff, rangoCof,
                json.optInt("cof", 1500), " ms")

            aplicarSeek(seekDebounce, tvDebounce, rangoDbn,
                json.optInt("dbn", 3), "")

            switchPuerta.isChecked = json.optInt("pta", 1) == 1
            switchArranque.isChecked = json.optInt("arq", 1) == 1
            switchBeacon.isChecked = json.optInt("bcn", 1) == 1

            val nombre = json.optString("nom", "")
            if (nombre.isNotEmpty()) {
                etNombre.setText(nombre)
            }

            cargandoConfig = false
            return
        }

        // Confirmación o rechazo de un cambio
        if (json.has("ok")) {
            if (!json.optBoolean("ok")) {
                val err = json.optString("err", "desconocido")
                Toast.makeText(
                    this,
                    "El módulo rechazó el cambio: $err",
                    Toast.LENGTH_SHORT
                ).show()

                // Resincronizar la UI con los valores reales
                bleManager.requestConfig()
            }
        }
    }

    private fun aplicarSeek(
        seek: SeekBar,
        tv: TextView,
        rango: Rango,
        valor: Int,
        unidad: String
    ) {
        seek.progress = rango.aProgreso(valor)
        tv.text = "${rango.aValor(seek.progress)}$unidad"
    }

    private fun bloquearPantalla() {
        dotConexion.setBackgroundResource(R.drawable.status_dot_disconnected)
        tvEstadoConexion.text = "Módulo no conectado — conéctate primero"
        tvEstadoConexion.setTextColor(
            ContextCompat.getColor(this, R.color.status_danger)
        )

        val controles = listOf<View>(
            seekVolumen, seekTimeout, seekPulso, seekArrTimeout,
            seekPostCrank, seekContactoOff, seekDebounce,
            switchPuerta, switchArranque, switchBeacon,
            etNombre, btnGuardarNombre, btnReiniciar
        )

        controles.forEach {
            it.isEnabled = false
            it.alpha = 0.4f
        }
    }

    private fun actualizarEstadoConexion() {
        if (conectado) {
            dotConexion.setBackgroundResource(R.drawable.status_dot_connected)
            tvEstadoConexion.text = "Módulo conectado"
            tvEstadoConexion.setTextColor(
                ContextCompat.getColor(this, R.color.status_ok)
            )
        } else {
            dotConexion.setBackgroundResource(R.drawable.status_dot_disconnected)
            tvEstadoConexion.text = "Módulo no conectado"
            tvEstadoConexion.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
        }
    }

    // ═══════════════════════════════════════════════
    // VOLUMEN → {"vol":n}   (rango directo 0-30, sin mapeo)
    // ═══════════════════════════════════════════════

    private fun configurarVolumen() {
        seekVolumen.max = 30

        seekVolumen.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvVolumen.text = "$progress"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (cargandoConfig) return
                val volumen = seekBar?.progress ?: return

                if (!verificarConexion()) return

                bleManager.sendCommand("{\"vol\":$volumen}")

                Toast.makeText(
                    this@ConfigModuleActivity,
                    "Volumen: $volumen",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    // ═══════════════════════════════════════════════
    // TIEMPOS → {"tmo":n} {"pul":n} {"art":n} {"pck":n}
    //           {"cof":n} {"dbn":n}
    // El valor se envía SOLO al soltar el dedo, para no
    // bombardear el módulo mientras se arrastra.
    // ═══════════════════════════════════════════════

    private fun configurarTiempos() {
        configurarSeekParametro(seekTimeout, tvTimeout, rangoTmo, " min", "tmo")
        configurarSeekParametro(seekPulso, tvPulso, rangoPul, " ms", "pul")
        configurarSeekParametro(seekArrTimeout, tvArrTimeout, rangoArt, " ms", "art")
        configurarSeekParametro(seekPostCrank, tvPostCrank, rangoPck, " ms", "pck")
        configurarSeekParametro(seekContactoOff, tvContactoOff, rangoCof, " ms", "cof")
        configurarSeekParametro(seekDebounce, tvDebounce, rangoDbn, "", "dbn")
    }

    private fun configurarSeekParametro(
        seek: SeekBar,
        tv: TextView,
        rango: Rango,
        unidad: String,
        claveJson: String
    ) {
        seek.max = rango.steps

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tv.text = "${rango.aValor(progress)}$unidad"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (cargandoConfig) return
                val progreso = seekBar?.progress ?: return

                if (!verificarConexion()) return

                val valor = rango.aValor(progreso)
                bleManager.sendCommand("{\"$claveJson\":$valor}")

                Toast.makeText(
                    this@ConfigModuleActivity,
                    "Enviado: $valor$unidad",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    // ═══════════════════════════════════════════════
    // SWITCHES → {"pta":b} {"arq":b} {"bcn":b}
    // ═══════════════════════════════════════════════

    private fun configurarSwitches() {
        switchPuerta.setOnCheckedChangeListener { _, isChecked ->
            if (cargandoConfig) return@setOnCheckedChangeListener

            if (!verificarConexion()) {
                switchPuerta.isChecked = !isChecked
                return@setOnCheckedChangeListener
            }

            bleManager.sendCommand("{\"pta\":$isChecked}")

            Toast.makeText(
                this,
                if (isChecked) "Desbloqueo de puerta habilitado"
                else "Desbloqueo de puerta deshabilitado",
                Toast.LENGTH_SHORT
            ).show()
        }

        switchArranque.setOnCheckedChangeListener { _, isChecked ->
            if (cargandoConfig) return@setOnCheckedChangeListener

            if (!verificarConexion()) {
                switchArranque.isChecked = !isChecked
                return@setOnCheckedChangeListener
            }

            bleManager.sendCommand("{\"arq\":$isChecked}")

            Toast.makeText(
                this,
                if (isChecked) "Arranque remoto habilitado"
                else "Arranque remoto deshabilitado",
                Toast.LENGTH_SHORT
            ).show()
        }

        switchBeacon.setOnCheckedChangeListener { _, isChecked ->
            if (cargandoConfig) return@setOnCheckedChangeListener

            if (!verificarConexion()) {
                switchBeacon.isChecked = !isChecked
                return@setOnCheckedChangeListener
            }

            if (isChecked) {
                bleManager.sendBeaconOn()      // {"bcn":true}
            } else {
                bleManager.sendBeaconOff()     // {"bcn":false}
            }

            Toast.makeText(
                this,
                if (isChecked) "Beacon activado" else "Beacon desactivado",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ═══════════════════════════════════════════════
    // NOMBRE Y REINICIO → {"nom":"..."} / {"rst":true}
    // ═══════════════════════════════════════════════

    private fun configurarDispositivo() {
        btnGuardarNombre.setOnClickListener {
            if (!verificarConexion()) return@setOnClickListener

            val nombre = etNombre.text.toString().trim()

            if (nombre.isEmpty()) {
                Toast.makeText(
                    this,
                    "El nombre no puede estar vacío",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // JSONObject escapa comillas y caracteres especiales
            val comando = JSONObject().put("nom", nombre).toString()
            bleManager.sendCommand(comando)

            Toast.makeText(
                this,
                "Nombre guardado. Reinicia el módulo para aplicarlo.",
                Toast.LENGTH_LONG
            ).show()
        }

        btnReiniciar.setOnClickListener {
            if (!verificarConexion()) return@setOnClickListener

            bleManager.sendCommand("{\"rst\":true}")

            Toast.makeText(
                this,
                "Reiniciando módulo... la conexión se perderá unos segundos",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun verificarConexion(): Boolean {
        if (!conectado) {
            Toast.makeText(
                this,
                "Conecta al módulo MT Guard primero",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        return true
    }
}