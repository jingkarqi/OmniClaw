package com.sora.omniclaw.service.host

import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostServiceCommandHandlerTest {
    @Test
    fun `invokes start dependency for start command and keeps service running on success`() = runBlocking {
        val dependencies = FakeHostServiceDependencies(
            startResult = HostResult.Success(Unit)
        )
        val desiredStateStore = FakeHostServiceDesiredStateStore()
        val handler = HostServiceCommandHandler(dependencies, desiredStateStore)

        val continuation = handler.handle(HostServiceCommand.Start)

        assertEquals(1, dependencies.startCalls)
        assertEquals(0, dependencies.stopCalls)
        assertTrue(desiredStateStore.desiredRunning)
        assertEquals(HostServiceContinuation.KeepRunning, continuation)
    }

    @Test
    fun `invokes stop dependency for stop command and clears desired running state`() = runBlocking {
        val dependencies = FakeHostServiceDependencies(
            stopResult = HostResult.Success(Unit)
        )
        val desiredStateStore = FakeHostServiceDesiredStateStore(
            desiredRunning = true,
        )
        val handler = HostServiceCommandHandler(dependencies, desiredStateStore)

        val continuation = handler.handle(HostServiceCommand.Stop)

        assertEquals(0, dependencies.startCalls)
        assertEquals(1, dependencies.stopCalls)
        assertFalse(desiredStateStore.desiredRunning)
        assertEquals(HostServiceContinuation.StopSelf, continuation)
    }

    @Test
    fun `stops service but preserves desired state on recoverable start failure`() = runBlocking {
        val dependencies = FakeHostServiceDependencies(
            startResult = HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Runtime,
                    message = "boom",
                    recoverable = true,
                )
            )
        )
        val desiredStateStore = FakeHostServiceDesiredStateStore()
        val handler = HostServiceCommandHandler(dependencies, desiredStateStore)

        val continuation = handler.handle(HostServiceCommand.Start)

        assertEquals(1, dependencies.startCalls)
        assertEquals(0, dependencies.stopCalls)
        assertTrue(desiredStateStore.desiredRunning)
        assertEquals(HostServiceContinuation.StopSelf, continuation)
    }

    @Test
    fun `clears desired state and stops service on non recoverable start failure`() = runBlocking {
        val dependencies = FakeHostServiceDependencies(
            startResult = HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Runtime,
                    message = "boom",
                )
            )
        )
        val desiredStateStore = FakeHostServiceDesiredStateStore()
        val handler = HostServiceCommandHandler(dependencies, desiredStateStore)

        val continuation = handler.handle(HostServiceCommand.Start)

        assertEquals(1, dependencies.startCalls)
        assertEquals(0, dependencies.stopCalls)
        assertFalse(desiredStateStore.desiredRunning)
        assertEquals(HostServiceContinuation.StopSelf, continuation)
    }
}

private class FakeHostServiceDesiredStateStore(
    override var desiredRunning: Boolean = false,
) : HostServiceDesiredStateStore

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
