package com.tuapp.mt_guard

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        GuardService.iniciar(this)

        val radar1 = findViewById<View>(R.id.radar1)
        val radar2 = findViewById<View>(R.id.radar2)
        val logo = findViewById<View>(R.id.ivLogo)
        val brand = findViewById<View>(R.id.tvBrand)
        val tagline = findViewById<View>(R.id.tvTagline)
        val secureMessage = findViewById<View>(R.id.tvSecureMessage)

        prepararVista(logo, 0.72f, 0f)
        prepararVista(brand, 1f, 20f)
        prepararVista(tagline, 1f, 18f)
        prepararVista(secureMessage, 1f, 14f)

        val radarOne = crearPulso(radar1, 0L)
        val radarTwo = crearPulso(radar2, 220L)

        val logoIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.72f, 1f),
                ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.72f, 1f)
            )

            duration = 560L
            startDelay = 330L
            interpolator = AccelerateDecelerateInterpolator()
        }

        val brandIn = crearEntradaTexto(
            view = brand,
            delay = 760L,
            durationMs = 370L
        )

        val taglineIn = crearEntradaTexto(
            view = tagline,
            delay = 930L,
            durationMs = 310L
        )

        val secureIn = crearEntradaTexto(
            view = secureMessage,
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

        window.decorView.postDelayed({
            startActivity(
                Intent(this, AuthActivity::class.java)
            )

            overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )

            finish()
        }, 1900L)
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
            interpolator = AccelerateDecelerateInterpolator()
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
            interpolator = AccelerateDecelerateInterpolator()
        }
    }
}