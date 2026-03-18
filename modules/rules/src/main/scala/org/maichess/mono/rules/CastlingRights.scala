package org.maichess.mono.rules

/** Tracks which castling options remain available for each side. */
case class CastlingRights(
  whiteKingSide:  Boolean,
  whiteQueenSide: Boolean,
  blackKingSide:  Boolean,
  blackQueenSide: Boolean
)

object CastlingRights:
  val all:  CastlingRights = CastlingRights(true,  true,  true,  true)
  val none: CastlingRights = CastlingRights(false, false, false, false)
