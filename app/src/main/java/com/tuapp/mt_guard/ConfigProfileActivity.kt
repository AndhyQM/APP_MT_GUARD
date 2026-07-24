package com.tuapp.mt_guard

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ConfigProfileActivity : AppCompatActivity() {

    companion object {
        const val PREFS = "MT_GUARD_Config"
        const val KEY_PHONE = "alert_phone"

        fun obtenerNumero(context: Context): String? {
            return context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_PHONE, null)
        }
    }

    private lateinit var etPhoneNumber: EditText
    private lateinit var layoutNumeroGuardado: LinearLayout
    private lateinit var tvNumeroGuardado: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config_profile)

        enlazarVistas()
        cargarNumeroGuardado()
        configurarBotones()
    }

    private fun enlazarVistas() {
        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        layoutNumeroGuardado = findViewById(R.id.layoutNumeroGuardado)
        tvNumeroGuardado = findViewById(R.id.tvNumeroGuardado)

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun cargarNumeroGuardado() {
        val numero = obtenerNumero(this)

        if (numero != null) {
            etPhoneNumber.setText(numero)
            mostrarNumeroGuardado(numero)
        }
    }

    private fun configurarBotones() {
        findViewById<View>(R.id.btnGuardarNumero).setOnClickListener {
            val numero = etPhoneNumber.text.toString()
                .trim()
                .replace(" ", "")
                .replace("-", "")

            if (numero.length < 9) {
                Toast.makeText(
                    this,
                    "Ingresa un número válido de al menos 9 dígitos",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            guardarNumero(numero)
            mostrarNumeroGuardado(numero)

            Toast.makeText(
                this,
                "Número guardado correctamente",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun mostrarNumeroGuardado(numero: String) {
        layoutNumeroGuardado.visibility = View.VISIBLE
        tvNumeroGuardado.text = "+51 $numero"
    }

    private fun guardarNumero(numero: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_PHONE, numero)
            .apply()
    }
}