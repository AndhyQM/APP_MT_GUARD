package com.tuapp.mt_guard

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.Locale

class AuthActivity : AppCompatActivity() {

    private lateinit var authRoot: View
    private lateinit var tvTitulo: TextView

    private lateinit var etPin: EditText
    private lateinit var etPinConfirm: EditText
    private lateinit var tvPregunta: TextView
    private lateinit var etRespuesta: EditText
    private lateinit var etPhoneAlert: EditText
    private lateinit var layoutPhoneAlert: View

    private var preguntaSeleccionada: Int = -1

    private lateinit var btnAccion: Button
    private lateinit var btnBiometria: Button

    private lateinit var tvOlvide: TextView
    private lateinit var fingerprintView: View
    private lateinit var keypadContainer: View

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    private val handler = Handler(
        Looper.getMainLooper()
    )

    private var verificacionPendiente: Runnable? = null
    private var biometriaPendiente: Runnable? = null

    private var campoPinActivo: EditText? = null

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

        private const val KEY_PREGUNTA_INDEX = "pregunta_index"

        private const val RETARDO_VERIFICACION_PIN = 180L
        private const val RETARDO_BIOMETRIA = 1100L

        val PREGUNTAS_SECRETAS = arrayOf(
            "¿Nombre de tu primera mascota?",
            "¿Nombre de tu mejor amigo de infancia?",
            "¿En qué ciudad naciste?",
            "¿Cuál es tu comida favorita?",
            "¿Nombre de tu primer colegio?",
            "¿Cuál es tu número de la suerte?",
            "¿Apodo que te ponían de niño?",
            "¿Marca de tu primer vehículo?"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        setContentView(R.layout.activity_auth)

        enlazarVistas()
        configurarTecladoNumericoFijo()
        configurarBiometria()
        configurarEventos()
        configurarVerificacionAutomatica()

        authRoot.requestFocus()

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
            programarBiometriaAutomatica()
        }
    }

    override fun onDestroy() {
        cancelarVerificacionAutomatica()
        cancelarBiometriaAutomatica()

        super.onDestroy()
    }

    // ═══════════════════════════════════════════════
    // VISTAS
    // ═══════════════════════════════════════════════

    private fun enlazarVistas() {
        authRoot = findViewById(R.id.authRoot)
        tvTitulo = findViewById(R.id.tvTitulo)

        etPin = findViewById(R.id.etPin)
        etPinConfirm = findViewById(R.id.etPinConfirm)
        tvPregunta = findViewById(R.id.tvPregunta)
        etRespuesta = findViewById(R.id.etRespuesta)
        etPhoneAlert = findViewById(R.id.etPhoneAlert)
        layoutPhoneAlert = findViewById(R.id.layoutPhoneAlert)

        btnAccion = findViewById(R.id.btnAccion)
        btnBiometria = findViewById(R.id.btnBiometria)

        tvOlvide = findViewById(R.id.tvOlvide)

        fingerprintView = findViewById(
            R.id.fingerprintView
        )

        keypadContainer = findViewById(
            R.id.keypadContainer
        )
    }

    // ═══════════════════════════════════════════════
    // TECLADO NUMÉRICO PROPIO
    // ═══════════════════════════════════════════════

    private fun configurarTecladoNumericoFijo() {

        etPin.showSoftInputOnFocus = false
        etPinConfirm.showSoftInputOnFocus = false

        etPin.isFocusableInTouchMode = true
        etPinConfirm.isFocusableInTouchMode = true

        configurarCampoPin(etPin)
        configurarCampoPin(etPinConfirm)

        configurarCampoTexto(etRespuesta)
        configurarCampoTexto(etPhoneAlert)

        tvPregunta.setOnClickListener {
            mostrarSelectorPreguntas()
        }

        val teclasNumericas = mapOf(
            R.id.key0 to "0",
            R.id.key1 to "1",
            R.id.key2 to "2",
            R.id.key3 to "3",
            R.id.key4 to "4",
            R.id.key5 to "5",
            R.id.key6 to "6",
            R.id.key7 to "7",
            R.id.key8 to "8",
            R.id.key9 to "9"
        )

        teclasNumericas.forEach { (id, numero) ->
            findViewById<View>(id).setOnClickListener { tecla ->
                tecla.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP
                )

                agregarNumeroAlPin(numero)
            }
        }

        findViewById<View>(
            R.id.keyBackspace
        ).setOnClickListener { tecla ->

            tecla.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP
            )

            borrarUltimoNumero()
        }

        findViewById<View>(
            R.id.keyClear
        ).setOnClickListener { tecla ->

            tecla.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP
            )

            limpiarCampoPinActivo()
        }
    }

    private fun configurarCampoPin(
        campo: EditText
    ) {
        campo.showSoftInputOnFocus = false

        campo.setOnClickListener {
            seleccionarCampoPin(campo)
        }

        campo.setOnFocusChangeListener { _, tieneFoco ->
            if (tieneFoco) {
                campo.showSoftInputOnFocus = false
                seleccionarCampoPin(campo)
            }
        }
    }

    private fun configurarCampoTexto(
        campo: EditText
    ) {
        campo.showSoftInputOnFocus = true

        campo.setOnClickListener {
            ocultarTecladoNumericoFijo()
            mostrarTecladoSistema(campo)
        }

        campo.setOnFocusChangeListener { _, tieneFoco ->
            if (tieneFoco) {
                ocultarTecladoNumericoFijo()
                mostrarTecladoSistema(campo)
            }
        }
    }

    private fun seleccionarCampoPin(
        campo: EditText
    ) {
        if (modo == Modo.RECUPERAR) {
            return
        }

        if (
            campo.visibility != View.VISIBLE ||
            !campo.isEnabled
        ) {
            return
        }

        campo.showSoftInputOnFocus = false
        campoPinActivo = campo

        keypadContainer.visibility = View.VISIBLE

        if (!campo.hasFocus()) {
            campo.requestFocus()
        }

        campo.setSelection(
            campo.text.length
        )

        ocultarTecladoSistema(campo)

        campo.post {
            ocultarTecladoSistema(campo)
        }
    }

    private fun agregarNumeroAlPin(
        numero: String
    ) {
        val campo = campoPinActivo ?: return

        if (
            campo.visibility != View.VISIBLE ||
            !campo.isEnabled ||
            campo.text.length >= 4
        ) {
            return
        }

        val seleccionActual = campo.selectionStart

        val posicion = if (seleccionActual >= 0) {
            seleccionActual.coerceIn(
                0,
                campo.text.length
            )
        } else {
            campo.text.length
        }

        campo.text.insert(
            posicion,
            numero
        )

        if (
            campo == etPin &&
            campo.text.length == 4 &&
            (
                    modo == Modo.CREAR_PIN ||
                            modo == Modo.NUEVO_PIN
                    )
        ) {
            etPinConfirm.postDelayed(
                {
                    seleccionarCampoPin(
                        etPinConfirm
                    )
                },
                100L
            )
        }
    }

    private fun borrarUltimoNumero() {
        var campo = campoPinActivo ?: return

        if (
            campo == etPinConfirm &&
            campo.text.isEmpty() &&
            etPin.text.isNotEmpty()
        ) {
            seleccionarCampoPin(etPin)
            campo = etPin
        }

        if (campo.text.isEmpty()) {
            return
        }

        val seleccionActual = campo.selectionStart

        val posicion = if (seleccionActual > 0) {
            seleccionActual
        } else {
            campo.text.length
        }

        if (posicion <= 0) {
            return
        }

        campo.text.delete(
            posicion - 1,
            posicion
        )
    }

    private fun limpiarCampoPinActivo() {
        val campo = campoPinActivo ?: return

        campo.setText("")
        campo.requestFocus()
        campo.setSelection(0)

        ocultarTecladoSistema(campo)
    }

    private fun mostrarTecladoNumericoFijo() {
        keypadContainer.visibility = View.VISIBLE
    }

    private fun ocultarTecladoNumericoFijo() {
        keypadContainer.visibility = View.GONE
        campoPinActivo = null
    }

    private fun ocultarTecladoSistema(
        view: View
    ) {
        val inputMethodManager = getSystemService(
            Context.INPUT_METHOD_SERVICE
        ) as InputMethodManager

        inputMethodManager.hideSoftInputFromWindow(
            view.windowToken,
            0
        )
    }

    private fun mostrarTecladoSistema(
        campo: EditText
    ) {
        campo.showSoftInputOnFocus = true

        campo.postDelayed(
            {
                if (
                    campo.hasFocus() &&
                    campo.visibility == View.VISIBLE
                ) {
                    val inputMethodManager = getSystemService(
                        Context.INPUT_METHOD_SERVICE
                    ) as InputMethodManager

                    inputMethodManager.showSoftInput(
                        campo,
                        InputMethodManager.SHOW_IMPLICIT
                    )
                }
            },
            100L
        )
    }

    // ═══════════════════════════════════════════════
    // SELECTOR DE PREGUNTAS
    // ═══════════════════════════════════════════════

    private fun mostrarSelectorPreguntas() {
        if (modo == Modo.RECUPERAR) return

        ocultarTecladoNumericoFijo()
        ocultarTecladoSistema(currentFocus ?: authRoot)

        AlertDialog.Builder(this)
            .setTitle("Elige una pregunta secreta")
            .setItems(PREGUNTAS_SECRETAS) { _, which ->
                preguntaSeleccionada = which
                tvPregunta.text = PREGUNTAS_SECRETAS[which]
                tvPregunta.setTextColor(
                    ContextCompat.getColor(this, R.color.text_primary)
                )

                // Pasa al campo de respuesta
                etRespuesta.requestFocus()
                mostrarTecladoSistema(etRespuesta)
            }
            .show()
    }

    // ═══════════════════════════════════════════════
    // EVENTOS
    // ═══════════════════════════════════════════════

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
            cancelarBiometriaAutomatica()
            intentarBiometrico()
        }

        fingerprintView.setOnClickListener {
            cancelarBiometriaAutomatica()
            intentarBiometrico()
        }

        tvOlvide.setOnClickListener {
            cancelarBiometriaAutomatica()
            mostrarRecuperar()
        }
    }

    // ═══════════════════════════════════════════════
    // VERIFICACIÓN AUTOMÁTICA
    // ═══════════════════════════════════════════════

    private fun configurarVerificacionAutomatica() {
        etPin.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    text: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    text: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                }

                override fun afterTextChanged(
                    text: Editable?
                ) {
                    cancelarVerificacionAutomatica()

                    if (
                        modo != Modo.LOGIN ||
                        text?.length != 4
                    ) {
                        return
                    }

                    val tarea = Runnable {
                        if (
                            modo == Modo.LOGIN &&
                            etPin.text.length == 4 &&
                            !isFinishing &&
                            !isDestroyed
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
    // MODOS
    // ═══════════════════════════════════════════════

    private fun mostrarCrearPin() {
        modo = Modo.CREAR_PIN

        cancelarVerificacionAutomatica()
        cancelarBiometriaAutomatica()

        tvTitulo.text = "Crea tu PIN de seguridad"

        fingerprintView.visibility = View.GONE
        btnBiometria.visibility = View.GONE

        etPin.visibility = View.VISIBLE
        etPinConfirm.visibility = View.VISIBLE
        tvPregunta.visibility = View.VISIBLE
        etRespuesta.visibility = View.VISIBLE
        layoutPhoneAlert.visibility = View.VISIBLE

        btnAccion.visibility = View.VISIBLE
        tvOlvide.visibility = View.GONE

        etPin.isEnabled = true
        etPinConfirm.isEnabled = true
        tvPregunta.isClickable = true
        etRespuesta.isEnabled = true
        etPhoneAlert.isEnabled = true

        btnAccion.text = "CREAR PIN"

        etPin.hint = "PIN de 4 dígitos"
        etPinConfirm.hint = "Confirmar PIN"
        tvPregunta.text = ""
        tvPregunta.hint = "Selecciona una pregunta secreta"
        etRespuesta.hint = "Tu respuesta"
        etPhoneAlert.hint = "9XX XXX XXX"

        preguntaSeleccionada = -1

        limpiarCampos()
        limpiarErrores()

        ocultarTecladoSistema(authRoot)
        mostrarTecladoNumericoFijo()
        seleccionarCampoPin(etPin)
    }

    private fun mostrarLogin() {
        modo = Modo.LOGIN

        cancelarVerificacionAutomatica()

        tvTitulo.text =
            "Confirma tu identidad para continuar"

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
        tvPregunta.visibility = View.GONE
        etRespuesta.visibility = View.GONE
        layoutPhoneAlert.visibility = View.GONE

        btnAccion.visibility = View.GONE
        tvOlvide.visibility = View.VISIBLE

        etPin.isEnabled = true
        etPin.hint = "PIN de 4 dígitos"
        etPin.setText("")

        limpiarErrores()

        ocultarTecladoSistema(authRoot)
        mostrarTecladoNumericoFijo()
        seleccionarCampoPin(etPin)
    }

    private fun mostrarRecuperar() {
        modo = Modo.RECUPERAR

        cancelarVerificacionAutomatica()
        cancelarBiometriaAutomatica()

        ocultarTecladoNumericoFijo()

        val prefs = getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        )

        val indexGuardado = prefs.getInt(
            KEY_PREGUNTA_INDEX,
            -1
        )

        // Compatibilidad: si no hay index, busca texto viejo
        val preguntaTexto = if (
            indexGuardado in PREGUNTAS_SECRETAS.indices
        ) {
            PREGUNTAS_SECRETAS[indexGuardado]
        } else {
            prefs.getString(KEY_PREGUNTA, "Pregunta no disponible")
        }

        tvTitulo.text = "Recuperar acceso"

        fingerprintView.visibility = View.GONE
        btnBiometria.visibility = View.GONE

        etPin.visibility = View.GONE
        etPinConfirm.visibility = View.GONE
        layoutPhoneAlert.visibility = View.GONE

        tvPregunta.visibility = View.VISIBLE
        etRespuesta.visibility = View.VISIBLE

        btnAccion.visibility = View.VISIBLE
        tvOlvide.visibility = View.GONE

        btnAccion.text = "VERIFICAR RESPUESTA"

        tvPregunta.text = preguntaTexto
        tvPregunta.isClickable = false

        etRespuesta.isEnabled = true
        etRespuesta.setText("")
        etRespuesta.hint = "Escribe tu respuesta"

        limpiarErrores()

        etRespuesta.requestFocus()
        mostrarTecladoSistema(etRespuesta)
    }

    private fun mostrarNuevoPin() {
        modo = Modo.NUEVO_PIN

        cancelarVerificacionAutomatica()
        cancelarBiometriaAutomatica()

        ocultarTecladoSistema(
            currentFocus ?: authRoot
        )

        tvTitulo.text = "Crea un nuevo PIN"

        fingerprintView.visibility = View.GONE
        btnBiometria.visibility = View.GONE

        etPin.visibility = View.VISIBLE
        etPinConfirm.visibility = View.VISIBLE
        tvPregunta.visibility = View.GONE
        etRespuesta.visibility = View.GONE
        layoutPhoneAlert.visibility = View.GONE

        btnAccion.visibility = View.VISIBLE
        tvOlvide.visibility = View.GONE

        btnAccion.text = "GUARDAR NUEVO PIN"

        etPin.isEnabled = true
        etPinConfirm.isEnabled = true

        etPin.setText("")
        etPinConfirm.setText("")

        etPin.hint = "Nuevo PIN de 4 dígitos"
        etPinConfirm.hint = "Confirmar nuevo PIN"

        limpiarErrores()

        mostrarTecladoNumericoFijo()
        seleccionarCampoPin(etPin)
    }

    // ═══════════════════════════════════════════════
    // CREAR PIN
    // ═══════════════════════════════════════════════

    private fun crearPin() {
        val pin = etPin.text.toString().trim()
        val pinConfirm =
            etPinConfirm.text.toString().trim()

        val respuesta =
            etRespuesta.text.toString().trim()

        val telefono = etPhoneAlert.text.toString()
            .trim()
            .replace(" ", "")
            .replace("-", "")

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

        if (preguntaSeleccionada < 0) {
            tvPregunta.setBackgroundResource(
                R.drawable.input_bg_error
            )
            animarSacudida(tvPregunta)
            ejecutarRespuestaTactil(tvPregunta)

            Toast.makeText(
                this,
                "Selecciona una pregunta secreta",
                Toast.LENGTH_SHORT
            ).show()

            tvPregunta.postDelayed({
                tvPregunta.setBackgroundResource(
                    R.drawable.input_bg
                )
            }, 900L)
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

        if (telefono.length < 9) {
            mostrarErrorCampos(
                mensaje = "Ingresa un número válido (mín. 9 dígitos)",
                limpiar = false,
                etPhoneAlert
            )
            return
        }

        // Guardar PIN y pregunta/respuesta
        val prefs = getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        )

        prefs.edit()
            .putString(
                KEY_PIN,
                pin.hashCode().toString()
            )
            .putInt(
                KEY_PREGUNTA_INDEX,
                preguntaSeleccionada
            )
            .putString(
                KEY_RESPUESTA,
                respuesta
                    .lowercase(Locale.ROOT)
                    .hashCode()
                    .toString()
            )
            .apply()

        // Guardar número de alerta en la misma config
        // que usa ConfigProfileActivity
        getSharedPreferences(
            ConfigProfileActivity.PREFS,
            MODE_PRIVATE
        ).edit()
            .putString(
                ConfigProfileActivity.KEY_PHONE,
                telefono
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
        cancelarBiometriaAutomatica()

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

        if (
            pin.hashCode().toString() ==
            pinGuardado
        ) {
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
        val respuesta =
            etRespuesta.text.toString().trim()

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
                .lowercase(Locale.ROOT)
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

        val pinConfirm =
            etPinConfirm.text.toString().trim()

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
        val executor =
            ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(
            this,
            executor,
            object :
                BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(
                    result:
                    BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)

                    cancelarBiometriaAutomatica()
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

        promptInfo =
            BiometricPrompt.PromptInfo.Builder()
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
        val biometricManager =
            BiometricManager.from(this)

        val resultado =
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
            )

        return resultado ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun programarBiometriaAutomatica() {
        cancelarBiometriaAutomatica()

        if (!biometriaDisponible()) {
            return
        }

        val tarea = Runnable {
            if (
                modo == Modo.LOGIN &&
                !isFinishing &&
                !isDestroyed
            ) {
                intentarBiometrico()
            }
        }

        biometriaPendiente = tarea

        handler.postDelayed(
            tarea,
            RETARDO_BIOMETRIA
        )
    }

    private fun cancelarBiometriaAutomatica() {
        biometriaPendiente?.let {
            handler.removeCallbacks(it)
        }

        biometriaPendiente = null
    }

    private fun intentarBiometrico() {
        if (modo != Modo.LOGIN) {
            return
        }

        if (!biometriaDisponible()) {
            Toast.makeText(
                this,
                "La biometría no está disponible",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        biometricPrompt.authenticate(
            promptInfo
        )
    }

    private fun mostrarErrorBiometrico() {
        animarSacudida(fingerprintView)
        ejecutarRespuestaTactil(
            fingerprintView
        )
    }

    // ═══════════════════════════════════════════════
    // ERRORES
    // ═══════════════════════════════════════════════

    private fun mostrarErrorCampos(
        mensaje: String,
        limpiar: Boolean,
        vararg campos: EditText
    ) {
        if (campos.isEmpty()) {
            return
        }

        campos.forEach { campo ->
            campo.setBackgroundResource(
                R.drawable.input_bg_error
            )

            campo.error = mensaje
            animarSacudida(campo)

            if (limpiar) {
                campo.setText("")
            }

            campo.postDelayed(
                {
                    campo.error = null

                    campo.setBackgroundResource(
                        R.drawable.input_bg
                    )
                },
                900L
            )
        }

        ejecutarRespuestaTactil(
            campos.first()
        )

        val primerCampo = campos.first()

        if (
            primerCampo == etPin ||
            primerCampo == etPinConfirm
        ) {
            seleccionarCampoPin(
                primerCampo
            )
        } else {
            ocultarTecladoNumericoFijo()
            primerCampo.requestFocus()
            mostrarTecladoSistema(primerCampo)
        }
    }

    private fun animarSacudida(
        view: View
    ) {
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

    private fun ejecutarRespuestaTactil(
        view: View
    ) {
        val tipo = if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.R
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
            etRespuesta,
            etPhoneAlert
        )

        campos.forEach { campo ->
            campo.error = null

            campo.setBackgroundResource(
                R.drawable.input_bg
            )
        }

        tvPregunta.setBackgroundResource(
            R.drawable.input_bg
        )
    }

    private fun limpiarCampos() {
        etPin.setText("")
        etPinConfirm.setText("")
        tvPregunta.text = ""
        etRespuesta.setText("")
        etPhoneAlert.setText("")
        preguntaSeleccionada = -1
    }

    private fun cancelarVerificacionAutomatica() {
        verificacionPendiente?.let {
            handler.removeCallbacks(it)
        }

        verificacionPendiente = null
    }

    // ═══════════════════════════════════════════════
    // ENTRAR
    // ═══════════════════════════════════════════════

    private fun entrarApp() {
        cancelarVerificacionAutomatica()
        cancelarBiometriaAutomatica()

        ocultarTecladoNumericoFijo()

        ocultarTecladoSistema(
            currentFocus ?: authRoot
        )

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