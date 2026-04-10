package com.sora.omniclaw.domain.runtime

import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.BridgeLifecycleState
import com.sora.omniclaw.core.model.BridgeStatus
import com.sora.omniclaw.core.model.HostLifecycleState
import com.sora.omniclaw.core.model.RuntimeStatus
import com.sora.omniclaw.testing.fake.FakeBridgeServer
import com.sora.omniclaw.testing.fake.FakeRuntimeManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StopHostUseCaseTest {
    @Test
    fun `stops runtime and bridge when host is running`() = runBlocking {
        val runtimeManager = FakeRuntimeManager(
            initialStatus = RuntimeStatus(lifecycleState = HostLifecycleState.Running)
        )
        val bridgeServer = FakeBridgeServer(
            initialStatus = BridgeStatus(lifecycleState = BridgeLifecycleState.Running)
        )
        val useCase = StopHostUseCase(
            runtimeManager = runtimeManager,
            bridgeServer = bridgeServer,
        )

        val result = useCase()

        assertTrue(result is HostResult.Success<*>)
        assertEquals(HostLifecycleState.Stopped, runtimeManager.status.value.lifecycleState)
        assertEquals(BridgeLifecycleState.Stopped, bridgeServer.status.value.lifecycleState)
    }
}
