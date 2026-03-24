package chess.model

/** A chess move. Subtypes represent the three move categories. */
sealed trait Move:
  /** The square the moving piece departs from. */
  def from: Square

  /** The square the moving piece arrives at (king's destination for castling). */
  def to: Square

/** A standard move: simple relocation with an optional promotion piece. */
case class NormalMove(
  from: Square,
  to: Square,
  promotion: Option[PieceType] = None,
) extends Move

/** A castling move, capturing both the king and rook relocations. */
case class CastlingMove(
  from: Square,
  to: Square,
  rookFrom: Square,
  rookTo: Square,
) extends Move

/** An en passant capture, including the square of the captured pawn. */
case class EnPassantMove(
  from: Square,
  to: Square,
  captured: Square,
) extends Move
