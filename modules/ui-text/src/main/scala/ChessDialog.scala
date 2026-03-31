package org.maichess.mono.ui

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.*
import java.awt.Toolkit
import java.awt.datatransfer.{DataFlavor, StringSelection}
import java.util.concurrent.atomic.AtomicReference
import org.maichess.mono.bots.{Bot, BotRegistry}

object ChessDialog:

  /** Displays a read-only text value with a copy-to-clipboard button. */
  def showExport(gui: WindowBasedTextGUI, title: String, content: String): Unit =
    val panel  = new Panel(new LinearLayout(Direction.VERTICAL))
    val _ = panel.addComponent(new Label(content))

    val window = new BasicWindow(title)
    window.setHints(java.util.Arrays.asList(Window.Hint.CENTERED))

    val buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL))
    val _ = buttonPanel.addComponent(new Button("Copy to Clipboard", new Runnable:
      def run(): Unit =
        Toolkit.getDefaultToolkit.getSystemClipboard
          .setContents(new StringSelection(content), (_, _) => ())
    ))
    val _ = buttonPanel.addComponent(new Button("Close", new Runnable:
      def run(): Unit = window.close()
    ))
    val _ = panel.addComponent(buttonPanel)
    window.setComponent(panel)
    gui.addWindowAndWait(window)

  /** Shows a text-input dialog with a paste-from-clipboard button; returns entered string or None. */
  def showImport(gui: WindowBasedTextGUI, title: String, prompt: String): Option[String] =
    val result  = new AtomicReference[Option[String]](None)
    val textBox = new TextBox(new TerminalSize(60, 5), TextBox.Style.MULTI_LINE)
    val panel   = new Panel(new LinearLayout(Direction.VERTICAL))
    val _ = panel.addComponent(new Label(prompt))
    val _ = panel.addComponent(textBox)

    val window = new BasicWindow(title)
    window.setHints(java.util.Arrays.asList(Window.Hint.CENTERED))

    val buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL))
    val _ = buttonPanel.addComponent(new Button("Paste from Clipboard", new Runnable:
      def run(): Unit =
        try
          Toolkit.getDefaultToolkit.getSystemClipboard
            .getData(DataFlavor.stringFlavor) match
            case s: String => textBox.setText(s)
            case _         => ()
        catch case _: Exception => ()
    ))
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

  /** Shows a bot-selection dialog; returns None for Human vs Human or Some(bot) for a bot opponent. */
  def showBotSelect(gui: WindowBasedTextGUI): Option[Bot] =
    val result = new AtomicReference[Option[Bot]](None)
    val panel  = new Panel(new LinearLayout(Direction.VERTICAL))
    val _ = panel.addComponent(new Label("Choose opponent:"))

    val window = new BasicWindow("New Game")
    window.setHints(java.util.Arrays.asList(Window.Hint.CENTERED))

    val buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL))
    val _ = buttonPanel.addComponent(new Button("Human vs Human", new Runnable:
      def run(): Unit = window.close()
    ))
    def addBotButton(bot: Bot): Unit =
      val label = "vs " + bot.name
      val _ = buttonPanel.addComponent(new Button(label, new Runnable:
        def run(): Unit =
          result.set(Some(bot))
          window.close()
      ))
    BotRegistry.all.foreach(addBotButton)
    val _ = panel.addComponent(buttonPanel)
    window.setComponent(panel)
    gui.addWindowAndWait(window)
    result.get()
