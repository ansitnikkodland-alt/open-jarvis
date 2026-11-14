package com.openjarvis.agent

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ApprovalRequestRegistryTest {
    @Test fun serviceShutdownRevokesPendingAndAlreadyAnsweredRequests() = runTest {
        val registry = ApprovalRequestRegistry()
        val pending = registry.create("pending")
        val answered = registry.create("answered")
        registry.respond(answered.id, true)
        registry.cancelAll()
        assertFalse(registry.await(pending.id, 1000))
        assertFalse(registry.await(answered.id, 1000))
        assertFalse(registry.respond(pending.id, true))
    }

    @Test fun explicitAllowWorksOnlyOnce() = runTest {
        val registry = ApprovalRequestRegistry()
        val request = registry.create("Tap Save")
        assertTrue(registry.respond(request.id, true))
        assertFalse(registry.respond(request.id, false))
        assertTrue(registry.await(request.id, 30_000L))
        assertNull(registry.get(request.id))
        assertFalse(registry.respond(request.id, true))
    }

    @Test fun explicitDenyBlocksExecution() = runTest {
        val registry = ApprovalRequestRegistry()
        val request = registry.create("Tap Delete")
        assertTrue(registry.respond(request.id, false))
        assertFalse(registry.await(request.id, 30_000L))
        assertFalse(registry.respond(request.id, true))
    }

    @Test fun timeoutDeniesAndRejectsLateApproval() = runTest {
        val registry = ApprovalRequestRegistry()
        val request = registry.create("Transfer money")
        assertFalse(registry.await(request.id, 30_000L))
        assertNull(registry.get(request.id))
        assertFalse(registry.respond(request.id, true))
    }

    @Test fun cancelledAwaitDeniesAndRemovesRequest() = runTest {
        val registry = ApprovalRequestRegistry()
        val request = registry.create("Send message")
        val job = launch { registry.await(request.id, 30_000L) }
        runCurrent()
        job.cancelAndJoin()
        assertNull(registry.get(request.id))
        assertFalse(registry.respond(request.id, true))
    }

    @Test fun twoRequestsCannotApproveEachOther() = runTest {
        val registry = ApprovalRequestRegistry()
        val a = registry.create("Tap A")
        val b = registry.create("Tap B")
        assertNotEquals(a.id, b.id)
        assertEquals("Tap A", registry.get(a.id)?.details)
        assertEquals("Tap B", registry.get(b.id)?.details)
        assertTrue(registry.respond(a.id, true))
        assertTrue(registry.respond(b.id, false))
        val first = async { registry.await(a.id, 30_000L) }
        val second = async { registry.await(b.id, 30_000L) }
        assertTrue(first.await())
        assertFalse(second.await())
    }

    @Test fun removingUnansweredRequestIsFailClosed() = runTest {
        val registry = ApprovalRequestRegistry()
        val request = registry.create("Type password")
        registry.remove(request.id)
        assertNull(registry.get(request.id))
        assertFalse(registry.respond(request.id, true))
        assertFalse(registry.await(request.id, 30_000L))
    }
}
