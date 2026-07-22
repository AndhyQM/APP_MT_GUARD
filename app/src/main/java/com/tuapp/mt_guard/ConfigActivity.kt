package com.tuapp.mt_guard

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ConfigActivity : AppCompatActivity() {

    private lateinit var btnVolver: TextView
    private lateinit var etNumero: EditText
    private lateinit var btnGuardar: Button
    private lateinit var tvEstado: TextView
    private lateinit var tvDeviceInfo: TextView

    companion object {
        private const val PREFS_NAME = "MT_GUARD_Prefs"
        private const val KEY_NUMERO = "numero_contacto"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        btnVolver    = findViewById(R.id.btnVolver)
        etNumero     = findViewById(R.id.etNumero)
        btnGuardar   = findViewById(R.id.btnGuardar)
        tvEstado     = findViewById(R.id.tvEstado)
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo)

        cargarNumeroGuardado()

        btnVolver.setOnClickListener { finish() }

        btnGuardar.setOnClickListener { guardarNumero() }
    }

    private fun guardarNumero() {
        val numero = etNumero.text.toString().trim()
        if (numero.isEmpty()) {
            Toast.makeText(this, "Escribe un número", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(KEY_NUMERO, numero).apply()
        tvEstado.text = "Guardado: $numero"
        Toast.makeText(this, "Número guardado", Toast.LENGTH_SHORT).show()
    }

    private fun cargarNumeroGuardado() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val numero = prefs.getString(KEY_NUMERO, null)
        if (numero != null) {
            etNumero.setText(numero)
            tvEstado.text = "Guardado: $numero"
        }
    }
}