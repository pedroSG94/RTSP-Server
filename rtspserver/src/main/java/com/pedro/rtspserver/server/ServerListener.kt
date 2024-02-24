package com.pedro.rtspserver.server

interface ServerListener {
  fun onClientDisconnected(client: ServerClient)
}