package com.tuapp.mt_guard

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID

class BleManager(
    private val context: Context,
    private val onConnectionChange: (Boolean) -> Unit,
    private val onAuthenticated: () -> Unit,
    private val onData: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "BleManager"
        private const val TARGET_NAME = "MT GUARD"
        private const val SCAN_TIMEOUT_MS = 15000L

        // UUIDs del ESP32 (deben coincidir con el firmware)
        private val UUID_APP_SVC = UUID.fromString("AC000001-0000-0000-0000-000000000000")
        private val UUID_APP_TX  = UUID.fromString("AC000001-0000-0000-0000-000000000001")  // Notify (ESP → App)
        private val UUID_APP_RX  = UUID.fromString("AC000001-0000-0000-0000-000000000002")  // Write  (App → ESP)
        private val UUID_CCCD    = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val handler = Handler(Looper.getMainLooper())
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var scanning = false
    var isConnected = false
        private set
    var isAuthenticated = false
        private set

    // ══════════════════════════════════════════════════
    //  ESCANEO Y CONEXIÓN
    // ══════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    fun connect() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            onError("Bluetooth apagado o no disponible")
            return
        }
        if (!hasPermissions()) {
            onError("Faltan permisos de Bluetooth")
            return
        }
        if (isConnected) {
            onError("Ya está conectado")
            return
        }

        scanner = adapter.bluetoothLeScanner
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanning = true
            scanner?.startScan(null, settings, scanCallback)
            Log.i(TAG, "Escaneando...")

            // Timeout de escaneo
            handler.postDelayed({
                if (scanning) {
                    stopScan()
                    onError("No se encontró MT GUARD")
                }
            }, SCAN_TIMEOUT_MS)
        } catch (e: Exception) {
            onError("Error al escanear: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
        rxCharacteristic = null
        isConnected = false
        isAuthenticated = false
        onConnectionChange(false)
        Log.i(TAG, "Desconectado")
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (scanning) {
            try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
            scanning = false
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name
            if (name != TARGET_NAME) return

            Log.i(TAG, "Encontrado: $name → ${result.device.address}")
            stopScan()

            // Conectar GATT
            gatt = result.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            onError("Escaneo falló: código $errorCode")
        }
    }

    // ══════════════════════════════════════════════════
    //  GATT CALLBACK
    // ══════════════════════════════════════════════════

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Conectado GATT — descubriendo servicios...")
                    isConnected = true
                    handler.post { onConnectionChange(true) }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(TAG, "Desconectado GATT status=$status")
                    isConnected = false
                    isAuthenticated = false
                    rxCharacteristic = null
                    this@BleManager.gatt?.close()
                    this@BleManager.gatt = null
                    handler.post { onConnectionChange(false) }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.post { onError("Error descubriendo servicios: $status") }
                return
            }

            val service = gatt.getService(UUID_APP_SVC)
            if (service == null) {
                handler.post { onError("Servicio APP no encontrado") }
                return
            }

            // Característica RX (App → ESP): para escribir comandos
            rxCharacteristic = service.getCharacteristic(UUID_APP_RX)
            if (rxCharacteristic == null) {
                handler.post { onError("Característica RX no encontrada") }
                return
            }

            // Característica TX (ESP → App): para recibir notificaciones
            val txChar = service.getCharacteristic(UUID_APP_TX)
            if (txChar != null) {
                gatt.setCharacteristicNotification(txChar, true)
                val descriptor = txChar.getDescriptor(UUID_CCCD)
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }

            Log.i(TAG, "Servicios descubiertos — listo para autenticar")

            // Enviar autenticación automáticamente
            handler.postDelayed({ authenticate() }, 500)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value ?: return
            val text = String(data, Charsets.UTF_8)
            Log.i(TAG, "← Notificación: $text")
            handler.post { onData(text) }
        }
    }

    // ══════════════════════════════════════════════════
    //  COMANDOS
    // ══════════════════════════════════════════════════

    fun authenticate() {
        sendCommand("1")
        isAuthenticated = true
        handler.post { onAuthenticated() }
        Log.i(TAG, "Autenticación enviada")
    }

    fun sendArrancar() {
        if (!isAuthenticated) {
            onError("No autenticado")
            return
        }
        sendCommand("ARRANCAR")
        Log.i(TAG, "→ ARRANCAR")
    }

    fun sendDesbloquear() {
        if (!isAuthenticated) {
            onError("No autenticado")
            return
        }
        sendCommand("DESBLOQUEAR")
        Log.i(TAG, "→ DESBLOQUEAR")
    }

    fun sendBeaconOn() {
        sendCommand("BEACON_ON")
    }

    fun sendBeaconOff() {
        sendCommand("BEACON_OFF")
    }

    @SuppressLint("MissingPermission")
    private fun sendCommand(cmd: String) {
        val char = rxCharacteristic
        val g = gatt
        if (char == null || g == null || !isConnected) {
            onError("No conectado")
            return
        }

        char.value = cmd.toByteArray(Charsets.UTF_8)
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val ok = g.writeCharacteristic(char)
        Log.d(TAG, "→ CMD '$cmd' enviado=$ok")
    }

    // ══════════════════════════════════════════════════
    //  PERMISOS
    // ══════════════════════════════════════════════════

    private fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
}