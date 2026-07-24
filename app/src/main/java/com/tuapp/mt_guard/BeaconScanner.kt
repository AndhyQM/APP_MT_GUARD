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
    private val targetMac: String? = null,
    private val onVehicleState: (contacto: Boolean, arranque: Boolean) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "BeaconScanner"
        private const val TARGET_PREFIX = "MT GUARD"
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
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner?.startScan(null, settings, scanCallback)
            scanning = true
            Log.i(TAG, "Beacon scan iniciado (MAC: ${targetMac ?: "cualquiera"})")
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
            val name = try {
                record.deviceName ?: result.device.name
            } catch (_: SecurityException) {
                null
            }

            // Filtrar por nombre — startsWith, no match exacto
            if (name == null || !name.startsWith(TARGET_PREFIX, ignoreCase = true)) return

            // Filtrar por MAC si se especificó
            if (targetMac != null) {
                val deviceAddress = try {
                    result.device.address
                } catch (_: SecurityException) {
                    return
                }
                if (!deviceAddress.equals(targetMac, ignoreCase = true)) return
            }

            // Leer manufacturer data
            // Con manufacturer_len=2 en Bluedroid, los 2 bytes del beacon
            // quedan como Company ID (little-endian), sin payload adicional.
            // Android los expone en manufacturerSpecificData.keyAt(i)
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