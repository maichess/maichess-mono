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

  test("Board.applyMove promotes pawn when NormalMove carries promotion"):
    val from = Square.fromAlgebraic("e7")
    val to   = Square.fromAlgebraic("e8")
    val result = for
      f <- from
      t <- to
    yield
      val board = Board(Map(f -> Piece(Color.White, PieceType.Pawn)))
      board.applyMove(NormalMove(f, t, Some(PieceType.Queen))).pieceAt(t)
    assertEquals(result, Some(Some(Piece(Color.White, PieceType.Queen))))

  test("Board.applyMove from empty source square leaves target empty"):
    val from = Square.fromAlgebraic("e4")  // empty board
    val to   = Square.fromAlgebraic("e5")
    val result = for
      f <- from
      t <- to
    yield Board.empty.applyMove(NormalMove(f, t)).pieceAt(t)
    assertEquals(result, Some(None))
