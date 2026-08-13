package com.tuapp.mt_guard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionsActivity : AppCompatActivity() {

    // TODOS los permisos, se piden de una sola vez al inicio
    private val permisosSolicitados: Array<String>
        get() {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.SEND_SMS
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                perms.add(Manifest.permission.BLUETOOTH_SCAN)
                perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            return perms.toTypedArray()
        }

    // Sin estos la app NO funciona (escanear / conectar / GPS)
    private val permisosCriticos: Array<String>
        get() {
            val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                perms.add(Manifest.permission.BLUETOOTH_SCAN)
                perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            return perms.toTypedArray()
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { evaluarResultado() }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { evaluarResultado() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fondo oscuro para no ver pantalla negra tras el diálogo (sin XML)
        window.decorView.setBackgroundColor(Color.parseColor("#0A0E1A"))

        if (criticosConcedidos()) {
            continuar()
        } else {
            permissionLauncher.launch(permisosSolicitados)
        }
    }

    private fun criticosConcedidos(): Boolean =
        permisosCriticos.all {
            ContextCompat.checkSelfPermission(this, it) ==
                    PackageManager.PERMISSION_GRANTED
        }

    private fun evaluarResultado() {
        if (criticosConcedidos()) {
            avisarOpcionalesFaltantes()
            continuar()
            return
        }

        val permanente = permisosCriticos
            .filter {
                ContextCompat.checkSelfPermission(this, it) !=
                        PackageManager.PERMISSION_GRANTED
            }
            .any { !shouldShowRequestPermissionRationale(it) }

        if (permanente) mostrarDialogoSettings()
        else mostrarDialogoReintentar()
    }

    private fun avisarOpcionalesFaltantes() {
        val sinSms = ContextCompat.checkSelfPermission(
            this, Manifest.permission.SEND_SMS
        ) != PackageManager.PERMISSION_GRANTED

        if (sinSms) {
            Toast.makeText(
                this,
                "Sin permiso de SMS no se enviarán alertas de robo",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun continuar() {
        // Solo arranca el servicio. Los pop-ups de batería/Xiaomi
        // se piden DESPUÉS del login, en ScannerActivity.
        GuardService.iniciar(this)

        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }

    private fun mostrarDialogoReintentar() {
        AlertDialog.Builder(this)
            .setTitle("Permisos necesarios")
            .setMessage(
                "MT GUARD necesita estos permisos para proteger tu vehículo:\n\n" +
                        "• Ubicación (rastreo GPS)\n" +
                        "• Bluetooth (conexión con el módulo)\n\n" +
                        "Sin ellos la app no puede funcionar."
            )
            .setPositiveButton("Reintentar") { _, _ ->
                permissionLauncher.launch(permisosSolicitados)
            }
            .setNegativeButton("Salir") { _, _ -> finishAffinity() }
            .setCancelable(false)
            .show()
    }

    private fun mostrarDialogoSettings() {
        AlertDialog.Builder(this)
            .setTitle("Permisos bloqueados")
            .setMessage(
                "Denegaste permisos de forma permanente. " +
                        "Actívalos manualmente en Configuración para continuar."
            )
            .setPositiveButton("Ir a Configuración") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .apply { data = Uri.fromParts("package", packageName, null) }
                settingsLauncher.launch(intent)
            }
            .setNegativeButton("Salir") { _, _ -> finishAffinity() }
            .setCancelable(false)
            .show()
    }
}