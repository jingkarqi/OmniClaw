package com.sora.omniclaw.service.host

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HostServiceLauncherTest {
    private val baseContext: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `start launches host foreground service with start action`() {
        val context = RecordingContext(baseContext)

        HostServiceLauncher.start(context)

        val intent = context.lastForegroundServiceIntent
        assertNotNull(intent)
        assertEquals(HostForegroundService.ACTION_START, intent?.action)
        assertEquals(HostForegroundService::class.java.name, intent?.component?.className)
        assertEquals(context.packageName, intent?.component?.packageName)
    }

    @Test
    fun `stop sends host foreground service stop action`() {
        val context = RecordingContext(baseContext)

        HostServiceLauncher.stop(context)

        val intent = context.lastStartedServiceIntent
        assertNotNull(intent)
        assertEquals(HostForegroundService.ACTION_STOP, intent?.action)
        assertEquals(HostForegroundService::class.java.name, intent?.component?.className)
        assertEquals(context.packageName, intent?.component?.packageName)
    }
}

private class RecordingContext(
    baseContext: Context,
) : ContextWrapper(baseContext) {
    var lastForegroundServiceIntent: Intent? = null
        private set

    var lastStartedServiceIntent: Intent? = null
        private set

    override fun startForegroundService(service: Intent): ComponentName {
        lastForegroundServiceIntent = service
        return ComponentName(packageName, service.component?.className.orEmpty())
    }

    override fun startService(service: Intent): ComponentName {
        lastStartedServiceIntent = service
        return ComponentName(packageName, service.component?.className.orEmpty())
    }
}
