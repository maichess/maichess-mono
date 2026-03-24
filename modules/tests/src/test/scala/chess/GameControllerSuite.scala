package chess

import chess.engine.{DrawReason, GameController, GameResult, GameState}
import chess.model.*
import chess.rules.{Board, CastlingRights, Situation, StandardRules}
import munit.FunSuite

class GameControllerSuite extends FunSuite:

  private val controller = GameController(StandardRules)
  private def sq(s: String): Square = Square.fromAlgebraic(s).get

  // ── Scholar's Mate ─────────────────────────────────────────────────────────

  test("Scholar's Mate results in checkmate after 7 half-moves"):
    val moves = List(
      NormalMove(sq("e2"), sq("e4")),
      NormalMove(sq("e7"), sq("e5")),
      NormalMove(sq("d1"), sq("h5")),
      NormalMove(sq("b8"), sq("c6")),
      NormalMove(sq("f1"), sq("c4")),
      NormalMove(sq("g8"), sq("f6")),
      NormalMove(sq("h5"), sq("f7")),
    )

    val finalState = moves.foldLeft(controller.newGame()): (state, move) =>
      controller.applyMove(state, move).getOrElse(fail(s"Unexpected illegal move: $move"))

    controller.gameResult(finalState) match
      case Some(GameResult.Checkmate(Color.White)) => ()
      case other                                   => fail(s"Expected White checkmate, got: $other")

  // ── Illegal move rejection ─────────────────────────────────────────────────

  test("Playing an illegal move returns Left and leaves state unchanged"):
    val state  = controller.newGame()
    assert(controller.applyMove(state, NormalMove(sq("e2"), sq("e5"))).isLeft)
    assert(controller.applyMove(state, NormalMove(sq("e2"), sq("e4"))).isRight)

  test("Playing a move for the wrong color is rejected"):
    val state = controller.newGame()
    assert(controller.applyMove(state, NormalMove(sq("e7"), sq("e5"))).isLeft)

  // ── Draw results ───────────────────────────────────────────────────────────

  test("gameResult returns Stalemate for stalemate position"):
    val board = Board(Map(
      sq("a8") -> Piece(Color.Black, PieceType.King),
      sq("b6") -> Piece(Color.White, PieceType.Queen),
      sq("c7") -> Piece(Color.White, PieceType.King),
    ))
    val state = GameState(Nil, Situation(board, Color.Black, CastlingRights.none, None, 0, 1))
    assertEquals(controller.gameResult(state), Some(GameResult.Stalemate))

  test("gameResult returns Draw(FiftyMoveRule) at 100 half-moves"):
    val sit   = Situation(Board.standard, Color.White, CastlingRights.all, None, 100, 50)
    val state = GameState(Nil, sit)
    assertEquals(controller.gameResult(state), Some(GameResult.Draw(DrawReason.FiftyMoveRule)))

  test("gameResult returns Draw(InsufficientMaterial) for K vs K"):
    val board = Board(Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King),
    ))
    val state = GameState(Nil, Situation(board, Color.White, CastlingRights.none, None, 0, 1))
    assertEquals(controller.gameResult(state), Some(GameResult.Draw(DrawReason.InsufficientMaterial)))

  test("gameResult returns Draw(ThreefoldRepetition) after three repetitions"):
    // 1.Nf3 Nf6 2.Ng1 Ng8 — three times returns to start
    val moves = List(
      NormalMove(sq("g1"), sq("f3")), NormalMove(sq("g8"), sq("f6")),
      NormalMove(sq("f3"), sq("g1")), NormalMove(sq("f6"), sq("g8")),
      NormalMove(sq("g1"), sq("f3")), NormalMove(sq("g8"), sq("f6")),
      NormalMove(sq("f3"), sq("g1")), NormalMove(sq("f6"), sq("g8")),
    )
    val finalState = moves.foldLeft(controller.newGame()): (state, move) =>
      controller.applyMove(state, move).getOrElse(fail(s"Move $move rejected"))
    assertEquals(controller.gameResult(finalState), Some(GameResult.Draw(DrawReason.ThreefoldRepetition)))

  // ── Basic game result checks ───────────────────────────────────────────────

  test("gameResult returns None at start of game"):
    assertEquals(controller.gameResult(controller.newGame()), None)

  test("gameResult returns None after one legal move"):
    val state = controller.applyMove(controller.newGame(), NormalMove(sq("e2"), sq("e4"))).getOrElse(fail(""))
    assertEquals(controller.gameResult(state), None)

  // ── Move sequence validity ─────────────────────────────────────────────────

  test("All standard opening moves are accepted"):
    val moves = List(
      NormalMove(sq("e2"), sq("e4")),
      NormalMove(sq("e7"), sq("e5")),
      NormalMove(sq("g1"), sq("f3")),
      NormalMove(sq("b8"), sq("c6")),
    )
    moves.foldLeft(controller.newGame()): (state, move) =>
      controller.applyMove(state, move).getOrElse(fail(s"Legal move rejected: $move"))
