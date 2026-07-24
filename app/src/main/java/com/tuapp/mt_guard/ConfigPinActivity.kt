package com.tuapp.mt_guard

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class ConfigPinActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "MT_GUARD_Auth"
        private const val KEY_PIN = "pin_hash"
    }

    private lateinit var pinIndicators: LinearLayout
    private lateinit var tvPinError: TextView
    private lateinit var pinDots: Array<View>
    private val currentPin = StringBuilder()
    private var verifying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config_pin)

        enlazarVistas()
        configurarTeclado()
        configurarHuella()
    }

    private fun enlazarVistas() {
        pinIndicators = findViewById(R.id.pinIndicators)
        tvPinError = findViewById(R.id.tvPinError)

        pinDots = arrayOf(
            findViewById(R.id.pinDot1),
            findViewById(R.id.pinDot2),
            findViewById(R.id.pinDot3),
            findViewById(R.id.pinDot4)
        )

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun configurarTeclado() {
        val keys = mapOf(
            R.id.key0 to "0", R.id.key1 to "1",
            R.id.key2 to "2", R.id.key3 to "3",
            R.id.key4 to "4", R.id.key5 to "5",
            R.id.key6 to "6", R.id.key7 to "7",
            R.id.key8 to "8", R.id.key9 to "9"
        )

        keys.forEach { entry ->
            findViewById<Button>(entry.key).setOnClickListener {
                agregarDigito(entry.value)
            }
        }

        findViewById<View>(R.id.keyDelete).setOnClickListener {
            eliminarDigito()
        }
    }

    private fun configurarHuella() {
        val btnHuella = findViewById<View>(R.id.keyFingerprint)

        // Verificar si el dispositivo soporta biometría
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
                    or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )

        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            // No hay huella configurada o no soporta → ocultar botón
            btnHuella.visibility = View.INVISIBLE
            return
        }

        val executor = ContextCompat.getMainExecutor(this)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult
            ) {
                super.onAuthenticationSucceeded(result)
                // Huella correcta → abrir configuración
                startActivity(
                    Intent(this@ConfigPinActivity, SettingsActivity::class.java)
                )
                finish()
            }

            override fun onAuthenticationError(
                errorCode: Int, errString: CharSequence
            ) {
                super.onAuthenticationError(errorCode, errString)
                // Usuario canceló o error → no hacer nada
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(
                    this@ConfigPinActivity,
                    "Huella no reconocida",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val biometricPrompt = BiometricPrompt(this, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("MT Guard")
            .setSubtitle("Usa tu huella para acceder")
            .setNegativeButtonText("Usar PIN")
            .build()

        btnHuella.setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    private fun agregarDigito(digit: String) {
        if (verifying || currentPin.length >= 4) return
        currentPin.append(digit)
        actualizarIndicadores()
        if (currentPin.length == 4) {
            verifying = true
            pinIndicators.postDelayed({ verificarPin() }, 180L)
        }
    }

    private fun eliminarDigito() {
        if (verifying || currentPin.isEmpty()) return
        currentPin.deleteCharAt(currentPin.lastIndex)
        actualizarIndicadores()
    }

    private fun actualizarIndicadores() {
        pinDots.forEachIndexed { index, view ->
            view.setBackgroundResource(
                if (index < currentPin.length) R.drawable.pin_dot_filled
                else R.drawable.pin_dot_empty
            )
        }
    }

    private fun verificarPin() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val savedPin = prefs.getString(KEY_PIN, "")
        val enteredPin = currentPin.toString()

        if (enteredPin.hashCode().toString() == savedPin) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        } else {
            mostrarError()
        }
    }

    private fun mostrarError() {
        tvPinError.visibility = View.VISIBLE

        ObjectAnimator.ofFloat(
            pinIndicators, View.TRANSLATION_X,
            0f, -18f, 18f, -14f, 14f, -8f, 8f, 0f
        ).apply {
            duration = 420L
            start()
        }

        val hapticType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }

        pinIndicators.performHapticFeedback(hapticType)

        pinIndicators.postDelayed({
            currentPin.clear()
            verifying = false
            actualizarIndicadores()
            tvPinError.visibility = View.INVISIBLE
        }, 650L)
    }
}