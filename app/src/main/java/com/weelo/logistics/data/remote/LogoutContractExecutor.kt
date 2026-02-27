package com.weelo.logistics.data.remote

internal data class LogoutContractActions(
    val unregisterToken: suspend () -> Unit,
    val markOffline: suspend () -> Unit,
    val backendLogout: suspend () -> Unit,
    val stopDisconnectAndClear: () -> Unit
)

internal suspend fun executeLogoutContract(
    strictEnforcement: Boolean,
    actions: LogoutContractActions
) {
    actions.unregisterToken()
    if (strictEnforcement) {
        actions.markOffline()
        actions.backendLogout()
    }
    actions.stopDisconnectAndClear()
}

