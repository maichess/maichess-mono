package org.maichess.mono.uifx

import org.maichess.mono.bots.Bot

/** The result of the "New Game" dialog — shared between the JavaFX and TUI frontends. */
case class NewGameSetup(
  whiteName:   String,
  blackName:   String,
  whiteBot:    Option[Bot],
  blackBot:    Option[Bot],
  clockConfig: Option[ClockConfig]
)
