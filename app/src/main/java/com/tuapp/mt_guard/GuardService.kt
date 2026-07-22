package com.tuapp.mt_guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

class GuardService : Service() {

    companion object {
        const val CHANNEL_ID = "mt_guard_channel"
        const val NOTIF_ID = 1
        const val TAG = "GuardService"
    }

    override fun onCreate() {
        super.onCreate()
        crearCanalNotificacion()
        Log.d(TAG, "Servicio creado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificacion = crearNotificacion()
        startForeground(NOTIF_ID, notificacion)
        Log.d(TAG, "Servicio en primer plano activo")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Servicio destruido")
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CHANNEL_ID,
                "MT GUARD Servicio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene MT GUARD activo en segundo plano"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canal)
        }
    }

    private fun crearNotificacion(): Notification {
        val intent = Intent(this, AuthActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("MT GUARD activo")
            .setContentText("Sistema de seguridad vehicular")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }
}