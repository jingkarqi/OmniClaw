package com.sora.omniclaw.service.host

import org.junit.Assert.assertEquals
import org.junit.Test

class HostServiceCommandTest {
    @Test
    fun `parses start action into start command`() {
        val command = HostServiceCommand.fromAction(HostForegroundService.ACTION_START)

        assertEquals(HostServiceCommand.Start, command)
    }

    @Test
    fun `parses stop action into stop command`() {
        val command = HostServiceCommand.fromAction(HostForegroundService.ACTION_STOP)

        assertEquals(HostServiceCommand.Stop, command)
    }

    @Test
    fun `treats null action as start command for sticky-service recovery`() {
        val command = HostServiceCommand.fromAction(null)

        assertEquals(HostServiceCommand.Start, command)
    }

    @Test
    fun `defaults unknown action to ignore command`() {
        val command = HostServiceCommand.fromAction(
            "com.sora.omniclaw.service.host.action.UNKNOWN"
        )

        assertEquals(HostServiceCommand.Ignore, command)
    }
}
