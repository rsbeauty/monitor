package com.screenmirror.sender

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import okhttp3.*
import org.json.JSONObject
import org.webrtc.*
import java.util.concurrent.TimeUnit

class ScreenShareService : Service() {

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: ScreenCapturerAndroid? = null
    private var videoTrack: VideoTrack? = null
    private var eglBase: EglBase? = null
    private var webSocket: WebSocket? = null
    private var roomTopic: String = ""
    private var msgRef = 1
    private var isStreamingToAdmin = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startNotification()

        val data: Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("DATA", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("DATA")
        } ?: return START_STICKY

        val sbUrl = intent?.getStringExtra("SB_URL") ?: "https://pigsylxeeoztnyuydqtr.supabase.co"
        val sbKey = intent?.getStringExtra("SB_KEY") ?: "sb_publishable_JrDxSeFli4NkkVhopnwCoQ_aa7HFsgl"
        val roomId = intent?.getStringExtra("ROOM_ID") ?: "device_1"
        roomTopic = "realtime:screen-room-$roomId"

        initMediaProjection(data)
        connectSupabaseRealtime(sbUrl, sbKey)

        return START_STICKY
    }

    private fun startNotification() {
        val channelId = "screen_share_channel"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Admin Screen Service", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Admin Session Active")
            .setContentText("Managed remotely by Admin Panel")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
    }

    private fun initMediaProjection(mediaProjectionData: Intent) {
        eglBase = EglBase.create()
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(applicationContext).createInitializationOptions()
        )
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase!!.eglBaseContext))
            .createPeerConnectionFactory()

        videoCapturer = ScreenCapturerAndroid(mediaProjectionData, object : MediaProjection.Callback() {
            override fun onStop() {
                sendTelemetry("SYSTEM_PROJECTION_STOPPED")
                stopSelf()
            }
        })

        val surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBase!!.eglBaseContext)
        val videoSource = peerConnectionFactory!!.createVideoSource(true)
        videoCapturer!!.initialize(surfaceHelper, applicationContext, videoSource.capturerObserver)
        videoTrack = peerConnectionFactory!!.createVideoTrack("SCREEN_TRACK", videoSource)
    }

    private fun startAdminStream() {
        if (!isStreamingToAdmin) {
            videoCapturer?.startCapture(720, 1280, 15)
            isStreamingToAdmin = true
        }
        createPeerAndSendOffer()
        sendTelemetry("STREAM_STARTED_BY_ADMIN")
    }

    private fun stopAdminStream() {
        if (isStreamingToAdmin) {
            try {
                videoCapturer?.stopCapture()
            } catch (_: Exception) {}
            isStreamingToAdmin = false
        }
        peerConnection?.close()
        peerConnection = null
        sendTelemetry("STREAM_PAUSED_BY_ADMIN")
    }

    private fun createPeerAndSendOffer() {
        peerConnection?.close()
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        peerConnection = peerConnectionFactory?.createPeerConnection(iceServers, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val payload = JSONObject().apply {
                    put("candidate", candidate.sdp)
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                }
                sendBroadcast("ice-android", payload)
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })

        videoTrack?.let { peerConnection?.addTrack(it, listOf("screen-stream")) }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(this, desc)
                val payload = JSONObject().apply { put("sdp", desc.description) }
                sendBroadcast("offer", payload)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
    }

    private fun connectSupabaseRealtime(sbUrl: String, sbKey: String) {
        val wsUrl = sbUrl.replace("https://", "wss://") + "/realtime/v1/websocket?apikey=$sbKey&vsn=1.0.0"
        val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()

        webSocket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                val joinMsg = JSONObject().apply {
                    put("topic", roomTopic)
                    put("event", "phx_join")
                    put("payload", JSONObject().apply {
                        put("config", JSONObject().apply {
                            put("broadcast", JSONObject().apply { put("self", false) })
                        })
                    })
                    put("ref", (msgRef++).toString())
                }
                ws.send(joinMsg.toString())
                sendTelemetry("DEVICE_ONLINE_READY")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                val json = JSONObject(text)
                if (json.optString("event") == "broadcast") {
                    val body = json.optJSONObject("payload") ?: return
                    val eventName = body.optString("event")
                    val payload = body.optJSONObject("payload") ?: return

                    when (eventName) {
                        "admin-command" -> {
                            when (payload.optString("command")) {
                                "ADMIN_START_STREAM", "REINIT_CAPTURE" -> startAdminStream()
                                "ADMIN_STOP_STREAM" -> stopAdminStream()
                                "PING_DEVICE", "HEALTH_CHECK" -> sendTelemetry("DEVICE_ALIVE_OK")
                            }
                        }
                        "answer" -> {
                            val sdp = payload.optString("sdp")
                            peerConnection?.setRemoteDescription(object : SdpObserver {
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onSetSuccess() {}
                                override fun onCreateFailure(p0: String?) {}
                                override fun onSetFailure(p0: String?) {}
                            }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
                        }
                        "ice-viewer" -> {
                            val cand = payload.optJSONObject("candidate") ?: return
                            peerConnection?.addIceCandidate(
                                IceCandidate(
                                    cand.optString("sdpMid"),
                                    cand.optInt("sdpMLineIndex"),
                                    cand.optString("candidate")
                                )
                            )
                        }
                    }
                }
            }
        })
    }

    private fun sendTelemetry(logMessage: String) {
        val data = JSONObject().apply {
            put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("os", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            put("log", logMessage)
        }
        sendBroadcast("device-telemetry", data)
    }

    private fun sendBroadcast(eventName: String, data: JSONObject) {
        val msg = JSONObject().apply {
            put("topic", roomTopic)
            put("event", "broadcast")
            put("payload", JSONObject().apply {
                put("type", "broadcast")
                put("event", eventName)
                put("payload", data)
            })
            put("ref", (msgRef++).toString())
        }
        webSocket?.send(msg.toString())
    }

    override fun onDestroy() {
        stopAdminStream()
        videoCapturer?.dispose()
        webSocket?.close(1000, "Destroyed")
        eglBase?.release()
        super.onDestroy()
    }
}
