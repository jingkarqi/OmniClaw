package com.sora.omniclaw.service.host

import android.content.Context

internal interface HostServiceDesiredStateStore {
    var desiredRunning: Boolean
}

internal class SharedPreferencesHostServiceDesiredStateStore(
    context: Context,
) : HostServiceDesiredStateStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override var desiredRunning: Boolean
        get() = preferences.getBoolean(KEY_DESIRED_RUNNING, false)
        set(value) {
            preferences.edit()
                .putBoolean(KEY_DESIRED_RUNNING, value)
                .commit()
        }

    private companion object {
        private const val PREFERENCES_NAME = "omniclaw.host.service"
        private const val KEY_DESIRED_RUNNING = "desired_running"
    }
}

internal fun Context.hostServiceDesiredStateStore(): HostServiceDesiredStateStore =
    SharedPreferencesHostServiceDesiredStateStore(applicationContext)
