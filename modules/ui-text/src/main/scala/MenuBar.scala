package org.maichess.mono.ui

import com.googlecode.lanterna.gui2.{Button, GridLayout, Panel}
import org.maichess.mono.uifx.Keymap

class MenuBar(
  onNewGame:   () => Unit,
  onResign:    () => Unit,
  onUndo:      () => Unit,
  onRedo:      () => Unit,
  onImportFen: () => Unit,
  onExportFen: () => Unit,
  onImportPgn: () => Unit,
  onExportPgn: () => Unit,
) extends Panel(new GridLayout(4)):

  private def btn(label: String, action: () => Unit): Button =
    new Button(label, new Runnable:
      def run(): Unit = action()
    )

  val _ = addComponent(btn(Keymap.newGame.buttonLabel,   onNewGame))
  val _ = addComponent(btn(Keymap.resign.buttonLabel,    onResign))
  val _ = addComponent(btn(Keymap.undo.buttonLabel,      onUndo))
  val _ = addComponent(btn(Keymap.redo.buttonLabel,      onRedo))
  val _ = addComponent(btn(Keymap.importFen.buttonLabel, onImportFen))
  val _ = addComponent(btn(Keymap.exportFen.buttonLabel, onExportFen))
  val _ = addComponent(btn(Keymap.importPgn.buttonLabel, onImportPgn))
  val _ = addComponent(btn(Keymap.exportPgn.buttonLabel, onExportPgn))
