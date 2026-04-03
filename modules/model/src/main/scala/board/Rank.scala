package org.maichess.mono.model

/** Row on the board: rank 1 (index 0) through rank 8 (index 7). */
opaque type Rank = Int

object Rank:
  /** Construct from internal index 0–7 (rank 1 = 0, rank 8 = 7). */
  def fromInt(i: Int): Option[Rank] =
    Option.when(i >= 0 && i <= 7)(i)

  /** Construct from chess character '1'–'8'. */
  def fromChar(c: Char): Option[Rank] =
    Option.when(c >= '1' && c <= '8')(c - '1')

  val values: IndexedSeq[Rank] = (0 to 7).toIndexedSeq

  extension (r: Rank)
    def toInt: Int   = r
    def toChar: Char = ('1' + r).toChar
