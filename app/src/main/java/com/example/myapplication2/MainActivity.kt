package com.example.myapplication2

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

class MainActivity : AppCompatActivity() {


    private lateinit var roleSelectionLayout: LinearLayout
    private lateinit var serverLayout: LinearLayout
    private lateinit var sensorLayout: LinearLayout
    private lateinit var selectServerButton: Button
    private lateinit var selectSensorButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var ipAddressTextView: TextView
    private lateinit var resetButton: Button
    private lateinit var serverIpEditText: EditText
    private lateinit var simulateIntruderButton: Button
    private lateinit var sensorStatusTextView: TextView

    // Lógica de Red
    private val serverPort = 5000
    private var serverSocket: ServerSocket? = null
    private var isAlarmActive = false
    @Volatile private var isSensorPolling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        roleSelectionLayout = findViewById(R.id.roleSelectionLayout)
        serverLayout = findViewById(R.id.serverLayout)
        sensorLayout = findViewById(R.id.sensorLayout)
        selectServerButton = findViewById(R.id.selectServerButton)
        selectSensorButton = findViewById(R.id.selectSensorButton)
        statusTextView = findViewById(R.id.statusTextView)
        ipAddressTextView = findViewById(R.id.ipAddressTextView)
        resetButton = findViewById(R.id.resetButton)
        serverIpEditText = findViewById(R.id.serverIpEditText)
        simulateIntruderButton = findViewById(R.id.simulateIntruderButton)
        sensorStatusTextView = findViewById(R.id.sensorStatusTextView)

        selectServerButton.setOnClickListener { setupServerMode() }
        selectSensorButton.setOnClickListener { setupSensorMode() }

        resetButton.setOnClickListener {
            isAlarmActive = false
            updateServerStatusUI()
            Toast.makeText(this, "Alarma restablecida.", Toast.LENGTH_SHORT).show()
        }

        simulateIntruderButton.setOnClickListener { sendAlertToServer() }
    }

    private fun setupServerMode() {
        roleSelectionLayout.visibility = View.GONE
        serverLayout.visibility = View.VISIBLE

        val ip = getIpAddress()
        ipAddressTextView.text = "IP del Servidor: $ip\n(Activa el Hotspot/Zona Wi-Fi)"
        startServer()
    }

    private fun setupSensorMode() {
        roleSelectionLayout.visibility = View.GONE
        sensorLayout.visibility = View.VISIBLE
        startSensorPolling()
    }

    private fun getIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        val isIPv4 = sAddr?.indexOf(':') ?: -1 < 0
                        if (isIPv4) return sAddr ?: "No disponible"
                    }
                }
            }
        } catch (ignored: Exception) {
        }
        return "No disponible"
    }

    private fun startServer() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(serverPort)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Servidor iniciado.", Toast.LENGTH_SHORT).show()
                }
                while (true) {
                    val clientSocket = serverSocket!!.accept()
                    handleClient(clientSocket)
                }
            } catch (e: IOException) {
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(clientSocket.inputStream))
                val writer = PrintWriter(clientSocket.outputStream, true)
                val request = reader.readLine()
                if (request != null) {
                    when (request) {
                        "ALERT" -> {
                            isAlarmActive = true
                            withContext(Dispatchers.Main) { updateServerStatusUI() }
                        }
                        "STATUS" -> {
                            writer.println(if (isAlarmActive) "ACTIVE" else "RESET")
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                clientSocket.close()
            }
        }
    }

    private fun sendAlertToServer() {
        val serverIp = serverIpEditText.text.toString().trim()
        if (serverIp.isEmpty()) {
            Toast.makeText(this, "Ingresa la IP del servidor", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            sendMessageToServer(serverIp, "ALERT")
            withContext(Dispatchers.Main) {
                sensorStatusTextView.text = "Alarma: ¡ALERTA ENVIADA!"
                sensorStatusTextView.setTextColor(Color.RED)
            }
        }
    }

    private fun startSensorPolling() {
        if (isSensorPolling) return
        isSensorPolling = true
        lifecycleScope.launch(Dispatchers.IO) {
            while (isSensorPolling) {
                val serverIp = serverIpEditText.text.toString().trim()
                if (serverIp.isNotEmpty()) {
                    val response = sendMessageToServer(serverIp, "STATUS")
                    if (response == "RESET") {
                        withContext(Dispatchers.Main) {
                            sensorStatusTextView.text = "Alarma: Sin Intrusos"
                            sensorStatusTextView.setTextColor(Color.parseColor("#006400"))
                        }
                    }
                }
                kotlinx.coroutines.delay(2000) // Espera 2 segundos
            }
        }
    }

    private fun sendMessageToServer(ip: String, message: String): String {
        return try {
            val socket = Socket(ip, serverPort)
            socket.soTimeout = 3000
            val writer = PrintWriter(socket.outputStream, true)
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            writer.println(message)
            val response = if (message == "STATUS") reader.readLine() else "OK"
            socket.close()
            response ?: "ERROR"
        } catch (e: IOException) {
            "ERROR"
        }
    }

    private fun updateServerStatusUI() {
        if (isAlarmActive) {
            statusTextView.text = "¡Intruso Detectado!"
            statusTextView.setTextColor(Color.RED)
        } else {
            statusTextView.text = "Sin Intrusos"
            statusTextView.setTextColor(Color.parseColor("#006400"))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isSensorPolling = false
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
