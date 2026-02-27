package com.weelo.logistics.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LogoutContractExecutorTest {

    @Test
    fun `strict contract executes deterministic full logout order`() = runBlocking {
        val sequence = mutableListOf<String>()

        executeLogoutContract(
            strictEnforcement = true,
            actions = LogoutContractActions(
                unregisterToken = { sequence += "unregister" },
                markOffline = { sequence += "offline" },
                backendLogout = { sequence += "backend_logout" },
                stopDisconnectAndClear = { sequence += "local_clear" }
            )
        )

        assertEquals(
            listOf("unregister", "offline", "backend_logout", "local_clear"),
            sequence
        )
    }

    @Test
    fun `non strict contract still clears local state after unregister`() = runBlocking {
        val sequence = mutableListOf<String>()

        executeLogoutContract(
            strictEnforcement = false,
            actions = LogoutContractActions(
                unregisterToken = { sequence += "unregister" },
                markOffline = { sequence += "offline" },
                backendLogout = { sequence += "backend_logout" },
                stopDisconnectAndClear = { sequence += "local_clear" }
            )
        )

        assertEquals(
            listOf("unregister", "local_clear"),
            sequence
        )
    }
}

