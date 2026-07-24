package com.tuapp.mt_guard

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class ConfigModuleActivity : AppCompatActivity() {

    private lateinit var dotConexion: View
    private lateinit var tvEstadoConexion: TextView
    private lateinit var tvVolumen: TextView
    private lateinit var seekVolumen: SeekBar
    private lateinit var switchPuerta: SwitchCompat
    private lateinit var switchArranque: SwitchCompat
    private lateinit var switchBeacon: SwitchCompat

    private lateinit var bleManager: BleManager

    private var conectado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config_module)

        enlazarVistas()
        configurarBleManager()

        // Si no está autenticado, bloquear y avisar
        if (!bleManager.isConnected) {
            bloquearPantalla()
            return
        }

        conectado = true
        actualizarEstadoConexion()
        configurarVolumen()
        configurarSwitches()
    }

    private fun enlazarVistas() {
        dotConexion = findViewById(R.id.dotConexion)
        tvEstadoConexion = findViewById(R.id.tvEstadoConexion)
        tvVolumen = findViewById(R.id.tvVolumen)
        seekVolumen = findViewById(R.id.seekVolumen)
        switchPuerta = findViewById(R.id.switchPuerta)
        switchArranque = findViewById(R.id.switchArranque)
        switchBeacon = findViewById(R.id.switchBeacon)

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
            onData = {},
            onError = { message ->
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun bloquearPantalla() {
        dotConexion.setBackgroundResource(R.drawable.status_dot_disconnected)
        tvEstadoConexion.text = "Módulo no conectado — inicia viaje seguro primero"
        tvEstadoConexion.setTextColor(
            ContextCompat.getColor(this, R.color.status_danger)
        )

        seekVolumen.isEnabled = false
        switchPuerta.isEnabled = false
        switchArranque.isEnabled = false
        switchBeacon.isEnabled = false

        seekVolumen.alpha = 0.4f
        switchPuerta.alpha = 0.4f
        switchArranque.alpha = 0.4f
        switchBeacon.alpha = 0.4f
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
    // VOLUMEN
    // ═══════════════════════════════════════════════

    private fun configurarVolumen() {
        seekVolumen.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvVolumen.text = "$progress"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val volumen = seekBar?.progress ?: return

                if (!verificarConexion()) return

                bleManager.sendCommand("VOL_$volumen")

                Toast.makeText(
                    this@ConfigModuleActivity,
                    "Volumen: $volumen",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    // ═══════════════════════════════════════════════
    // SWITCHES DE SALIDAS Y BEACON
    // ═══════════════════════════════════════════════

    private fun configurarSwitches() {
        switchPuerta.setOnCheckedChangeListener { _, isChecked ->
            if (!verificarConexion()) {
                switchPuerta.isChecked = !isChecked
                return@setOnCheckedChangeListener
            }

            if (isChecked) {
                bleManager.sendCommand("PUERTA_ON")
                Toast.makeText(this, "Desbloqueo de puerta habilitado", Toast.LENGTH_SHORT).show()
            } else {
                bleManager.sendCommand("PUERTA_OFF")
                Toast.makeText(this, "Desbloqueo de puerta deshabilitado", Toast.LENGTH_SHORT).show()
            }
        }

        switchArranque.setOnCheckedChangeListener { _, isChecked ->
            if (!verificarConexion()) {
                switchArranque.isChecked = !isChecked
                return@setOnCheckedChangeListener
            }

            if (isChecked) {
                bleManager.sendCommand("ARRANQUE_EN")
                Toast.makeText(this, "Arranque remoto habilitado", Toast.LENGTH_SHORT).show()
            } else {
                bleManager.sendCommand("ARRANQUE_DIS")
                Toast.makeText(this, "Arranque remoto deshabilitado", Toast.LENGTH_SHORT).show()
            }
        }

        switchBeacon.setOnCheckedChangeListener { _, isChecked ->
            if (!verificarConexion()) {
                switchBeacon.isChecked = !isChecked
                return@setOnCheckedChangeListener
            }

            if (isChecked) {
                bleManager.sendBeaconOn()
                Toast.makeText(this, "Beacon activado", Toast.LENGTH_SHORT).show()
            } else {
                bleManager.sendBeaconOff()
                Toast.makeText(this, "Beacon desactivado", Toast.LENGTH_SHORT).show()
            }
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