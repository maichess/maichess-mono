package org.maichess.mono.tests

import munit.FunSuite
import org.maichess.mono.model.*
import org.maichess.mono.rules.*
import org.maichess.mono.engine.*

class FenSuite extends FunSuite:

  val codec    = new Fen()
  val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  def sq(alg: String): Square =
    Square.fromAlgebraic(alg).getOrElse(throw new AssertionError(s"Invalid square: $alg"))

  // ── Encode ────────────────────────────────────────────────────────────────

  test("encode standard starting position"):
    assertEquals(codec.encode(Situation.standard), startFen)

  test("encode position with no castling rights"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    val fen = codec.encode(sit)
    assert(fen.contains(" - "), s"Expected '-' for no castling in '$fen'")

  test("encode FEN with en passant square"):
    val ctrl  = GameController(StandardRules)
    val state = ctrl.applyMove(ctrl.newGame(), NormalMove(sq("e2"), sq("e4")))
      .getOrElse(fail("illegal move"))
    val fen = codec.encode(state.current)
    assert(fen.contains("e3"), s"Expected en passant e3 in '$fen'")

  test("encode position after multiple moves round-trips"):
    val ctrl  = GameController(StandardRules)
    val moves = List(
      NormalMove(sq("e2"), sq("e4")),
      NormalMove(sq("e7"), sq("e5")),
      NormalMove(sq("g1"), sq("f3"))
    )
    val state = moves.foldLeft(ctrl.newGame()) { (s, m) =>
      ctrl.applyMove(s, m).getOrElse(fail(s"illegal move $m"))
    }
    val fen     = codec.encode(state.current)
    val decoded = codec.decode(fen)
    assert(decoded.isRight)
    decoded.foreach(sit => assertEquals(sit.board, state.current.board))

  // ── Decode: valid FENs ────────────────────────────────────────────────────

  test("decode standard starting FEN"):
    val result = codec.decode(startFen)
    assert(result.isRight)
    result.foreach { sit =>
      assertEquals(sit.board,           Situation.standard.board)
      assertEquals(sit.turn,            Color.White)
      assertEquals(sit.castlingRights,  CastlingRights.all)
      assertEquals(sit.enPassantSquare, None)
      assertEquals(sit.halfMoveClock,   0)
      assertEquals(sit.fullMoveNumber,  1)
    }

  test("encode-decode round trip preserves situation"):
    val encoded = codec.encode(Situation.standard)
    val decoded = codec.decode(encoded)
    decoded.foreach(sit => assertEquals(sit, Situation.standard))
    assert(decoded.isRight)

  test("decode FEN with en passant square"):
    val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
    codec.decode(fen).foreach(sit => assertEquals(sit.enPassantSquare, Some(sq("e3"))))

  test("decode FEN with black to move"):
    val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
    codec.decode(fen).foreach(sit => assertEquals(sit.turn, Color.Black))

  test("decode FEN with no castling rights"):
    val fen = "8/8/4k3/8/8/4K3/8/8 w - - 0 1"
    codec.decode(fen).foreach(sit => assertEquals(sit.castlingRights, CastlingRights.none))

  test("decode FEN with partial castling rights"):
    val fen = "r3k2r/8/8/8/8/8/8/R3K2R w Kq - 0 1"
    codec.decode(fen).foreach { sit =>
      assertEquals(sit.castlingRights.whiteKingSide,  true)
      assertEquals(sit.castlingRights.whiteQueenSide, false)
      assertEquals(sit.castlingRights.blackKingSide,  false)
      assertEquals(sit.castlingRights.blackQueenSide, true)
    }

  test("decode FEN respects fullMoveNumber"):
    codec.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 42")
      .foreach(sit => assertEquals(sit.fullMoveNumber, 42))

  test("decode FEN respects halfMoveClock"):
    codec.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 17 1")
      .foreach(sit => assertEquals(sit.halfMoveClock, 17))

  // ── Decode: invalid FENs ──────────────────────────────────────────────────

  test("decode invalid FEN returns Left"):
    assert(codec.decode("not a fen").isLeft)

  test("decode FEN with wrong number of fields returns Left"):
    assert(codec.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w").isLeft)

  test("decode FEN with invalid turn returns Left"):
    assert(codec.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1").isLeft)

  test("decode FEN with invalid en passant returns Left"):
    assert(codec.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq z9 0 1").isLeft)

  test("decode FEN with wrong number of ranks returns Left"):
    assert(codec.decode("8/8/8 w - - 0 1").isLeft)

  test("decode FEN with unknown piece character returns Left"):
    assert(codec.decode("xnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1").isLeft)

  test("decode FEN with too many pieces in a rank returns Left"):
    assert(codec.decode("rnbqkbnrr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1").isLeft)

  test("decode FEN with invalid castling chars returns Left"):
    assert(codec.decode("8/8/8/8/8/8/8/8 w XY - 0 1").isLeft)

  test("decode FEN with non-numeric halfMoveClock returns Left"):
    assert(codec.decode("8/8/8/8/8/8/8/8 w - - abc 1").isLeft)

  test("decode FEN with non-numeric fullMoveNumber returns Left"):
    assert(codec.decode("8/8/8/8/8/8/8/8 w - - 0 xyz").isLeft)
