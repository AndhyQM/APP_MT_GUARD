package com.tuapp.mt_guard

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
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
    private lateinit var btnBiometria: Button

    private lateinit var tvOlvide: TextView
    private lateinit var fingerprintView: View

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    private val handler = Handler(Looper.getMainLooper())
    private var verificacionPendiente: Runnable? = null

    private var modo = Modo.LOGIN

    enum class Modo {
        CREAR_PIN,
        LOGIN,
        RECUPERAR,
        NUEVO_PIN
    }

    companion object {
        private const val PREFS = "MT_GUARD_Auth"
        private const val KEY_PIN = "pin_hash"
        private const val KEY_PREGUNTA = "pregunta"
        private const val KEY_RESPUESTA = "respuesta_hash"

        private const val RETARDO_VERIFICACION_PIN = 180L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_auth)

        enlazarVistas()
        configurarBiometria()
        configurarEventos()
        configurarVerificacionAutomatica()

        val prefs = getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        )

        val pinGuardado = prefs.getString(
            KEY_PIN,
            null
        )

        if (pinGuardado == null) {
            mostrarCrearPin()
        } else {
            mostrarLogin()

            fingerprintView.postDelayed({
                intentarBiometrico()
            }, 400L)
        }
    }

    override fun onDestroy() {
        verificacionPendiente?.let {
            handler.removeCallbacks(it)
        }

        super.onDestroy()
    }

    private fun enlazarVistas() {
        tvTitulo = findViewById(R.id.tvTitulo)

        etPin = findViewById(R.id.etPin)
        etPinConfirm = findViewById(R.id.etPinConfirm)
        etPregunta = findViewById(R.id.etPregunta)
        etRespuesta = findViewById(R.id.etRespuesta)

        btnAccion = findViewById(R.id.btnAccion)
        btnBiometria = findViewById(R.id.btnBiometria)

        tvOlvide = findViewById(R.id.tvOlvide)

        fingerprintView = findViewById(
            R.id.fingerprintView
        )
    }

    private fun configurarEventos() {
        btnAccion.setOnClickListener {
            when (modo) {
                Modo.CREAR_PIN -> crearPin()
                Modo.LOGIN -> verificarPin()
                Modo.RECUPERAR -> verificarRespuesta()
                Modo.NUEVO_PIN -> crearNuevoPin()
            }
        }

        btnBiometria.setOnClickListener {
            intentarBiometrico()
        }

        fingerprintView.setOnClickListener {
            intentarBiometrico()
        }

        tvOlvide.setOnClickListener {
            mostrarRecuperar()
        }
    }

    /*
     * Cuando el campo alcanza cuatro números,
     * programa automáticamente la comprobación.
     */
    private fun configurarVerificacionAutomatica() {
        etPin.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    text: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                    // No se necesita acción.
                }

                override fun onTextChanged(
                    text: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    // No se necesita acción.
                }

                override fun afterTextChanged(text: Editable?) {
                    verificacionPendiente?.let {
                        handler.removeCallbacks(it)
                    }

                    if (
                        modo != Modo.LOGIN ||
                        text?.length != 4
                    ) {
                        return
                    }

                    val tarea = Runnable {
                        if (
                            modo == Modo.LOGIN &&
                            etPin.text.toString().length == 4
                        ) {
                            verificarPin()
                        }
                    }

                    verificacionPendiente = tarea

                    handler.postDelayed(
                        tarea,
                        RETARDO_VERIFICACION_PIN
                    )
                }
            }
        )
    }

    // ═══════════════════════════════════════════════
    // MODOS DE PANTALLA
    // ═══════════════════════════════════════════════

    private fun mostrarCrearPin() {
        modo = Modo.CREAR_PIN

        cancelarVerificacionAutomatica()

        tvTitulo.text = "Crea tu PIN de seguridad"

        fingerprintView.visibility = View.GONE
        btnBiometria.visibility = View.GONE

        etPin.visibility = View.VISIBLE
        etPinConfirm.visibility = View.VISIBLE
        etPregunta.visibility = View.VISIBLE
        etRespuesta.visibility = View.VISIBLE

        btnAccion.visibility = View.VISIBLE
        tvOlvide.visibility = View.GONE

        etPregunta.isEnabled = true

        btnAccion.text = "CREAR PIN"

        etPin.hint = "PIN de 4 dígitos"
        etPinConfirm.hint = "Confirmar PIN"
        etPregunta.hint = "Ejemplo: nombre de tu mascota"
        etRespuesta.hint = "Tu respuesta"

        limpiarCampos()
        limpiarErrores()
    }

    private fun mostrarLogin() {
        modo = Modo.LOGIN

        cancelarVerificacionAutomatica()

        tvTitulo.text = "Confirma tu identidad para continuar"

        val disponible = biometriaDisponible()

        fingerprintView.visibility = if (disponible) {
            View.VISIBLE
        } else {
            View.GONE
        }

        btnBiometria.visibility = if (disponible) {
            View.VISIBLE
        } else {
            View.GONE
        }

        etPin.visibility = View.VISIBLE
        etPinConfirm.visibility = View.GONE
        etPregunta.visibility = View.GONE
        etRespuesta.visibility = View.GONE

        /*
         * Ya no se necesita presionar este botón.
         * El PIN se comprueba al completar 4 dígitos.
         */
        btnAccion.visibility = View.GONE

        tvOlvide.visibility = View.VISIBLE

        etPin.hint = "PIN de 4 dígitos"
        etPin.setText("")

        limpiarErrores()

        etPin.requestFocus()
    }

    private fun mostrarRecuperar() {
        modo = Modo.RECUPERAR

        cancelarVerificacionAutomatica()

        val prefs = getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        )

        val pregunta = prefs.getString(
            KEY_PREGUNTA,
            ""
        )

        tvTitulo.text = "Recuperar acceso"

        fingerprintView.visibility = View.GONE
        btnBiometria.visibility = View.GONE

        etPin.visibility = View.GONE
        etPinConfirm.visibility = View.GONE

        etPregunta.visibility = View.VISIBLE
        etRespuesta.visibility = View.VISIBLE

        btnAccion.visibility = View.VISIBLE
        tvOlvide.visibility = View.GONE

        btnAccion.text = "VERIFICAR RESPUESTA"

        etPregunta.setText(pregunta)
        etPregunta.isEnabled = false

        etRespuesta.setText("")
        etRespuesta.hint = "Escribe tu respuesta"

        limpiarErrores()
    }

    private fun mostrarNuevoPin() {
        modo = Modo.NUEVO_PIN

        cancelarVerificacionAutomatica()

        tvTitulo.text = "Crea un nuevo PIN"

        fingerprintView.visibility = View.GONE
        btnBiometria.visibility = View.GONE

        etPin.visibility = View.VISIBLE
        etPinConfirm.visibility = View.VISIBLE
        etPregunta.visibility = View.GONE
        etRespuesta.visibility = View.GONE

        btnAccion.visibility = View.VISIBLE
        tvOlvide.visibility = View.GONE

        btnAccion.text = "GUARDAR NUEVO PIN"

        etPin.setText("")
        etPinConfirm.setText("")

        etPin.hint = "Nuevo PIN de 4 dígitos"
        etPinConfirm.hint = "Confirmar nuevo PIN"

        limpiarErrores()
    }

    // ═══════════════════════════════════════════════
    // CREAR PIN
    // ═══════════════════════════════════════════════

    private fun crearPin() {
        val pin = etPin.text.toString().trim()
        val pinConfirm = etPinConfirm.text.toString().trim()
        val pregunta = etPregunta.text.toString().trim()
        val respuesta = etRespuesta.text.toString().trim()

        if (pin.length != 4) {
            mostrarErrorCampos(
                mensaje = "El PIN debe tener 4 dígitos",
                limpiar = true,
                etPin
            )
            return
        }

        if (pinConfirm.length != 4) {
            mostrarErrorCampos(
                mensaje = "Confirma el PIN",
                limpiar = true,
                etPinConfirm
            )
            return
        }

        if (pin != pinConfirm) {
            mostrarErrorCampos(
                mensaje = "Los PIN no coinciden",
                limpiar = true,
                etPin,
                etPinConfirm
            )
            return
        }

        if (pregunta.isEmpty()) {
            mostrarErrorCampos(
                mensaje = "Escribe una pregunta secreta",
                limpiar = false,
                etPregunta
            )
            return
        }

        if (respuesta.isEmpty()) {
            mostrarErrorCampos(
                mensaje = "Escribe la respuesta secreta",
                limpiar = false,
                etRespuesta
            )
            return
        }

        val prefs = getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        )

        prefs.edit()
            .putString(
                KEY_PIN,
                pin.hashCode().toString()
            )
            .putString(
                KEY_PREGUNTA,
                pregunta
            )
            .putString(
                KEY_RESPUESTA,
                respuesta
                    .lowercase()
                    .hashCode()
                    .toString()
            )
            .apply()

        Toast.makeText(
            this,
            "PIN creado correctamente",
            Toast.LENGTH_SHORT
        ).show()

        entrarApp()
    }

    // ═══════════════════════════════════════════════
    // VERIFICAR PIN
    // ═══════════════════════════════════════════════

    private fun verificarPin() {
        cancelarVerificacionAutomatica()

        val pin = etPin.text.toString().trim()

        if (pin.length != 4) {
            mostrarErrorCampos(
                mensaje = "Ingresa los 4 dígitos",
                limpiar = true,
                etPin
            )
            return
        }

        val prefs = getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        )

        val pinGuardado = prefs.getString(
            KEY_PIN,
            ""
        )

        if (pin.hashCode().toString() == pinGuardado) {
            etPin.isEnabled = false
            entrarApp()
        } else {
            mostrarErrorCampos(
                mensaje = "PIN incorrecto",
                limpiar = true,
                etPin
            )
        }
    }

    // ═══════════════════════════════════════════════
    // RECUPERAR PIN
    // ═══════════════════════════════════════════════

    private fun verificarRespuesta() {
        val respuesta = etRespuesta.text.toString().trim()

        if (respuesta.isEmpty()) {
            mostrarErrorCampos(
                mensaje = "Escribe tu respuesta",
                limpiar = false,
                etRespuesta
            )
            return
        }

        val prefs = getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        )

        val respuestaGuardada = prefs.getString(
            KEY_RESPUESTA,
            ""
        )

        if (
            respuesta
                .lowercase()
                .hashCode()
                .toString() == respuestaGuardada
        ) {
            Toast.makeText(
                this,
                "Respuesta correcta",
                Toast.LENGTH_SHORT
            ).show()

            mostrarNuevoPin()
        } else {
            mostrarErrorCampos(
                mensaje = "Respuesta incorrecta",
                limpiar = true,
                etRespuesta
            )
        }
    }

    private fun crearNuevoPin() {
        val pin = etPin.text.toString().trim()
        val pinConfirm = etPinConfirm.text.toString().trim()

        if (pin.length != 4) {
            mostrarErrorCampos(
                mensaje = "El PIN debe tener 4 dígitos",
                limpiar = true,
                etPin
            )
            return
        }

        if (pinConfirm.length != 4) {
            mostrarErrorCampos(
                mensaje = "Confirma el nuevo PIN",
                limpiar = true,
                etPinConfirm
            )
            return
        }

        if (pin != pinConfirm) {
            mostrarErrorCampos(
                mensaje = "Los PIN no coinciden",
                limpiar = true,
                etPin,
                etPinConfirm
            )
            return
        }

        val prefs = getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        )

        prefs.edit()
            .putString(
                KEY_PIN,
                pin.hashCode().toString()
            )
            .apply()

        Toast.makeText(
            this,
            "PIN actualizado correctamente",
            Toast.LENGTH_SHORT
        ).show()

        entrarApp()
    }

    // ═══════════════════════════════════════════════
    // BIOMETRÍA
    // ═══════════════════════════════════════════════

    private fun configurarBiometria() {
        val executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    entrarApp()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()

                    mostrarErrorBiometrico()

                    Toast.makeText(
                        this@AuthActivity,
                        "Huella no reconocida",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    super.onAuthenticationError(
                        errorCode,
                        errString
                    )

                    if (
                        errorCode !=
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode !=
                        BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode !=
                        BiometricPrompt.ERROR_CANCELED
                    ) {
                        Toast.makeText(
                            this@AuthActivity,
                            errString,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("MT GUARD")
            .setSubtitle("Confirma tu identidad")
            .setDescription(
                "Usa tu huella digital para acceder al sistema"
            )
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .setNegativeButtonText("Usar PIN")
            .build()
    }

    private fun biometriaDisponible(): Boolean {
        val biometricManager = BiometricManager.from(this)

        val resultado = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        )

        return resultado ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun intentarBiometrico() {
        if (modo != Modo.LOGIN) return

        if (!biometriaDisponible()) {
            Toast.makeText(
                this,
                "La biometría no está disponible",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        biometricPrompt.authenticate(promptInfo)
    }

    private fun mostrarErrorBiometrico() {
        animarSacudida(fingerprintView)
        ejecutarRespuestaTactil(fingerprintView)
    }

    // ═══════════════════════════════════════════════
    // ERRORES
    // ═══════════════════════════════════════════════

    private fun mostrarErrorCampos(
        mensaje: String,
        limpiar: Boolean,
        vararg campos: EditText
    ) {
        if (campos.isEmpty()) return

        campos.forEach { campo ->
            campo.setBackgroundResource(
                R.drawable.input_bg_error
            )

            campo.error = mensaje

            animarSacudida(campo)

            if (limpiar) {
                campo.setText("")
            }

            campo.postDelayed({
                campo.error = null

                campo.setBackgroundResource(
                    R.drawable.input_bg
                )
            }, 900L)
        }

        ejecutarRespuestaTactil(campos.first())

        campos.first().requestFocus()
    }

    private fun animarSacudida(view: View) {
        ObjectAnimator.ofFloat(
            view,
            View.TRANSLATION_X,
            0f,
            -18f,
            18f,
            -14f,
            14f,
            -8f,
            8f,
            0f
        ).apply {
            duration = 420L
            start()
        }
    }

    private fun ejecutarRespuestaTactil(view: View) {
        val tipo = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }

        view.performHapticFeedback(tipo)
    }

    private fun limpiarErrores() {
        val campos = arrayOf(
            etPin,
            etPinConfirm,
            etPregunta,
            etRespuesta
        )

        campos.forEach { campo ->
            campo.error = null
            campo.setBackgroundResource(
                R.drawable.input_bg
            )
        }
    }

    private fun limpiarCampos() {
        etPin.setText("")
        etPinConfirm.setText("")
        etPregunta.setText("")
        etRespuesta.setText("")
    }

    private fun cancelarVerificacionAutomatica() {
        verificacionPendiente?.let {
            handler.removeCallbacks(it)
        }

        verificacionPendiente = null
    }

    // ═══════════════════════════════════════════════
    // ENTRAR A LA APLICACIÓN
    // ═══════════════════════════════════════════════

    private fun entrarApp() {
        cancelarVerificacionAutomatica()

        val intent = Intent(
            this,
            ScannerActivity::class.java
        )

        startActivity(intent)

        overridePendingTransition(
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )

        finish()
    }

}