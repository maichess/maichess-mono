package org.maichess.mono.tests

import munit.FunSuite
import org.maichess.mono.model.*
import org.maichess.mono.rules.*

class RulesSuite extends FunSuite:

  def sq(alg: String): Square =
    Square.fromAlgebraic(alg).getOrElse(throw new AssertionError(s"Invalid square: $alg"))

  // White king a1, black queen b2, black king c3:
  //   Qb2 attacks a1 diagonally (check).
  //   a2 covered by Qb2 (same rank), b1 by Qb2 (same file), b2 defended by Kc3.
  test("isCheckmate detects back-rank mate"):
    val pieces = Map(
      sq("a1") -> Piece(Color.White, PieceType.King),
      sq("b2") -> Piece(Color.Black, PieceType.Queen),
      sq("c3") -> Piece(Color.Black, PieceType.King)
    )
    val s = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    assert(StandardRules.isCheckmate(s))

  test("isCheckmate returns false for non-checkmate"):
    assert(!StandardRules.isCheckmate(Situation.standard))

  test("isStalemate detects stalemate"):
    // White king a1, black queen c2, black king c1 → white to move, stalemate
    val pieces = Map(
      sq("a1") -> Piece(Color.White, PieceType.King),
      sq("c2") -> Piece(Color.Black, PieceType.Queen),
      sq("c1") -> Piece(Color.Black, PieceType.King)
    )
    val s = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    assert(StandardRules.isStalemate(s))

  test("isStalemate returns false for starting position"):
    assert(!StandardRules.isStalemate(Situation.standard))

  test("fifty-move rule triggers at halfMoveClock >= 100"):
    val s = Situation.standard.copy(halfMoveClock = 100)
    assert(StandardRules.isFiftyMoveRule(s))

  test("fifty-move rule does not trigger at halfMoveClock 99"):
    val s = Situation.standard.copy(halfMoveClock = 99)
    assert(!StandardRules.isFiftyMoveRule(s))

  def kings: Map[Square, Piece] = Map(
    sq("e1") -> Piece(Color.White, PieceType.King),
    sq("e8") -> Piece(Color.Black, PieceType.King)
  )

  test("isInsufficientMaterial: single lone bishop"):
    val s = Situation(Board(kings + (sq("d4") -> Piece(Color.White, PieceType.Bishop))),
                      Color.White, CastlingRights.none, None, 0, 1)
    assert(StandardRules.isInsufficientMaterial(s))

  test("isInsufficientMaterial: single lone knight"):
    val s = Situation(Board(kings + (sq("d4") -> Piece(Color.White, PieceType.Knight))),
                      Color.White, CastlingRights.none, None, 0, 1)
    assert(StandardRules.isInsufficientMaterial(s))

  test("isInsufficientMaterial: two bishops of opposite colors"):
    val s = Situation(Board(kings
      + (sq("c1") -> Piece(Color.White, PieceType.Bishop))
      + (sq("f8") -> Piece(Color.Black, PieceType.Bishop))),
      Color.White, CastlingRights.none, None, 0, 1)
    assert(StandardRules.isInsufficientMaterial(s))

  test("isInsufficientMaterial: false for two knights"):
    val s = Situation(Board(kings
      + (sq("b1") -> Piece(Color.White, PieceType.Knight))
      + (sq("g8") -> Piece(Color.Black, PieceType.Knight))),
      Color.White, CastlingRights.none, None, 0, 1)
    assert(!StandardRules.isInsufficientMaterial(s))
