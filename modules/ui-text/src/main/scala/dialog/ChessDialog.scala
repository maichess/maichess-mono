package org.maichess.mono.ui

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.*
import java.awt.Toolkit
import java.awt.datatransfer.{DataFlavor, StringSelection}
import java.util.concurrent.atomic.AtomicReference
import org.maichess.mono.bots.{Bot, BotRegistry}
import org.maichess.mono.uifx.{ClockConfig, NewGameSetup}

object ChessDialog:

  private val clockPresets: List[(String, Option[ClockConfig])] = List(
    "Unlimited"      -> None,
    "Bullet 1+0"     -> Some(ClockConfig(60_000L,    0L)),
    "Bullet 2+1"     -> Some(ClockConfig(120_000L,   1_000L)),
    "Blitz 3+2"      -> Some(ClockConfig(180_000L,   2_000L)),
    "Blitz 5+0"      -> Some(ClockConfig(300_000L,   0L)),
    "Rapid 10+0"     -> Some(ClockConfig(600_000L,   0L)),
    "Rapid 15+10"    -> Some(ClockConfig(900_000L,   10_000L)),
    "Classical 30+0" -> Some(ClockConfig(1_800_000L, 0L)),
    "Custom..."      -> None
  )

  /** Displays a read-only text value with a copy-to-clipboard button. */
  def showExport(gui: WindowBasedTextGUI, title: String, content: String): Unit =
    val panel  = new Panel(new LinearLayout(Direction.VERTICAL))
    val _      = panel.addComponent(new Label(content))
    val window = centeredWindow(title)
    val btnRow = new Panel(new LinearLayout(Direction.HORIZONTAL))
    val _ = btnRow.addComponent(new Button("Copy to Clipboard", new Runnable:
      def run(): Unit =
        Toolkit.getDefaultToolkit.getSystemClipboard
          .setContents(new StringSelection(content), (_, _) => ())
    ))
    val _ = btnRow.addComponent(new Button("Close", new Runnable:
      def run(): Unit = window.close()
    ))
    val _ = panel.addComponent(btnRow)
    window.setComponent(panel)
    gui.addWindowAndWait(window)

  /** Shows a text-input dialog with a paste-from-clipboard button; returns entered string or None. */
  def showImport(gui: WindowBasedTextGUI, title: String, prompt: String): Option[String] =
    val result  = new AtomicReference[Option[String]](None)
    val textBox = new TextBox(new TerminalSize(60, 5), TextBox.Style.MULTI_LINE)
    val panel   = new Panel(new LinearLayout(Direction.VERTICAL))
    val _       = panel.addComponent(new Label(prompt))
    val _       = panel.addComponent(textBox)
    val window  = centeredWindow(title)
    val btnRow  = new Panel(new LinearLayout(Direction.HORIZONTAL))
    val _ = btnRow.addComponent(new Button("Paste from Clipboard", new Runnable:
      def run(): Unit =
        try
          Toolkit.getDefaultToolkit.getSystemClipboard
            .getData(DataFlavor.stringFlavor) match
            case s: String => textBox.setText(s)
            case _         => ()
        catch case _: Exception => ()
    ))
    val _ = btnRow.addComponent(new Button("OK", new Runnable:
      def run(): Unit =
        result.set(Some(textBox.getText))
        window.close()
    ))
    val _ = btnRow.addComponent(new Button("Cancel", new Runnable:
      def run(): Unit = window.close()
    ))
    val _ = panel.addComponent(btnRow)
    window.setComponent(panel)
    gui.addWindowAndWait(window)
    result.get()

  /** Single combined dialog: player names, bot selection, and time control.
   *  Returns None if cancelled. */
  def showGameSetup(gui: WindowBasedTextGUI): Option[NewGameSetup] =
    val result = new AtomicReference[Option[NewGameSetup]](None)
    val panel  = new Panel(new LinearLayout(Direction.VERTICAL))
    val window = centeredWindow("New Game")

    // ── Player setup rows ──────────────────────────────────────────────
    val whiteNameBox = new TextBox(new TerminalSize(20, 1))
    val blackNameBox = new TextBox(new TerminalSize(20, 1))
    whiteNameBox.setText("White")
    blackNameBox.setText("Black")

    val whiteCombo = makePlayerCombo()
    val blackCombo = makePlayerCombo()

    val _ = panel.addComponent(new Label("─── White Player ───────────────────"))
    val _ = panel.addComponent(labeledRow("Name:", whiteNameBox))
    val _ = panel.addComponent(labeledRow("Type:", whiteCombo))
    val _ = panel.addComponent(new Label("─── Black Player ───────────────────"))
    val _ = panel.addComponent(labeledRow("Name:", blackNameBox))
    val _ = panel.addComponent(labeledRow("Type:", blackCombo))

    // ── Clock preset ───────────────────────────────────────────────────
    val clockCombo = new ComboBox[String]()
    clockPresets.foreach { case (label, _) => clockCombo.addItem(label) }

    val customMinsBox = new TextBox(new TerminalSize(4, 1))
    val customSecsBox = new TextBox(new TerminalSize(4, 1))
    val customIncBox  = new TextBox(new TerminalSize(4, 1))
    customMinsBox.setText("5")
    customSecsBox.setText("0")
    customIncBox.setText("0")

    val _ = panel.addComponent(new Label("─── Time Control ───────────────────"))
    val _ = panel.addComponent(labeledRow("Preset:", clockCombo))
    val _ = panel.addComponent(new Label("  Custom → Min / Sec / +Inc(sec):"))
    val customRow = new Panel(new LinearLayout(Direction.HORIZONTAL))
    val _ = customRow.addComponent(customMinsBox)
    val _ = customRow.addComponent(new Label("/"))
    val _ = customRow.addComponent(customSecsBox)
    val _ = customRow.addComponent(new Label("+"))
    val _ = customRow.addComponent(customIncBox)
    val _ = panel.addComponent(customRow)

    // ── Buttons ────────────────────────────────────────────────────────
    val btnRow = new Panel(new LinearLayout(Direction.HORIZONTAL))
    val _ = btnRow.addComponent(new Button("Start", new Runnable:
      def run(): Unit =
        val whiteName  = trimOrDefault(whiteNameBox.getText, "White")
        val blackName  = trimOrDefault(blackNameBox.getText, "Black")
        val whiteBot   = selectedBot(whiteCombo)
        val blackBot   = selectedBot(blackCombo)
        val clockCfg   = resolveClockConfig(
          clockCombo.getSelectedItem,
          customMinsBox.getText,
          customSecsBox.getText,
          customIncBox.getText
        )
        result.set(Some(NewGameSetup(whiteName, blackName, whiteBot, blackBot, clockCfg)))
        window.close()
    ))
    val _ = btnRow.addComponent(new Button("Cancel", new Runnable:
      def run(): Unit = window.close()
    ))
    val _ = panel.addComponent(btnRow)

    window.setComponent(panel)
    gui.addWindowAndWait(window)
    result.get()

  private def makePlayerCombo(): ComboBox[String] =
    val combo = new ComboBox[String]()
    combo.addItem("Human")
    BotRegistry.all.foreach(b => combo.addItem(b.name))
    combo

  private def selectedBot(combo: ComboBox[String]): Option[Bot] =
    BotRegistry.all.find(_.name == combo.getSelectedItem)

  private def resolveClockConfig(
    preset:  String,
    minsStr: String,
    secsStr: String,
    incStr:  String
  ): Option[ClockConfig] =
    preset match
      case "Custom..." =>
        val ms    = (parseLong(minsStr) * 60L + parseLong(secsStr)) * 1000L
        val incMs = parseLong(incStr) * 1000L
        if ms <= 0L then None else Some(ClockConfig(ms, incMs))
      case label =>
        clockPresets.find(_._1 == label).flatMap(_._2)

  private def parseLong(s: String): Long =
    try s.trim.toLong catch case _: NumberFormatException => 0L

  private def trimOrDefault(s: String, default: String): String =
    val t = s.trim
    if t.isEmpty then default else t

  private def labeledRow(label: String, comp: Component): Panel =
    val row = new Panel(new LinearLayout(Direction.HORIZONTAL))
    val _ = row.addComponent(new Label(label))
    val _ = row.addComponent(comp)
    row

  private def centeredWindow(title: String): BasicWindow =
    val w = new BasicWindow(title)
    w.setHints(java.util.Arrays.asList(Window.Hint.CENTERED))
    w
