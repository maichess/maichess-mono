package chess.engine

import chess.model.Move

/** Returned when a move is rejected because it is not legal in the current position. */
case class IllegalMove(move: Move, reason: String)
