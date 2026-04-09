package org.maichess.mono.api

import cats.effect.{IO, IOApp}
import org.maichess.mono.api.bridge.GameHub
import org.maichess.mono.engine.GameController
import org.maichess.mono.rules.StandardRules
import org.maichess.mono.uifx.SharedGameModel

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    val ctrl = new GameController(StandardRules)
    val hub  = new GameHub(new SharedGameModel(ctrl))
    Server.make(hub).use(_ => IO.never)
