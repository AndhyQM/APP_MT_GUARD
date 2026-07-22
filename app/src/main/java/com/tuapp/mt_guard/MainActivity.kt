package com.tuapp.mt_guard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // UI
    private lateinit var viewBleDot: View
    private lateinit var tvBleStatus: TextView
    private lateinit var dotContacto: View
    private lateinit var tvContacto: TextView
    private lateinit var dotArranque: View
    private lateinit var tvArranque: TextView
    private lateinit var dotAuth: View
    private lateinit var tvAuth: TextView
    private lateinit var btnArrancar: Button
    private lateinit var btnDesbloquear: Button
    private lateinit var btnConectar: Button
    private lateinit var btnAlerta: Button
    private lateinit var tvUltimoEnvio: TextView
    private lateinit var btnConfig: Button

    // Lógica
    private lateinit var bleManager: BleManager
    private lateinit var beaconScanner: BeaconScanner
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

        // ── Bind UI ──
        viewBleDot    = findViewById(R.id.viewBleDot)
        tvBleStatus   = findViewById(R.id.tvBleStatus)
        dotContacto   = findViewById(R.id.dotContacto)
        tvContacto    = findViewById(R.id.tvContacto)
        dotArranque   = findViewById(R.id.dotArranque)
        tvArranque    = findViewById(R.id.tvArranque)
        dotAuth       = findViewById(R.id.dotAuth)
        tvAuth        = findViewById(R.id.tvAuth)
        btnArrancar   = findViewById(R.id.btnArrancar)
        btnDesbloquear= findViewById(R.id.btnDesbloquear)
        btnConectar   = findViewById(R.id.btnConectar)
        btnAlerta     = findViewById(R.id.btnAlerta)
        tvUltimoEnvio = findViewById(R.id.tvUltimoEnvio)
        btnConfig     = findViewById(R.id.btnConfig)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        pedirPermisos()

        // ── BLE Manager ──
        bleManager = BleManager(
            context = this,
            onConnectionChange = { connected ->
                runOnUiThread { actualizarEstadoBle(connected) }
            },
            onAuthenticated = {
                runOnUiThread { actualizarEstadoAuth(true) }
            },
            onData = { data ->
                runOnUiThread {
                    Toast.makeText(this, "ESP32: $data", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { msg ->
                runOnUiThread {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )

        // ── Beacon Scanner ──
        beaconScanner = BeaconScanner(
            context = this,
            onVehicleState = { contacto, arranque ->
                runOnUiThread { actualizarEstadoVehiculo(contacto, arranque) }
            },
            onError = { msg ->
                runOnUiThread {
                    Toast.makeText(this, "Beacon: $msg", Toast.LENGTH_SHORT).show()
                }
            }
        )

        // ── Botones ──
        btnConectar.setOnClickListener {
            if (bleManager.isConnected) {
                bleManager.disconnect()
            } else {
                btnConectar.text = "BUSCANDO..."
                btnConectar.isEnabled = false
                bleManager.connect()
            }
        }

        btnArrancar.setOnClickListener {
            bleManager.sendArrancar()
            Toast.makeText(this, "Arranque enviado (3 seg)", Toast.LENGTH_SHORT).show()
        }

        btnDesbloquear.setOnClickListener {
            bleManager.sendDesbloquear()
            Toast.makeText(this, "Puerta desbloqueada", Toast.LENGTH_SHORT).show()
        }

        btnAlerta.setOnClickListener { enviarAlerta() }

        btnConfig.setOnClickListener {
            startActivity(Intent(this, ConfigActivity::class.java))
        }

        iniciarServicio()
    }

    override fun onResume() {
        super.onResume()
        iniciarGPS()
        beaconScanner.start()
        // Refrescar estado visual
        actualizarEstadoBle(bleManager.isConnected)
        actualizarEstadoAuth(bleManager.isAuthenticated)
    }

    override fun onPause() {
        super.onPause()
        locationManager.removeUpdates(locationListener)
        beaconScanner.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.disconnect()
    }

    // ══════════════════════════════════════════════════
    //  ACTUALIZAR UI
    // ══════════════════════════════════════════════════

    private fun actualizarEstadoBle(connected: Boolean) {
        if (connected) {
            viewBleDot.setBackgroundResource(R.drawable.status_dot_connected)
            tvBleStatus.text = "Conectado"
            tvBleStatus.setTextColor(ContextCompat.getColor(this, R.color.status_ok))
            btnConectar.text = "DESCONECTAR"
            btnConectar.isEnabled = true
        } else {
            viewBleDot.setBackgroundResource(R.drawable.status_dot_disconnected)
            tvBleStatus.text = "Desconectado"
            tvBleStatus.setTextColor(ContextCompat.getColor(this, R.color.status_danger))
            btnConectar.text = "CONECTAR"
            btnConectar.isEnabled = true
            btnArrancar.isEnabled = false
            btnDesbloquear.isEnabled = false
            actualizarEstadoAuth(false)
        }
    }

    private fun actualizarEstadoAuth(authenticated: Boolean) {
        if (authenticated) {
            dotAuth.setBackgroundResource(R.drawable.status_dot_connected)
            tvAuth.text = "SÍ"
            tvAuth.setTextColor(ContextCompat.getColor(this, R.color.status_ok))
            btnArrancar.isEnabled = true
            btnDesbloquear.isEnabled = true
            btnArrancar.alpha = 1.0f
            btnDesbloquear.alpha = 1.0f
        } else {
            dotAuth.setBackgroundResource(R.drawable.status_dot_disconnected)
            tvAuth.text = "NO"
            tvAuth.setTextColor(ContextCompat.getColor(this, R.color.status_danger))
            btnArrancar.isEnabled = false
            btnDesbloquear.isEnabled = false
            btnArrancar.alpha = 0.4f
            btnDesbloquear.alpha = 0.4f
        }
    }

    private fun actualizarEstadoVehiculo(contacto: Boolean, arranque: Boolean) {
        // Contacto
        if (contacto) {
            dotContacto.setBackgroundResource(R.drawable.status_dot_connected)
            tvContacto.text = "ABIERTO"
            tvContacto.setTextColor(ContextCompat.getColor(this, R.color.status_ok))
        } else {
            dotContacto.setBackgroundResource(R.drawable.status_dot_disconnected)
            tvContacto.text = "CERRADO"
            tvContacto.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }

        // Arranque
        if (arranque) {
            dotArranque.setBackgroundResource(R.drawable.status_dot_connected)
            tvArranque.text = "ON"
            tvArranque.setTextColor(ContextCompat.getColor(this, R.color.status_ok))
        } else {
            dotArranque.setBackgroundResource(R.drawable.status_dot_disconnected)
            tvArranque.text = "OFF"
            tvArranque.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    // ══════════════════════════════════════════════════
    //  ALERTA SMS
    // ══════════════════════════════════════════════════

    private fun enviarAlerta() {
        val numero = obtenerNumeroGuardado()
        if (numero == null) {
            Toast.makeText(this, "Configura un número de emergencia primero", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, ConfigActivity::class.java))
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
        sb.append("ALERTA MT GUARD\n")
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

    private fun obtenerNumeroGuardado(): String? {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getString(KEY_NUMERO, null)
    }

    // ══════════════════════════════════════════════════
    //  GPS
    // ══════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════
    //  SERVICIO Y PERMISOS
    // ══════════════════════════════════════════════════

    private fun iniciarServicio() {
        val intent = Intent(this, GuardService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun pedirPermisos() {
        val faltantes = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            faltantes.add(Manifest.permission.SEND_SMS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            faltantes.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                faltantes.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                faltantes.add(Manifest.permission.BLUETOOTH_SCAN)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                faltantes.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
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