package org.maichess.mono.rules

import org.maichess.mono.model.*

/** Placeholder stub — full implementation in Task 6. */
case class Situation(
  board:         Board,
  turn:          Color,
  castlingRights: CastlingRights,
  enPassant:     Option[Square],
  halfMoveClock: Int,
  fullMove:      Int
)

object Situation:
  val standard: Situation =
    Situation(Board.standard, Color.White, CastlingRights.all, None, 0, 1)
