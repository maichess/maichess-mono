package org.maichess.mono.uifx

case class KeyBinding(key: Char, label: String, icon: String):
  /** Used by the TUI menu bar — keeps the "KEY Label" format. */
  def buttonLabel: String = key.toUpper.toString + " " + label
  /** Used as tooltip text in the JavaFX toolbar buttons. */
  def tooltipText: String = label + " [" + key.toUpper + "]"

object Keymap:
  val newGame:   KeyBinding = KeyBinding('n', "New Game",   "\u2795")   // ➕
  val resign:    KeyBinding = KeyBinding('r', "Resign",     "\u2691")   // ⚑
  val undo:      KeyBinding = KeyBinding('z', "Undo",       "\u21a9")   // ↩
  val redo:      KeyBinding = KeyBinding('y', "Redo",       "\u21aa")   // ↪
  val importFen: KeyBinding = KeyBinding('f', "Import FEN", "\u2913")   // ⤓
  val exportFen: KeyBinding = KeyBinding('e', "Export FEN", "\u2912")   // ⤒
  val importPgn: KeyBinding = KeyBinding('p', "Import PGN", "\u2913")   // ⤓
  val exportPgn: KeyBinding = KeyBinding('o', "Export PGN", "\u2912")   // ⤒
  val pause:     KeyBinding = KeyBinding('u', "Pause",      "\u23f8")   // ⏸
  val themeNext: KeyBinding = KeyBinding('t', "Theme",      "\u25d1")   // ◑
