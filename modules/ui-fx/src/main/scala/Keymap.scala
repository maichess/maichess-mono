package org.maichess.mono.uifx

case class KeyBinding(key: Char, label: String):
  def buttonLabel: String = key.toUpper.toString + " " + label

object Keymap:
  val newGame:   KeyBinding = KeyBinding('n', "New")
  val resign:    KeyBinding = KeyBinding('r', "Resign")
  val undo:      KeyBinding = KeyBinding('z', "Undo")
  val redo:      KeyBinding = KeyBinding('y', "Redo")
  val importFen: KeyBinding = KeyBinding('f', "Import FEN")
  val exportFen: KeyBinding = KeyBinding('e', "Export FEN")
  val importPgn: KeyBinding = KeyBinding('p', "Import PGN")
  val exportPgn: KeyBinding = KeyBinding('o', "Export PGN")
