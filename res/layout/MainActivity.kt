
package com.example.torellidiagnostics

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var etMacAddress: EditText
    private lateinit var btnConnect: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvRpm: TextView
    private lateinit var tvGasPress: TextView
    private lateinit var tvMapPress: TextView
    private lateinit var tvTGas: TextView
    private lateinit var tvTReducer: TextView

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etMacAddress = findViewById(R.id.etMacAddress)
        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)
        tvRpm = findViewById(R.id.tvRpm)
        tvGasPress = findViewById(R.id.tvGasPress)
        tvMapPress = findViewById(R.id.tvMapPress)
        tvTGas = findViewById(R.id.tvTGas)
        tvTReducer = findViewById(R.id.tvTReducer)

        checkPermissions()

        btnConnect.setOnClickListener {
            val mac = etMacAddress.text.toString().trim()
            if (mac.isNotEmpty()) {
                connectToDevice(mac)
            } else {
                Toast.makeText(this, "Введіть MAC-адресу Bluetooth", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                1
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(macAddress: String) {
        tvStatus.text = "Статус: Підключення..."
        Thread {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                val device = adapter.getRemoteDevice(macAddress)
                adapter.cancelDiscovery()

                socket = device.createRfcommSocketToServiceRecord(sppUuid)
                socket?.connect()

                inputStream = socket?.inputStream
                outputStream = socket?.outputStream
                isConnected = true

                runOnUiThread {
                    tvStatus.text = "Статус: Підключено"
                    btnConnect.isEnabled = false
                }

                startTelemetryLoop()

            } catch (e: Exception) {
                isConnected = false
                runOnUiThread {
                    tvStatus.text = "Помилка підключення: ${e.localizedMessage}"
                }
            }
        }.start()
    }

    private fun startTelemetryLoop() {
        val requestFrame = byteArrayOf(0x3A, 0x01, 0x02, 0x00, 0x00, 0x3C)
        val buffer = ByteArray(64)

        Thread {
            while (isConnected) {
                try {
                    outputStream?.write(requestFrame)
                    outputStream?.flush()

                    Thread.sleep(100) // Затримка між запитами

                    val bytesRead = inputStream?.read(buffer) ?: -1
                    if (bytesRead >= 10) {
                        parseTelemetry(buffer)
                    }

                    Thread.sleep(150)
                } catch (e: Exception) {
                    isConnected = false
                    runOnUiThread { tvStatus.text = "Зв'язок втрачено" }
                    break
                }
            }
        }.start()
    }

    private fun parseTelemetry(data: ByteArray) {
        // Простий парсинг структури даних Torelli T3
        val rpm = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        val gasPressRaw = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        val mapPressRaw = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        val tGas = data[8].toInt() - 40
        val tReducer = data[9].toInt() - 40

        runOnUiThread {
            tvRpm.text = "Оберти: $rpm об/хв"
            tvGasPress.text = String.format("Тиск газу: %.2f бар", gasPressRaw / 100.0f)
            tvMapPress.text = String.format("Тиск MAP: %.2f бар", mapPressRaw / 100.0f)
            tvTGas.text = "Темп. газу: $tGas °C"
            tvTReducer.text = "Темп. редуктора: $tReducer °C"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            isConnected = false
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
