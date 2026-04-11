package com.sora.omniclaw.service.host

import org.junit.Assert.assertEquals
import org.junit.Test

class HostServiceCommandTest {
    @Test
    fun `parses start action into start command`() {
        val command = HostServiceCommand.fromAction(
            HostForegroundService.ACTION_START,
            desiredRunning = false,
        )

        assertEquals(HostServiceCommand.Start, command)
    }

    @Test
    fun `parses stop action into stop command`() {
        val command = HostServiceCommand.fromAction(
            HostForegroundService.ACTION_STOP,
            desiredRunning = true,
        )

        assertEquals(HostServiceCommand.Stop, command)
    }

    @Test
    fun `treats null intent as start command when desired state is running`() {
        val command = HostServiceCommand.fromIntent(
            intent = null,
            desiredRunning = true,
        )

        assertEquals(HostServiceCommand.Start, command)
    }

    @Test
    fun `treats null intent as ignore command when desired state is stopped`() {
        val command = HostServiceCommand.fromIntent(
            intent = null,
            desiredRunning = false,
        )

        assertEquals(HostServiceCommand.Ignore, command)
    }

    @Test
    fun `defaults unknown action to ignore command`() {
        val command = HostServiceCommand.fromAction(
            "com.sora.omniclaw.service.host.action.UNKNOWN",
            desiredRunning = true,
        )

        assertEquals(HostServiceCommand.Ignore, command)
    }
}
