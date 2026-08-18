package com.tuapp.mt_guard

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GuardService : Service() {

    companion object {
        const val CHANNEL_ID = "mt_guard_channel"          // canal silencioso (servicio)
        const val CHANNEL_ALERTAS = "mt_guard_alertas"     // canal con sonido (alertas/SMS)
        const val NOTIF_ID = 1          // notificación persistente del servicio
        const val NOTIF_ID_ALERTA = 2   // alertas y estado de SMS (aparte, con sonido)
        const val TAG = "GuardService"

        private const val ACTION_SMS_SENT = "com.tuapp.mt_guard.SMS_SENT"
        private const val ACTION_SMS_DELIVERED = "com.tuapp.mt_guard.SMS_DELIVERED"

        /*
         * Prefs para pedir cosas UNA SOLA VEZ. Si el cliente dijo
         * que no, no se lo vuelve a mostrar en cada apertura — puede
         * activarlo después desde Ajustes de la app si quiere.
         */
        private const val PREFS_GUARD = "guard_service_prefs"
        private const val KEY_BATERIA_PEDIDA = "bateria_solicitada"
        private const val KEY_AUTOSTART_PEDIDO = "autostart_solicitado"

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

        /*
         * Pide ignorar la optimización de batería UNA sola vez.
         * force=true para reabrir desde un botón en Ajustes.
         */
        fun solicitarIgnorarBateria(context: Context, force: Boolean = false) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

            val pm = context.getSystemService(
                Context.POWER_SERVICE
            ) as PowerManager

            if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
                return  // ya está exenta, nada que pedir
            }

            val prefs = context.getSharedPreferences(
                PREFS_GUARD, Context.MODE_PRIVATE
            )

            if (!force && prefs.getBoolean(KEY_BATERIA_PEDIDA, false)) {
                return  // ya se pidió una vez — no insistir
            }

            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)

                prefs.edit().putBoolean(KEY_BATERIA_PEDIDA, true).apply()

            } catch (e: Exception) {
                Log.e(TAG, "Error batería: ${e.message}")
            }
        }

        /*
         * Pantalla de autoinicio de Xiaomi/Redmi/Poco — también UNA
         * sola vez (force=true para reabrir desde Ajustes).
         */
        fun abrirAutoStartXiaomi(context: Context, force: Boolean = false) {
            if (!esXiaomi()) return

            val prefs = context.getSharedPreferences(
                PREFS_GUARD, Context.MODE_PRIVATE
            )

            if (!force && prefs.getBoolean(KEY_AUTOSTART_PEDIDO, false)) {
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

                prefs.edit().putBoolean(KEY_AUTOSTART_PEDIDO, true).apply()

            } catch (e: Exception) {
                try {
                    val intent = Intent("miui.intent.action.OP_AUTO_START")
                    intent.addCategory(Intent.CATEGORY_DEFAULT)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)

                    prefs.edit().putBoolean(KEY_AUTOSTART_PEDIDO, true).apply()
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
    private var ultimaAlertaMs = 0L

    // Latido: última vez que se actualizó la notificación persistente
    private var ultimoLatidoMs = 0L

    // ═══════════════════════════════════════════════
    // RECEIVER DE RESULTADO DE SMS
    // El sistema avisa acá si el SMS salió (SENT) y si el
    // teléfono destino lo recibió (DELIVERED).
    // ═══════════════════════════════════════════════

    /*
     * Resultado del SMS: SOLO se registra en el log (para diagnóstico
     * por Logcat). El cliente ve UNA única notificación: la alerta.
     */
    private val smsResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {

                ACTION_SMS_SENT -> when (resultCode) {
                    Activity.RESULT_OK ->
                        Log.i(TAG, "SMS: enviado OK")
                    SmsManager.RESULT_ERROR_NO_SERVICE ->
                        Log.e(TAG, "SMS: NO enviado — sin señal")
                    SmsManager.RESULT_ERROR_RADIO_OFF ->
                        Log.e(TAG, "SMS: NO enviado — modo avión")
                    else ->
                        Log.e(TAG, "SMS: error code=$resultCode")
                }

                ACTION_SMS_DELIVERED -> when (resultCode) {
                    Activity.RESULT_OK ->
                        Log.i(TAG, "SMS: ENTREGADO al destino")
                    else ->
                        Log.w(TAG, "SMS: entrega no confirmada code=$resultCode")
                }
            }
        }
    }

    // ═══════════════════════════════════════════════
    // CICLO DE VIDA DEL SERVICIO
    // ═══════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        crearCanales()
        registrarReceiverSms()
        adquirirWakeLock()
        iniciarUbicacion()
        iniciarBeaconScanner()
        running = true
        Log.d(TAG, "Servicio creado — beacon + ubicación activos")
    }

    private fun registrarReceiverSms() {
        val filter = IntentFilter().apply {
            addAction(ACTION_SMS_SENT)
            addAction(ACTION_SMS_DELIVERED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                smsResultReceiver, filter, Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(smsResultReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificacion = crearNotificacion(
            "MT GUARD activo",
            "Protegiendo tu vehículo en segundo plano"
        )

        /*
         * Desde Android 10 hay que declarar el TIPO al pasar a primer
         * plano; desde Android 14 es obligatorio y debe coincidir con
         * el manifest (connectedDevice|location).
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notificacion,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIF_ID, notificacion)
        }

        Log.d(TAG, "Servicio en primer plano activo")

        // Si el beacon murió (reinicio por START_STICKY), re-levantarlo
        if (beaconScanner?.isScanning != true) {
            iniciarBeaconScanner()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { unregisterReceiver(smsResultReceiver) } catch (_: Exception) {}
        wakeLockHandler.removeCallbacks(renovarWakeLockRunnable)
        beaconScanner?.stop()
        beaconScanner = null
        detenerUbicacion()
        liberarWakeLock()
        running = false
        Log.d(TAG, "Servicio destruido")
        super.onDestroy()
    }

    /*
     * App cerrada desde recientes: el vigía SIGUE VIVO.
     * Solo se corta el GATT de control y se limpia la autenticación.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "App cerrada — GATT liberado, VIGILANCIA CONTINÚA")

        autenticadoGlobal = false

        val bleManager = BleManager(
            context = this,
            onConnectionChange = {},
            onAuthenticated = {},
            onData = {},
            onError = {}
        )
        bleManager.disconnect()

        if (beaconScanner?.isScanning != true) {
            iniciarBeaconScanner()
        }

        actualizarNotificacionPersistente(
            "MT GUARD vigilando",
            "Monitoreando tu vehículo con la app cerrada"
        )

        super.onTaskRemoved(rootIntent)
        // SIN stopSelf(): el servicio queda vivo.
    }

    // ═══════════════════════════════════════════════
    // BEACON SCANNER — corre SIEMPRE, con o sin app abierta
    // ═══════════════════════════════════════════════

    private fun iniciarBeaconScanner() {
        beaconScanner?.stop()

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
        /*
         * LATIDO: cada beacon recibido prueba que (a) el servicio
         * está corriendo y (b) el vehículo está en rango. Se refleja
         * en la notificación persistente con la hora, actualizada
         * como máximo 1 vez por minuto para no gastar batería.
         */
        val ahora = System.currentTimeMillis()
        if (ahora - ultimoLatidoMs > 60_000L) {
            ultimoLatidoMs = ahora

            val hora = SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(Date(ahora))

            val estado = when {
                arranque -> "arranque detectado"
                contacto -> "contacto encendido"
                else     -> "en reposo"
            }

            actualizarNotificacionPersistente(
                "MT GUARD activo",
                "Vehículo en rango ($estado) · $hora"
            )
        }

        // Estado GLOBAL de autenticación (compartido con BleManager)
        val autenticado = autenticadoGlobal

        /*
         * Rearme por tiempo: si pasaron más de 30 s desde la última
         * alerta, se considera un intento NUEVO aunque el beacon no
         * haya alcanzado a reportar arranque=0 en el medio (cranks
         * cortos + intervalo de advertising pueden saltarse el 0).
         */
        if (alertaEnviada &&
            System.currentTimeMillis() - ultimaAlertaMs > 30_000L
        ) {
            alertaEnviada = false
        }

        // ALERTA: SOLO arranque activo + NO autenticado
        if (arranque && !autenticado) {
            if (!alertaEnviada) {
                alertaEnviada = true
                ultimaAlertaMs = System.currentTimeMillis()
                Log.e(TAG, "⚠ ALERTA: Intento de arranque sin autenticación!")

                notificarAlerta(
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
    // SMS DE ALERTA CON UBICACIÓN + CONFIRMACIÓN
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

            /*
             * PendingIntents de confirmación: el sistema los dispara
             * cuando el SMS sale (SENT) y cuando llega al destino
             * (DELIVERED). Uno por cada parte del mensaje; el receiver
             * notifica según el resultado. requestCode único por envío
             * para que los intents no se pisen entre alertas.
             */
            val base = (System.currentTimeMillis() and 0xFFFF).toInt()

            val sentIntents = ArrayList<PendingIntent>(partes.size)
            val deliveredIntents = ArrayList<PendingIntent>(partes.size)

            for (i in partes.indices) {
                sentIntents.add(
                    PendingIntent.getBroadcast(
                        this, base + i,
                        Intent(ACTION_SMS_SENT).setPackage(packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or
                                PendingIntent.FLAG_IMMUTABLE
                    )
                )
                deliveredIntents.add(
                    PendingIntent.getBroadcast(
                        this, base + 100 + i,
                        Intent(ACTION_SMS_DELIVERED).setPackage(packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or
                                PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }

            smsManager.sendMultipartTextMessage(
                numeroCompleto,
                null,
                partes,
                sentIntents,
                deliveredIntents
            )

            Log.i(TAG, "SMS de alerta despachado a $numeroCompleto")

        } catch (e: Exception) {
            Log.e(TAG, "Error enviando SMS: ${e.message}")
        }
    }

    private fun construirMensajeAlerta(): String {
        val ubicacion = ultimaUbicacion

        val sb = StringBuilder()
        sb.append("ALERTA MT GUARD\n")
        sb.append("Intento de arranque no autorizado.\n")

        val hora = SimpleDateFormat(
            "dd/MM/yyyy HH:mm:ss",
            Locale.getDefault()
        ).format(Date())

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
    // WAKE LOCK — con renovación automática cada 12 h
    // (se adquiere por 24 h; sin renovarlo, un servicio que lleve
    // más de un día corriendo quedaría sin wakelock en silencio)
    // ═══════════════════════════════════════════════

    private val wakeLockHandler = android.os.Handler(
        android.os.Looper.getMainLooper()
    )

    private val renovarWakeLockRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            liberarWakeLock()
            adquirirWakeLock()
            Log.d(TAG, "WakeLock renovado")
            wakeLockHandler.postDelayed(this, 12 * 60 * 60 * 1000L)
        }
    }

    private fun adquirirWakeLock() {
        if (wakeLock != null) return

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager

        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MTGuard::GuardServiceWakeLock"
        )

        wakeLock?.acquire(24 * 60 * 60 * 1000L)

        // Programar la renovación (se cancela sola en onDestroy)
        wakeLockHandler.removeCallbacks(renovarWakeLockRunnable)
        wakeLockHandler.postDelayed(
            renovarWakeLockRunnable, 12 * 60 * 60 * 1000L
        )

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
    // NOTIFICACIONES
    //
    // Dos canales:
    //  - CHANNEL_ID: silencioso, para la notificación persistente
    //    del servicio (con el latido "en rango · HH:mm").
    //  - CHANNEL_ALERTAS: importancia alta CON sonido, para alertas
    //    de seguridad y confirmaciones de SMS.
    // ═══════════════════════════════════════════════

    private fun crearCanales() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val canalServicio = NotificationChannel(
                CHANNEL_ID,
                "MT GUARD Protección",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene MT GUARD activo protegiendo tu vehículo"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val canalAlertas = NotificationChannel(
                CHANNEL_ALERTAS,
                "MT GUARD Alertas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas de seguridad y confirmaciones de SMS"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            manager.createNotificationChannel(canalServicio)
            manager.createNotificationChannel(canalAlertas)
        }
    }

    private fun crearNotificacion(
        titulo: String,
        texto: String,
        canal: String = CHANNEL_ID
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
            Notification.Builder(this, canal)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(Notification.BigTextStyle().bigText(texto))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(canal == CHANNEL_ID)
            .setCategory(
                if (canal == CHANNEL_ALERTAS) Notification.CATEGORY_ALARM
                else Notification.CATEGORY_SERVICE
            )
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    /* Actualiza la notificación fija del servicio (silenciosa). */
    private fun actualizarNotificacionPersistente(titulo: String, texto: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, crearNotificacion(titulo, texto, CHANNEL_ID))
    }

    /*
     * Notificación de alerta o estado de SMS (con sonido/vibración).
     * Cada una usa un ID ÚNICO: no se pisan entre sí, así en la
     * barra queda el historial completo (alerta, SMS enviado,
     * entregado) y cada intento de arranque genera una notificación
     * nueva que sí salta en pantalla.
     */
    /*
     * LA notificación de alerta: una sola, ID fijo. Solo se emite
     * al detectar un intento de arranque no autorizado — el estado
     * del SMS no genera notificaciones (va al Logcat).
     */
    private fun notificarAlerta(titulo: String, texto: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            NOTIF_ID_ALERTA,
            crearNotificacion(titulo, texto, CHANNEL_ALERTAS)
        )
    }
}