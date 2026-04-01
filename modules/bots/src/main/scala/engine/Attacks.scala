package org.maichess.mono.bots.engine

// ── Static attack tables ──────────────────────────────────────────────────────
// Built once at JVM startup.  Every lookup is a single array read — O(1) with
// no branching and no allocation.
//
// Slider attacks (bishop, rook, queen) are NOT included here; those require
// Magic Bitboards and will be added in the move-generation step.
object Attacks:

  // Pawn attacks: indexed by [color][square]
  // pawnAttacks(Col.White)(sq) = bitboard of squares attacked by a white pawn on sq
  val pawnAttacks: Array[Array[Long]] = Array(
    new Array[Long](64),   // White
    new Array[Long](64)    // Black
  )

  val knightAttacks: Array[Long] = new Array[Long](64)
  val kingAttacks:   Array[Long] = new Array[Long](64)

  // castlingMask(sq): AND this into castling rights when a piece moves from/to sq.
  // Any rook or king move instantly clears the relevant castling right in O(1).
  // Squares not involved in castling have mask 15 (no bits cleared).
  val castlingMask: Array[Int] = Array.fill(64)(Castling.All)

  // ── Table initialisation ──────────────────────────────────────────────────
  locally:
    var sq = 0
    while sq < 64 do
      val s   = Square(sq)
      val bb  = BB.bit(s)

      // White pawn attacks (pushes NE + NW)
      pawnAttacks(Col.White)(sq) = BB.shiftNE(bb) | BB.shiftNW(bb)
      // Black pawn attacks (pushes SE + SW)
      pawnAttacks(Col.Black)(sq) = BB.shiftSE(bb) | BB.shiftSW(bb)

      // Knight: up to 8 jumps, clamped by file masks to prevent wrap
      val notA  = BB.NotFileA
      val notH  = BB.NotFileH
      val notAB = notA & (notA << 1)   // not files A or B
      val notGH = notH & (notH >>> 1)  // not files G or H
      knightAttacks(sq) =
        ((bb & notA)  << 15) | ((bb & notH)  << 17) |
        ((bb & notA)  >>> 17)| ((bb & notH)  >>> 15)|
        ((bb & notAB) << 6)  | ((bb & notGH) << 10) |
        ((bb & notAB) >>> 10)| ((bb & notGH) >>> 6)

      // King: up to 8 adjacent squares
      val side = BB.shiftE(bb) | BB.shiftW(bb) | bb
      kingAttacks(sq) = (BB.shiftN(side) | BB.shiftS(side) | side) & ~bb

      sq += 1

    // Castling mask: moving from/to these squares clears specific rights
    castlingMask(Sq.A1.toInt) &= ~Castling.WQ
    castlingMask(Sq.H1.toInt) &= ~Castling.WK
    castlingMask(Sq.E1.toInt) &= ~(Castling.WK | Castling.WQ)
    castlingMask(Sq.A8.toInt) &= ~Castling.BQ
    castlingMask(Sq.H8.toInt) &= ~Castling.BK
    castlingMask(Sq.E8.toInt) &= ~(Castling.BK | Castling.BQ)
