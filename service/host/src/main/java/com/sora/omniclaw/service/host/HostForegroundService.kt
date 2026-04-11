package com.sora.omniclaw.service.host

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.sora.omniclaw.core.common.HostResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HostForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val desiredStateStore = applicationContext.hostServiceDesiredStateStore()
        val command = HostServiceCommand.fromIntent(
            intent = intent,
            desiredRunning = desiredStateStore.desiredRunning,
        )
        if (command == HostServiceCommand.Ignore) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val dependencies = applicationContext.findHostServiceDependencies()
        if (dependencies == null) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        if (command == HostServiceCommand.Start) {
            desiredStateStore.desiredRunning = true
            startForeground(NOTIFICATION_ID, buildNotification())
        } else if (command == HostServiceCommand.Stop) {
            desiredStateStore.desiredRunning = false
        }

        val handler = HostServiceCommandHandler(dependencies, desiredStateStore)
        serviceScope.launch {
            when (handler.handle(command)) {
                HostServiceContinuation.KeepRunning -> Unit
                HostServiceContinuation.StopSelf -> stopSelfResult(startId)
            }
        }

        return when (command) {
            HostServiceCommand.Start -> START_STICKY
            HostServiceCommand.Stop,
            HostServiceCommand.Ignore -> START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "OmniClaw Host",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("OmniClaw host")
            .setContentText("Host runtime is active.")
            .setOngoing(true)
            .build()

    companion object {
        internal const val ACTION_START =
            "com.sora.omniclaw.service.host.action.START"
        internal const val ACTION_STOP =
            "com.sora.omniclaw.service.host.action.STOP"

        private const val NOTIFICATION_CHANNEL_ID = "omniclaw.host.lifecycle"
        private const val NOTIFICATION_ID = 1001
    }
}

internal enum class HostServiceCommand {
    Start,
    Stop,
    Ignore,
    ;

    companion object {
        fun fromIntent(
            intent: Intent?,
            desiredRunning: Boolean,
        ): HostServiceCommand = fromAction(intent?.action, desiredRunning)

        fun fromAction(
            action: String?,
            desiredRunning: Boolean,
        ): HostServiceCommand = when (action) {
            null -> if (desiredRunning) Start else Ignore
            HostForegroundService.ACTION_START -> Start
            HostForegroundService.ACTION_STOP -> Stop
            else -> Ignore
        }
    }
}

internal enum class HostServiceContinuation {
    KeepRunning,
    StopSelf,
}

internal class HostServiceCommandHandler(
    private val dependencies: HostServiceDependencies,
    private val desiredStateStore: HostServiceDesiredStateStore,
) {
    suspend fun handle(command: HostServiceCommand): HostServiceContinuation = when (command) {
        HostServiceCommand.Start -> {
            desiredStateStore.desiredRunning = true
            when (val result = dependencies.startHost()) {
                is HostResult.Success -> HostServiceContinuation.KeepRunning
                is HostResult.Failure -> {
                    if (result.error.recoverable) {
                        HostServiceContinuation.StopSelf
                    } else {
                        desiredStateStore.desiredRunning = false
                        HostServiceContinuation.StopSelf
                    }
                }
            }
        }

        HostServiceCommand.Stop -> {
            desiredStateStore.desiredRunning = false
            dependencies.stopHost()
            HostServiceContinuation.StopSelf
        }

        HostServiceCommand.Ignore -> HostServiceContinuation.StopSelf
    }
}
