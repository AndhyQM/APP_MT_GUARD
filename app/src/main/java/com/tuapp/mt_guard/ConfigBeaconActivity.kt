package com.tuapp.mt_guard

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ConfigBeaconActivity : AppCompatActivity() {

    companion object {
        const val PREFS = "MT_GUARD_Config"
        const val KEY_MAC = "beacon_mac"

        fun guardarMacDesde(context: Context, mac: String) {
            context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_MAC, mac)
                .apply()
        }
    }

    private lateinit var etMacAddress: EditText
    private lateinit var dotContacto: View
    private lateinit var tvContactoEstado: TextView
    private lateinit var dotArranque: View
    private lateinit var tvArranqueEstado: TextView
    private lateinit var tvUltimaActualizacion: TextView

    private var beaconScanner: BeaconScanner? = null
    private var formateandoMac = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config_beacon)

        enlazarVistas()
        cargarMacGuardada()
        configurarFormatoMac()
        configurarBotones()
        iniciarEscaneoBeacon()
    }

    private fun enlazarVistas() {
        etMacAddress = findViewById(R.id.etMacAddress)
        dotContacto = findViewById(R.id.dotContacto)
        tvContactoEstado = findViewById(R.id.tvContactoEstado)
        dotArranque = findViewById(R.id.dotArranque)
        tvArranqueEstado = findViewById(R.id.tvArranqueEstado)
        tvUltimaActualizacion = findViewById(R.id.tvUltimaActualizacion)

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun cargarMacGuardada() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val macGuardada = prefs.getString(KEY_MAC, "") ?: ""

        if (macGuardada.isNotEmpty()) {
            etMacAddress.setText(macGuardada)
        }
    }

    private fun configurarFormatoMac() {
        etMacAddress.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (formateandoMac || s == null) return
                formateandoMac = true

                val limpio = s.toString()
                    .uppercase()
                    .replace("[^0-9A-F]".toRegex(), "")
                    .take(12)

                val formateado = StringBuilder()
                for (i in limpio.indices) {
                    if (i > 0 && i % 2 == 0) {
                        formateado.append(":")
                    }
                    formateado.append(limpio[i])
                }

                etMacAddress.setText(formateado)
                etMacAddress.setSelection(formateado.length)

                formateandoMac = false
            }
        })
    }

    private fun configurarBotones() {
        findViewById<View>(R.id.btnGuardarMac).setOnClickListener {
            val mac = etMacAddress.text.toString().trim()

            if (!esFormatoMacValido(mac)) {
                Toast.makeText(
                    this,
                    "Formato de MAC inválido (AA:BB:CC:DD:EE:FF)",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            guardarMac(mac)
            reiniciarEscaneoBeacon()

            Toast.makeText(
                this,
                "MAC guardada correctamente",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ═══════════════════════════════════════════════
    // BEACON SCANNER — lee advertising sin conexión
    // ═══════════════════════════════════════════════

    private fun iniciarEscaneoBeacon() {
        val macGuardada = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_MAC, null)

        beaconScanner = BeaconScanner(
            context = this,
            targetMac = macGuardada,

            onVehicleState = { contacto, arranque ->
                runOnUiThread {
                    actualizarEstado(contacto, arranque)
                }
            },

            onError = { message ->
                runOnUiThread {
                    tvUltimaActualizacion.text = message
                }
            }
        )

        beaconScanner?.start()

        tvUltimaActualizacion.text = if (macGuardada != null) {
            "Escuchando beacon de $macGuardada..."
        } else {
            "Sin MAC configurada — escuchando cualquier MT Guard..."
        }
    }

    private fun reiniciarEscaneoBeacon() {
        beaconScanner?.stop()
        iniciarEscaneoBeacon()
    }

    private fun actualizarEstado(contactoAbierto: Boolean, arranqueActivo: Boolean) {
        if (contactoAbierto) {
            dotContacto.setBackgroundResource(R.drawable.status_dot_connected)
            tvContactoEstado.text = "ABIERTO"
            tvContactoEstado.setTextColor(
                ContextCompat.getColor(this, R.color.status_ok)
            )
        } else {
            dotContacto.setBackgroundResource(R.drawable.status_dot_disconnected)
            tvContactoEstado.text = "CERRADO"
            tvContactoEstado.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
        }

        if (arranqueActivo) {
            dotArranque.setBackgroundResource(R.drawable.status_dot_connected)
            tvArranqueEstado.text = "ACTIVO"
            tvArranqueEstado.setTextColor(
                ContextCompat.getColor(this, R.color.status_warning)
            )
        } else {
            dotArranque.setBackgroundResource(R.drawable.status_dot_disconnected)
            tvArranqueEstado.text = "INACTIVO"
            tvArranqueEstado.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
        }

        val hora = java.text.SimpleDateFormat(
            "HH:mm:ss", java.util.Locale.getDefault()
        ).format(java.util.Date())

        tvUltimaActualizacion.text = "Actualizado a las $hora"
    }

    private fun esFormatoMacValido(mac: String): Boolean {
        return mac.matches(
            "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$".toRegex()
        )
    }

    private fun guardarMac(mac: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_MAC, mac)
            .apply()
    }

    override fun onDestroy() {
        beaconScanner?.stop()
        super.onDestroy()
    }
}