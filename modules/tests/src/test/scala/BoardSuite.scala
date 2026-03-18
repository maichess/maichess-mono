package org.maichess.mono.tests

import munit.FunSuite
import org.maichess.mono.model.*

class BoardSuite extends FunSuite:

  test("Board.standard has exactly 32 pieces"):
    assertEquals(Board.standard.pieces.size, 32)

  test("Board.standard white king is on e1"):
    val piece = Square.fromAlgebraic("e1").flatMap(Board.standard.pieceAt)
    assertEquals(piece, Some(Piece(Color.White, PieceType.King)))

  test("Board.standard black king is on e8"):
    val piece = Square.fromAlgebraic("e8").flatMap(Board.standard.pieceAt)
    assertEquals(piece, Some(Piece(Color.Black, PieceType.King)))

  test("Board.standard has 16 white pieces"):
    assertEquals(Board.standard.pieces.values.count(_.color == Color.White), 16)

  test("Board.standard has 16 black pieces"):
    assertEquals(Board.standard.pieces.values.count(_.color == Color.Black), 16)

  test("Board.pieceAt returns None for empty square e4"):
    assertEquals(Square.fromAlgebraic("e4").flatMap(Board.standard.pieceAt), None)

  test("Board.applyMove source square is empty after move"):
    val result = for
      from <- Square.fromAlgebraic("e2")
      to   <- Square.fromAlgebraic("e4")
    yield Board.standard.applyMove(NormalMove(from, to)).pieceAt(from)
    assertEquals(result, Some(None))

  test("Board.applyMove target square has piece after move"):
    val result = for
      from <- Square.fromAlgebraic("e2")
      to   <- Square.fromAlgebraic("e4")
    yield Board.standard.applyMove(NormalMove(from, to)).pieceAt(to)
    assertEquals(result, Some(Some(Piece(Color.White, PieceType.Pawn))))

  test("Board.empty has no pieces"):
    assertEquals(Board.empty.pieces.size, 0)
