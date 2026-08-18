package com.tuapp.mt_guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/*
 * Relanza el GuardService cuando el teléfono termina de encender,
 * para que el vigía del beacon quede activo sin que el cliente
 * tenga que abrir la app.
 *
 * Solo lo hace si ya hay un módulo vinculado (MAC guardada):
 * en un teléfono recién instalado sin vincular no tiene sentido
 * levantar el servicio.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val accion = intent.action ?: return

        val esArranque =
            accion == Intent.ACTION_BOOT_COMPLETED ||
                    accion == "android.intent.action.QUICKBOOT_POWERON" ||
                    accion == Intent.ACTION_MY_PACKAGE_REPLACED

        if (!esArranque) return

        val macVinculada = context.getSharedPreferences(
            ConfigBeaconActivity.PREFS, Context.MODE_PRIVATE
        ).getString(ConfigBeaconActivity.KEY_MAC, null)

        if (macVinculada == null) {
            Log.i(TAG, "Sin módulo vinculado — no se inicia el servicio")
            return
        }

        Log.i(TAG, "Teléfono encendido — relanzando vigía MT Guard")
        GuardService.iniciar(context)
    }
}