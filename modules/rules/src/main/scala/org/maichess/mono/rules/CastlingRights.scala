package org.maichess.mono.rules

/** Placeholder stub — full implementation in Task 6. */
case class CastlingRights(whiteKingSide: Boolean, whiteQueenSide: Boolean, blackKingSide: Boolean, blackQueenSide: Boolean)

object CastlingRights:
  val none: CastlingRights = CastlingRights(false, false, false, false)
  val all: CastlingRights  = CastlingRights(true, true, true, true)
