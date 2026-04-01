package org.maichess.mono.tests

import munit.FunSuite
import org.maichess.mono.model.*
import org.maichess.mono.rules.*
import org.maichess.mono.engine.*

class GameControllerSuite extends FunSuite:

  val ctrl = GameController(StandardRules)

  def sq(alg: String): Square =
    Square.fromAlgebraic(alg).getOrElse(throw new AssertionError(s"Invalid square: $alg"))
  def move(from: String, to: String): NormalMove = NormalMove(sq(from), sq(to))

  test("Scholar's Mate ends in checkmate after 4 moves"):
    // 1. e4 e5  2. Qh5 Nc6  3. Bc4 Nf6??  4. Qxf7#
    val moves = List(
      move("e2", "e4"),
      move("e7", "e5"),
      move("d1", "h5"),
      move("b8", "c6"),
      move("f1", "c4"),
      move("g8", "f6"),
      move("h5", "f7")  // Qxf7#
    )
    ctrl.replay(moves).run(ctrl.newGame()) match
      case Ply.Step.Terminal(result, _) =>
        assertEquals(result, GameResult.Checkmate(Color.White))
      case Ply.Step.Active(_, _) =>
        fail("Expected checkmate after Scholar's Mate")

  test("Applying an illegal move returns Left and does not change state"):
    val state = ctrl.newGame()
    // e2 pawn cannot jump to e5
    val result = ctrl.applyMove(state, move("e2", "e5"))
    assert(result.isLeft)

  test("newGame starts with standard position"):
    val state = ctrl.newGame()
    assertEquals(state.current.board.pieces.size, 32)
    assertEquals(state.current.turn, Color.White)
    assertEquals(state.history, Nil)

  test("gameResult returns None for ongoing game"):
    assertEquals(ctrl.gameResult(ctrl.newGame()), None)

  test("gameResult detects stalemate"):
    val pieces = Map(
      sq("a1") -> Piece(Color.White, PieceType.King),
      sq("c2") -> Piece(Color.Black, PieceType.Queen),
      sq("c1") -> Piece(Color.Black, PieceType.King)
    )
    val state = GameState(Nil, Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1))
    assertEquals(ctrl.gameResult(state), Some(GameResult.Stalemate))

  test("gameResult detects fifty-move draw"):
    val state = GameState(Nil, Situation.standard.copy(halfMoveClock = 100))
    assertEquals(ctrl.gameResult(state), Some(GameResult.Draw(DrawReason.FiftyMoveRule)))

  test("gameResult detects insufficient material"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val state = GameState(Nil, Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1))
    assertEquals(ctrl.gameResult(state), Some(GameResult.Draw(DrawReason.InsufficientMaterial)))

  test("gameResult detects threefold repetition"):
    val sit   = Situation.standard
    val state = GameState(List(sit, sit), sit)
    assertEquals(ctrl.gameResult(state), Some(GameResult.Draw(DrawReason.ThreefoldRepetition)))

  test("applyMove increments halfMoveClock on quiet piece move"):
    val state = ctrl.newGame()
    ctrl.applyMove(state, move("b1", "c3")) match
      case Right(s) => assertEquals(s.current.halfMoveClock, 1)
      case Left(e)  => fail(e.reason)

  test("applyMove resets halfMoveClock on pawn move"):
    val state = ctrl.newGame()
    ctrl.applyMove(state, move("e2", "e4")) match
      case Right(s) => assertEquals(s.current.halfMoveClock, 0)
      case Left(e)  => fail(e.reason)

  test("applyMove revokes both white castling rights when king moves"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val state = GameState(Nil, Situation(Board(pieces), Color.White, CastlingRights.all, None, 0, 1))
    ctrl.applyMove(state, move("e1", "d1")) match
      case Right(s) =>
        assertEquals(s.current.castlingRights.whiteKingSide,  false)
        assertEquals(s.current.castlingRights.whiteQueenSide, false)
        assertEquals(s.current.castlingRights.blackKingSide,  true)
      case Left(e) => fail(e.reason)

  test("applyMove revokes white king-side castling right when h1 rook moves"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("h1") -> Piece(Color.White, PieceType.Rook),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val state = GameState(Nil, Situation(Board(pieces), Color.White, CastlingRights.all, None, 0, 1))
    ctrl.applyMove(state, move("h1", "h3")) match
      case Right(s) =>
        assertEquals(s.current.castlingRights.whiteKingSide,  false)
        assertEquals(s.current.castlingRights.whiteQueenSide, true)
      case Left(e) => fail(e.reason)

  test("applyMove revokes white queen-side castling right when a1 rook moves"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("a1") -> Piece(Color.White, PieceType.Rook),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val state = GameState(Nil, Situation(Board(pieces), Color.White, CastlingRights.all, None, 0, 1))
    ctrl.applyMove(state, move("a1", "a3")) match
      case Right(s) =>
        assertEquals(s.current.castlingRights.whiteQueenSide, false)
        assertEquals(s.current.castlingRights.whiteKingSide,  true)
      case Left(e) => fail(e.reason)

  test("applyMove revokes black castling rights when black king moves"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val state = GameState(Nil, Situation(Board(pieces), Color.Black, CastlingRights.all, None, 0, 1))
    ctrl.applyMove(state, move("e8", "d8")) match
      case Right(s) =>
        assertEquals(s.current.castlingRights.blackKingSide,  false)
        assertEquals(s.current.castlingRights.blackQueenSide, false)
      case Left(e) => fail(e.reason)

  test("applyMove sets en passant square after double pawn push"):
    val state = ctrl.newGame()
    ctrl.applyMove(state, move("e2", "e4")) match
      case Right(s) =>
        assertEquals(s.current.enPassantSquare, Square.fromAlgebraic("e3"))
      case Left(e) => fail(e.reason)

  test("applyMove clears en passant square and resets halfMoveClock on en passant capture"):
    val pieces = Map(
      sq("e5") -> Piece(Color.White, PieceType.Pawn),
      sq("d5") -> Piece(Color.Black, PieceType.Pawn),
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val state = GameState(Nil, Situation(Board(pieces), Color.White, CastlingRights.none, Some(sq("d6")), 5, 1))
    val epMove = EnPassantMove(sq("e5"), sq("d6"), sq("d5"))
    ctrl.applyMove(state, epMove) match
      case Right(s) =>
        assertEquals(s.current.enPassantSquare, None)
        assertEquals(s.current.halfMoveClock,    0)
      case Left(e) => fail(e.reason)

  test("applyMove does not change castling rights when non-corner white rook moves"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("b1") -> Piece(Color.White, PieceType.Rook),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val state = GameState(Nil, Situation(Board(pieces), Color.White, CastlingRights.all, None, 0, 1))
    ctrl.applyMove(state, move("b1", "b3")) match
      case Right(s) =>
        assertEquals(s.current.castlingRights.whiteKingSide,  true)
        assertEquals(s.current.castlingRights.whiteQueenSide, true)
      case Left(e) => fail(e.reason)

  test("applyMove revokes black queen-side castling right when a8 rook moves"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King),
      sq("a8") -> Piece(Color.Black, PieceType.Rook)
    )
    val state = GameState(Nil, Situation(Board(pieces), Color.Black, CastlingRights.all, None, 0, 1))
    ctrl.applyMove(state, move("a8", "a6")) match
      case Right(s) =>
        assertEquals(s.current.castlingRights.blackQueenSide, false)
        assertEquals(s.current.castlingRights.blackKingSide,  true)
      case Left(e) => fail(e.reason)

  test("applyMove revokes black king-side castling right when h8 rook moves"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King),
      sq("h8") -> Piece(Color.Black, PieceType.Rook)
    )
    val state = GameState(Nil, Situation(Board(pieces), Color.Black, CastlingRights.all, None, 0, 1))
    ctrl.applyMove(state, move("h8", "h6")) match
      case Right(s) =>
        assertEquals(s.current.castlingRights.blackKingSide,  false)
        assertEquals(s.current.castlingRights.blackQueenSide, true)
      case Left(e) => fail(e.reason)

  test("applyMove does not change castling rights when non-corner black rook moves"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King),
      sq("b8") -> Piece(Color.Black, PieceType.Rook)
    )
    val state = GameState(Nil, Situation(Board(pieces), Color.Black, CastlingRights.all, None, 0, 1))
    ctrl.applyMove(state, move("b8", "b6")) match
      case Right(s) =>
        assertEquals(s.current.castlingRights.blackKingSide,  true)
        assertEquals(s.current.castlingRights.blackQueenSide, true)
      case Left(e) => fail(e.reason)
