package com.tuapp.mt_guard
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var etNumero: EditText
    private lateinit var btnGuardar: Button
    private lateinit var btnAlerta: Button
    private lateinit var tvEstado: TextView
    private lateinit var tvUltimoEnvio: TextView

    private lateinit var locationManager: LocationManager
    private var ultimaUbicacion: Location? = null

    companion object {
        private const val PREFS_NAME = "MT_GUARD_Prefs"
        private const val KEY_NUMERO = "numero_contacto"
        private const val REQUEST_PERMISOS = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etNumero = findViewById(R.id.etNumero)
        btnGuardar = findViewById(R.id.btnGuardar)
        btnAlerta = findViewById(R.id.btnAlerta)
        tvEstado = findViewById(R.id.tvEstado)
        tvUltimoEnvio = findViewById(R.id.tvUltimoEnvio)

        cargarNumeroGuardado()
        pedirPermisos()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        btnGuardar.setOnClickListener { guardarNumero() }
        btnAlerta.setOnClickListener { enviarAlerta() }
    }

    override fun onResume() {
        super.onResume()
        iniciarGPS()
    }

    override fun onPause() {
        super.onPause()
        locationManager.removeUpdates(locationListener)
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

    private fun obtenerNumeroGuardado(): String? {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getString(KEY_NUMERO, null)
    }

    private fun enviarAlerta() {
        val numero = obtenerNumeroGuardado()
        if (numero == null) {
            Toast.makeText(this, "Primero guarda un número", Toast.LENGTH_LONG).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "No hay permiso SMS", Toast.LENGTH_LONG).show()
            pedirPermisos()
            return
        }

        val mensaje = armarMensaje()
        try {
            val smsManager = SmsManager.getDefault()
            val partes = smsManager.divideMessage(mensaje)
            smsManager.sendMultipartTextMessage(numero, null, partes, null, null)

            val hora = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            tvUltimoEnvio.text = "Enviado: $hora a $numero"
            Toast.makeText(this, "Alerta enviada a $numero", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun armarMensaje(): String {
        val sb = StringBuilder()
        sb.append("ALERTA DE EMERGENCIA\n")
        val hora = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        sb.append("Fecha: $hora\n")
        if (ultimaUbicacion != null) {
            val lat = ultimaUbicacion!!.latitude
            val lon = ultimaUbicacion!!.longitude
            sb.append("https://maps.google.com/?q=$lat,$lon")
        } else {
            sb.append("Ubicación: No disponible")
        }
        return sb.toString()
    }

    private fun iniciarGPS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider != null) {
            locationManager.requestLocationUpdates(provider, 5000L, 10f, locationListener)
            val ultima = locationManager.getLastKnownLocation(provider)
            if (ultima != null) ultimaUbicacion = ultima
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) { ultimaUbicacion = location }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun pedirPermisos() {
        val faltantes = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            faltantes.add(Manifest.permission.SEND_SMS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            faltantes.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (faltantes.isNotEmpty())
            ActivityCompat.requestPermissions(this, faltantes.toTypedArray(), REQUEST_PERMISOS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISOS) {
            for (i in permissions.indices) {
                if (permissions[i] == Manifest.permission.ACCESS_FINE_LOCATION && grantResults[i] == PackageManager.PERMISSION_GRANTED)
                    iniciarGPS()
            }
        }
    }
}