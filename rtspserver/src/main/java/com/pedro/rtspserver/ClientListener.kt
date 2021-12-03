package com.pedro.rtspserver

interface ClientListener {
  fun onConnected(client: ServerClient)

  fun onDisconnected(client: ServerClient)
}