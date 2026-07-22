package com.tuapp.mt_guard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class AuthActivity : AppCompatActivity() {

    private lateinit var tvTitulo: TextView
    private lateinit var etPin: EditText
    private lateinit var etPinConfirm: EditText
    private lateinit var etPregunta: EditText
    private lateinit var etRespuesta: EditText
    private lateinit var btnAccion: Button
    private lateinit var tvOlvide: TextView

    private var modo = Modo.LOGIN

    enum class Modo { CREAR_PIN, LOGIN, RECUPERAR, NUEVO_PIN }

    companion object {
        private const val PREFS = "MT_GUARD_Auth"
        private const val KEY_PIN = "pin_hash"
        private const val KEY_PREGUNTA = "pregunta"
        private const val KEY_RESPUESTA = "respuesta_hash"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        tvTitulo     = findViewById(R.id.tvTitulo)
        etPin        = findViewById(R.id.etPin)
        etPinConfirm = findViewById(R.id.etPinConfirm)
        etPregunta   = findViewById(R.id.etPregunta)
        etRespuesta  = findViewById(R.id.etRespuesta)
        btnAccion    = findViewById(R.id.btnAccion)
        tvOlvide     = findViewById(R.id.tvOlvide)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val pinGuardado = prefs.getString(KEY_PIN, null)

        if (pinGuardado == null) {
            mostrarCrearPin()
        } else {
            mostrarLogin()
            intentarBiometrico()
        }

        btnAccion.setOnClickListener {
            when (modo) {
                Modo.CREAR_PIN -> crearPin()
                Modo.LOGIN -> verificarPin()
                Modo.RECUPERAR -> verificarRespuesta()
                Modo.NUEVO_PIN -> crearNuevoPin()
            }
        }

        tvOlvide.setOnClickListener { mostrarRecuperar() }
    }

    // ══════════════════════════════════════════════════
    //  MODOS DE LA PANTALLA
    // ══════════════════════════════════════════════════

    private fun mostrarCrearPin() {
        modo = Modo.CREAR_PIN
        tvTitulo.text = "Crea tu PIN"
        etPin.visibility = View.VISIBLE
        etPinConfirm.visibility = View.VISIBLE
        etPregunta.visibility = View.VISIBLE
        etRespuesta.visibility = View.VISIBLE
        tvOlvide.visibility = View.GONE
        btnAccion.text = "CREAR PIN"
        etPin.hint = "PIN (4 dígitos)"
        etPinConfirm.hint = "Confirmar PIN"
        etPregunta.hint = "Ej: Nombre de tu mascota"
        etRespuesta.hint = "Tu respuesta"
    }

    private fun mostrarLogin() {
        modo = Modo.LOGIN
        tvTitulo.text = "Ingresa tu PIN"
        etPin.visibility = View.VISIBLE
        etPinConfirm.visibility = View.GONE
        etPregunta.visibility = View.GONE
        etRespuesta.visibility = View.GONE
        tvOlvide.visibility = View.VISIBLE
        btnAccion.text = "ENTRAR"
        etPin.hint = "PIN"
        etPin.setText("")
    }

    private fun mostrarRecuperar() {
        modo = Modo.RECUPERAR
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val pregunta = prefs.getString(KEY_PREGUNTA, "")

        tvTitulo.text = "Recuperar PIN"
        etPin.visibility = View.GONE
        etPinConfirm.visibility = View.GONE
        etPregunta.visibility = View.VISIBLE
        etRespuesta.visibility = View.VISIBLE
        tvOlvide.visibility = View.GONE
        btnAccion.text = "VERIFICAR"
        etPregunta.setText(pregunta)
        etPregunta.isEnabled = false
        etRespuesta.setText("")
        etRespuesta.hint = "Tu respuesta"
    }

    private fun mostrarNuevoPin() {
        modo = Modo.NUEVO_PIN
        tvTitulo.text = "Crea nuevo PIN"
        etPin.visibility = View.VISIBLE
        etPinConfirm.visibility = View.VISIBLE
        etPregunta.visibility = View.GONE
        etRespuesta.visibility = View.GONE
        tvOlvide.visibility = View.GONE
        btnAccion.text = "GUARDAR NUEVO PIN"
        etPin.setText("")
        etPinConfirm.setText("")
        etPin.hint = "Nuevo PIN (4 dígitos)"
        etPinConfirm.hint = "Confirmar nuevo PIN"
    }

    // ══════════════════════════════════════════════════
    //  LÓGICA
    // ══════════════════════════════════════════════════

    private fun crearPin() {
        val pin = etPin.text.toString().trim()
        val pinConfirm = etPinConfirm.text.toString().trim()
        val pregunta = etPregunta.text.toString().trim()
        val respuesta = etRespuesta.text.toString().trim()

        if (pin.length != 4) {
            Toast.makeText(this, "El PIN debe ser de 4 dígitos", Toast.LENGTH_SHORT).show()
            return
        }
        if (pin != pinConfirm) {
            Toast.makeText(this, "Los PIN no coinciden", Toast.LENGTH_SHORT).show()
            return
        }
        if (pregunta.isEmpty() || respuesta.isEmpty()) {
            Toast.makeText(this, "Llena la pregunta y respuesta secreta", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PIN, pin.hashCode().toString())
            .putString(KEY_PREGUNTA, pregunta)
            .putString(KEY_RESPUESTA, respuesta.lowercase().hashCode().toString())
            .apply()

        Toast.makeText(this, "PIN creado correctamente", Toast.LENGTH_SHORT).show()
        entrarApp()
    }

    private fun verificarPin() {
        val pin = etPin.text.toString().trim()
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val pinGuardado = prefs.getString(KEY_PIN, "")

        if (pin.hashCode().toString() == pinGuardado) {
            entrarApp()
        } else {
            Toast.makeText(this, "PIN incorrecto", Toast.LENGTH_SHORT).show()
            etPin.setText("")
        }
    }

    private fun verificarRespuesta() {
        val respuesta = etRespuesta.text.toString().trim()
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val respuestaGuardada = prefs.getString(KEY_RESPUESTA, "")

        if (respuesta.lowercase().hashCode().toString() == respuestaGuardada) {
            Toast.makeText(this, "Correcto, crea un nuevo PIN", Toast.LENGTH_SHORT).show()
            mostrarNuevoPin()
        } else {
            Toast.makeText(this, "Respuesta incorrecta", Toast.LENGTH_SHORT).show()
        }
    }

    private fun crearNuevoPin() {
        val pin = etPin.text.toString().trim()
        val pinConfirm = etPinConfirm.text.toString().trim()

        if (pin.length != 4) {
            Toast.makeText(this, "El PIN debe ser de 4 dígitos", Toast.LENGTH_SHORT).show()
            return
        }
        if (pin != pinConfirm) {
            Toast.makeText(this, "Los PIN no coinciden", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        prefs.edit().putString(KEY_PIN, pin.hashCode().toString()).apply()
        Toast.makeText(this, "PIN actualizado", Toast.LENGTH_SHORT).show()
        entrarApp()
    }

    // ══════════════════════════════════════════════════
    //  BIOMÉTRICO
    // ══════════════════════════════════════════════════

    private fun intentarBiometrico() {
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                        or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            != BiometricManager.BIOMETRIC_SUCCESS) {
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                entrarApp()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(this@AuthActivity, "Huella no reconocida", Toast.LENGTH_SHORT).show()
            }
        }

        val biometricPrompt = BiometricPrompt(this, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("MT GUARD")
            .setSubtitle("Usa tu huella para entrar")
            .setNegativeButtonText("Usar PIN")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun entrarApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}