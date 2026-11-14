package com.openjarvis.agent

import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class DeviceSessionTest {
    @After fun cleanup() { DeviceSession.stop() }

    @Test fun disableCancelsAllTasksAndReconnectRejectsOldGeneration() {
        DeviceSession.start()
        val old = DeviceSession.current()!!
        val active = Job()
        val queued = Job()
        assertTrue(DeviceSession.register(old, active))
        assertTrue(DeviceSession.register(old, queued))
        DeviceSession.stop()
        assertTrue(active.isCancelled)
        assertTrue(queued.isCancelled)
        assertNull(DeviceSession.current())
        DeviceSession.start()
        assertFalse(DeviceSession.valid(old))
        val late = Job()
        assertFalse(DeviceSession.register(old, late))
        assertTrue(late.isCancelled)
        assertTrue(DeviceSession.valid(DeviceSession.current()!!))
    }

    @Test fun connectingAgainInvalidatesExistingWork() {
        DeviceSession.start()
        val old = DeviceSession.current()!!
        val job = Job()
        DeviceSession.register(old, job)
        DeviceSession.start()
        assertTrue(job.isCancelled)
        assertFalse(DeviceSession.valid(old))
    }
}
