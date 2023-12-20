package com.pedro.rtspserver

/**
 * Created by pedro on 20/12/23.
 */
interface ClientListener {

  fun onClientConnected(client: ServerClient)

  fun onClientDisconnected(client: ServerClient)
}