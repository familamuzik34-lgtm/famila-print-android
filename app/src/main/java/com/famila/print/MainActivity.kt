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
    private lateinit var brandInput: EditText
    private lateinit var productInput: EditText
    private lateinit var barcodeInput: EditText
    private lateinit var widthInput: EditText
    private lateinit var heightInput: EditText
    private lateinit var countInput: EditText
    private lateinit var printButton: Button
    private lateinit var statusText: TextView

    private val printers = mutableListOf<BluetoothDevice>()
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val prefs by lazy { getSharedPreferences("famila_print", MODE_PRIVATE) }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        printerSpinner = findViewById(R.id.printerSpinner)
        brandInput = findViewById(R.id.brandInput)
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
        val uri = intent?.data ?: return
        if (uri.scheme != "famila-print") return
        uri.getQueryParameter("brand")?.let { brandInput.setText(it) }
        uri.getQueryParameter("name")?.let { productInput.setText(it) }
        uri.getQueryParameter("barcode")?.let { barcodeInput.setText(it) }
        uri.getQueryParameter("width")?.let { widthInput.setText(it) }
        uri.getQueryParameter("height")?.let { heightInput.setText(it) }
        uri.getQueryParameter("count")?.let { countInput.setText(it) }
    }

    private fun ensureBluetoothPermissionAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1001)
            return
        }
        loadPairedPrinters()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) loadPairedPrinters()
            else setStatus("Yakındaki cihazlar izni verilmedi. Yazıcıya bağlanmak için bu izin gerekli.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedPrinters() {
        val adapter = bluetoothAdapter ?: run { setStatus("Bu telefonda Bluetooth bulunamadı."); return }
        if (!adapter.isEnabled) { setStatus("Bluetooth kapalı. Önce Bluetooth'u açın."); return }
        printers.clear(); printers.addAll(adapter.bondedDevices.sortedBy { it.name ?: it.address })
        if (printers.isEmpty()) {
            printerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Eşleşmiş yazıcı yok"))
            setStatus("XP-P328B'yi önce Android Bluetooth ayarlarından eşleştirin."); return
        }
        val labels = printers.map { "${it.name?.takeIf { n -> n.isNotBlank() } ?: "Bluetooth cihazı"}  •  ${it.address}" }
        printerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val savedMac = prefs.getString("printer_mac", null)
        val savedIndex = printers.indexOfFirst { it.address == savedMac }
        if (savedIndex >= 0) printerSpinner.setSelection(savedIndex)
        setStatus("${printers.size} eşleşmiş Bluetooth cihazı bulundu. XP-P328B'yi seçin.")
    }

    private fun printLabel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1001); return
        }
        if (printers.isEmpty()) { Toast.makeText(this, "Önce XP-P328B'yi Bluetooth ile eşleştirin.", Toast.LENGTH_LONG).show(); return }
        val width = widthInput.text.toString().replace(',', '.').toDoubleOrNull()
        val height = heightInput.text.toString().replace(',', '.').toDoubleOrNull()
        val count = countInput.text.toString().toIntOrNull()
        val brand = brandInput.text.toString().trim(); val product = productInput.text.toString().trim(); val barcode = barcodeInput.text.toString().trim()
        if (width == null || width !in 10.0..80.0) { widthInput.error = "10–80 mm arasında bir genişlik girin"; return }
        if (height == null || height !in 10.0..80.0) { heightInput.error = "10–80 mm arasında bir yükseklik girin"; return }
        if (count == null || count !in 1..100) { countInput.error = "1–100 arasında adet girin"; return }
        if (barcode.isBlank()) { barcodeInput.error = "Barkod boş olamaz"; return }
        val device = printers[printerSpinner.selectedItemPosition.coerceIn(0, printers.lastIndex)]
        prefs.edit().putString("width", width.toString().removeSuffix(".0")).putString("height", height.toString().removeSuffix(".0")).putString("count", count.toString()).putString("printer_mac", device.address).apply()
        printButton.isEnabled = false; setStatus("${device.name ?: "Yazıcı"} cihazına bağlanılıyor…")
        Thread {
            try {
                sendTspl(device, width, height, count, brand, product, barcode)
                runOnUiThread { printButton.isEnabled = true; setStatus("✅ $count etiket yazıcıya gönderildi."); Toast.makeText(this, "Etiket gönderildi", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                runOnUiThread { printButton.isEnabled = true; setStatus("❌ Yazdırma başarısız: ${e.message ?: e.javaClass.simpleName}") }
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun sendTspl(device: BluetoothDevice, widthMm: Double, heightMm: Double, count: Int, brand: String, product: String, barcode: String) {
        val command = buildTspl(widthMm, heightMm, count, brand, product, barcode)
        val bytes = command.toByteArray(Charset.forName("windows-1254"))
        var lastError: Exception? = null
        val attempts = listOf<(BluetoothDevice) -> BluetoothSocket>(
            { d -> d.createInsecureRfcommSocketToServiceRecord(sppUuid) },
            { d -> d.createRfcommSocketToServiceRecord(sppUuid) },
            { d -> val method = d.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType); method.invoke(d, 1) as BluetoothSocket }
        )
        for (factory in attempts) {
            var socket: BluetoothSocket? = null
            try {
                socket = factory(device); socket.connect(); Thread.sleep(250)
                socket.outputStream.apply { write(bytes); flush() }; Thread.sleep(350); return
            } catch (e: Exception) { lastError = e } finally { try { socket?.close() } catch (_: Exception) {} }
        }
        throw Exception("Bluetooth bağlantısı kurulamadı. ${lastError?.message ?: "SPP kanal hatası"}")
    }

    private fun buildTspl(widthMm: Double, heightMm: Double, count: Int, brand: String, product: String, barcode: String): String {
        val dotsPerMm = 8; val hDots = (heightMm * dotsPerMm).toInt(); val compact = heightMm < 20
        fun safe(value: String) = value.replace("\"", "'").replace("\r", " ").replace("\n", " ").take(42)
        val sb = StringBuilder()
        sb.append("SIZE ${fmt(widthMm)} mm,${fmt(heightMm)} mm\r\nGAP 2 mm,0 mm\r\nDIRECTION 1\r\nREFERENCE 0,0\r\nCLS\r\nCODEPAGE 1254\r\n")
        if (compact) {
            val title = listOf(brand, product).filter { it.isNotBlank() }.joinToString(" ")
            if (title.isNotBlank()) sb.append("TEXT 12,4,\"2\",0,1,1,\"${safe(title)}\"\r\n")
            val barcodeY = 26; val barcodeHeight = (hDots - barcodeY - 18).coerceIn(34, 62)
            sb.append("BARCODE 12,$barcodeY,\"128\",$barcodeHeight,1,0,2,2,\"${safe(barcode)}\"\r\n")
        } else {
            if (brand.isNotBlank()) sb.append("TEXT 16,10,\"3\",0,1,1,\"${safe(brand)}\"\r\n")
            if (product.isNotBlank()) sb.append("TEXT 16,38,\"2\",0,1,1,\"${safe(product)}\"\r\n")
            val barcodeY = 70; val barcodeHeight = (hDots - barcodeY - 28).coerceIn(55, 105)
            sb.append("BARCODE 16,$barcodeY,\"128\",$barcodeHeight,1,0,2,2,\"${safe(barcode)}\"\r\n")
        }
        sb.append("PRINT $count,1\r\n"); return sb.toString()
    }

    private fun fmt(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(java.util.Locale.US, value)
    private fun setStatus(text: String) { statusText.text = text; statusText.visibility = View.VISIBLE }
}
