package com.khosravi.devin.present.data

import com.khosravi.devin.present.client.ClientData

sealed class ClientLoadedState {
    data object Zero : ClientLoadedState()
    class Single(val client: ClientData) : ClientLoadedState()
    class Multi(val clients: List<ClientData>) : ClientLoadedState()
}