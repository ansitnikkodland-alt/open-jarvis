package com.openjarvis.bridge

import org.junit.Assert.assertFalse
import org.junit.Test

class SocketAccessPolicyTest {
    @Test fun loopbackClientsAreDeniedWithoutVerifiedAuthentication() {
        assertFalse(SocketAccessPolicy.allowUnauthenticatedClient())
    }
}
