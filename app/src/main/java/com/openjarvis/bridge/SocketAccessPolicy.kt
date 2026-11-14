package com.openjarvis.bridge

/**
 * Loopback TCP connections cannot be authenticated with Process.myUid().
 * Do not enable until a peer-authentication protocol is implemented and tested.
 */
internal object SocketAccessPolicy {
    fun allowUnauthenticatedClient(): Boolean = false
}
