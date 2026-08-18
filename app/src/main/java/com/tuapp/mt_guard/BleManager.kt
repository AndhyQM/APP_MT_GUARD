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
import android.bluetooth.BluetoothStatusCodes
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

        /*
         * PROTOCOLO JSON DEL FIRMWARE MT GUARD
         *
         * Comandos:   {"cmd":"AUTH"} {"cmd":"ARRANCAR"} {"cmd":"STOP_START"}
         *             {"cmd":"DESBLOQUEAR"} {"cmd":"VIAJE_SEGURO"} {"cmd":"GET"}
         * Config:     {"vol":30,"tmo":5,"bcn":true,...}
         * Reinicio:   {"rst":true}
         *
         * El firmware responde por APP_TX con JSON:
         *   {"ok":true,...} / {"ok":false,"err":"..."} / {"tipo":"config",...}
         */
        private const val CMD_AUTH         = "{\"cmd\":\"AUTH\"}"
        private const val CMD_ARRANCAR     = "{\"cmd\":\"ARRANCAR\"}"
        private const val CMD_STOP_START   = "{\"cmd\":\"STOP_START\"}"
        private const val CMD_DESBLOQUEAR  = "{\"cmd\":\"DESBLOQUEAR\"}"
        private const val CMD_VIAJE_SEGURO = "{\"cmd\":\"VIAJE_SEGURO\"}"
        private const val CMD_GET_CONFIG   = "{\"cmd\":\"GET\"}"
        private const val CMD_BEACON_ON    = "{\"bcn\":true}"
        private const val CMD_BEACON_OFF   = "{\"bcn\":false}"

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
        connectionCallback = onConnectionChange
        authenticatedCallback = onAuthenticated
        dataCallback = onData
        errorCallback = onError
    }

    // ═══════════════════════════════════════════════
    // ESCANEO AUTOMÁTICO ANTIGUO
    // ═══════════════════════════════════════════════

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
                        "Conectado GATT. Negociando MTU..."
                    )

                    sharedConnected = true
                    sharedAuthenticated = false

                    notificarConexion(true)

                    /*
                     * CRÍTICO: el MTU por defecto es 23 bytes (20 útiles)
                     * y el JSON de config del módulo mide ~150. Sin esto,
                     * el firmware notifica y el stack trunca a 20 bytes
                     * ("attribute value too long, truncated to 20").
                     * El descubrimiento de servicios se hace DESPUÉS,
                     * en onMtuChanged.
                     */
                    val mtuPedido = bluetoothGatt.requestMtu(512)

                    if (!mtuPedido) {
                        // Si no se pudo ni pedir, seguir igual con MTU 23
                        Log.w(TAG, "requestMtu falló — descubriendo igual")

                        val started = bluetoothGatt.discoverServices()

                        if (!started) {
                            notificarError(
                                "No se pudo iniciar el descubrimiento " +
                                        "de servicios"
                            )
                        }
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

        /*
         * Llega cuando termina la negociación de MTU (bien o mal).
         * Recién acá se descubren los servicios: si se hiciera antes,
         * la suscripción a APP_TX podría completarse con MTU 23 y el
         * primer SEND_CFG del firmware llegaría truncado.
         */
        @SuppressLint("MissingPermission")
        override fun onMtuChanged(
            bluetoothGatt: BluetoothGatt,
            mtu: Int,
            status: Int
        ) {
            Log.i(
                TAG,
                "MTU negociado: $mtu (status=$status). " +
                        "Descubriendo servicios..."
            )

            val started = bluetoothGatt.discoverServices()

            if (!started) {
                notificarError(
                    "No se pudo iniciar el descubrimiento " +
                            "de servicios"
                )
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
                    if (Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.TIRAMISU
                    ) {
                        // API 33+: método nuevo, sin descriptor.value
                        bluetoothGatt.writeDescriptor(
                            descriptor,
                            BluetoothGattDescriptor
                                .ENABLE_NOTIFICATION_VALUE
                        )
                    } else {
                        // Android 12 o menor: API legacy
                        @Suppress("DEPRECATION")
                        descriptor.value =
                            BluetoothGattDescriptor
                                .ENABLE_NOTIFICATION_VALUE

                        @Suppress("DEPRECATION")
                        bluetoothGatt.writeDescriptor(
                            descriptor
                        )
                    }
                }
            }

            /*
             * NO envía AUTH aquí. Solo notifica que los servicios
             * están listos para que ScannerActivity navegue a
             * MainActivity. La autenticación real (AUTH/VIAJE_SEGURO)
             * se hace cuando el usuario presiona Viaje Seguro.
             */
            Log.i(TAG, "Servicios listos. Esperando activación...")

            notificarAutenticado()
        }

        /*
         * API 33+ (Android 13 en adelante): el sistema entrega los
         * datos directamente como parámetro, sin characteristic.value.
         */
        override fun onCharacteristicChanged(
            bluetoothGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            procesarDatos(value)
        }

        /*
         * Android 12 o menor: el sistema sigue llamando a esta
         * versión vieja. En Android 13+ NO se invoca (llama a la
         * de arriba), así que no hay riesgo de procesar doble.
         */
        @Deprecated(
            "Método conservado para Android 12 o menor"
        )
        @Suppress("DEPRECATION")
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
    //
    // El firmware exige autenticación (AUTH o VIAJE_SEGURO) SOLO
    // para el arranque. La puerta funciona con la pura conexión,
    // para poder abrir el carro ANTES de subirse.
    // ═══════════════════════════════════════════════

    /*
     * Envía {"cmd":"AUTH"} al ESP32 y marca como autenticado.
     */
    fun authenticate() {
        val sent = enviarComando(CMD_AUTH)

        if (sent) {
            sharedAuthenticated = true
            Log.i(TAG, "Autenticación enviada — comandos habilitados")
        }
    }

    fun sendArrancar() {
        if (!sharedAuthenticated) {
            notificarError("Active Viaje Seguro primero")
            return
        }

        enviarComando(CMD_ARRANCAR)
    }

    fun sendDetenerArranque() {
        if (!sharedAuthenticated) return

        enviarComando(CMD_STOP_START)
    }

    /*
     * La puerta NO requiere Viaje Seguro: se puede desbloquear
     * apenas hay conexión BLE. El firmware valida igual.
     */
    fun sendDesbloquear() {
        enviarComando(CMD_DESBLOQUEAR)
    }

    /*
     * VIAJE_SEGURO ya autentica en el firmware (pone auth=true
     * y enciende el beacon), así que no hace falta mandar AUTH
     * por separado.
     */
    fun sendIniciarViajeSeguro() {
        val sent = enviarComando(CMD_VIAJE_SEGURO)

        if (sent) {
            sharedAuthenticated = true
            Log.i(TAG, "Viaje Seguro enviado — autenticado")
        }
    }

    fun requestConfig() {
        enviarComando(CMD_GET_CONFIG)
    }

    fun sendBeaconOn() {
        enviarComando(CMD_BEACON_ON)
    }

    fun sendBeaconOff() {
        enviarComando(CMD_BEACON_OFF)
    }

    /*
     * Para las pantallas de configuración: manda el JSON tal cual
     * (ej: {"vol":25,"tmo":10}). No requiere autenticación.
     */
    fun sendCommand(command: String) {
        enviarComando(command)
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
            val payload = command.toByteArray(Charsets.UTF_8)

            val sent: Boolean

            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {
                // API 33+: método nuevo, retorna código de estado
                val status = bluetoothGatt.writeCharacteristic(
                    characteristic,
                    payload,
                    BluetoothGattCharacteristic
                        .WRITE_TYPE_DEFAULT
                )

                sent = (status == BluetoothStatusCodes.SUCCESS)

            } else {
                // Android 12 o menor: API legacy
                @Suppress("DEPRECATION")
                characteristic.value = payload

                characteristic.writeType =
                    BluetoothGattCharacteristic
                        .WRITE_TYPE_DEFAULT

                @Suppress("DEPRECATION")
                sent = bluetoothGatt.writeCharacteristic(
                    characteristic
                )
            }

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