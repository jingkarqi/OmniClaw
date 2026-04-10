package com.sora.omniclaw

import android.content.Context
import com.sora.omniclaw.bridge.api.BridgeServer
import com.sora.omniclaw.bridge.api.DeviceCapabilityGateway
import com.sora.omniclaw.bridge.impl.StubBridgeServer
import com.sora.omniclaw.bridge.impl.StubDeviceCapabilityGateway
import com.sora.omniclaw.core.model.DiagnosticsSummary
import com.sora.omniclaw.core.model.HostOverview
import com.sora.omniclaw.core.model.PermissionSummary
import com.sora.omniclaw.core.model.ProviderConfigDraft
import com.sora.omniclaw.core.model.RuntimeStatus
import com.sora.omniclaw.core.storage.AndroidProviderConfigStore
import com.sora.omniclaw.core.storage.AndroidSecretStore
import com.sora.omniclaw.core.storage.ProviderConfigStore
import com.sora.omniclaw.core.storage.SecretStore
import com.sora.omniclaw.domain.bridge.ObservePermissionSummaryUseCase
import com.sora.omniclaw.domain.provider.ObserveProviderConfigUseCase
import com.sora.omniclaw.domain.provider.SaveProviderConfigUseCase
import com.sora.omniclaw.domain.runtime.ObserveHostOverviewUseCase
import com.sora.omniclaw.domain.runtime.StartHostUseCase
import com.sora.omniclaw.domain.runtime.StopHostUseCase
import com.sora.omniclaw.runtime.api.PayloadLocator
import com.sora.omniclaw.runtime.api.RuntimeManager
import com.sora.omniclaw.runtime.impl.AssetPayloadLocator
import com.sora.omniclaw.runtime.impl.StubRuntimeManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class AppGraph(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    private val providerConfigStore: ProviderConfigStore by lazy {
        AndroidProviderConfigStore(applicationContext)
    }

    private val secretStore: SecretStore by lazy {
        AndroidSecretStore(applicationContext)
    }

    private val payloadLocator: PayloadLocator by lazy {
        AssetPayloadLocator(applicationContext)
    }

    private val bridgeServer: BridgeServer by lazy {
        StubBridgeServer()
    }

    private val deviceCapabilityGateway: DeviceCapabilityGateway by lazy {
        StubDeviceCapabilityGateway()
    }

    val runtimeManager: RuntimeManager by lazy {
        StubRuntimeManager(payloadLocator)
    }

    private val observeProviderConfigUseCase: ObserveProviderConfigUseCase by lazy {
        ObserveProviderConfigUseCase(
            providerConfigStore = providerConfigStore,
            secretStore = secretStore,
        )
    }

    val saveProviderConfig: SaveProviderConfigUseCase by lazy {
        SaveProviderConfigUseCase(
            providerConfigStore = providerConfigStore,
            secretStore = secretStore,
        )
    }

    private val observePermissionSummaryUseCase: ObservePermissionSummaryUseCase by lazy {
        ObservePermissionSummaryUseCase(
            deviceCapabilityGateway = deviceCapabilityGateway,
        )
    }

    private val observeHostOverviewUseCase: ObserveHostOverviewUseCase by lazy {
        ObserveHostOverviewUseCase(
            runtimeManager = runtimeManager,
            bridgeServer = bridgeServer,
            deviceCapabilityGateway = deviceCapabilityGateway,
            providerConfigStore = providerConfigStore,
            secretStore = secretStore,
        )
    }

    val startHost: StartHostUseCase by lazy {
        StartHostUseCase(
            bridgeServer = bridgeServer,
            runtimeManager = runtimeManager,
            deviceCapabilityGateway = deviceCapabilityGateway,
            providerConfigStore = providerConfigStore,
            secretStore = secretStore,
        )
    }

    val stopHost: StopHostUseCase by lazy {
        StopHostUseCase(
            runtimeManager = runtimeManager,
            bridgeServer = bridgeServer,
        )
    }

    val providerConfig: Flow<ProviderConfigDraft> by lazy {
        observeProviderConfigUseCase()
    }

    val permissionSummary: Flow<PermissionSummary> by lazy {
        observePermissionSummaryUseCase()
    }

    val hostOverview: Flow<HostOverview> by lazy {
        observeHostOverviewUseCase()
    }

    val runtimeStatus: StateFlow<RuntimeStatus>
        get() = runtimeManager.status

    val runtimeDiagnostics: StateFlow<DiagnosticsSummary>
        get() = runtimeManager.diagnostics
}
