package org.maichess.mono.ui

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton
import java.util.concurrent.atomic.AtomicReference

object ChessDialog:

  /** Displays a read-only text value the user can copy. */
  def showExport(gui: WindowBasedTextGUI, title: String, content: String): Unit =
    val _ = new MessageDialogBuilder()
      .setTitle(title)
      .setText(content)
      .addButton(MessageDialogButton.Close)
      .build()
      .showDialog(gui)

  /** Shows a text-input dialog and returns the entered string, or None if cancelled. */
  def showImport(gui: WindowBasedTextGUI, title: String, prompt: String): Option[String] =
    val result  = new AtomicReference[Option[String]](None)
    val textBox = new TextBox(new TerminalSize(60, 5), TextBox.Style.MULTI_LINE)
    val panel   = new Panel(new LinearLayout(Direction.VERTICAL))
    val _ = panel.addComponent(new Label(prompt))
    val _ = panel.addComponent(textBox)

    val window = new BasicWindow(title)
    window.setHints(java.util.Arrays.asList(Window.Hint.CENTERED))

    val buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL))
    val _ = buttonPanel.addComponent(new Button("OK", new Runnable:
      def run(): Unit =
        result.set(Some(textBox.getText))
        window.close()
    ))
    val _ = buttonPanel.addComponent(new Button("Cancel", new Runnable:
      def run(): Unit = window.close()
    ))
    val _ = panel.addComponent(buttonPanel)
    window.setComponent(panel)
    gui.addWindowAndWait(window)
    result.get()
