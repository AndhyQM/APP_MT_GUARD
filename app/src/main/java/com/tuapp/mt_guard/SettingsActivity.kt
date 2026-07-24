package com.tuapp.mt_guard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var optionModule: View
    private lateinit var bleManager: BleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        configurarBleManager()
        configurarBotonRegresar()
        configurarOpciones()
    }

    private fun configurarBleManager() {
        bleManager = BleManager(
            context = this,
            onConnectionChange = { connected ->
                runOnUiThread {
                    actualizarBotonModule(connected)
                }
            },
            onAuthenticated = {},
            onData = {},
            onError = {}
        )
    }

    private fun configurarBotonRegresar() {
        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun configurarOpciones() {
        optionModule = findViewById(R.id.optionModule)

        findViewById<View>(R.id.optionBeacon).setOnClickListener {
            startActivity(Intent(this, ConfigBeaconActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        findViewById<View>(R.id.optionProfile).setOnClickListener {
            startActivity(Intent(this, ConfigProfileActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        optionModule.setOnClickListener {
            if (!bleManager.isConnected) {
                Toast.makeText(
                    this,
                    "Conecta al módulo MT Guard primero",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            startActivity(Intent(this, ConfigModuleActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Estado inicial
        actualizarBotonModule(bleManager.isConnected)
    }

    private fun actualizarBotonModule(connected: Boolean) {
        optionModule.alpha = if (connected) 1f else 0.4f
        optionModule.isEnabled = connected
        optionModule.isClickable = connected
    }

    override fun onResume() {
        super.onResume()
        actualizarBotonModule(bleManager.isConnected)
    }
}