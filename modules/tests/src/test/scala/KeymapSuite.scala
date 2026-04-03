package org.maichess.mono.tests

import munit.FunSuite
import org.maichess.mono.uifx.{KeyBinding, Keymap}

class KeymapSuite extends FunSuite:

  private val allBindings: List[KeyBinding] = List(
    Keymap.newGame, Keymap.resign, Keymap.undo, Keymap.redo,
    Keymap.importFen, Keymap.exportFen, Keymap.importPgn, Keymap.exportPgn,
    Keymap.pause, Keymap.themeNext
  )

  test("all keys are distinct — no conflicts"):
    val keys = allBindings.map(_.key)
    assertEquals(keys.distinct.length, keys.length)

  test("buttonLabel formats as 'KEY Label' for TUI"):
    assertEquals(Keymap.newGame.buttonLabel,   "N New Game")
    assertEquals(Keymap.resign.buttonLabel,    "R Resign")
    assertEquals(Keymap.undo.buttonLabel,      "Z Undo")
    assertEquals(Keymap.redo.buttonLabel,      "Y Redo")
    assertEquals(Keymap.importFen.buttonLabel, "F Import FEN")
    assertEquals(Keymap.exportFen.buttonLabel, "E Export FEN")
    assertEquals(Keymap.importPgn.buttonLabel, "P Import PGN")
    assertEquals(Keymap.exportPgn.buttonLabel, "O Export PGN")
    assertEquals(Keymap.pause.buttonLabel,     "U Pause")
    assertEquals(Keymap.themeNext.buttonLabel, "T Theme")

  test("tooltipText formats as 'Label [KEY]' for GUI"):
    assertEquals(Keymap.newGame.tooltipText,   "New Game [N]")
    assertEquals(Keymap.resign.tooltipText,    "Resign [R]")
    assertEquals(Keymap.undo.tooltipText,      "Undo [Z]")
    assertEquals(Keymap.redo.tooltipText,      "Redo [Y]")
    assertEquals(Keymap.importFen.tooltipText, "Import FEN [F]")
    assertEquals(Keymap.exportFen.tooltipText, "Export FEN [E]")
    assertEquals(Keymap.importPgn.tooltipText, "Import PGN [P]")
    assertEquals(Keymap.exportPgn.tooltipText, "Export PGN [O]")
    assertEquals(Keymap.pause.tooltipText,     "Pause [U]")
    assertEquals(Keymap.themeNext.tooltipText, "Theme [T]")

  test("key values match TUI shortcuts"):
    assertEquals(Keymap.newGame.key,   'n')
    assertEquals(Keymap.resign.key,    'r')
    assertEquals(Keymap.undo.key,      'z')
    assertEquals(Keymap.redo.key,      'y')
    assertEquals(Keymap.importFen.key, 'f')
    assertEquals(Keymap.exportFen.key, 'e')
    assertEquals(Keymap.importPgn.key, 'p')
    assertEquals(Keymap.exportPgn.key, 'o')
    assertEquals(Keymap.pause.key,     'u')
    assertEquals(Keymap.themeNext.key, 't')

  test("all keys are lowercase"):
    allBindings.foreach(kb => assert(kb.key.isLower, s"${kb.key} is not lowercase"))

  test("buttonLabel prefix is uppercase of key"):
    allBindings.foreach { kb =>
      assert(
        kb.buttonLabel.startsWith(kb.key.toUpper.toString),
        s"${kb.buttonLabel} does not start with ${kb.key.toUpper}"
      )
    }

  test("icon field is non-empty for all bindings"):
    allBindings.foreach(kb => assert(kb.icon.nonEmpty, s"${kb.label} has empty icon"))
