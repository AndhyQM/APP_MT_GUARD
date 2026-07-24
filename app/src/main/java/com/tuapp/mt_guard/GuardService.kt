package com.tuapp.mt_guard

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

class GuardService : Service() {

    companion object {
        const val CHANNEL_ID = "mt_guard_channel"
        const val NOTIF_ID = 1
        const val TAG = "GuardService"

        private var running = false

        @Volatile
        var autenticadoGlobal: Boolean = false

        fun isRunning(): Boolean = running

        fun iniciar(context: Context) {
            if (running) return

            val intent = Intent(context, GuardService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun detener(context: Context) {
            context.stopService(
                Intent(context, GuardService::class.java)
            )
        }

        fun solicitarIgnorarBateria(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(
                    Context.POWER_SERVICE
                ) as PowerManager

                if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                    try {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error batería: ${e.message}")
                    }
                }
            }
        }

        fun abrirAutoStartXiaomi(context: Context) {
            val manufacturer = Build.MANUFACTURER.lowercase()

            if (!manufacturer.contains("xiaomi") &&
                !manufacturer.contains("redmi") &&
                !manufacturer.contains("poco")
            ) {
                return
            }

            try {
                val intent = Intent()
                intent.setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent("miui.intent.action.OP_AUTO_START")
                    intent.addCategory(Intent.CATEGORY_DEFAULT)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (_: Exception) {
                }
            }
        }

        fun esXiaomi(): Boolean {
            val manufacturer = Build.MANUFACTURER.lowercase()
            return manufacturer.contains("xiaomi") ||
                    manufacturer.contains("redmi") ||
                    manufacturer.contains("poco")
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var beaconScanner: BeaconScanner? = null
    private var locationManager: LocationManager? = null
    private var ultimaUbicacion: Location? = null

    // Control para no mandar SMS repetidos
    private var alertaEnviada = false

    // ═══════════════════════════════════════════════
    // CICLO DE VIDA DEL SERVICIO
    // ═══════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        crearCanalNotificacion()
        adquirirWakeLock()
        iniciarUbicacion()
        iniciarBeaconScanner()
        running = true
        Log.d(TAG, "Servicio creado — beacon + ubicación activos")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificacion = crearNotificacion(
            "MT GUARD activo",
            "Protegiendo tu vehículo en segundo plano"
        )
        startForeground(NOTIF_ID, notificacion)
        Log.d(TAG, "Servicio en primer plano activo")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        beaconScanner?.stop()
        beaconScanner = null
        detenerUbicacion()
        liberarWakeLock()
        running = false
        Log.d(TAG, "Servicio destruido")
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "App cerrada — desconectando BLE y deteniendo")

        // Desconectar BLE GATT
        val bleManager = BleManager(
            context = this,
            onConnectionChange = {},
            onAuthenticated = {},
            onData = {},
            onError = {}
        )
        bleManager.disconnect()

        // Detener beacon
        beaconScanner?.stop()
        beaconScanner = null

        detenerUbicacion()
        liberarWakeLock()
        running = false
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    // ═══════════════════════════════════════════════
    // BEACON SCANNER — corre siempre con la app
    // ═══════════════════════════════════════════════

    private fun iniciarBeaconScanner() {
        val macGuardada = getSharedPreferences(
            ConfigBeaconActivity.PREFS, MODE_PRIVATE
        ).getString(ConfigBeaconActivity.KEY_MAC, null)

        beaconScanner = BeaconScanner(
            context = this,
            targetMac = macGuardada,

            onVehicleState = { contacto, arranque ->
                procesarEstadoVehiculo(contacto, arranque)
            },

            onError = { message ->
                Log.e(TAG, "Beacon error: $message")
            }
        )

        beaconScanner?.start()
        Log.i(TAG, "BeaconScanner iniciado (MAC: ${macGuardada ?: "cualquiera"})")
    }

    private fun procesarEstadoVehiculo(contacto: Boolean, arranque: Boolean) {
        // Estado GLOBAL de autenticación (compartido con BleManager)
        val autenticado = autenticadoGlobal

        // ALERTA: SOLO arranque activo + NO autenticado
        if (arranque && !autenticado) {
            if (!alertaEnviada) {
                alertaEnviada = true
                Log.e(TAG, "⚠ ALERTA: Intento de arranque sin autenticación!")

                actualizarNotificacion(
                    "⚠ ALERTA DE SEGURIDAD",
                    "Intento de arranque no autorizado detectado"
                )

                enviarSmsAlerta()
            }
        }

        // Resetear alerta cuando arranque se apaga
        if (!arranque) {
            alertaEnviada = false
        }
    }
    // ═══════════════════════════════════════════════
    // SMS DE ALERTA CON UBICACIÓN
    // ═══════════════════════════════════════════════

    private fun enviarSmsAlerta() {
        val numero = ConfigProfileActivity.obtenerNumero(this)

        if (numero == null) {
            Log.w(TAG, "No hay número de alerta configurado")
            return
        }

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Sin permiso de SMS")
            return
        }

        val mensaje = construirMensajeAlerta()
        val numeroCompleto = "+51$numero"

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val partes = smsManager.divideMessage(mensaje)
            smsManager.sendMultipartTextMessage(
                numeroCompleto,
                null,
                partes,
                null,
                null
            )

            Log.i(TAG, "SMS de alerta enviado a $numeroCompleto")

        } catch (e: Exception) {
            Log.e(TAG, "Error enviando SMS: ${e.message}")
        }
    }

    private fun construirMensajeAlerta(): String {
        val ubicacion = ultimaUbicacion

        val sb = StringBuilder()
        sb.append("ALERTA MT GUARD\n")
        sb.append("Intento de arranque no autorizado.\n")

        val hora = java.text.SimpleDateFormat(
            "dd/MM/yyyy HH:mm:ss",
            java.util.Locale.getDefault()
        ).format(java.util.Date())

        sb.append("Hora: $hora\n")

        if (ubicacion != null) {
            val lat = ubicacion.latitude
            val lng = ubicacion.longitude

            sb.append("Ubicacion: $lat, $lng\n")
            sb.append("https://maps.google.com/?q=$lat,$lng\n")
            sb.append("Precision: ${ubicacion.accuracy.toInt()}m")
        } else {
            sb.append("Ubicacion: no disponible")
        }

        return sb.toString()
    }

    // ═══════════════════════════════════════════════
    // UBICACIÓN GPS
    // ═══════════════════════════════════════════════

    private fun iniciarUbicacion() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Sin permiso de ubicación")
            return
        }

        locationManager = getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                30_000L,
                10f,
                locationListener
            )

            locationManager?.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                30_000L,
                10f,
                locationListener
            )

            ultimaUbicacion = locationManager?.getLastKnownLocation(
                LocationManager.GPS_PROVIDER
            ) ?: locationManager?.getLastKnownLocation(
                LocationManager.NETWORK_PROVIDER
            )

            Log.i(TAG, "Ubicación iniciada — última: ${ultimaUbicacion != null}")

        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando ubicación: ${e.message}")
        }
    }

    private fun detenerUbicacion() {
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: Exception) {
        }
        locationManager = null
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            ultimaUbicacion = location
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Deprecated in API")
        override fun onStatusChanged(
            provider: String?,
            status: Int,
            extras: Bundle?
        ) {}
    }

    // ═══════════════════════════════════════════════
    // WAKE LOCK
    // ═══════════════════════════════════════════════

    private fun adquirirWakeLock() {
        if (wakeLock != null) return

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager

        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MTGuard::GuardServiceWakeLock"
        )

        wakeLock?.acquire(24 * 60 * 60 * 1000L)
        Log.d(TAG, "WakeLock adquirido")
    }

    private fun liberarWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock liberado")
            }
        }
        wakeLock = null
    }

    // ═══════════════════════════════════════════════
    // NOTIFICACIÓN
    // ═══════════════════════════════════════════════

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CHANNEL_ID,
                "MT GUARD Protección",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene MT GUARD activo protegiendo tu vehículo"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canal)
        }
    }

    private fun crearNotificacion(
        titulo: String,
        texto: String
    ): Notification {
        val intent = Intent(this, ScannerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(titulo)
            .setContentText(texto)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    private fun actualizarNotificacion(titulo: String, texto: String) {
        val notificacion = crearNotificacion(titulo, texto)

        val manager = getSystemService(
            NotificationManager::class.java
        )

        manager.notify(NOTIF_ID, notificacion)
    }
}