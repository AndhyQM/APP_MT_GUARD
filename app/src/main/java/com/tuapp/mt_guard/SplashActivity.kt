package com.tuapp.mt_guard

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import com.airbnb.lottie.LottieAnimationView

class SplashActivity : AppCompatActivity() {

    private lateinit var radar1: View
    private lateinit var radar2: View

    private lateinit var lottieSplashLogo: LottieAnimationView

    private lateinit var tvBrand: View
    private lateinit var tvTagline: View
    private lateinit var tvSecureMessage: View

    private val handler = Handler(Looper.getMainLooper())

    private val abrirAuthRunnable = Runnable {
        abrirPantallaDeAutenticacion()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_splash)

        GuardService.iniciar(this)

        enlazarVistas()
        prepararVistas()
        iniciarAnimaciones()
        programarCambioDePantalla()
    }

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

        /*
         * La animación se reproduce a una velocidad
         * más moderada.
         */
        lottieSplashLogo.speed = 1.5f
        lottieSplashLogo.repeatCount = 0
    }

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

        /*
         * Inicia Lottie cuando comienza a aparecer
         * visualmente el logo.
         */
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
            abrirAuthRunnable,
            3600L
        )
    }

    private fun abrirPantallaDeAutenticacion() {
        if (isFinishing || isDestroyed) {
            return
        }

        val intent = Intent(
            this,
            AuthActivity::class.java
        )

        val opciones = ActivityOptionsCompat.makeCustomAnimation(
            this,
            R.anim.auth_enter,
            R.anim.splash_exit
        )

        ActivityCompat.startActivity(
            this,
            intent,
            opciones.toBundle()
        )

        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(
            abrirAuthRunnable
        )

        if (::lottieSplashLogo.isInitialized) {
            lottieSplashLogo.cancelAnimation()
        }

        super.onDestroy()
    }
}