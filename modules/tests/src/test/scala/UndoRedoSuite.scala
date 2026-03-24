package org.maichess.mono.tests

import munit.FunSuite
import org.maichess.mono.model.*
import org.maichess.mono.rules.*
import org.maichess.mono.engine.*

class UndoRedoSuite extends FunSuite:

  val ctrl = GameController(StandardRules)

  def sq(alg: String): Square =
    Square.fromAlgebraic(alg).getOrElse(throw new AssertionError(s"Invalid square: $alg"))
  def move(from: String, to: String): NormalMove = NormalMove(sq(from), sq(to))

  test("undo on new game returns None"):
    assertEquals(ctrl.undo(ctrl.newGame()), None)

  test("redo on new game returns None"):
    assertEquals(ctrl.redo(ctrl.newGame()), None)

  test("undo after one move returns starting position"):
    val start = ctrl.newGame()
    val after = ctrl.applyMove(start, move("e2", "e4")).getOrElse(fail("illegal move"))
    val undone = ctrl.undo(after).getOrElse(fail("undo returned None"))
    assertEquals(undone.current.board, start.current.board)
    assertEquals(undone.history, Nil)

  test("undo places current in future"):
    val start = ctrl.newGame()
    val after  = ctrl.applyMove(start, move("e2", "e4")).getOrElse(fail("illegal move"))
    val undone = ctrl.undo(after).getOrElse(fail("undo returned None"))
    assertEquals(undone.future, List(after.current))

  test("redo after undo restores the moved position"):
    val start  = ctrl.newGame()
    val after  = ctrl.applyMove(start, move("e2", "e4")).getOrElse(fail("illegal move"))
    val undone = ctrl.undo(after).getOrElse(fail("undo returned None"))
    val redone = ctrl.redo(undone).getOrElse(fail("redo returned None"))
    assertEquals(redone.current.board, after.current.board)
    assertEquals(redone.future, Nil)

  test("redo on state with no future returns None"):
    val start = ctrl.newGame()
    val after = ctrl.applyMove(start, move("e2", "e4")).getOrElse(fail("illegal move"))
    assertEquals(ctrl.redo(after), None)

  test("new move after undo clears the redo future"):
    val start  = ctrl.newGame()
    val after  = ctrl.applyMove(start, move("e2", "e4")).getOrElse(fail("illegal move"))
    val undone = ctrl.undo(after).getOrElse(fail("undo returned None"))
    val branch = ctrl.applyMove(undone, move("d2", "d4")).getOrElse(fail("illegal move"))
    assertEquals(branch.future, Nil)

  test("multiple undos walk back through history"):
    val start  = ctrl.newGame()
    val s1     = ctrl.applyMove(start,  move("e2", "e4")).getOrElse(fail("move 1"))
    val s2     = ctrl.applyMove(s1,     move("e7", "e5")).getOrElse(fail("move 2"))
    val s3     = ctrl.applyMove(s2,     move("d2", "d4")).getOrElse(fail("move 3"))
    val u1     = ctrl.undo(s3).getOrElse(fail("undo 1"))
    val u2     = ctrl.undo(u1).getOrElse(fail("undo 2"))
    val u3     = ctrl.undo(u2).getOrElse(fail("undo 3"))
    assertEquals(u3.current.board, start.current.board)
    assertEquals(u3.future.length, 3)

  test("undo then redo is identity for history"):
    val start  = ctrl.newGame()
    val s1     = ctrl.applyMove(start, move("e2", "e4")).getOrElse(fail("move 1"))
    val s2     = ctrl.applyMove(s1,    move("e7", "e5")).getOrElse(fail("move 2"))
    val u1     = ctrl.undo(s2).getOrElse(fail("undo"))
    val r1     = ctrl.redo(u1).getOrElse(fail("redo"))
    assertEquals(r1.current.board,   s2.current.board)
    assertEquals(r1.history.length,  s2.history.length)
    assertEquals(r1.future,          Nil)
