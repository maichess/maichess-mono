package org.maichess.mono.rules

import org.maichess.mono.model.*

/** Complete game state needed for rule evaluation. */
case class Situation(
  board:           Board,
  turn:            Color,
  castlingRights:  CastlingRights,
  enPassantSquare: Option[Square],
  halfMoveClock:   Int,
  fullMoveNumber:  Int
)

object Situation:
  /** Returns the standard chess starting situation. */
  def standard: Situation = Situation(
    board           = Board.standard,
    turn            = Color.White,
    castlingRights  = CastlingRights.all,
    enPassantSquare = None,
    halfMoveClock   = 0,
    fullMoveNumber  = 1
  )
