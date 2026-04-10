package com.sora.omniclaw.service.host

import android.content.ComponentName
import android.content.Context
import android.content.Intent

object HostServiceLauncher {
    fun start(context: Context): ComponentName? =
        context.startForegroundService(createStartIntent(context))

    fun stop(context: Context): ComponentName? =
        context.startService(createStopIntent(context))

    internal fun createStartIntent(context: Context): Intent =
        Intent(context, HostForegroundService::class.java)
            .setAction(HostForegroundService.ACTION_START)

    internal fun createStopIntent(context: Context): Intent =
        Intent(context, HostForegroundService::class.java)
            .setAction(HostForegroundService.ACTION_STOP)
}
