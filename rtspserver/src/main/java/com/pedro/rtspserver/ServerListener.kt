package com.pedro.rtspserver

interface ServerListener {
  fun onClientDisconnected(client: ServerClient)
}