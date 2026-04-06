package org.maichess.mono.bots

import org.maichess.mono.engine.{Fen, FenCodec}

object BotRegistry:
  private val fen: FenCodec = new Fen()
  val all: List[Bot] = List(
    new MinimaxBot("Easy",   2),
    new MinimaxBot("Medium", 3),
    new MinimaxBot("Hard",   4),
    new engine.UciBot(1000,  fen),
    new engine.UciBot(2000,  fen),
    new engine.UciBot(3000,  fen),
    new engine.UciBot(4000,  fen),
    new engine.UciBot(5000,  fen),
    new engine.UciBot(6000,  fen),
    new engine.UciBot(7000,  fen),
    new engine.UciBot(8000,  fen),
    new engine.UciBot(9000,  fen),
    new engine.UciBot(10000, fen)
  )
