package com.tuapp.mt_guard

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieAnimationView

class SplashActivity : AppCompatActivity() {

    private lateinit var radar1: View
    private lateinit var radar2: View

    private lateinit var lottieSplashLogo: LottieAnimationView

    private lateinit var tvBrand: View
    private lateinit var tvTagline: View
    private lateinit var tvSecureMessage: View

    private val handler = Handler(Looper.getMainLooper())

    private val abrirSiguienteRunnable = Runnable {
        verificarPermisos()
    }

    // ═══════════════════════════════════════════════
    // PERMISOS
    // ═══════════════════════════════════════════════

    private val permisosSolicitados: Array<String>
        get() {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.SEND_SMS
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                perms.add(Manifest.permission.BLUETOOTH_SCAN)
                perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            return perms.toTypedArray()
        }

    private val permisosCriticos: Array<String>
        get() {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                perms.add(Manifest.permission.BLUETOOTH_SCAN)
                perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            return perms.toTypedArray()
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { evaluarResultado() }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { evaluarResultado() }

    // ═══════════════════════════════════════════════
    // CICLO DE VIDA
    // ═══════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_splash)

        enlazarVistas()
        prepararVistas()
        iniciarAnimaciones()
        programarCambioDePantalla()
    }

    override fun onDestroy() {
        handler.removeCallbacks(abrirSiguienteRunnable)

        if (::lottieSplashLogo.isInitialized) {
            lottieSplashLogo.cancelAnimation()
        }

        super.onDestroy()
    }

    // ═══════════════════════════════════════════════
    // VISTAS
    // ═══════════════════════════════════════════════

    private fun enlazarVistas() {
        radar1 = findViewById(R.id.radar1)
        radar2 = findViewById(R.id.radar2)

        lottieSplashLogo = findViewById(
            R.id.lottieSplashLogo
        )

        tvBrand = findViewById(R.id.tvBrand)
        tvTagline = findViewById(R.id.tvTagline)

        tvSecureMessage = findViewById(
            R.id.tvSecureMessage
        )
    }

    private fun prepararVistas() {
        prepararVista(
            view = lottieSplashLogo,
            scale = 0.72f,
            translationY = 0f
        )

        prepararVista(
            view = tvBrand,
            scale = 1f,
            translationY = 20f
        )

        prepararVista(
            view = tvTagline,
            scale = 1f,
            translationY = 18f
        )

        prepararVista(
            view = tvSecureMessage,
            scale = 1f,
            translationY = 14f
        )

        lottieSplashLogo.speed = 1.5f
        lottieSplashLogo.repeatCount = 0
    }

    // ═══════════════════════════════════════════════
    // ANIMACIONES
    // ═══════════════════════════════════════════════

    private fun iniciarAnimaciones() {
        val radarOne = crearPulso(
            view = radar1,
            delay = 0L
        )

        val radarTwo = crearPulso(
            view = radar2,
            delay = 220L
        )

        val logoIn = crearEntradaLogo()

        val brandIn = crearEntradaTexto(
            view = tvBrand,
            delay = 760L,
            durationMs = 370L
        )

        val taglineIn = crearEntradaTexto(
            view = tvTagline,
            delay = 930L,
            durationMs = 310L
        )

        val secureIn = crearEntradaTexto(
            view = tvSecureMessage,
            delay = 1110L,
            durationMs = 290L
        )

        AnimatorSet().apply {
            playTogether(
                radarOne,
                radarTwo,
                logoIn,
                brandIn,
                taglineIn,
                secureIn
            )

            start()
        }

        lottieSplashLogo.postDelayed(
            {
                if (!isFinishing && !isDestroyed) {
                    lottieSplashLogo.playAnimation()
                }
            },
            330L
        )
    }

    private fun crearEntradaLogo(): AnimatorSet {
        return AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(
                    lottieSplashLogo,
                    View.ALPHA,
                    0f,
                    1f
                ),
                ObjectAnimator.ofFloat(
                    lottieSplashLogo,
                    View.SCALE_X,
                    0.72f,
                    1f
                ),
                ObjectAnimator.ofFloat(
                    lottieSplashLogo,
                    View.SCALE_Y,
                    0.72f,
                    1f
                )
            )

            duration = 560L
            startDelay = 330L

            interpolator =
                AccelerateDecelerateInterpolator()
        }
    }

    private fun prepararVista(
        view: View,
        scale: Float,
        translationY: Float
    ) {
        view.alpha = 0f
        view.scaleX = scale
        view.scaleY = scale
        view.translationY = translationY
    }

    private fun crearPulso(
        view: View,
        delay: Long
    ): AnimatorSet {

        view.alpha = 0f
        view.scaleX = 0.35f
        view.scaleY = 0.35f

        return AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(
                    view,
                    View.ALPHA,
                    0f,
                    0.70f,
                    0f
                ),
                ObjectAnimator.ofFloat(
                    view,
                    View.SCALE_X,
                    0.35f,
                    1.55f
                ),
                ObjectAnimator.ofFloat(
                    view,
                    View.SCALE_Y,
                    0.35f,
                    1.55f
                )
            )

            duration = 820L
            startDelay = delay

            interpolator =
                AccelerateDecelerateInterpolator()
        }
    }

    private fun crearEntradaTexto(
        view: View,
        delay: Long,
        durationMs: Long
    ): AnimatorSet {

        return AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(
                    view,
                    View.ALPHA,
                    0f,
                    1f
                ),
                ObjectAnimator.ofFloat(
                    view,
                    View.TRANSLATION_Y,
                    view.translationY,
                    0f
                )
            )

            duration = durationMs
            startDelay = delay

            interpolator =
                AccelerateDecelerateInterpolator()
        }
    }

    private fun programarCambioDePantalla() {
        handler.postDelayed(
            abrirSiguienteRunnable,
            3600L
        )
    }

    // ═══════════════════════════════════════════════
    // LÓGICA DE PERMISOS
    // ═══════════════════════════════════════════════

    private fun verificarPermisos() {
        if (isFinishing || isDestroyed) return

        if (criticosConcedidos()) {
            continuar()
        } else {
            permissionLauncher.launch(permisosSolicitados)
        }
    }

    private fun criticosConcedidos(): Boolean =
        permisosCriticos.all {
            ContextCompat.checkSelfPermission(this, it) ==
                    PackageManager.PERMISSION_GRANTED
        }

    private fun evaluarResultado() {
        if (criticosConcedidos()) {
            avisarOpcionalesFaltantes()
            continuar()
            return
        }

        val permanente = permisosCriticos
            .filter {
                ContextCompat.checkSelfPermission(this, it) !=
                        PackageManager.PERMISSION_GRANTED
            }
            .any { !shouldShowRequestPermissionRationale(it) }

        if (permanente) mostrarDialogoSettings()
        else mostrarDialogoReintentar()
    }

    private fun avisarOpcionalesFaltantes() {
        val sinSms = ContextCompat.checkSelfPermission(
            this, Manifest.permission.SEND_SMS
        ) != PackageManager.PERMISSION_GRANTED

        if (sinSms) {
            Toast.makeText(
                this,
                "Sin permiso de SMS no se enviarán alertas de robo",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun continuar() {
        if (isFinishing || isDestroyed) return

        // Solicitar batería sin restricciones (abre diálogo del sistema)
        GuardService.solicitarIgnorarBateria(this)

        // Xiaomi/Redmi/Poco: abrir autostart
        if (GuardService.esXiaomi()) {
            handler.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    GuardService.abrirAutoStartXiaomi(this)
                }
            }, 800L)
        }

        GuardService.iniciar(this)

        // Dar tiempo para que el usuario vea el diálogo de batería
        // antes de cambiar de pantalla
        val retardo = if (GuardService.esXiaomi()) 1800L else 600L

        handler.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed

            val intent = Intent(this, AuthActivity::class.java)

            val opciones = ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.auth_enter,
                R.anim.splash_exit
            )

            startActivity(intent, opciones.toBundle())

            finish()
        }, retardo)
    }

    private fun mostrarDialogoReintentar() {
        AlertDialog.Builder(this)
            .setTitle("Permisos necesarios")
            .setMessage(
                "MT GUARD necesita estos permisos para proteger tu vehículo:\n\n" +
                        "• Ubicación (rastreo GPS)\n" +
                        "• Bluetooth (conexión con el módulo)\n\n" +
                        "Sin ellos la app no puede funcionar."
            )
            .setPositiveButton("Reintentar") { _, _ ->
                permissionLauncher.launch(permisosSolicitados)
            }
            .setNegativeButton("Salir") { _, _ -> finishAffinity() }
            .setCancelable(false)
            .show()
    }

    private fun mostrarDialogoSettings() {
        AlertDialog.Builder(this)
            .setTitle("Permisos bloqueados")
            .setMessage(
                "Denegaste permisos de forma permanente. " +
                        "Actívalos manualmente en Configuración para continuar."
            )
            .setPositiveButton("Ir a Configuración") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                ).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                settingsLauncher.launch(intent)
            }
            .setNegativeButton("Salir") { _, _ -> finishAffinity() }
            .setCancelable(false)
            .show()
    }
}