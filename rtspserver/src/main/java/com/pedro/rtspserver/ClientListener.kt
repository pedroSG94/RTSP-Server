package com.pedro.rtspserver

interface ClientListener {
  fun onDisconnected(client: ServerClient)
}