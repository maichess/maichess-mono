package org.maichess.mono.model

/** Placeholder stub — full implementation in Task 5. */
case class Board(pieces: Map[Square, Piece]):
  def pieceAt(sq: Square): Option[Piece] = pieces.get(sq)
  def applyMove(move: Move): Board        = this

object Board:
  val empty: Board    = Board(Map.empty)
  val standard: Board = Board(Map.empty)
