package org.maichess.mono.ui

import com.googlecode.lanterna.gui2.{Button, GridLayout, Panel}

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

  val _ = addComponent(btn("N New",    onNewGame))
  val _ = addComponent(btn("R Resign", onResign))
  val _ = addComponent(btn("Z Undo",   onUndo))
  val _ = addComponent(btn("Y Redo",   onRedo))
  val _ = addComponent(btn("F Imp FEN", onImportFen))
  val _ = addComponent(btn("E Exp FEN", onExportFen))
  val _ = addComponent(btn("P Imp PGN", onImportPgn))
  val _ = addComponent(btn("O Exp PGN", onExportPgn))
