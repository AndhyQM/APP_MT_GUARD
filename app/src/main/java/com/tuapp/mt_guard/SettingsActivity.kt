package com.tuapp.mt_guard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)

        configurarBotonRegresar()
        configurarOpciones()
    }

    private fun configurarBotonRegresar() {
        val btnBack = findViewById<View>(R.id.btnBack)

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun configurarOpciones() {
        val optionBeacon = findViewById<View>(R.id.optionBeacon)
        val optionProfile = findViewById<View>(R.id.optionProfile)
        val optionModule = findViewById<View>(R.id.optionModule)

        optionBeacon.setOnClickListener {
            startActivity(
                Intent(this, ConfigBeaconActivity::class.java)
            )
            overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        }

        optionProfile.setOnClickListener {
            Toast.makeText(
                this,
                "Configuración de perfil: siguiente etapa",
                Toast.LENGTH_SHORT
            ).show()
        }

        optionModule.setOnClickListener {
            Toast.makeText(
                this,
                "Configuración MT Guard Module: siguiente etapa",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}