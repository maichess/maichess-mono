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
