package org.maichess.mono.bots

object BotRegistry:
  val all: List[Bot] = List(
    new MinimaxBot("Easy",   2),
    new MinimaxBot("Medium", 3),
    new MinimaxBot("Hard",   4),
    new engine.UciBot(1000),
    new engine.UciBot(2000),
    new engine.UciBot(3000),
    new engine.UciBot(4000),
    new engine.UciBot(5000),
    new engine.UciBot(6000),
    new engine.UciBot(7000),
    new engine.UciBot(8000),
    new engine.UciBot(9000),
    new engine.UciBot(10000)
  )
