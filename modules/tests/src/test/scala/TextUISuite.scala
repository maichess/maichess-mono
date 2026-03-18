package org.maichess.mono.tests

import munit.FunSuite
import org.maichess.mono.model.*
import org.maichess.mono.ui.{CursorState, Direction, TextUI}

class TextUISuite extends FunSuite:

  private def sq(s: String): Square  = Square.fromAlgebraic(s).get
  private def mv(f: String, t: String): Move = NormalMove(sq(f), sq(t))

  // ── cursorSquare ────────────────────────────────────────────────────────────

  test("cursorSquare returns cursor when Navigating"):
    assertEquals(TextUI.cursorSquare(CursorState.Navigating(sq("e2"))), sq("e2"))

  test("cursorSquare returns current target square when PieceSelected"):
    val targets = IndexedSeq(mv("e2", "e4"), mv("e2", "e3"))
    assertEquals(
      TextUI.cursorSquare(CursorState.PieceSelected(sq("e2"), 1, targets)),
      sq("e3")
    )

  // ── selectedSquare ──────────────────────────────────────────────────────────

  test("selectedSquare is None when Navigating"):
    assertEquals(TextUI.selectedSquare(CursorState.Navigating(sq("e2"))), None)

  test("selectedSquare is Some(from) when PieceSelected"):
    val targets = IndexedSeq(mv("e2", "e4"))
    assertEquals(
      TextUI.selectedSquare(CursorState.PieceSelected(sq("e2"), 0, targets)),
      Some(sq("e2"))
    )

  // ── targetSquares ───────────────────────────────────────────────────────────

  test("targetSquares is empty when Navigating"):
    assertEquals(TextUI.targetSquares(CursorState.Navigating(sq("e2"))), Set.empty[Square])

  test("targetSquares returns all target squares when PieceSelected"):
    val targets = IndexedSeq(mv("e2", "e4"), mv("e2", "e3"))
    assertEquals(
      TextUI.targetSquares(CursorState.PieceSelected(sq("e2"), 0, targets)),
      Set(sq("e4"), sq("e3"))
    )

  // ── moveCursorFree ──────────────────────────────────────────────────────────

  test("moveCursorFree Up increases rank"):
    assertEquals(TextUI.moveCursorFree(sq("e2"), Direction.Up), sq("e3"))

  test("moveCursorFree Down decreases rank"):
    assertEquals(TextUI.moveCursorFree(sq("e3"), Direction.Down), sq("e2"))

  test("moveCursorFree Right increases file"):
    assertEquals(TextUI.moveCursorFree(sq("e2"), Direction.Right), sq("f2"))

  test("moveCursorFree Left decreases file"):
    assertEquals(TextUI.moveCursorFree(sq("e2"), Direction.Left), sq("d2"))

  test("moveCursorFree Up wraps from rank 8 to rank 1"):
    assertEquals(TextUI.moveCursorFree(sq("e8"), Direction.Up), sq("e1"))

  test("moveCursorFree Down wraps from rank 1 to rank 8"):
    assertEquals(TextUI.moveCursorFree(sq("e1"), Direction.Down), sq("e8"))

  test("moveCursorFree Right wraps from h-file to a-file"):
    assertEquals(TextUI.moveCursorFree(sq("h4"), Direction.Right), sq("a4"))

  test("moveCursorFree Left wraps from a-file to h-file"):
    assertEquals(TextUI.moveCursorFree(sq("a4"), Direction.Left), sq("h4"))

  // ── moveCursorTargets ───────────────────────────────────────────────────────

  test("moveCursorTargets Down advances index"):
    val targets = IndexedSeq(mv("e2","e3"), mv("e2","e4"))
    assertEquals(TextUI.moveCursorTargets(targets, 0, Direction.Down), 1)

  test("moveCursorTargets Up retreats index"):
    val targets = IndexedSeq(mv("e2","e3"), mv("e2","e4"))
    assertEquals(TextUI.moveCursorTargets(targets, 1, Direction.Up), 0)

  test("moveCursorTargets Down wraps from last to first"):
    val targets = IndexedSeq(mv("e2","e3"), mv("e2","e4"))
    assertEquals(TextUI.moveCursorTargets(targets, 1, Direction.Down), 0)

  test("moveCursorTargets Up wraps from first to last"):
    val targets = IndexedSeq(mv("e2","e3"), mv("e2","e4"))
    assertEquals(TextUI.moveCursorTargets(targets, 0, Direction.Up), 1)

  test("moveCursorTargets Right advances index"):
    val targets = IndexedSeq(mv("e2","e3"), mv("e2","e4"))
    assertEquals(TextUI.moveCursorTargets(targets, 0, Direction.Right), 1)

  test("moveCursorTargets Left retreats index"):
    val targets = IndexedSeq(mv("e2","e3"), mv("e2","e4"))
    assertEquals(TextUI.moveCursorTargets(targets, 1, Direction.Left), 0)
