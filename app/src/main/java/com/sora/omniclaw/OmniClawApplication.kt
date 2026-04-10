package com.sora.omniclaw

import android.app.Application
import com.sora.omniclaw.service.host.HostServiceDependencies
import com.sora.omniclaw.service.host.HostServiceDependenciesProvider

class OmniClawApplication : Application(), HostServiceDependenciesProvider {
    val appGraph: AppGraph by lazy(LazyThreadSafetyMode.NONE) {
        AppGraph(this)
    }

    override fun provideHostServiceDependencies(): HostServiceDependencies {
        return object : HostServiceDependencies {
            override suspend fun startHost() = appGraph.startHost()

            override suspend fun stopHost() = appGraph.stopHost()
        }
    }
}
