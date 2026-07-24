package com.tuapp.mt_guard

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ScannerActivity : AppCompatActivity() {

    companion object {
        private const val TARGET_PREFIX = "MT GUARD"
        private const val SCAN_DURATION_MS = 20_000L

        const val DEMO_MODE = false

        private const val DEMO_SALTO_DIRECTO = false
        private const val DEMO_NOMBRE = "MT GUARD • EE01"
        private const val DEMO_MAC = "AA:BB:CC:DD:EE:01"
    }

    private lateinit var radarView: RadarView
    private lateinit var tvScanStatus: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var deviceContainer: LinearLayout
    private lateinit var btnScan: Button
    private lateinit var connectionOverlay: View
    private lateinit var tvConnectionStatus: TextView

    private lateinit var bleManager: BleManager

    private val handler = Handler(Looper.getMainLooper())

    private var scanner: BluetoothLeScanner? = null
    private var scanning = false
    private var connecting = false
    private var navigationFinished = false

    private val devices = linkedMapOf<String, FoundDevice>()
    private val deviceRows = mutableMapOf<String, DeviceRow>()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(
            BLUETOOTH_SERVICE
        ) as? BluetoothManager
        bluetoothManager?.adapter
    }

    private data class FoundDevice(
        val device: BluetoothDevice,
        var name: String,
        var rssi: Int
    )

    private data class DeviceRow(
        val root: View,
        val tvName: TextView,
        val tvMac: TextView,
        val tvRssi: TextView,
        val tvSignal: TextView
    )

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val allGranted = result.values.all { it }

            if (allGranted) {
                verificarBluetooth()
            } else {
                tvScanStatus.text =
                    "Permisos denegados — actívalos en Ajustes"
                Toast.makeText(
                    this,
                    "Debes autorizar Bluetooth, Ubicación y SMS para usar la app",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (bluetoothAdapter?.isEnabled == true) {
                iniciarEscaneo()
            } else {
                tvScanStatus.text = "Bluetooth desactivado"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        enlazarVistas()
        configurarEventos()

        if (DEMO_MODE) {
            iniciarModoDemo()
            return
        }

        configurarBleManager()
        prepararEscaneo()
    }

    private fun enlazarVistas() {
        radarView = findViewById(R.id.radarView)
        tvScanStatus = findViewById(R.id.tvScanStatus)
        tvEmpty = findViewById(R.id.tvEmpty)
        deviceContainer = findViewById(R.id.deviceContainer)
        btnScan = findViewById(R.id.btnScan)
        connectionOverlay = findViewById(R.id.connectionOverlay)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)

        findViewById<View>(R.id.btnConfig).setOnClickListener {
            startActivity(
                Intent(this, ConfigPinActivity::class.java)
            )
            overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        }
    }

    private fun configurarBleManager() {
        bleManager = BleManager(
            context = this,
            onConnectionChange = { connected ->
                runOnUiThread {
                    if (navigationFinished) return@runOnUiThread
                    if (connected) {
                        tvConnectionStatus.text =
                            "Verificando módulo MT Guard..."
                    } else if (connecting) {
                        mostrarErrorConexion(
                            "No se pudo establecer la conexión"
                        )
                    }
                }
            },
            onAuthenticated = {
                runOnUiThread { abrirPanelPrincipal() }
            },
            onData = {},
            onError = { message ->
                runOnUiThread { mostrarErrorConexion(message) }
            }
        )
    }

    private fun configurarEventos() {
        btnScan.setOnClickListener {
            if (connecting) return@setOnClickListener

            if (DEMO_MODE) {
                if (scanning) detenerEscaneoDemo()
                else iniciarEscaneoDemo()
                return@setOnClickListener
            }

            if (scanning) detenerEscaneo(showFinishedText = true)
            else prepararEscaneo()
        }
    }

    // ═══════════════════════════════════════════════
    // MODO DEMO
    // ═══════════════════════════════════════════════

    private fun iniciarModoDemo() {
        tvScanStatus.text = "MODO DEMO — sin hardware"

        if (DEMO_SALTO_DIRECTO) {
            connectionOverlay.visibility = View.VISIBLE
            tvConnectionStatus.text = "Conectando con $DEMO_NOMBRE..."
            handler.postDelayed({
                abrirPanelPrincipalDemo(DEMO_NOMBRE, DEMO_MAC)
            }, 600L)
            return
        }

        iniciarEscaneoDemo()
    }

    private fun iniciarEscaneoDemo() {
        handler.removeCallbacksAndMessages(null)
        deviceRows.clear()
        deviceContainer.removeAllViews()

        tvEmpty.visibility = View.VISIBLE
        tvEmpty.text = "Buscando módulos MT Guard cercanos..."

        scanning = true
        radarView.setScanning(true)

        tvScanStatus.text = "Escaneando dispositivos cercanos..."
        btnScan.text = "DETENER ESCANEO"
        btnScan.isEnabled = true

        handler.postDelayed({
            crearTarjetaDemo("MT GUARD", "AA:BB:CC:DD:EE:01", -52)
        }, 1200L)

        handler.postDelayed({
            crearTarjetaDemo("MT GUARD", "AA:BB:CC:DD:EE:02", -71)
        }, 2400L)

        handler.postDelayed({
            crearTarjetaDemo("MT GUARD 03", "AA:BB:CC:DD:EE:03", -84)
        }, 3600L)

        handler.postDelayed({ detenerEscaneoDemo() }, 5200L)
    }

    private fun detenerEscaneoDemo() {
        if (connecting || navigationFinished) return
        scanning = false
        radarView.setScanning(false)
        btnScan.text = "BUSCAR NUEVAMENTE"
        btnScan.isEnabled = true
        tvScanStatus.text = "${deviceRows.size} dispositivo(s) encontrado(s)"
    }

    private fun crearTarjetaDemo(name: String, address: String, rssi: Int) {
        if (connecting || navigationFinished) return

        val item = LayoutInflater.from(this)
            .inflate(R.layout.item_mt_device, deviceContainer, false)

        val row = DeviceRow(
            root = item,
            tvName = item.findViewById(R.id.tvDeviceName),
            tvMac = item.findViewById(R.id.tvDeviceMac),
            tvRssi = item.findViewById(R.id.tvDeviceRssi),
            tvSignal = item.findViewById(R.id.tvSignalBars)
        )

        deviceRows[address] = row

        val visualName = crearNombreVisual(name, address)
        row.tvName.text = visualName
        row.tvMac.text = address
        row.tvRssi.text = "$rssi dBm"
        row.tvSignal.text = obtenerBarrasSenal(rssi)

        val signalColor = obtenerColorSenal(rssi)
        row.tvSignal.setTextColor(signalColor)
        row.tvRssi.setTextColor(signalColor)

        item.setOnClickListener {
            if (!connecting) simularConexionDemo(visualName, address)
        }

        deviceContainer.addView(item)
        tvEmpty.visibility = View.GONE
        tvScanStatus.text = "${deviceRows.size} dispositivo(s) MT Guard"
    }

    private fun simularConexionDemo(visualName: String, address: String) {
        if (connecting) return
        connecting = true
        scanning = false
        handler.removeCallbacksAndMessages(null)
        radarView.setScanning(false)
        habilitarTarjetas(false)
        btnScan.isEnabled = false
        connectionOverlay.visibility = View.VISIBLE
        tvConnectionStatus.text = "Conectando con $visualName..."

        handler.postDelayed({
            tvConnectionStatus.text = "Verificando módulo MT Guard..."
            handler.postDelayed({
                abrirPanelPrincipalDemo(visualName, address)
            }, 900L)
        }, 1200L)
    }

    private fun abrirPanelPrincipalDemo(name: String, address: String) {
        if (navigationFinished) return
        navigationFinished = true
        connecting = false

        ConfigBeaconActivity.guardarMacDesde(this, address)

        tvConnectionStatus.text = "Conexión segura establecida"

        connectionOverlay.postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("DEVICE_NAME", name)
            intent.putExtra("DEVICE_ADDRESS", address)
            intent.putExtra("DEMO_MODE", true)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 350L)
    }

    // ═══════════════════════════════════════════════
    // PERMISOS
    // ═══════════════════════════════════════════════

    private fun prepararEscaneo() {
        if (!tienePermisos()) {
            solicitarPermisos()
            return
        }

        pedirPermisoNotificaciones()
        verificarBluetooth()
    }

    @SuppressLint("MissingPermission")
    private fun verificarBluetooth() {
        val adapter = bluetoothAdapter

        if (adapter == null) {
            tvScanStatus.text = "Este teléfono no tiene Bluetooth"
            return
        }

        if (!adapter.isEnabled) {
            enableBluetoothLauncher.launch(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            )
            return
        }

        iniciarEscaneo()
    }

    private fun tienePermisos(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this, Manifest.permission.SEND_SMS
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this, Manifest.permission.SEND_SMS
                    ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun solicitarPermisos() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.SEND_SMS
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.SEND_SMS
            )
        }

        permissionLauncher.launch(permissions)
    }

    private fun pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                )
            }
        }
    }

    // ═══════════════════════════════════════════════
    // ESCANEO
    // ═══════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    private fun iniciarEscaneo() {
        if (scanning || connecting) return

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            tvScanStatus.text = "Bluetooth desactivado"
            return
        }

        devices.clear()
        deviceRows.clear()
        deviceContainer.removeAllViews()

        tvEmpty.visibility = View.VISIBLE
        tvEmpty.text = "Buscando módulos MT Guard cercanos..."

        scanner = adapter.bluetoothLeScanner

        if (scanner == null) {
            tvScanStatus.text = "No se pudo iniciar el escáner"
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner?.startScan(null, settings, scanCallback)
            scanning = true
            radarView.setScanning(true)
            tvScanStatus.text = "Escaneando dispositivos cercanos..."
            btnScan.text = "DETENER ESCANEO"
            btnScan.isEnabled = true
            handler.removeCallbacks(stopScanRunnable)
            handler.postDelayed(stopScanRunnable, SCAN_DURATION_MS)

        } catch (exception: Exception) {
            scanning = false
            radarView.setScanning(false)
            tvScanStatus.text = "Error al iniciar el escaneo"
            Toast.makeText(
                this,
                exception.message ?: "Error de Bluetooth",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val stopScanRunnable = Runnable {
        detenerEscaneo(showFinishedText = true)
    }

    @SuppressLint("MissingPermission")
    private fun detenerEscaneo(showFinishedText: Boolean) {
        handler.removeCallbacks(stopScanRunnable)

        if (scanning) {
            try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
        }

        scanning = false
        radarView.setScanning(false)

        if (!connecting) {
            btnScan.text = "BUSCAR NUEVAMENTE"
            btnScan.isEnabled = true
        }

        if (showFinishedText) {
            tvScanStatus.text = if (devices.isEmpty()) {
                "No se encontraron módulos MT Guard"
            } else {
                "${devices.size} dispositivo(s) encontrado(s)"
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceName = try {
                result.scanRecord?.deviceName ?: result.device.name
            } catch (_: SecurityException) { null }

            if (deviceName == null ||
                !deviceName.startsWith(TARGET_PREFIX, ignoreCase = true)
            ) return

            val address = try {
                result.device.address
            } catch (_: SecurityException) { return }

            runOnUiThread {
                if (connecting) return@runOnUiThread
                agregarOActualizarDispositivo(
                    address = address,
                    device = result.device,
                    name = deviceName,
                    rssi = result.rssi
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread {
                scanning = false
                radarView.setScanning(false)
                btnScan.text = "INTENTAR NUEVAMENTE"
                btnScan.isEnabled = true
                tvScanStatus.text = "Error de escaneo: $errorCode"
            }
        }
    }

    // ═══════════════════════════════════════════════
    // TARJETAS DE DISPOSITIVOS
    // ═══════════════════════════════════════════════

    private fun agregarOActualizarDispositivo(
        address: String,
        device: BluetoothDevice,
        name: String,
        rssi: Int
    ) {
        val existingDevice = devices[address]

        if (existingDevice == null) {
            val foundDevice = FoundDevice(device = device, name = name, rssi = rssi)
            devices[address] = foundDevice
            crearTarjeta(address = address, foundDevice = foundDevice)
        } else {
            existingDevice.name = name
            existingDevice.rssi = rssi
            actualizarTarjeta(address = address, foundDevice = existingDevice)
        }

        tvEmpty.visibility = View.GONE
        tvScanStatus.text = "${devices.size} dispositivo(s) MT Guard"
    }

    private fun crearTarjeta(address: String, foundDevice: FoundDevice) {
        val item = LayoutInflater.from(this)
            .inflate(R.layout.item_mt_device, deviceContainer, false)

        val row = DeviceRow(
            root = item,
            tvName = item.findViewById(R.id.tvDeviceName),
            tvMac = item.findViewById(R.id.tvDeviceMac),
            tvRssi = item.findViewById(R.id.tvDeviceRssi),
            tvSignal = item.findViewById(R.id.tvSignalBars)
        )

        deviceRows[address] = row
        actualizarTarjeta(address = address, foundDevice = foundDevice)

        item.setOnClickListener {
            if (!connecting) vincularDispositivo(foundDevice)
        }

        deviceContainer.addView(item)
    }

    private fun actualizarTarjeta(address: String, foundDevice: FoundDevice) {
        val row = deviceRows[address] ?: return
        row.tvName.text = crearNombreVisual(foundDevice.name, address)
        row.tvMac.text = address
        row.tvRssi.text = "${foundDevice.rssi} dBm"
        row.tvSignal.text = obtenerBarrasSenal(foundDevice.rssi)

        val signalColor = obtenerColorSenal(foundDevice.rssi)
        row.tvSignal.setTextColor(signalColor)
        row.tvRssi.setTextColor(signalColor)
    }

    private fun habilitarTarjetas(enabled: Boolean) {
        deviceRows.values.forEach { row ->
            row.root.isEnabled = enabled
            row.root.isClickable = enabled
            row.root.alpha = if (enabled) 1f else 0.55f
        }
    }

    private fun crearNombreVisual(advertisedName: String, address: String): String {
        if (advertisedName.trim() != TARGET_PREFIX) return advertisedName
        val suffix = address.replace(":", "").takeLast(4)
        return if (suffix.isNotEmpty()) "$TARGET_PREFIX • $suffix" else TARGET_PREFIX
    }

    private fun obtenerBarrasSenal(rssi: Int): String {
        return when {
            rssi >= -55 -> "▂▄▆█"
            rssi >= -67 -> "▂▄▆"
            rssi >= -78 -> "▂▄"
            else -> "▂"
        }
    }

    private fun obtenerColorSenal(rssi: Int): Int {
        return when {
            rssi >= -60 -> ContextCompat.getColor(this, R.color.status_ok)
            rssi >= -75 -> ContextCompat.getColor(this, R.color.accent_light)
            else -> ContextCompat.getColor(this, R.color.status_warning)
        }
    }

    // ═══════════════════════════════════════════════
    // CONEXIÓN
    // ═══════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    private fun vincularDispositivo(foundDevice: FoundDevice) {
        if (connecting) return
        connecting = true

        detenerEscaneo(showFinishedText = false)
        habilitarTarjetas(false)
        btnScan.isEnabled = false

        val address = try { foundDevice.device.address } catch (_: SecurityException) { "" }
        val visualName = crearNombreVisual(foundDevice.name, address)

        connectionOverlay.visibility = View.VISIBLE
        tvConnectionStatus.text = "Conectando con $visualName..."

        bleManager.connect(foundDevice.device)
    }

    private fun mostrarErrorConexion(message: String) {
        if (navigationFinished) return
        connecting = false
        connectionOverlay.visibility = View.GONE
        habilitarTarjetas(true)
        btnScan.isEnabled = true
        btnScan.text = "BUSCAR NUEVAMENTE"
        tvScanStatus.text = "No se pudo conectar"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun abrirPanelPrincipal() {
        if (navigationFinished) return
        navigationFinished = true
        connecting = false

        bleManager.connectedDeviceAddress?.let { mac ->
            ConfigBeaconActivity.guardarMacDesde(this, mac)
        }

        tvConnectionStatus.text = "Conexión segura establecida"

        connectionOverlay.postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("DEVICE_NAME", bleManager.connectedDeviceName)
            intent.putExtra("DEVICE_ADDRESS", bleManager.connectedDeviceAddress)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 350L)
    }

    override fun onPause() {
        if (DEMO_MODE) { super.onPause(); return }
        if (!connecting) detenerEscaneo(showFinishedText = false)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (DEMO_MODE) { super.onDestroy(); return }
        detenerEscaneo(showFinishedText = false)
        super.onDestroy()
    }
}