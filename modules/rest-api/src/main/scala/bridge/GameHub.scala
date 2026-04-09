package org.maichess.mono.api.bridge

import org.maichess.mono.bots.Bot
import org.maichess.mono.uifx.{ClockConfig, SharedGameModel}

class GameHub(val model: SharedGameModel):
  @volatile private var _resigned: Boolean = false

  def isResigned: Boolean = _resigned

  def newGame(
    white:       Option[Bot]         = None,
    black:       Option[Bot]         = None,
    whiteName:   String              = "White",
    blackName:   String              = "Black",
    clockConfig: Option[ClockConfig] = None
  ): Unit =
    _resigned = false
    model.newGame(white, black, whiteName, blackName, clockConfig)

  def resign(): Unit =
    _resigned = true
    model.resign()

  def importFen(fen: String): Either[String, Unit] =
    val result = model.importFen(fen)
    if result.isRight then _resigned = false
    result

  def importPgn(pgn: String): Either[String, Unit] =
    val result = model.importPgn(pgn)
    if result.isRight then _resigned = false
    result
