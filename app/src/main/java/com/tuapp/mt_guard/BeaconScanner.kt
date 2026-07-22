package com.tuapp.mt_guard

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class BeaconScanner(
    private val context: Context,
    private val onVehicleState: (contacto: Boolean, arranque: Boolean) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "BeaconScanner"
        private const val TARGET_NAME = "MT GUARD"
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    private var scanner: BluetoothLeScanner? = null
    private var scanning = false

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
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner?.startScan(null, settings, scanCallback)
            scanning = true
            Log.i(TAG, "Escaneo beacon iniciado")
        } catch (e: Exception) {
            onError("Error escaneo: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!scanning) return
        try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
        scanning = false
        Log.i(TAG, "Escaneo beacon detenido")
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord ?: return
            val name = record.deviceName ?: result.device.name
            if (name != TARGET_NAME) return

            // Leer manufacturer data
            val manufacturerData = record.manufacturerSpecificData ?: return
            for (i in 0 until manufacturerData.size()) {
                val companyId = manufacturerData.keyAt(i)
                val data = manufacturerData.valueAt(i) ?: continue

                // El ESP32 manda 2 bytes como beacon:
                // byte alto (bit 8) = arranque (0x01 o 0x00)
                // byte bajo (bit 0) = contacto (0x01 o 0x00)
                //
                // Android lee manufacturer data como:
                // companyId = primeros 2 bytes little-endian
                // El uint16 beacon_val queda en companyId

                val beaconVal = companyId and 0xFFFF
                val arranque = ((beaconVal shr 8) and 0x01) == 1
                val contacto = (beaconVal and 0x01) == 1

                Log.d(TAG, "Beacon: arranque=$arranque contacto=$contacto (raw=0x${beaconVal.toString(16)})")
                onVehicleState(contacto, arranque)
                return
            }
        }

        override fun onScanFailed(errorCode: Int) {
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