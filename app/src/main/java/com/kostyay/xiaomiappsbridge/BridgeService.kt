package com.kostyay.xiaomiappsbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.IBinder
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import com.flyfishxu.kadb.shell.AdbShellPacket
import java.io.File
import java.util.concurrent.Executors
import okio.Path.Companion.toPath

fun Context.startBridge(action: String? = null) {
    val intent = BridgeService.intent(this).setAction(action)
    startForegroundService(intent)
}

@Suppress("UnspecifiedRegisterReceiverFlag")
fun Context.registerStatusReceiver(receiver: BroadcastReceiver) {
    val filter = IntentFilter(BridgeService.ACTION_STATUS)
    if (SDK_INT >= TIRAMISU) {
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        registerReceiver(receiver, filter)
    }
}

class BridgeService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val controller = Executors.newSingleThreadExecutor()
    private val output = StringBuilder()

    @Volatile private var stopped = false

    @Volatile private var mode = Mode.NORMAL

    @Volatile private var listener: AutoCloseable? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Connecting to local ADB"))
        KadbCert.configure(
            OkioFilePrivateKeyStore(File(filesDir, "adbkey.pem").absolutePath.toPath())
        )
        worker.execute(::listen)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TEST) {
            mode = Mode.TEST
            updateStatus("Test mode: press a mapped button")
        } else if (intent?.action == ACTION_CAPTURE) {
            mode = Mode.CAPTURE
            updateStatus("Press the remote button to map")
        } else if (intent?.action == ACTION_CANCEL_CAPTURE) {
            mode = Mode.NORMAL
            updateStatus("Key capture canceled")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopped = true
        updateStatus("Bridge stopped", false)
        runCatching { listener?.close() }
        worker.shutdownNow()
        controller.shutdownNow()
        super.onDestroy()
    }

    @Suppress("NestedBlockDepth", "TooGenericExceptionCaught")
    private fun listen() {
        while (!stopped) {
            try {
                Kadb.create("127.0.0.1", ADB_PORT, connectTimeout = ADB_TIMEOUT_MS).use { adb ->
                    adb.shell("true")
                    updateStatus("Working: ${loadMappings().size} key mapping(s)", true)
                    adb.openShell("exec getevent -lt | grep ' EV_KEY '").use { shell ->
                        listener = shell
                        while (!stopped) {
                            when (val packet = shell.read()) {
                                is AdbShellPacket.StdOut -> consume(packet.payload)
                                is AdbShellPacket.Exit -> break
                                is AdbShellPacket.StdError -> Unit
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                if (!stopped) {
                    updateStatus("Waiting for ADB: ${error.javaClass.simpleName}", false)
                    try {
                        Thread.sleep(RETRY_DELAY_MS)
                    } catch (_: InterruptedException) {
                        return
                    }
                }
            } finally {
                listener = null
            }
        }
    }

    private fun consume(bytes: ByteArray) {
        output.append(String(bytes))
        var newline = output.indexOf("\n")
        while (newline >= 0) {
            val line = output.substring(0, newline)
            output.delete(0, newline + 1)
            KEY_EVENT.find(line)?.groupValues?.get(1)?.let(::onKey)
            newline = output.indexOf("\n")
        }
    }

    private fun onKey(key: String) {
        if (mode == Mode.CAPTURE) {
            mode = Mode.NORMAL
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(CAPTURED_KEY, key).apply()
            updateStatus("Detected $key", true)
        } else {
            val mapping = loadMappings().firstOrNull { it.key == key } ?: return
            if (mode == Mode.NORMAL) {
                controller.execute { runAction(key, mapping) }
                return
            }
            mode = Mode.NORMAL
            updateStatus("OK: $key detected", true)
        }
    }

    private fun runAction(key: String, mapping: KeyMapping) {
        val command = mapping.command() ?: return
        runCatching {
            Kadb.create("127.0.0.1", ADB_PORT, connectTimeout = ADB_TIMEOUT_MS).use { adb ->
                adb.shell(command)
            }
            updateStatus("Ran mapping for $key", true)
        }.onFailure {
            updateStatus("Action failed: ${it.javaClass.simpleName}", true)
        }
    }

    private fun updateStatus(text: String, adbConnected: Boolean? = null) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(STATUS, text).apply {
            if (adbConnected != null) putBoolean(ADB_CONNECTED, adbConnected)
        }.apply()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName))
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "TV Key Mapper", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("TV Key Mapper")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_TEST = "com.kostyay.xiaomiappsbridge.TEST"
        const val ACTION_CAPTURE = "com.kostyay.xiaomiappsbridge.CAPTURE"
        const val ACTION_CANCEL_CAPTURE = "com.kostyay.xiaomiappsbridge.CANCEL_CAPTURE"
        const val ACTION_STATUS = "com.kostyay.xiaomiappsbridge.STATUS"
        const val PREFS = "bridge"
        const val STATUS = "status"
        const val ADB_CONNECTED = "adb_connected"
        const val CAPTURED_KEY = "captured_key"
        const val MAPPINGS = "mappings"
        private const val CHANNEL_ID = "bridge"
        private const val NOTIFICATION_ID = 1
        private const val ADB_PORT = 5555
        private const val ADB_TIMEOUT_MS = 5_000
        private const val RETRY_DELAY_MS = 1_000L
        private val KEY_EVENT = Regex("EV_KEY\\s+((?:KEY|BTN)_[A-Z0-9_]+)\\s+DOWN")

        fun intent(context: Context) = Intent(context, BridgeService::class.java)
    }

    private enum class Mode { NORMAL, TEST, CAPTURE }
}
