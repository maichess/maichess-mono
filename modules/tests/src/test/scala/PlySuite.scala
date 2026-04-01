package org.maichess.mono.tests

import munit.FunSuite
import org.maichess.mono.model.*
import org.maichess.mono.rules.*
import org.maichess.mono.engine.*

class PlySuite extends FunSuite:

  val ctrl = GameController(StandardRules)

  def sq(alg: String): Square =
    Square.fromAlgebraic(alg).getOrElse(throw new AssertionError(s"Invalid square: $alg"))
  def move(from: String, to: String): NormalMove = NormalMove(sq(from), sq(to))

  // ── Ply primitives ────────────────────────────────────────────────────────

  test("pure wraps a value without touching game state"):
    val initial = ctrl.newGame()
    Ply.pure(42).run(initial) match
      case Ply.Step.Active(v, s) =>
        assertEquals(v, 42)
        assertEquals(s, initial)
      case Ply.Step.Terminal(_, _) => fail("Expected Active")

  test("get reads the current game state as the result value"):
    val initial = ctrl.newGame()
    Ply.get.run(initial) match
      case Ply.Step.Active(v, s) =>
        assertEquals(v, initial)
        assertEquals(s, initial)
      case Ply.Step.Terminal(_, _) => fail("Expected Active")

  test("set replaces the current game state"):
    val initial  = ctrl.newGame()
    val modified = ctrl.applyMove(initial, move("e2", "e4")).getOrElse(fail("illegal"))
    Ply.set(modified).run(initial) match
      case Ply.Step.Active(_, s) => assertEquals(s, modified)
      case Ply.Step.Terminal(_, _) => fail("Expected Active")

  test("end terminates with the given result, carrying state"):
    val initial = ctrl.newGame()
    Ply.end(GameResult.Stalemate).run(initial) match
      case Ply.Step.Terminal(r, s) =>
        assertEquals(r, GameResult.Stalemate)
        assertEquals(s, initial)
      case Ply.Step.Active(_, _) => fail("Expected Terminal")

  // ── finalState ────────────────────────────────────────────────────────────

  test("finalState returns inner state from an Active step"):
    val initial  = ctrl.newGame()
    val modified = ctrl.applyMove(initial, move("e2", "e4")).getOrElse(fail("illegal"))
    val state    = Ply.set(modified).finalState(initial)
    assertEquals(state, modified)

  test("finalState returns inner state from a Terminal step"):
    val initial = ctrl.newGame()
    val state   = Ply.end(GameResult.Stalemate).finalState(initial)
    assertEquals(state, initial)

  // ── map ───────────────────────────────────────────────────────────────────

  test("map transforms the value of an Active step"):
    val initial = ctrl.newGame()
    Ply.pure(3).map(_ * 2).run(initial) match
      case Ply.Step.Active(v, _) => assertEquals(v, 6)
      case Ply.Step.Terminal(_, _) => fail("Expected Active")

  test("map passes Terminal through unchanged"):
    val initial = ctrl.newGame()
    Ply.end[Int](GameResult.Stalemate).map(_ + 1).run(initial) match
      case Ply.Step.Terminal(r, s) =>
        assertEquals(r, GameResult.Stalemate)
        assertEquals(s, initial)
      case Ply.Step.Active(_, _) => fail("Expected Terminal")

  // ── flatMap ───────────────────────────────────────────────────────────────

  test("flatMap sequences two Active computations"):
    val initial = ctrl.newGame()
    val ply = Ply.pure(1).flatMap(n => Ply.pure(n + 1))
    ply.run(initial) match
      case Ply.Step.Active(v, _) => assertEquals(v, 2)
      case Ply.Step.Terminal(_, _) => fail("Expected Active")

  test("flatMap short-circuits when the first step is Terminal"):
    val initial  = ctrl.newGame()
    var sideEffect = false
    val ply = Ply.end[Unit](GameResult.Stalemate).flatMap { _ =>
      sideEffect = true
      Ply.pure(())
    }
    ply.run(initial) match
      case Ply.Step.Terminal(r, _) =>
        assertEquals(r, GameResult.Stalemate)
        assert(!sideEffect, "flatMap must not evaluate f after Terminal")
      case Ply.Step.Active(_, _) => fail("Expected Terminal")

  // ── GameController.step ───────────────────────────────────────────────────

  test("step returns Active for a non-terminal position"):
    val initial = ctrl.newGame()
    ctrl.step(move("e2", "e4")).run(initial) match
      case Ply.Step.Active(_, s) =>
        assertEquals(s.current.turn, Color.Black)
        assertEquals(s.history.length, 1)
      case Ply.Step.Terminal(_, _) => fail("Expected Active")

  test("step returns Terminal after checkmate (Scholar's Mate final move)"):
    // Position after 1.e4 e5 2.Qh5 Nc6 3.Bc4 Nf6, White plays Qxf7#
    val moves = List(
      move("e2", "e4"), move("e7", "e5"),
      move("d1", "h5"), move("b8", "c6"),
      move("f1", "c4"), move("g8", "f6")
    )
    val preCheckmate = moves.foldLeft(ctrl.newGame()) { (s, m) =>
      ctrl.applyMove(s, m).getOrElse(fail(s"illegal: $m"))
    }
    ctrl.step(move("h5", "f7")).run(preCheckmate) match
      case Ply.Step.Terminal(r, _) =>
        assertEquals(r, GameResult.Checkmate(Color.White))
      case Ply.Step.Active(_, _) => fail("Expected Terminal for checkmate")

  // ── GameController.replay ─────────────────────────────────────────────────

  test("replay with an empty move list returns unchanged state"):
    val initial = ctrl.newGame()
    val state   = ctrl.replay(Nil).finalState(initial)
    assertEquals(state, initial)

  test("replay builds history for a sequence of moves"):
    val moves = List(move("e2", "e4"), move("e7", "e5"), move("d2", "d4"))
    val state = ctrl.replay(moves).finalState(ctrl.newGame())
    assertEquals(state.history.length, 3)
    assertEquals(state.current.turn, Color.Black)

  test("replay stops at checkmate and carries the terminal state"):
    val moves = List(
      move("e2", "e4"), move("e7", "e5"),
      move("d1", "h5"), move("b8", "c6"),
      move("f1", "c4"), move("g8", "f6"),
      move("h5", "f7")   // Qxf7# — checkmate
    )
    ctrl.replay(moves).run(ctrl.newGame()) match
      case Ply.Step.Terminal(r, s) =>
        assertEquals(r, GameResult.Checkmate(Color.White))
        assertEquals(ctrl.gameResult(s), Some(GameResult.Checkmate(Color.White)))
      case Ply.Step.Active(_, _) => fail("Expected Terminal after checkmate")
