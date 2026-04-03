package org.maichess.mono.engine

/** Returned when an attempted move is not legal in the current position. */
case class IllegalMove(reason: String)
