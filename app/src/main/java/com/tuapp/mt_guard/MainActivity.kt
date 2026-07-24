package com.tuapp.mt_guard

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
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

    /*
     * Modo demo: llega como extra desde ScannerActivity.
     * Permite probar animaciones sin el módulo ESP32.
     */
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
    }

    // ═══════════════════════════════════════════════
    // VISTAS
    // ═══════════════════════════════════════════════

    private fun enlazarVistas() {
        btnBack = findViewById(
            R.id.btnBack
        )

        btnConfig = findViewById(
            R.id.btnConfig
        )

        viewBleDot = findViewById(
            R.id.viewBleDot
        )

        tvBleStatus = findViewById(
            R.id.tvBleStatus
        )

        tvDeviceName = findViewById(
            R.id.tvDeviceName
        )

        tvDeviceMac = findViewById(
            R.id.tvDeviceMac
        )

        btnDesbloquear = findViewById(
            R.id.btnDesbloquear
        )

        btnArrancar = findViewById(
            R.id.btnArrancar
        )

        btnViajeSeguro = findViewById(
            R.id.btnViajeSeguro
        )

        lottieVehicle = findViewById(
            R.id.lottieVehicle
        )

        lottieShield = findViewById(
            R.id.lottieShield
        )
    }

    // ═══════════════════════════════════════════════
    // ANIMACIONES
    // ═══════════════════════════════════════════════

    private fun configurarAnimaciones() {
        /*
         * El vehículo arranca congelado en el primer
         * frame. Solo se anima al iniciar viaje seguro.
         */
        lottieVehicle.repeatCount = 0
        lottieVehicle.progress = 0f

        /*
         * El escudo queda invisible y quieto hasta
         * que el viaje seguro se active.
         */
        lottieShield.repeatCount = 0
        lottieShield.progress = 0f
        lottieShield.alpha = 0f

        /*
         * La composición se carga en background.
         * Los KeyPath solo resuelven después de esto.
         */
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

        /*
         * "buldings" está mal escrito en el JSON original.
         * Hay que respetarlo tal cual o el KeyPath no resuelve.
         */
        lottieVehicle.addValueCallback(
            KeyPath("Pre-comp 2", "Layer 1/buldings.ai", "**"),
            LottieProperty.COLOR_FILTER,
            LottieValueCallback(colorEdificios)
        )
    }

    private fun pintarLineaPiso() {
        /*
         * Shape Layer 1 sí es vector real, acepta
         * STROKE_COLOR directo. Venía en #242E44,
         * invisible sobre fondo oscuro.
         */
        lottieVehicle.addValueCallback(
            KeyPath("Shape Layer 1", "Shape 1", "Stroke 1"),
            LottieProperty.STROKE_COLOR,
            LottieValueCallback(Color.WHITE)
        )
    }

    private fun reproducirViajeSeguro() {
        viajeSeguroActivo = true

        // El carro sí queda en bucle infinito
        lottieVehicle.cancelAnimation()
        lottieVehicle.repeatCount = LottieDrawable.INFINITE
        lottieVehicle.progress = 0f
        lottieVehicle.playAnimation()

        /*
         * El escudo se reproduce una sola vez
         * y se queda congelado en el último frame.
         */
        lottieShield.cancelAnimation()
        lottieShield.repeatCount = 0
        lottieShield.progress = 0f
        lottieShield.playAnimation()

        lottieShield.animate()
            .alpha(1f)
            .setDuration(400)
            .start()

        btnViajeSeguro.text =
            "VIAJE SEGURO ACTIVO"

        /*
         * Una vez activado ya no se puede volver
         * a tocar desde la app.
         */
        btnViajeSeguro.isEnabled = false
    }

    /*
     * No se usa desde el botón. Queda disponible para
     * cuando el módulo reporte que el viaje terminó.
     */
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

        btnViajeSeguro.text =
            "INICIAR VIAJE SEGURO"

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
            ContextCompat.getColor(
                this,
                R.color.status_ok
            )
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
                    if (demoMode) {
                        return@runOnUiThread
                    }

                    actualizarConexion(
                        connected
                    )
                }
            },

            onAuthenticated = {
                runOnUiThread {
                    habilitarControles(
                        true
                    )
                }
            },

            onData = { data ->
                /*
                 * Los datos recibidos no se muestran
                 * en el panel principal.
                 *
                 * Después se mostrarán dentro de:
                 * Configuración → Beacon.
                 */
                android.util.Log.d(
                    "MainActivity",
                    "Datos MT Guard: $data"
                )
            },

            onError = { message ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    // ═══════════════════════════════════════════════
    // BOTONES
    // ═══════════════════════════════════════════════

    private fun configurarBotones() {
        btnBack.setOnClickListener {
            volverAlEscaner()
        }

        btnConfig.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    ConfigPinActivity::class.java
                )
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

        btnArrancar.setOnClickListener {
            if (!listoParaComandos()) {
                mostrarMensajeNoConectado()
                return@setOnClickListener
            }

            if (!demoMode) {
                bleManager.sendArrancar()
            }

            Toast.makeText(
                this,
                "Comando de arranque enviado",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnViajeSeguro.setOnClickListener {
            /*
             * El viaje seguro es de una sola vía:
             * el chofer lo activa y ya no se apaga
             * desde este botón.
             */
            if (viajeSeguroActivo) {
                return@setOnClickListener
            }

            if (!listoParaComandos()) {
                mostrarMensajeNoConectado()
                return@setOnClickListener
            }

            reproducirViajeSeguro()

            if (!demoMode) {
                bleManager.sendIniciarViajeSeguro()
            }

            Toast.makeText(
                this,
                "Viaje seguro iniciado",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Retroalimentación táctil en los tres botones
        aplicarEfectoPresion(btnDesbloquear)
        aplicarEfectoPresion(btnArrancar)
        aplicarEfectoPresion(btnViajeSeguro)
    }

    /*
     * Encoge el botón mientras se mantiene presionado
     * y dispara una vibración corta al tocarlo.
     */
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

            /*
             * false para que el OnClickListener
             * siga funcionando normal.
             */
            false
        }
    }

    private fun listoParaComandos(): Boolean {
        if (demoMode) {
            return true
        }

        return bleManager.isConnected &&
                bleManager.isAuthenticated
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
        val rawName =
            intent.getStringExtra(
                "DEVICE_NAME"
            )
                ?: bleManager.connectedDeviceName
                ?: "MT GUARD"

        val deviceAddress =
            intent.getStringExtra(
                "DEVICE_ADDRESS"
            )
                ?: bleManager.connectedDeviceAddress
                ?: "Dirección no disponible"

        tvDeviceName.text = crearNombreVisual(
            rawName,
            deviceAddress
        )

        tvDeviceMac.text = deviceAddress
    }

    private fun crearNombreVisual(
        rawName: String,
        address: String
    ): String {
        if (
            !rawName.trim().equals(
                "MT GUARD",
                ignoreCase = true
            )
        ) {
            return rawName
        }

        val cleanAddress = address
            .replace(":", "")
            .replace("-", "")

        val suffix =
            if (
                address.contains(":") &&
                cleanAddress.length >= 4
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

    private fun actualizarConexion(
        connected: Boolean
    ) {
        if (connected) {
            viewBleDot.setBackgroundResource(
                R.drawable.status_dot_connected
            )

            tvBleStatus.text =
                "Conectado"

            tvBleStatus.setTextColor(
                ContextCompat.getColor(
                    this,
                    R.color.status_ok
                )
            )
        } else {
            viewBleDot.setBackgroundResource(
                R.drawable.status_dot_disconnected
            )

            tvBleStatus.text =
                "Desconectado"

            tvBleStatus.setTextColor(
                ContextCompat.getColor(
                    this,
                    R.color.status_danger
                )
            )
        }

        habilitarControles(
            connected &&
                    bleManager.isAuthenticated
        )
    }

    private fun habilitarControles(
        enabled: Boolean
    ) {
        btnDesbloquear.isEnabled = enabled
        btnArrancar.isEnabled = enabled

        /*
         * El viaje seguro no se rehabilita si ya
         * está activo, ni siquiera al volver de
         * otra pantalla.
         */
        btnViajeSeguro.isEnabled =
            enabled && !viajeSeguroActivo

        val alpha = if (enabled) {
            1f
        } else {
            0.45f
        }

        btnDesbloquear.alpha = alpha
        btnArrancar.alpha = alpha
        btnViajeSeguro.alpha = alpha
    }

    // ═══════════════════════════════════════════════
    // REGRESAR AL ESCÁNER
    // ═══════════════════════════════════════════════

    private fun volverAlEscaner() {
        if (saliendoAlEscaner) {
            return
        }

        saliendoAlEscaner = true

        if (!demoMode) {
            bleManager.disconnect()
        }

        val intent = Intent(
            this,
            ScannerActivity::class.java
        )

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

        actualizarConexion(
            bleManager.isConnected
        )

        habilitarControles(
            bleManager.isConnected &&
                    bleManager.isAuthenticated
        )
    }

    override fun onDestroy() {
        /*
         * No desconectamos al abrir configuración
         * ni durante un cambio de orientación.
         *
         * La desconexión se realiza explícitamente
         * cuando el usuario regresa al escáner.
         */
        super.onDestroy()
    }
}