package com.sora.omniclaw.service.host

import android.content.Context
import com.sora.omniclaw.core.common.HostResult

interface HostServiceDependencies {
    suspend fun startHost(): HostResult<Unit>

    suspend fun stopHost(): HostResult<Unit>
}

fun interface HostServiceDependenciesProvider {
    fun provideHostServiceDependencies(): HostServiceDependencies
}

internal fun Context.findHostServiceDependencies(): HostServiceDependencies? =
    (applicationContext as? HostServiceDependenciesProvider)?.provideHostServiceDependencies()
