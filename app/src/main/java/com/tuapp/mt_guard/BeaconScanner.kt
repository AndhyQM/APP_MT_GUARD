package com.tuapp.mt_guard

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class BeaconScanner(
    private val context: Context,
    private val targetMac: String? = null,
    private val onVehicleState: (contacto: Boolean, arranque: Boolean) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "BeaconScanner"
        private const val TARGET_PREFIX = "MT GUARD"

        // Nombre EXACTO que advierte el firmware (fijo desde que se
        // quitó el nombre configurable). Los ScanFilter de Android no
        // aceptan prefijos, solo match exacto.
        private const val TARGET_NAME_EXACTO = "MT GUARD 01"
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    private var scanner: BluetoothLeScanner? = null
    private var scanning = false

    val isScanning: Boolean
        get() = scanning

    @SuppressLint("MissingPermission")
    fun start() {
        if (scanning) return

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            onError("Bluetooth apagado")
            return
        }
        if (!hasScanPermission()) {
            onError("Faltan permisos BLE")
            return
        }

        scanner = adapter.bluetoothLeScanner

        /*
         * CRÍTICO PARA SEGUNDO PLANO:
         * Desde Android 8.1 los escaneos SIN filtro se bloquean con
         * la pantalla apagada o la app en background — el callback
         * simplemente deja de recibir resultados aunque el servicio
         * siga vivo. Con filtros de HARDWARE el sistema mantiene el
         * escaneo siempre.
         *
         * Los filtros son OR entre sí: matchea el que cumpla
         * cualquiera. Se filtra por MAC (si hay una vinculada) y por
         * nombre exacto como respaldo.
         */
        val filtros = ArrayList<ScanFilter>()

        if (targetMac != null) {
            /*
             * MAC vinculada: se escucha EXCLUSIVAMENTE ese módulo.
             * Ningún otro MT Guard cercano genera eventos ni alertas.
             */
            try {
                filtros.add(
                    ScanFilter.Builder()
                        .setDeviceAddress(targetMac.uppercase())
                        .build()
                )
            } catch (e: Exception) {
                Log.w(TAG, "MAC inválida para filtro: $targetMac")
            }
        }

        if (filtros.isEmpty()) {
            /*
             * SOLO si todavía no hay módulo vinculado (primera vez,
             * o MAC guardada inválida): filtrar por nombre exacto
             * para que el escaneo igual sobreviva en background.
             */
            filtros.add(
                ScanFilter.Builder()
                    .setDeviceName(TARGET_NAME_EXACTO)
                    .build()
            )
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner?.startScan(filtros, settings, scanCallback)
            scanning = true
            Log.i(
                TAG,
                "Beacon scan iniciado con ${filtros.size} filtro(s) " +
                        "(MAC: ${targetMac ?: "ninguna"})"
            )
        } catch (e: Exception) {
            onError("Error escaneo: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!scanning) return
        try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
        scanning = false
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return

            /*
             * Verificación en software ADEMÁS del filtro de hardware:
             * si el resultado entró por el filtro de MAC, el nombre
             * puede venir null en ese paquete — en ese caso se acepta
             * (la MAC ya es identificación suficiente).
             */
            val name = try {
                record.deviceName ?: result.device.name
            } catch (_: SecurityException) {
                null
            }

            val deviceAddress = try {
                result.device.address
            } catch (_: SecurityException) {
                return
            }

            val macCoincide = targetMac != null &&
                    deviceAddress.equals(targetMac, ignoreCase = true)

            val nombreCoincide = name != null &&
                    name.startsWith(TARGET_PREFIX, ignoreCase = true)

            if (!macCoincide && !nombreCoincide) return

            // Si hay MAC vinculada, solo aceptar ESE módulo
            if (targetMac != null && !macCoincide) return

            // Leer manufacturer data
            // Con manufacturer_len=2 en el firmware, los 2 bytes del
            // beacon quedan como Company ID (little-endian), sin
            // payload adicional. Android los expone en keyAt(i).
            val mfgData = record.manufacturerSpecificData ?: return
            if (mfgData.size() == 0) return

            val companyId = mfgData.keyAt(0)
            val beaconVal = companyId and 0xFFFF

            // byte bajo = contacto, byte alto = arranque
            val contacto = (beaconVal and 0x01) == 1
            val arranque = ((beaconVal shr 8) and 0x01) == 1

            onVehicleState(contacto, arranque)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            onError("Escaneo falló: $errorCode")
        }
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}