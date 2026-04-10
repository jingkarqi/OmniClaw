package com.sora.omniclaw.service.host

import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HostServiceCommandHandlerTest {
    @Test
    fun `invokes start dependency for start command and keeps service running on success`() = runBlocking {
        val dependencies = FakeHostServiceDependencies(
            startResult = HostResult.Success(Unit)
        )
        val handler = HostServiceCommandHandler(dependencies)

        val continuation = handler.handle(HostServiceCommand.Start)

        assertEquals(1, dependencies.startCalls)
        assertEquals(0, dependencies.stopCalls)
        assertEquals(HostServiceContinuation.KeepRunning, continuation)
    }

    @Test
    fun `invokes stop dependency for stop command and requests service stop`() = runBlocking {
        val dependencies = FakeHostServiceDependencies(
            stopResult = HostResult.Success(Unit)
        )
        val handler = HostServiceCommandHandler(dependencies)

        val continuation = handler.handle(HostServiceCommand.Stop)

        assertEquals(0, dependencies.startCalls)
        assertEquals(1, dependencies.stopCalls)
        assertEquals(HostServiceContinuation.StopSelf, continuation)
    }

    @Test
    fun `requests service stop when start dependency fails`() = runBlocking {
        val dependencies = FakeHostServiceDependencies(
            startResult = HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Runtime,
                    message = "boom",
                )
            )
        )
        val handler = HostServiceCommandHandler(dependencies)

        val continuation = handler.handle(HostServiceCommand.Start)

        assertEquals(1, dependencies.startCalls)
        assertEquals(0, dependencies.stopCalls)
        assertEquals(HostServiceContinuation.StopSelf, continuation)
    }
}

private class FakeHostServiceDependencies(
    private val startResult: HostResult<Unit> = HostResult.Success(Unit),
    private val stopResult: HostResult<Unit> = HostResult.Success(Unit),
) : HostServiceDependencies {
    var startCalls: Int = 0
        private set

    var stopCalls: Int = 0
        private set

    override suspend fun startHost(): HostResult<Unit> {
        startCalls += 1
        return startResult
    }

    override suspend fun stopHost(): HostResult<Unit> {
        stopCalls += 1
        return stopResult
    }
}
