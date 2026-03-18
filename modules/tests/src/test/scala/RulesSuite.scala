package org.maichess.mono.tests

import munit.FunSuite
import org.maichess.mono.model.*
import org.maichess.mono.rules.*

class RulesSuite extends FunSuite:

  def sq(alg: String): Square =
    Square.fromAlgebraic(alg).getOrElse(throw new AssertionError(s"Invalid square: $alg"))

  // Back-rank mate: white king h1, white rook g1, black queen g2, black rooks a8+b8
  // It's black's turn; black queen gives checkmate at h2 is not it — use a simple position:
  // White king a1, black queen b3, black king c3 → white is in checkmate (Qb3#)
  test("isCheckmate detects back-rank mate"):
    val pieces = Map(
      sq("a1") -> Piece(Color.White, PieceType.King),
      sq("b3") -> Piece(Color.Black, PieceType.Queen),
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
