package com.tuapp.mt_guard

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback

class MainActivity : AppCompatActivity() {

    private lateinit var btnBack: View
    private lateinit var btnConfig: View

    private lateinit var viewBleDot: View
    private lateinit var tvBleStatus: TextView

    private lateinit var tvDeviceName: TextView
    private lateinit var tvDeviceMac: TextView

    private lateinit var btnDesbloquear: Button
    private lateinit var btnArrancar: Button
    private lateinit var btnViajeSeguro: Button

    private lateinit var lottieVehicle: LottieAnimationView
    private lateinit var lottieShield: LottieAnimationView

    private lateinit var bleManager: BleManager

    private var saliendoAlEscaner = false
    private var viajeSeguroActivo = false

    // Arranque tipo llave de contacto
    private val arranqueHandler = Handler(Looper.getMainLooper())
    private var arranqueActivo = false
    private val ARRANQUE_INTERVALO_MS = 350L

    private val demoMode: Boolean by lazy {
        intent.getBooleanExtra("DEMO_MODE", false) ||
                ScannerActivity.DEMO_MODE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        enlazarVistas()
        configurarAnimaciones()
        configurarBleManager()
        configurarBotones()
        configurarBotonAtrasDelSistema()
        mostrarDispositivo()

        if (demoMode) {
            aplicarEstadoDemo()
        }

        // ═══ SERVICIO EN SEGUNDO PLANO ═══
        GuardService.iniciar(this)
        GuardService.solicitarIgnorarBateria(this)

        // Xiaomi/MIUI: pedir autostart solo la primera vez
        val prefs = getSharedPreferences("MT_GUARD_Config", MODE_PRIVATE)
        if (GuardService.esXiaomi() && !prefs.getBoolean("autostart_pedido", false)) {
            prefs.edit().putBoolean("autostart_pedido", true).apply()
            GuardService.abrirAutoStartXiaomi(this)
        }
    }

    // ═══════════════════════════════════════════════
    // VISTAS
    // ═══════════════════════════════════════════════

    private fun enlazarVistas() {
        btnBack = findViewById(R.id.btnBack)
        btnConfig = findViewById(R.id.btnConfig)
        viewBleDot = findViewById(R.id.viewBleDot)
        tvBleStatus = findViewById(R.id.tvBleStatus)
        tvDeviceName = findViewById(R.id.tvDeviceName)
        tvDeviceMac = findViewById(R.id.tvDeviceMac)
        btnDesbloquear = findViewById(R.id.btnDesbloquear)
        btnArrancar = findViewById(R.id.btnArrancar)
        btnViajeSeguro = findViewById(R.id.btnViajeSeguro)
        lottieVehicle = findViewById(R.id.lottieVehicle)
        lottieShield = findViewById(R.id.lottieShield)
    }

    // ═══════════════════════════════════════════════
    // ANIMACIONES
    // ═══════════════════════════════════════════════

    private fun configurarAnimaciones() {
        lottieVehicle.repeatCount = 0
        lottieVehicle.progress = 0f

        lottieShield.repeatCount = 0
        lottieShield.progress = 0f
        lottieShield.alpha = 0f

        lottieVehicle.addLottieOnCompositionLoadedListener {
            pintarEdificios()
            pintarLineaPiso()
        }
    }

    private fun pintarEdificios() {
        val colorEdificios = PorterDuffColorFilter(
            Color.WHITE,
            PorterDuff.Mode.SRC_ATOP
        )

        lottieVehicle.addValueCallback(
            KeyPath("Pre-comp 2", "Layer 1/buldings.ai", "**"),
            LottieProperty.COLOR_FILTER,
            LottieValueCallback(colorEdificios)
        )
    }

    private fun pintarLineaPiso() {
        lottieVehicle.addValueCallback(
            KeyPath("Shape Layer 1", "Shape 1", "Stroke 1"),
            LottieProperty.STROKE_COLOR,
            LottieValueCallback(Color.WHITE)
        )
    }

    private fun reproducirViajeSeguro() {
        viajeSeguroActivo = true

        lottieVehicle.cancelAnimation()
        lottieVehicle.repeatCount = LottieDrawable.INFINITE
        lottieVehicle.progress = 0f
        lottieVehicle.playAnimation()

        lottieShield.cancelAnimation()
        lottieShield.repeatCount = 0
        lottieShield.progress = 0f
        lottieShield.playAnimation()

        lottieShield.animate()
            .alpha(1f)
            .setDuration(400)
            .start()

        btnViajeSeguro.text = "VIAJE SEGURO ACTIVO"
        btnViajeSeguro.isEnabled = false
    }

    private fun detenerViajeSeguro() {
        viajeSeguroActivo = false

        lottieShield.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                lottieShield.cancelAnimation()
                lottieShield.progress = 0f
            }
            .start()

        lottieVehicle.cancelAnimation()
        lottieVehicle.repeatCount = 0
        lottieVehicle.progress = 0f

        btnViajeSeguro.text = "INICIAR VIAJE SEGURO"
        btnViajeSeguro.isEnabled = listoParaComandos()
    }

    // ═══════════════════════════════════════════════
    // MODO DEMO
    // ═══════════════════════════════════════════════

    private fun aplicarEstadoDemo() {
        viewBleDot.setBackgroundResource(
            R.drawable.status_dot_connected
        )

        tvBleStatus.text = "Conectado (demo)"

        tvBleStatus.setTextColor(
            ContextCompat.getColor(this, R.color.status_ok)
        )

        habilitarControles(true)
    }

    // ═══════════════════════════════════════════════
    // BLE
    // ═══════════════════════════════════════════════

    private fun configurarBleManager() {
        bleManager = BleManager(
            context = this,

            onConnectionChange = { connected ->
                runOnUiThread {
                    if (demoMode) return@runOnUiThread
                    actualizarConexion(connected)
                }
            },

            onAuthenticated = {
                runOnUiThread {
                    // Ya no se usa para habilitar controles automáticamente
                    // La autenticación ahora se hace al presionar Viaje Seguro
                }
            },

            onData = { data ->
                android.util.Log.d(
                    "MainActivity",
                    "Datos MT Guard: $data"
                )
            },

            onError = { message ->
                runOnUiThread {
                    Toast.makeText(
                        this, message, Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    // ═══════════════════════════════════════════════
    // BOTONES
    // ═══════════════════════════════════════════════

    @SuppressLint("ClickableViewAccessibility")
    private fun configurarBotones() {
        btnBack.setOnClickListener {
            volverAlEscaner()
        }

        btnConfig.setOnClickListener {
            startActivity(
                Intent(this, ConfigPinActivity::class.java)
            )

            overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        }

        btnDesbloquear.setOnClickListener {
            if (!listoParaComandos()) {
                mostrarMensajeNoConectado()
                return@setOnClickListener
            }

            if (!demoMode) {
                bleManager.sendDesbloquear()
            }

            Toast.makeText(
                this,
                "Comando de desbloqueo enviado",
                Toast.LENGTH_SHORT
            ).show()
        }

        // ═══ ARRANCAR — tipo llave de contacto ═══
        btnArrancar.setOnTouchListener { view, event ->
            if (!view.isEnabled) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!listoParaComandos()) {
                        mostrarMensajeNoConectado()
                        return@setOnTouchListener true
                    }

                    view.animate()
                        .scaleX(0.96f)
                        .scaleY(0.96f)
                        .setDuration(90)
                        .start()

                    view.performHapticFeedback(
                        HapticFeedbackConstants.VIRTUAL_KEY
                    )

                    (view as Button).setBackgroundResource(
                        R.drawable.btn_arranque_active
                    )

                    iniciarArranque()
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .start()

                    (view as Button).setBackgroundResource(
                        R.drawable.panel_button_green
                    )

                    detenerArranque()
                    true
                }

                else -> false
            }
        }

        // ═══ VIAJE SEGURO — autentica y habilita los demás ═══
        btnViajeSeguro.setOnClickListener {
            if (viajeSeguroActivo) {
                return@setOnClickListener
            }

            if (!listoParaComandos()) {
                mostrarMensajeNoConectado()
                return@setOnClickListener
            }

            // Autenticar enviando "1" al ESP32
            if (!demoMode) {
                bleManager.authenticate()
            }

            // ═══ MARCAR AUTENTICADO GLOBALMENTE ═══
            GuardService.autenticadoGlobal = true
            reproducirViajeSeguro()

            if (!demoMode) {
                bleManager.sendIniciarViajeSeguro()
            }

            // Ahora sí habilitar arranque y desbloqueo
            habilitarControles(true)

            Toast.makeText(
                this,
                "Viaje seguro iniciado",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Solo desbloquear — arrancar tiene su propio touch
        aplicarEfectoPresion(btnDesbloquear)
        aplicarEfectoPresion(btnViajeSeguro)
    }

    // ═══════════════════════════════════════════════
    // ARRANQUE TIPO LLAVE DE CONTACTO
    // ═══════════════════════════════════════════════

    private fun iniciarArranque() {
        if (arranqueActivo) return
        arranqueActivo = true

        btnArrancar.text = "ARRANCANDO..."

        Toast.makeText(
            this,
            "Mantenga presionado para arrancar",
            Toast.LENGTH_SHORT
        ).show()

        enviarComandoArranque()
    }

    private fun enviarComandoArranque() {
        if (!arranqueActivo) return

        if (!demoMode) {
            bleManager.sendArrancar()
        }

        arranqueHandler.postDelayed({
            enviarComandoArranque()
        }, ARRANQUE_INTERVALO_MS)
    }

    private fun detenerArranque() {
        if (!arranqueActivo) return

        arranqueActivo = false
        arranqueHandler.removeCallbacksAndMessages(null)

        btnArrancar.text = "ARRANCAR VEHÍCULO"

        if (!demoMode) {
            arranqueHandler.postDelayed({
                bleManager.sendDetenerArranque()
            }, 150L)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun aplicarEfectoPresion(boton: View) {
        boton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (view.isEnabled) {
                        view.animate()
                            .scaleX(0.96f)
                            .scaleY(0.96f)
                            .setDuration(90)
                            .start()

                        view.performHapticFeedback(
                            HapticFeedbackConstants.VIRTUAL_KEY
                        )
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .start()
                }
            }

            false
        }
    }

    private fun listoParaComandos(): Boolean {
        if (demoMode) return true
        return bleManager.isConnected
    }

    private fun configurarBotonAtrasDelSistema() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    volverAlEscaner()
                }
            }
        )
    }

    private fun mostrarMensajeNoConectado() {
        Toast.makeText(
            this,
            "El módulo MT Guard no está conectado",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ═══════════════════════════════════════════════
    // INFORMACIÓN DEL DISPOSITIVO
    // ═══════════════════════════════════════════════

    private fun mostrarDispositivo() {
        val rawName = intent.getStringExtra("DEVICE_NAME")
            ?: bleManager.connectedDeviceName
            ?: "MT GUARD"

        val deviceAddress = intent.getStringExtra("DEVICE_ADDRESS")
            ?: bleManager.connectedDeviceAddress
            ?: "Dirección no disponible"

        tvDeviceName.text = crearNombreVisual(rawName, deviceAddress)
        tvDeviceMac.text = deviceAddress
    }

    private fun crearNombreVisual(
        rawName: String,
        address: String
    ): String {
        if (!rawName.trim().equals("MT GUARD", ignoreCase = true)) {
            return rawName
        }

        val cleanAddress = address
            .replace(":", "")
            .replace("-", "")

        val suffix = if (
            address.contains(":") && cleanAddress.length >= 4
        ) {
            cleanAddress.takeLast(4)
        } else {
            "001"
        }

        return "MTGUARD-$suffix"
    }

    // ═══════════════════════════════════════════════
    // ESTADO DE CONEXIÓN
    // ═══════════════════════════════════════════════

    private fun actualizarConexion(connected: Boolean) {
        if (connected) {
            viewBleDot.setBackgroundResource(R.drawable.status_dot_connected)
            tvBleStatus.text = "Conectado"
            tvBleStatus.setTextColor(ContextCompat.getColor(this, R.color.status_ok))
        } else {
            viewBleDot.setBackgroundResource(R.drawable.status_dot_disconnected)
            tvBleStatus.text = "Desconectado"
            tvBleStatus.setTextColor(ContextCompat.getColor(this, R.color.status_danger))

            // ═══ SI SE PIERDE CONEXIÓN, YA NO ESTÁ AUTENTICADO ═══
            GuardService.autenticadoGlobal = false   // 👈 AGREGA ESTO
        }

        habilitarControles(connected)
    }


    private fun habilitarControles(enabled: Boolean) {
        // Viaje Seguro se habilita con la conexión (si no está ya activo)
        btnViajeSeguro.isEnabled = enabled && !viajeSeguroActivo

        // Arrancar y Desbloquear SOLO si viaje seguro ya está activo
        btnDesbloquear.isEnabled = enabled && viajeSeguroActivo
        btnArrancar.isEnabled = enabled && viajeSeguroActivo

        btnViajeSeguro.alpha =
            if (btnViajeSeguro.isEnabled) 1f else 0.45f

        btnDesbloquear.alpha =
            if (btnDesbloquear.isEnabled) 1f else 0.45f

        btnArrancar.alpha =
            if (btnArrancar.isEnabled) 1f else 0.45f
    }

    // ═══════════════════════════════════════════════
    // REGRESAR AL ESCÁNER
    // ═══════════════════════════════════════════════

    private fun volverAlEscaner() {
        if (saliendoAlEscaner) return
        saliendoAlEscaner = true

        detenerArranque()

        GuardService.autenticadoGlobal = false

        if (!demoMode) {
            bleManager.disconnect()
        }

        val intent = Intent(this, ScannerActivity::class.java)

        intent.flags =
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP

        startActivity(intent)

        overridePendingTransition(
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )

        finish()
    }

    // ═══════════════════════════════════════════════
    // CICLO DE VIDA
    // ═══════════════════════════════════════════════

    override fun onResume() {
        super.onResume()

        if (demoMode) {
            aplicarEstadoDemo()
            return
        }

        actualizarConexion(bleManager.isConnected)
    }

    override fun onDestroy() {
        arranqueHandler.removeCallbacksAndMessages(null)
        arranqueActivo = false
        super.onDestroy()
    }
}