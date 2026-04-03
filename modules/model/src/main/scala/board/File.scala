package org.maichess.mono.model

/** Column on the board: a (0) through h (7). Opaque to prevent raw-Int confusion. */
opaque type File = Int

object File:
  /** Construct from 'a'–'h'; returns None for other characters. */
  def fromChar(c: Char): Option[File] =
    Option.when(c >= 'a' && c <= 'h')(c - 'a')

  def fromInt(i: Int): Option[File] =
    Option.when(i >= 0 && i <= 7)(i)

  val values: IndexedSeq[File] = (0 to 7).toIndexedSeq

  extension (f: File)
    def toChar: Char    = ('a' + f).toChar
    def toInt: Int      = f
