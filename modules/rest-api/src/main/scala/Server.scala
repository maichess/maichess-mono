package org.maichess.mono.api

import cats.effect.{IO, Resource}
import cats.syntax.semigroupk.*
import com.comcast.ip4s.*
import org.http4s.server.{Router, Server => Http4sServer}
import org.http4s.ember.server.EmberServerBuilder
import org.maichess.mono.api.bridge.GameHub
import org.maichess.mono.api.routes.{BotRoutes, GameRoutes}

object Server:
  def make(hub: GameHub): Resource[IO, Http4sServer] =
    val routes = Router("/api/v1" -> (GameRoutes(hub) <+> BotRoutes()))
    EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port"5005")
      .withHttpApp(routes.orNotFound)
      .build
