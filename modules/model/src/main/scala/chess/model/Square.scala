package chess.model

/** A square on the chess board, identified by file and rank. */
case class Square(file: File, rank: Rank):
  /** Algebraic notation for this square, e.g. "e4". */
  def toAlgebraic: String = s"${file.toChar}${rank.toChar}"

object Square:
  /** Parses algebraic notation (e.g. "e4"). Returns [[None]] if the string is invalid. */
  def fromAlgebraic(s: String): Option[Square] =
    if s.length != 2 then None
    else
      for
        f <- File.fromChar(s(0))
        r <- Rank.fromChar(s(1))
      yield Square(f, r)

  /** All 64 squares ordered by rank (1→8) then file (a→h). */
  val all: IndexedSeq[Square] =
    for
      r <- Rank.values
      f <- File.values
    yield Square(f, r)
