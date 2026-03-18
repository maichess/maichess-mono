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
    val finalState = moves.foldLeft(Right(ctrl.newGame()): Either[IllegalMove, GameState]) {
      case (Right(state), m) => ctrl.applyMove(state, m)
      case (left, _)         => left
    }
    finalState match
      case Right(state) =>
        assertEquals(ctrl.gameResult(state), Some(GameResult.Checkmate(Color.White)))
      case Left(err) =>
        fail(s"Unexpected illegal move: $err")

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
