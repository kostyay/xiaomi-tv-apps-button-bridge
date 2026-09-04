package com.kostyay.xiaomiappsbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import com.flyfishxu.kadb.shell.AdbShellPacket
import okio.Path.Companion.toPath
import java.io.File
import java.util.concurrent.Executors

fun Context.startBridge(action: String? = null) {
    val intent = BridgeService.intent(this).setAction(action)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
}

class BridgeService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val controller = Executors.newSingleThreadExecutor()
    private val output = StringBuilder()
    @Volatile private var stopped = false
    @Volatile private var testMode = false
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
            testMode = true
            updateStatus("Test mode: press the Apps button")
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

    private fun listen() {
        while (!stopped) {
            try {
                Kadb.create("127.0.0.1", 5555, connectTimeout = 5_000).use { adb ->
                    adb.shell("true")
                    updateStatus("Working: Apps opens the selected target", true)
                    adb.openShell("exec getevent -l /dev/input/event7").use { shell ->
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
                        Thread.sleep(1_000)
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
            if (line.contains("EV_KEY") && line.contains("KEY_CHAT") && line.contains("DOWN")) {
                onAppsButton()
            }
            newline = output.indexOf("\n")
        }
    }

    private fun onAppsButton() {
        if (testMode) {
            testMode = false
            updateStatus("OK: Apps button detected", true)
        } else {
            updateStatus("Working: Apps opens the selected target", true)
            controller.execute(::openTarget)
        }
    }

    private fun openTarget() {
        val target = getSharedPreferences(PREFS, MODE_PRIVATE).getString(TARGET, DEFAULT_TARGET)
        val command = when {
            target == DEFAULT_TARGET ->
                "am start -a tv.projectivy.ALL_APPS -c android.intent.category.DEFAULT"
            target?.matches(Regex("[A-Za-z0-9._]+")) == true ->
                "monkey -p $target -c android.intent.category.LEANBACK_LAUNCHER 1"
            else -> return
        }
        runCatching {
            Kadb.create("127.0.0.1", 5555, connectTimeout = 5_000).use { it.shell(command) }
        }.onFailure {
            updateStatus("Target did not open: ${it.javaClass.simpleName}", true)
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
            NotificationChannel(CHANNEL_ID, "Apps button bridge", NotificationManager.IMPORTANCE_LOW)
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
            .setContentTitle("Xiaomi Apps Button Bridge")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_TEST = "com.kostyay.xiaomiappsbridge.TEST"
        const val ACTION_STATUS = "com.kostyay.xiaomiappsbridge.STATUS"
        const val PREFS = "bridge"
        const val STATUS = "status"
        const val ADB_CONNECTED = "adb_connected"
        const val TARGET = "target"
        const val DEFAULT_TARGET = "projectivy_apps"
        private const val CHANNEL_ID = "bridge"
        private const val NOTIFICATION_ID = 1

        fun intent(context: Context) = Intent(context, BridgeService::class.java)
    }
}
