package org.maichess.mono.bots

object BotRegistry:
  val all: List[Bot] = List(
    new MinimaxBot("Easy",   2),
    new MinimaxBot("Medium", 3),
    new MinimaxBot("Hard",   4),
    new engine.UciBot()
  )
