package com.famila.print

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.nio.charset.Charset
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var printerSpinner: Spinner
    private lateinit var typeInput: EditText
    private lateinit var brandInput: EditText
    private lateinit var colorInput: EditText
    private lateinit var productInput: EditText
    private lateinit var barcodeInput: EditText
    private lateinit var widthInput: EditText
    private lateinit var heightInput: EditText
    private lateinit var countInput: EditText
    private lateinit var printButton: Button
    private lateinit var statusText: TextView
    private val printers = mutableListOf<BluetoothDevice>()
    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val prefs by lazy { getSharedPreferences("famila_print", MODE_PRIVATE) }
    private val bluetoothAdapter: BluetoothAdapter? by lazy { (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        printerSpinner = findViewById(R.id.printerSpinner)
        typeInput = findViewById(R.id.typeInput)
        brandInput = findViewById(R.id.brandInput)
        colorInput = findViewById(R.id.colorInput)
        productInput = findViewById(R.id.productInput)
        barcodeInput = findViewById(R.id.barcodeInput)
        widthInput = findViewById(R.id.widthInput)
        heightInput = findViewById(R.id.heightInput)
        countInput = findViewById(R.id.countInput)
        printButton = findViewById(R.id.printButton)
        statusText = findViewById(R.id.statusText)
        widthInput.setText(prefs.getString("width", "40"))
        heightInput.setText(prefs.getString("height", "30"))
        countInput.setText(prefs.getString("count", "1"))
        findViewById<Button>(R.id.openBluetoothButton).setOnClickListener { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        printButton.setOnClickListener { printLabel() }
        readIncomingIntent(intent)
        ensureBluetoothPermissionAndLoad()
    }

    override fun onResume() { super.onResume(); if (::printerSpinner.isInitialized) ensureBluetoothPermissionAndLoad() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readIncomingIntent(intent) }

    private fun readIncomingIntent(intent: Intent?) {
        val u = intent?.data ?: return
        if (u.scheme != "famila-print") return
        u.getQueryParameter("type")?.let { typeInput.setText(it) }
        u.getQueryParameter("brand")?.let { brandInput.setText(it) }
        u.getQueryParameter("color")?.let { colorInput.setText(it) }
        u.getQueryParameter("name")?.let { productInput.setText(it) }
        u.getQueryParameter("barcode")?.let { barcodeInput.setText(it) }
        u.getQueryParameter("width")?.let { widthInput.setText(it) }
        u.getQueryParameter("height")?.let { heightInput.setText(it) }
        u.getQueryParameter("count")?.let { countInput.setText(it) }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1001)
    }

    private fun ensureBluetoothPermissionAndLoad() {
        if (!hasBluetoothPermissions()) { requestBluetoothPermissions(); return }
        loadPairedPrinters()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (hasBluetoothPermissions()) loadPairedPrinters() else setStatus("Bluetooth bağlantı izni gerekli.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedPrinters() {
        val a = bluetoothAdapter ?: run { setStatus("Bu telefonda Bluetooth bulunamadı."); return }
        if (!a.isEnabled) { setStatus("Bluetooth kapalı."); return }
        printers.clear(); printers.addAll(a.bondedDevices.sortedBy { it.name ?: it.address })
        if (printers.isEmpty()) {
            printerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Eşleşmiş yazıcı yok"))
            return
        }
        printerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, printers.map { "${it.name ?: "Bluetooth cihazı"}  •  ${it.address}" })
        val i = printers.indexOfFirst { it.address == prefs.getString("printer_mac", null) }
        if (i >= 0) printerSpinner.setSelection(i)
        setStatus("${printers.size} eşleşmiş Bluetooth cihazı bulundu.")
    }

    private fun printLabel() {
        if (!hasBluetoothPermissions()) { requestBluetoothPermissions(); return }
        if (printers.isEmpty()) { Toast.makeText(this, "Önce yazıcıyı eşleştirin.", Toast.LENGTH_LONG).show(); return }
        val w = widthInput.text.toString().replace(',', '.').toDoubleOrNull()
        val h = heightInput.text.toString().replace(',', '.').toDoubleOrNull()
        val c = countInput.text.toString().toIntOrNull()
        val type = typeInput.text.toString().trim()
        val brand = brandInput.text.toString().trim()
        val color = colorInput.text.toString().trim()
        val product = productInput.text.toString().trim()
        val barcode = barcodeInput.text.toString().trim()
        if (w == null || w !in 10.0..80.0) { widthInput.error = "10–80 mm"; return }
        if (h == null || h !in 10.0..80.0) { heightInput.error = "10–80 mm"; return }
        if (c == null || c !in 1..100) { countInput.error = "1–100"; return }
        if (barcode.isBlank()) { barcodeInput.error = "Barkod zorunlu"; return }
        val d = printers[printerSpinner.selectedItemPosition.coerceIn(0, printers.lastIndex)]
        prefs.edit().putString("width", fmt(w)).putString("height", fmt(h)).putString("count", c.toString()).putString("printer_mac", d.address).apply()
        printButton.isEnabled = false
        setStatus("${d.name ?: "Yazıcı"} cihazına bağlanılıyor…")
        Thread {
            try {
                sendTspl(d, w, h, c, type, brand, color, product, barcode)
                runOnUiThread { printButton.isEnabled = true; setStatus("✅ $c etiket yazıcıya gönderildi.") }
            } catch (e: Exception) {
                runOnUiThread { printButton.isEnabled = true; setStatus("❌ Yazdırma başarısız: ${e.message}") }
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun sendTspl(d: BluetoothDevice, w: Double, h: Double, c: Int, type: String, brand: String, color: String, product: String, barcode: String) {
        val bytes = buildTspl(w, h, c, type, brand, color, product, barcode).toByteArray(Charset.forName("windows-1254"))
        var last: Exception? = null
        val tries = listOf<(BluetoothDevice) -> BluetoothSocket>(
            { it.createInsecureRfcommSocketToServiceRecord(sppUuid) },
            { it.createRfcommSocketToServiceRecord(sppUuid) },
            { x -> x.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(x, 1) as BluetoothSocket }
        )
        for (f in tries) {
            var s: BluetoothSocket? = null
            try {
                s = f(d); s.connect(); Thread.sleep(200)
                s.outputStream.apply { write(bytes); flush() }
                Thread.sleep(300); return
            } catch (e: Exception) { last = e }
            finally { try { s?.close() } catch (_: Exception) {} }
        }
        throw Exception(last?.message ?: "SPP kanal hatası")
    }

    private fun buildTspl(w: Double, h: Double, count: Int, type: String, brand: String, color: String, product: String, barcode: String): String {
        fun safe(v: String) = v.replace("\"", "'").replace("\r", " ").replace("\n", " ").take(38)
        val sb = StringBuilder("SIZE ${fmt(w)} mm,${fmt(h)} mm\r\nGAP 2 mm,0 mm\r\nDIRECTION 1\r\nREFERENCE 0,0\r\nCLS\r\nCODEPAGE 1254\r\n")
        when {
            h < 15 || w < 32 -> {
                if (type.isNotBlank()) sb.append("TEXT 8,3,\"2\",0,1,1,\"${safe(type)}\"\r\n")
                sb.append("BARCODE 8,23,\"128\",42,1,0,1,2,\"${safe(barcode)}\"\r\n")
            }
            h < 24 || w < 38 -> {
                if (type.isNotBlank()) sb.append("TEXT 10,4,\"2\",0,1,1,\"${safe(type)}\"\r\n")
                if (brand.isNotBlank()) sb.append("TEXT 10,27,\"1\",0,1,1,\"${safe(brand)}\"\r\n")
                sb.append("BARCODE 10,48,\"128\",58,1,0,1,2,\"${safe(barcode)}\"\r\n")
            }
            else -> {
                if (type.isNotBlank()) sb.append("TEXT 14,7,\"3\",0,1,1,\"${safe(type)}\"\r\n")
                if (brand.isNotBlank()) sb.append("TEXT 14,38,\"2\",0,1,1,\"${safe(brand)}\"\r\n")
                val detail = listOf(color, product).filter { it.isNotBlank() }.joinToString(" ")
                if (detail.isNotBlank()) sb.append("TEXT 14,63,\"1\",0,1,1,\"${safe(detail)}\"\r\n")
                val bh = ((h * 8).toInt() - 104).coerceIn(55, 92)
                sb.append("BARCODE 14,88,\"128\",$bh,1,0,2,2,\"${safe(barcode)}\"\r\n")
            }
        }
        sb.append("PRINT $count,1\r\n")
        return sb.toString()
    }

    private fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(java.util.Locale.US, v)
    private fun setStatus(t: String) { statusText.text = t; statusText.visibility = View.VISIBLE }
}
