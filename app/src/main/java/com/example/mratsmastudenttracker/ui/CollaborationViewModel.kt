package com.example.mratsmastudenttracker.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mratsmastudenttracker.ui.utils.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

sealed class CollaborationEvent {
    data class ConnectionSuccess(val sessionId: Long) : CollaborationEvent()
    data class ConnectionError(val message: String) : CollaborationEvent()
}

class CollaborationViewModel(application: Application) : AndroidViewModel(application) {
    private val SERVICE_ID = "com.example.mratsmastudenttracker.COLLABORATION"
    private val PORT = 8888
    
    var isHosting by mutableStateOf(false)
        private set
    var hostSessionId by mutableStateOf<Long?>(null)
        private set
    var localIp by mutableStateOf<String?>(null)
        private set
    var publicIp by mutableStateOf<String?>(null)
        private set
    
    var isAssistant by mutableStateOf(false)
        private set
    var connectedHostId by mutableStateOf<String?>(null)
        private set
    var assistantSessionId by mutableStateOf<Long?>(null)
        private set
    private var assistantHostIp: String? = null
        
    var connectionStatus by mutableStateOf("Idle")
        private set
        
    private val _events = MutableSharedFlow<CollaborationEvent>()
    val events = _events.asSharedFlow()

    private var currentStudentViewModel: StudentViewModel? = null
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    fun startHosting(sessionId: Long, studentViewModel: StudentViewModel) {
        if (isHosting && sessionId == hostSessionId) return

        hostSessionId = sessionId
        currentStudentViewModel = studentViewModel

        localIp = NetworkUtils.getLocalIpAddress() ?: "No local IP found"
        publicIp = "No public IP found"
        connectionStatus = "Detecting IP..."
        
        viewModelScope.launch {
            try {
                val pIp = NetworkUtils.getMyPublicIpAsync().await()
                if (!pIp.contains("error")) {
                    publicIp = pIp
                }
            } catch (e: Exception) {
                Log.e("Collaboration", "Public IP fetch failed", e)
            } finally {
                startServer()
            }
        }
    }

    private fun startServer() {
        if (isHosting) return
        isHosting = true
        connectionStatus = "Server starting..."
        
        serverJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(PORT)
                connectionStatus = "Hosting on $publicIp:$PORT" // TODO add local IP
                Log.d("Collaboration", "Server started on port $PORT")
                
                while (isActive && isHosting) {
                    val client = try {
                        serverSocket?.accept()
                    } catch (e: Exception) {
                        null
                    }
                    if (client != null) {
                        handleClient(client)
                    }
                }
            } catch (e: Exception) {
                Log.e("Collaboration", "Server error", e)
                connectionStatus = "Server error: ${e.message}"
                isHosting = false
            } finally {
                serverSocket?.close()
            }
        }
    }

    private fun handleClient(socket: Socket) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                socket.use { s ->
                    val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                    val writer = PrintWriter(s.getOutputStream(), true)
                    
                    val data = reader.readLine()
                    if (data != null) {
                        val response = processRequest(data)
                        writer.println(response)
                    }
                }
            } catch (e: Exception) {
                Log.e("Collaboration", "Error handling client", e)
            }
        }
    }

    private suspend fun processRequest(data: String): String {
        return try {
            val json = JSONObject(data)
            when (json.optString("type")) {
                "ping" -> JSONObject().apply { put("status", "ok") }.toString()
                "scan" -> {
                    val studentId = json.getString("studentId")
                    val sessionId = hostSessionId ?: return createErrorResponse("No active session")
                    val student = currentStudentViewModel?.getStudentById(studentId)?.firstOrNull()
                    
                    if (student != null) {
                        val isTargeted = currentStudentViewModel?.isStudentTargetedForSession(sessionId, studentId) ?: true
                        currentStudentViewModel?.markAttendance(sessionId, studentId)
                        JSONObject().apply {
                            put("type", "scan_result")
                            put("status", "success")
                            put("isTargeted", isTargeted)
                            put("studentName", "${student.firstName} ${student.lastName}")
                        }.toString()
                    } else {
                        createErrorResponse("Student not found")
                    }
                }
                else -> createErrorResponse("Unknown request type")
            }
        } catch (e: Exception) {
            createErrorResponse("Malformed request: ${e.message}")
        }
    }

    private fun createErrorResponse(message: String) = JSONObject().apply {
        put("type", "scan_result")
        put("status", "error")
        put("message", message)
    }.toString()

    fun stopHosting() {
        isHosting = false
        connectionStatus = "Stopping..."
        serverJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                serverSocket?.close()
            } catch (e: Exception) {}
            serverSocket = null
            withContext(Dispatchers.Main) {
                hostSessionId = null
                localIp = null
                publicIp = null
                connectionStatus = "Idle"
            }
        }
    }

    fun joinAsAssistant(qrContent: String) {
        try {
            val json = JSONObject(qrContent)
            val sessionId = json.getLong("sessionId")
            val lIp = json.getString("localIp")
            val pIp = if (json.has("publicIp")) json.getString("publicIp") else null
            
            assistantSessionId = sessionId
            connectionStatus = "Connecting..."
            
            viewModelScope.launch(Dispatchers.IO) {
                // Try the primary IP (usually local) first
                if (pingHost(lIp)) {
                    assistantHostIp = lIp
                    isAssistant = true
                    connectionStatus = "Connected to Host (via Local IP)"
                    _events.emit(CollaborationEvent.ConnectionSuccess(sessionId))
                } else if (pIp != null && pIp != lIp && pingHost(pIp)) {
                    // Try the public IP as a fallback
                    assistantHostIp = pIp
                    isAssistant = true
                    connectionStatus = "Connected to Host (via Public IP)"
                    _events.emit(CollaborationEvent.ConnectionSuccess(sessionId))
                } else {
                    connectionStatus = "Cannot reach Host"
                    _events.emit(CollaborationEvent.ConnectionError("Cannot reach Host at $lIp" + (if(pIp != null) " or $pIp" else "")))
                }
            }
        } catch (e: Exception) {
            Log.e("Collaboration", "Failed to parse QR content", e)
            connectionStatus = "Invalid QR Code"
            viewModelScope.launch {
                _events.emit(CollaborationEvent.ConnectionError("Invalid QR Code"))
            }
        }
    }

    private suspend fun pingHost(ip: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, PORT), 5000)
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    
                    writer.println(JSONObject().apply { put("type", "ping") }.toString())
                    val response = reader.readLine()
                    response != null && JSONObject(response).optString("status") == "ok"
                }
            } catch (e: Exception) {
                Log.e("Collaboration", "Ping failed to $ip", e)
                false
            }
        }
    }

    fun stopAssistant() {
        isAssistant = false
        assistantSessionId = null
        assistantHostIp = null
        connectionStatus = "Idle"
    }

    suspend fun scanStudentAsAssistant(studentId: String): Triple<Boolean, Boolean, String> {
        val ip = assistantHostIp ?: return Triple(false, false, "No host connection")
        
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, PORT), 5000)
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    
                    val request = JSONObject().apply {
                        put("type", "scan")
                        put("studentId", studentId)
                    }
                    writer.println(request.toString())
                    
                    val response = reader.readLine()
                    if (response != null) {
                        val json = JSONObject(response)
                        if (json.optString("status") == "success") {
                            val isTargeted = json.optBoolean("isTargeted", true)
                            Triple(true, isTargeted, "Scanned: ${json.optString("studentName")}")
                        } else {
                            Triple(false, false, "Error: ${json.optString("message", "Unknown error")}")
                        }
                    } else {
                        Triple(false, false, "No response from host")
                    }
                }
            } catch (e: Exception) {
                Log.e("Collaboration", "Scan request failed", e)
                Triple(false, false, "Connection error: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopHosting()
        stopAssistant()
    }
}
