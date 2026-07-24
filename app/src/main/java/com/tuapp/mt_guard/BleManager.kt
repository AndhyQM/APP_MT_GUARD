package com.tuapp.mt_guard

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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
    onConnectionChange: (Boolean) -> Unit,
    onAuthenticated: () -> Unit,
    onData: (String) -> Unit,
    onError: (String) -> Unit
) {

    companion object {

        private const val TAG = "BleManager"
        private const val TARGET_NAME = "MT GUARD"
        private const val SCAN_TIMEOUT_MS = 15_000L

        private val UUID_APP_SVC = UUID.fromString(
            "AC000001-0000-0000-0000-000000000000"
        )

        private val UUID_APP_TX = UUID.fromString(
            "AC000001-0000-0000-0000-000000000001"
        )

        private val UUID_APP_RX = UUID.fromString(
            "AC000001-0000-0000-0000-000000000002"
        )

        private val UUID_CCCD = UUID.fromString(
            "00002902-0000-1000-8000-00805f9b34fb"
        )

        /*
         * Estos valores son compartidos entre ScannerActivity
         * y MainActivity. De esta forma la conexión no se pierde
         * al cambiar de pantalla.
         */
        private val mainHandler = Handler(Looper.getMainLooper())

        private var sharedScanner: BluetoothLeScanner? = null
        private var sharedGatt: BluetoothGatt? = null

        private var sharedRxCharacteristic:
                BluetoothGattCharacteristic? = null

        private var sharedScanning = false
        private var sharedConnected = false
        private var sharedAuthenticated = false

        private var sharedDeviceAddress: String? = null
        private var sharedDeviceName: String? = null

        private var connectionCallback:
                ((Boolean) -> Unit)? = null

        private var authenticatedCallback:
                (() -> Unit)? = null

        private var dataCallback:
                ((String) -> Unit)? = null

        private var errorCallback:
                ((String) -> Unit)? = null
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.applicationContext
            .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

        manager?.adapter
    }

    val isConnected: Boolean
        get() = sharedConnected

    val isAuthenticated: Boolean
        get() = sharedAuthenticated

    val connectedDeviceAddress: String?
        get() = sharedDeviceAddress

    val connectedDeviceName: String?
        get() = sharedDeviceName

    init {
        /*
         * Cada nueva Activity actualiza los callbacks,
         * pero conserva la misma conexión GATT.
         */
        connectionCallback = onConnectionChange
        authenticatedCallback = onAuthenticated
        dataCallback = onData
        errorCallback = onError
    }

    // ═══════════════════════════════════════════════
    // ESCANEO AUTOMÁTICO ANTIGUO
    // ═══════════════════════════════════════════════

    /*
     * Se conserva para que MainActivity continúe compilando.
     * La nueva ScannerActivity utilizará connect(device).
     */
    @SuppressLint("MissingPermission")
    fun connect() {
        val adapter = bluetoothAdapter

        if (adapter == null || !adapter.isEnabled) {
            notificarError("Bluetooth apagado o no disponible")
            return
        }

        if (!tienePermisos()) {
            notificarError("Faltan permisos de Bluetooth")
            return
        }

        if (sharedConnected) {
            notificarConexion(true)

            if (sharedAuthenticated) {
                notificarAutenticado()
            }

            return
        }

        sharedScanner = adapter.bluetoothLeScanner

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            sharedScanning = true

            sharedScanner?.startScan(
                null,
                settings,
                internalScanCallback
            )

            Log.i(TAG, "Buscando MT GUARD...")

            mainHandler.postDelayed({
                if (sharedScanning) {
                    detenerEscaneoInterno()
                    notificarError("No se encontró MT GUARD")
                }
            }, SCAN_TIMEOUT_MS)

        } catch (exception: Exception) {
            sharedScanning = false

            notificarError(
                "Error al escanear: ${exception.message}"
            )
        }
    }

    private val internalScanCallback = object : ScanCallback() {

        @SuppressLint("MissingPermission")
        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            val name = try {
                result.scanRecord?.deviceName
                    ?: result.device.name
            } catch (_: SecurityException) {
                null
            }

            if (
                name == null ||
                !name.startsWith(
                    TARGET_NAME,
                    ignoreCase = true
                )
            ) {
                return
            }

            detenerEscaneoInterno()
            connect(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            sharedScanning = false

            notificarError(
                "Escaneo falló: código $errorCode"
            )
        }
    }

    // ═══════════════════════════════════════════════
    // CONECTAR DISPOSITIVO SELECCIONADO
    // ═══════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        val adapter = bluetoothAdapter

        if (adapter == null || !adapter.isEnabled) {
            notificarError("Bluetooth apagado o no disponible")
            return
        }

        if (!tienePermisos()) {
            notificarError("Faltan permisos de Bluetooth")
            return
        }

        val address = try {
            device.address
        } catch (_: SecurityException) {
            null
        }

        val name = try {
            device.name
        } catch (_: SecurityException) {
            null
        }

        if (
            sharedConnected &&
            address != null &&
            address == sharedDeviceAddress
        ) {
            notificarConexion(true)

            if (sharedAuthenticated) {
                notificarAutenticado()
            }

            return
        }

        detenerEscaneoInterno()
        cerrarGattAnterior()

        sharedConnected = false
        sharedAuthenticated = false
        sharedRxCharacteristic = null

        sharedDeviceAddress = address
        sharedDeviceName = name ?: TARGET_NAME

        Log.i(
            TAG,
            "Conectando con ${sharedDeviceName} " +
                    "(${sharedDeviceAddress})"
        )

        try {
            sharedGatt = device.connectGatt(
                context.applicationContext,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } catch (exception: Exception) {
            cerrarGattAnterior()

            notificarError(
                "No se pudo conectar: ${exception.message}"
            )
        }
    }

    // ═══════════════════════════════════════════════
    // GATT
    // ═══════════════════════════════════════════════

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(
            bluetoothGatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            when (newState) {

                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(
                        TAG,
                        "Conectado GATT. Descubriendo servicios..."
                    )

                    sharedConnected = true
                    sharedAuthenticated = false

                    notificarConexion(true)

                    val started = bluetoothGatt.discoverServices()

                    if (!started) {
                        notificarError(
                            "No se pudo iniciar el descubrimiento " +
                                    "de servicios"
                        )
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(
                        TAG,
                        "GATT desconectado. Estado=$status"
                    )

                    sharedConnected = false
                    sharedAuthenticated = false
                    sharedRxCharacteristic = null

                    try {
                        bluetoothGatt.close()
                    } catch (_: Exception) {
                    }

                    if (sharedGatt === bluetoothGatt) {
                        sharedGatt = null
                    }

                    notificarConexion(false)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(
            bluetoothGatt: BluetoothGatt,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                notificarError(
                    "Error descubriendo servicios: $status"
                )
                return
            }

            val service = bluetoothGatt.getService(
                UUID_APP_SVC
            )

            if (service == null) {
                notificarError(
                    "Servicio MT Guard no encontrado"
                )
                return
            }

            sharedRxCharacteristic = service
                .getCharacteristic(UUID_APP_RX)

            if (sharedRxCharacteristic == null) {
                notificarError(
                    "Característica RX no encontrada"
                )
                return
            }

            val txCharacteristic = service
                .getCharacteristic(UUID_APP_TX)

            if (txCharacteristic != null) {
                bluetoothGatt.setCharacteristicNotification(
                    txCharacteristic,
                    true
                )

                val descriptor = txCharacteristic
                    .getDescriptor(UUID_CCCD)

                if (descriptor != null) {
                    descriptor.value =
                        BluetoothGattDescriptor
                            .ENABLE_NOTIFICATION_VALUE

                    bluetoothGatt.writeDescriptor(
                        descriptor
                    )
                }
            }

            Log.i(
                TAG,
                "Servicios encontrados. Autenticando..."
            )

            mainHandler.postDelayed({
                authenticate()
            }, 500L)
        }

        @Deprecated(
            "Método conservado para compatibilidad"
        )
        override fun onCharacteristicChanged(
            bluetoothGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            procesarDatos(characteristic.value)
        }
    }

    private fun procesarDatos(data: ByteArray?) {
        if (data == null) return

        val text = String(
            data,
            Charsets.UTF_8
        )

        Log.i(TAG, "Notificación recibida: $text")

        mainHandler.post {
            dataCallback?.invoke(text)
        }
    }

    // ═══════════════════════════════════════════════
    // AUTENTICACIÓN Y COMANDOS
    // ═══════════════════════════════════════════════

    fun authenticate() {
        val sent = enviarComando("1")

        if (sent) {
            sharedAuthenticated = true

            Log.i(TAG, "Autenticación enviada")

            notificarAutenticado()
        }
    }

    fun sendArrancar() {
        if (!sharedAuthenticated) {
            notificarError("Dispositivo no autenticado")
            return
        }

        enviarComando("ARRANCAR")
    }

    fun sendDesbloquear() {
        if (!sharedAuthenticated) {
            notificarError("Dispositivo no autenticado")
            return
        }

        enviarComando("DESBLOQUEAR")
    }

    fun sendIniciarViajeSeguro() {
        if (!sharedAuthenticated) {
            notificarError("Dispositivo no autenticado")
            return
        }

        enviarComando("VIAJE_SEGURO_ON")
    }

    fun sendBeaconOn() {
        enviarComando("BEACON_ON")
    }

    fun sendBeaconOff() {
        enviarComando("BEACON_OFF")
    }

    @SuppressLint("MissingPermission")
    private fun enviarComando(command: String): Boolean {
        val characteristic = sharedRxCharacteristic
        val bluetoothGatt = sharedGatt

        if (
            characteristic == null ||
            bluetoothGatt == null ||
            !sharedConnected
        ) {
            notificarError("Dispositivo no conectado")
            return false
        }

        return try {
            characteristic.value = command.toByteArray(
                Charsets.UTF_8
            )

            characteristic.writeType =
                BluetoothGattCharacteristic
                    .WRITE_TYPE_DEFAULT

            val sent = bluetoothGatt.writeCharacteristic(
                characteristic
            )

            Log.d(
                TAG,
                "Comando '$command' enviado=$sent"
            )

            if (!sent) {
                notificarError(
                    "No se pudo enviar el comando"
                )
            }

            sent

        } catch (exception: Exception) {
            notificarError(
                "Error enviando comando: ${exception.message}"
            )

            false
        }
    }

    // ═══════════════════════════════════════════════
    // DESCONEXIÓN
    // ═══════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    fun disconnect() {
        detenerEscaneoInterno()

        try {
            sharedGatt?.disconnect()
        } catch (_: Exception) {
        }

        try {
            sharedGatt?.close()
        } catch (_: Exception) {
        }

        sharedGatt = null
        sharedRxCharacteristic = null

        sharedConnected = false
        sharedAuthenticated = false

        sharedDeviceAddress = null
        sharedDeviceName = null

        notificarConexion(false)

        Log.i(TAG, "Desconectado")
    }

    @SuppressLint("MissingPermission")
    private fun detenerEscaneoInterno() {
        if (!sharedScanning) return

        try {
            sharedScanner?.stopScan(
                internalScanCallback
            )
        } catch (_: Exception) {
        }

        sharedScanning = false
    }

    @SuppressLint("MissingPermission")
    private fun cerrarGattAnterior() {
        try {
            sharedGatt?.disconnect()
        } catch (_: Exception) {
        }

        try {
            sharedGatt?.close()
        } catch (_: Exception) {
        }

        sharedGatt = null
        sharedRxCharacteristic = null
    }

    // ═══════════════════════════════════════════════
    // CALLBACKS
    // ═══════════════════════════════════════════════

    private fun notificarConexion(connected: Boolean) {
        mainHandler.post {
            connectionCallback?.invoke(connected)
        }
    }

    private fun notificarAutenticado() {
        mainHandler.post {
            authenticatedCallback?.invoke()
        }
    }

    private fun notificarError(message: String) {
        Log.e(TAG, message)

        mainHandler.post {
            errorCallback?.invoke(message)
        }
    }

    // ═══════════════════════════════════════════════
    // PERMISOS
    // ═══════════════════════════════════════════════

    private fun tienePermisos(): Boolean {
        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}