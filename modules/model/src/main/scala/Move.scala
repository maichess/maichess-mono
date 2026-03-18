package org.maichess.mono.model

/** Sealed move hierarchy covers all legal move categories. */
sealed trait Move:
  def from: Square
  def to:   Square

/** Standard move, optionally promoting a pawn. */
case class NormalMove(
  from:      Square,
  to:        Square,
  promotion: Option[PieceType]
) extends Move

object NormalMove:
  def apply(from: Square, to: Square): NormalMove = NormalMove(from, to, None)

/** King and rook both relocate atomically. */
case class CastlingMove(
  from:     Square,
  to:       Square,
  rookFrom: Square,
  rookTo:   Square
) extends Move

/** Pawn captures the enemy pawn on `captured`, which is not on `to`. */
case class EnPassantMove(
  from:     Square,
  to:       Square,
  captured: Square
) extends Move
