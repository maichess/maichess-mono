package chess.model

/** A rank (row) on the chess board, 1–8, stored internally as 0–7. */
opaque type Rank = Int

object Rank:
  /** Constructs a [[Rank]] from a 1-based integer 1–8. Returns [[None]] if out of range. */
  def fromInt(i: Int): Option[Rank] =
    Option.when(i >= 1 && i <= 8)(i - 1)

  /** Constructs a [[Rank]] from a digit character '1'–'8'. Returns [[None]] if invalid. */
  def fromChar(c: Char): Option[Rank] =
    Option.when(c >= '1' && c <= '8')(c - '1')

  /** All 8 ranks in board order, rank 1 to rank 8. */
  val values: IndexedSeq[Rank] = (0 to 7).toIndexedSeq

  extension (r: Rank)
    /** The 1-based rank number (1–8) used in algebraic notation. */
    def toInt: Int = r + 1

    /** The 0-based internal index of this rank (0 = rank 1, 7 = rank 8). */
    def index: Int = r

    /** The digit character for this rank ('1'–'8'). */
    def toChar: Char = ('1' + r).toChar
