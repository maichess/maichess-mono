package org.maichess.mono.ui

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.{BasicWindow, BorderLayout, DefaultWindowManager, EmptySpace, MultiWindowTextGUI, Panel, WindowBasedTextGUI}
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.{DefaultTerminalFactory, ExtendedTerminal, MouseCaptureMode}
import com.googlecode.lanterna.terminal.swing.SwingTerminalFontConfiguration
import java.awt.Font
import org.maichess.mono.engine.{DrawReason, Fen, GameController, GameResult, Pgn}
import org.maichess.mono.model.*
import org.maichess.mono.rules.{StandardRules, Situation}
import org.maichess.mono.uifx.{Change, FxUI, Keymap, SharedGameModel}

object LanternaUI:

  def start(model: SharedGameModel, gui: WindowBasedTextGUI): Unit =
    val sidePanel = new SidePanel()

    def recomputeCaptures(state: org.maichess.mono.engine.GameState): (List[Piece], List[Piece]) =
      val sits = state.history.reverse :+ state.current
      sits.zip(sits.drop(1)).foldLeft((List.empty[Piece], List.empty[Piece])) {
        case ((cw, cb), (before, after)) =>
          val caps = SidePanel.capturedPieces(before.board, after.board, before.turn)
          if before.turn == Color.White then (cw ++ caps, cb) else (cw, cb ++ caps)
      }

    def syncUiFrom(s: SharedGameModel.State): Unit =
      val (cw, cb) = recomputeCaptures(s.game)
      s.change match
        case Change.Reset =>
          boardComponent.reset(s.game)
          boardComponent.setBoardEnabled(true)
        case _ =>
          boardComponent.updateState(s.game)
      sidePanel.update(s.game, s.moveHistory, cw, cb)
      model.gameResult().foreach { result =>
        sidePanel.showResult(resultMessage(result))
        boardComponent.setBoardEnabled(false)
      }

    // Observer: sync TUI whenever the model changes (including from FX UI).
    // Wrapped in invokeLater so it always runs on the Lanterna GUI thread.
    model.addObserver { s =>
      try gui.getGUIThread.invokeLater(() => syncUiFrom(s))
      catch case _: Exception => ()
    }

    def doImportFen(): Unit =
      ChessDialog.showImport(gui, "Import FEN", "Paste a FEN string:").foreach { input =>
        model.importFen(input.trim) match
          case Left(err) => ChessDialog.showExport(gui, "FEN Error", err)
          case Right(()) => ()
      }

    def doExportFen(): Unit =
      ChessDialog.showExport(gui, "Export FEN", model.exportFen())

    def doImportPgn(): Unit =
      ChessDialog.showImport(gui, "Import PGN", "Paste a PGN string:").foreach { input =>
        model.importPgn(input.trim) match
          case Left(err) => ChessDialog.showExport(gui, "PGN Error", err)
          case Right(()) => ()
      }

    def doExportPgn(): Unit =
      ChessDialog.showExport(gui, "Export PGN", model.exportPgn())

    def doResign(): Unit =
      val turn   = model.state.game.current.turn
      val loser  = if turn == Color.White then "White" else "Black"
      val winner = if turn == Color.White then "Black" else "White"
      sidePanel.showResult(loser + " resigned. " + winner + " wins.")
      boardComponent.setBoardEnabled(false)

    def doNewGame(): Unit =
      val mode = ChessDialog.showModeSelect(gui)
      model.newGameWithMode(mode)

    lazy val shortcutMap: Map[Char, () => Unit] = Map(
      Keymap.newGame.key   -> (() => doNewGame()),
      Keymap.resign.key    -> (() => doResign()),
      Keymap.undo.key      -> (() => model.undo()),
      Keymap.redo.key      -> (() => model.redo()),
      Keymap.importFen.key -> (() => doImportFen()),
      Keymap.exportFen.key -> (() => doExportFen()),
      Keymap.importPgn.key -> (() => doImportPgn()),
      Keymap.exportPgn.key -> (() => doExportPgn())
    )

    lazy val boardComponent: BoardComponent = new BoardComponent(model.state.game, move => {
      val finalMove = move match
        case nm: NormalMove if nm.promotion.isDefined =>
          NormalMove(nm.from, nm.to, Some(PromotionDialog.show(gui)))
        case _ => move
      val notation = SidePanel.moveNotation(finalMove)
      model.applyMove(finalMove, notation)
      ()
    }, shortcutMap)

    val menuBar = new MenuBar(
      onNewGame   = () => doNewGame(),
      onResign    = () => doResign(),
      onUndo      = () => model.undo(),
      onRedo      = () => model.redo(),
      onImportFen = () => doImportFen(),
      onExportFen = () => doExportFen(),
      onImportPgn = () => doImportPgn(),
      onExportPgn = () => doExportPgn()
    )

    val mainPanel = new Panel(new BorderLayout())
    val _ = mainPanel.addComponent(menuBar,        BorderLayout.Location.TOP)
    val _ = mainPanel.addComponent(boardComponent, BorderLayout.Location.CENTER)
    val _ = mainPanel.addComponent(sidePanel,      BorderLayout.Location.RIGHT)

    val window = new BasicWindow("MaiChess")
    window.setComponent(mainPanel)
    model.addShutdownObserver(() => window.close())
    val _ = gui.addWindowAndWait(window)

  private def resultMessage(result: GameResult): String = result match
    case GameResult.Checkmate(Color.White)                => "Checkmate \u2014 White wins."
    case GameResult.Checkmate(Color.Black)                => "Checkmate \u2014 Black wins."
    case GameResult.Stalemate                             => "Stalemate \u2014 draw."
    case GameResult.Draw(DrawReason.FiftyMoveRule)        => "Draw: 50-move rule."
    case GameResult.Draw(DrawReason.InsufficientMaterial) => "Draw: insufficient material."
    case GameResult.Draw(DrawReason.ThreefoldRepetition)  => "Draw: threefold repetition."
    case GameResult.Draw(DrawReason.Agreement)            => "Draw by agreement."

@main def runGame(): Unit =
  // Suppress JavaFX startup log messages before they can reach the terminal
  // and corrupt Lanterna's raw-mode display.
  java.util.logging.Logger.getLogger("com.sun.javafx").setLevel(java.util.logging.Level.OFF)
  java.util.logging.Logger.getLogger("com.sun.glass").setLevel(java.util.logging.Level.OFF)

  val ctrl  = new GameController(StandardRules)
  val model = new SharedGameModel(ctrl)

  // Start JavaFX asynchronously (non-blocking).  Platform.startup fires the
  // runnable on the FX Application Thread; we wait until the window is shown
  // before touching the terminal, so all FX init noise is flushed first.
  javafx.application.Platform.setImplicitExit(false)
  val fxReady = new java.util.concurrent.CountDownLatch(1)
  javafx.application.Platform.startup(() =>
    val stage = new javafx.stage.Stage()
    FxUI.start(model, stage)
    fxReady.countDown()
  )
  fxReady.await()

  // Run Lanterna on the main thread — same as the original single-UI setup.
  val factory   = new DefaultTerminalFactory()
  val isWindows = System.getProperty("os.name").toLowerCase.contains("win")
  if isWindows then
    val font = new Font(Font.MONOSPACED, Font.PLAIN, 24)
    factory.setTerminalEmulatorFontConfiguration(SwingTerminalFontConfiguration.newInstance(font))
  else
    factory.setForceTextTerminal(true)

  val terminal = factory.createTerminal()
  terminal match
    case t: ExtendedTerminal => t.setMouseCaptureMode(MouseCaptureMode.CLICK_RELEASE)
    case _                   => ()
  val screen = new TerminalScreen(terminal)
  screen.startScreen()
  try
    val gui = new MultiWindowTextGUI(
      screen,
      new DefaultWindowManager(),
      new EmptySpace(TextColor.ANSI.BLACK)
    )
    LanternaUI.start(model, gui)
  finally
    screen.stopScreen()
    terminal.close()
    javafx.application.Platform.exit()
