package org.maichess.mono.api.routes

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.maichess.mono.api.codec.Codecs.given
import org.maichess.mono.api.model.BotResponse
import org.maichess.mono.bots.BotRegistry

object BotRoutes:
  def apply(): HttpRoutes[IO] =
    HttpRoutes.of[IO]:
      case GET -> Root / "bots" =>
        Ok(BotRegistry.all.map(b => BotResponse(b.name)).asJson)
