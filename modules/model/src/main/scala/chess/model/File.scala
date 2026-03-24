package chess.model

/** A file (column) on the chess board, a–h, stored internally as 0–7. */
opaque type File = Int

object File:
  /** Constructs a [[File]] from a character 'a'–'h'. Returns [[None]] if out of range. */
  def fromChar(c: Char): Option[File] =
    Option.when(c >= 'a' && c <= 'h')(c - 'a')

  /** All 8 files in board order, a to h. */
  val values: IndexedSeq[File] = (0 to 7).toIndexedSeq

  extension (f: File)
    /** The letter character for this file ('a'–'h'). */
    def toChar: Char = ('a' + f).toChar

    /** The 0-based index of this file (0 = a, 7 = h). */
    def index: Int = f
