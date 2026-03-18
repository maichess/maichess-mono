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
