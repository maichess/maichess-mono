package chess

import chess.engine.{GameController, GameResult}
import chess.model.*
import chess.rules.StandardRules
import munit.FunSuite

class GameControllerSuite extends FunSuite:

  private val controller = GameController(StandardRules)
  private def sq(s: String): Square = Square.fromAlgebraic(s).get

  // ── Scholar's Mate ─────────────────────────────────────────────────────────

  test("Scholar's Mate results in checkmate after 4 moves"):
    // 1. e4  e5
    // 2. Qh5 Nc6
    // 3. Bc4 Nf6??
    // 4. Qxf7#
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
      controller.applyMove(state, move) match
        case Right(next) => next
        case Left(err)   => fail(s"Unexpected illegal move: ${err.move} — ${err.reason}")

    controller.gameResult(finalState) match
      case Some(GameResult.Checkmate(Color.White)) => () // expected
      case other => fail(s"Expected White checkmate, got: $other")

  // ── Illegal move rejection ─────────────────────────────────────────────────

  test("Playing an illegal move returns Left and leaves state unchanged"):
    val state        = controller.newGame()
    val illegalMove  = NormalMove(sq("e2"), sq("e5")) // pawn can't jump 3 squares
    val result       = controller.applyMove(state, illegalMove)
    assert(result.isLeft, "illegal move should return Left")
    // State is unchanged — verify by playing a legal move from the original state still works
    val legalResult  = controller.applyMove(state, NormalMove(sq("e2"), sq("e4")))
    assert(legalResult.isRight, "legal move on original state should succeed")

  test("Playing a move for the wrong color is illegal"):
    val state  = controller.newGame()
    val result = controller.applyMove(state, NormalMove(sq("e7"), sq("e5"))) // black's pawn, white's turn
    assert(result.isLeft)

  // ── Game result detection ──────────────────────────────────────────────────

  test("gameResult returns None at start of game"):
    assertEquals(controller.gameResult(controller.newGame()), None)

  test("gameResult returns None after one legal move"):
    val state = controller.applyMove(controller.newGame(), NormalMove(sq("e2"), sq("e4"))).getOrElse(fail(""))
    assertEquals(controller.gameResult(state), None)

  // ── Move sequence validity ─────────────────────────────────────────────────

  test("All standard opening moves are accepted by the controller"):
    val moves = List(
      NormalMove(sq("e2"), sq("e4")),
      NormalMove(sq("e7"), sq("e5")),
      NormalMove(sq("g1"), sq("f3")),
      NormalMove(sq("b8"), sq("c6")),
    )
    moves.foldLeft(controller.newGame()): (state, move) =>
      controller.applyMove(state, move) match
        case Right(next) => next
        case Left(err)   => fail(s"Legal move rejected: ${err.move} — ${err.reason}")
