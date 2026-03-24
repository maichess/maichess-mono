package chess

import chess.model.*
import chess.rules.Board
import munit.FunSuite

class BoardSuite extends FunSuite:

  private def sq(s: String): Square = Square.fromAlgebraic(s).get

  // Piece count ──────────────────────────────────────────────────────────────

  test("Standard board has 32 pieces"):
    assertEquals(Board.standard.pieces.size, 32)

  test("Standard board has 16 white pieces"):
    assertEquals(Board.standard.pieces.count(_._2.color == Color.White), 16)

  test("Standard board has 16 black pieces"):
    assertEquals(Board.standard.pieces.count(_._2.color == Color.Black), 16)

  // pieceAt ──────────────────────────────────────────────────────────────────

  test("pieceAt e1 returns white king on standard board"):
    assertEquals(Board.standard.pieceAt(sq("e1")), Some(Piece(Color.White, PieceType.King)))

  test("pieceAt e8 returns black king on standard board"):
    assertEquals(Board.standard.pieceAt(sq("e8")), Some(Piece(Color.Black, PieceType.King)))

  test("pieceAt a1 returns white rook on standard board"):
    assertEquals(Board.standard.pieceAt(sq("a1")), Some(Piece(Color.White, PieceType.Rook)))

  test("pieceAt e2 returns white pawn on standard board"):
    assertEquals(Board.standard.pieceAt(sq("e2")), Some(Piece(Color.White, PieceType.Pawn)))

  test("pieceAt e4 returns None on standard board"):
    assertEquals(Board.standard.pieceAt(sq("e4")), None)

  test("pieceAt all empty squares returns None"):
    val occupied = Board.standard.pieces.keySet
    Square.all.filterNot(occupied.contains).foreach: s =>
      assertEquals(Board.standard.pieceAt(s), None, s"${s.toAlgebraic} should be empty")

  // applyMove ────────────────────────────────────────────────────────────────

  test("After NormalMove from e2 to e4, source is empty"):
    val board  = Board.standard
    val newB   = board.applyMove(NormalMove(sq("e2"), sq("e4")))
    assertEquals(newB.pieceAt(sq("e2")), None)

  test("After NormalMove from e2 to e4, target has the pawn"):
    val board  = Board.standard
    val pawn   = board.pieceAt(sq("e2")).get
    val newB   = board.applyMove(NormalMove(sq("e2"), sq("e4")))
    assertEquals(newB.pieceAt(sq("e4")), Some(pawn))

  test("applyMove preserves piece count for non-capture"):
    val board = Board.standard
    val newB  = board.applyMove(NormalMove(sq("e2"), sq("e4")))
    assertEquals(newB.pieces.size, 32)

  test("applyMove reduces piece count on capture"):
    // Place white queen on d5 capturing a black pawn on e6 (custom board)
    val board = Board(Map(
      sq("d5") -> Piece(Color.White, PieceType.Queen),
      sq("e6") -> Piece(Color.Black, PieceType.Pawn),
    ))
    val newB = board.applyMove(NormalMove(sq("d5"), sq("e6")))
    assertEquals(newB.pieces.size, 1)
    assertEquals(newB.pieceAt(sq("e6")), Some(Piece(Color.White, PieceType.Queen)))

  test("EnPassantMove removes the captured pawn"):
    val board = Board(Map(
      sq("e5") -> Piece(Color.White, PieceType.Pawn),
      sq("d5") -> Piece(Color.Black, PieceType.Pawn),
    ))
    val move = EnPassantMove(sq("e5"), sq("d6"), sq("d5"))
    val newB = board.applyMove(move)
    assertEquals(newB.pieceAt(sq("d5")), None)
    assertEquals(newB.pieceAt(sq("d6")), Some(Piece(Color.White, PieceType.Pawn)))
    assertEquals(newB.pieceAt(sq("e5")), None)

  test("CastlingMove moves both king and rook"):
    val board = Board(Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("h1") -> Piece(Color.White, PieceType.Rook),
    ))
    val move = CastlingMove(sq("e1"), sq("g1"), sq("h1"), sq("f1"))
    val newB = board.applyMove(move)
    assertEquals(newB.pieceAt(sq("g1")), Some(Piece(Color.White, PieceType.King)))
    assertEquals(newB.pieceAt(sq("f1")), Some(Piece(Color.White, PieceType.Rook)))
    assertEquals(newB.pieceAt(sq("e1")), None)
    assertEquals(newB.pieceAt(sq("h1")), None)
