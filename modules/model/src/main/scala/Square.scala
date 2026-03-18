package org.maichess.mono.model

/** A board square identified by file and rank. */
case class Square(file: File, rank: Rank):
  /** Returns algebraic notation, e.g. "e4". */
  def toAlgebraic: String = file.toChar.toString + rank.toChar.toString

  /** Returns the square offset by (df, dr), or None if off-board. */
  def offset(df: Int, dr: Int): Option[Square] =
    for
      f <- File.fromInt(file.toInt + df)
      r <- Rank.fromInt(rank.toInt + dr)
    yield Square(f, r)

object Square:
  /** Parses algebraic notation ("e4"); returns None for invalid strings. */
  def fromAlgebraic(s: String): Option[Square] =
    Option.when(s.length == 2) {
      for
        f <- File.fromChar(s(0))
        r <- Rank.fromChar(s(1))
      yield Square(f, r)
    }.flatten

  /** All 64 squares, rank-major order. */
  val all: IndexedSeq[Square] =
    for
      r <- Rank.values
      f <- File.values
    yield Square(f, r)
