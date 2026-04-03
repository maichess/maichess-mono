package org.maichess.mono.ui

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.{BasicWindow, BorderLayout, DefaultWindowManager, EmptySpace, MultiWindowTextGUI, Panel, WindowBasedTextGUI}
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.{DefaultTerminalFactory, ExtendedTerminal, MouseCaptureMode}
import com.googlecode.lanterna.terminal.swing.SwingTerminalFontConfiguration
import java.awt.Font
import org.maichess.mono.engine.{DrawReason, GameController, GameResult}
import org.maichess.mono.model.*
import org.maichess.mono.rules.StandardRules
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
      s.change match
        case Change.ClockTick =>
          sidePanel.updateClock(s.clock)
          s.clock.flatMap(_.flagged).foreach { color =>
            sidePanel.showResult(timeExpiredMessage(color))
            boardComponent.setBoardEnabled(false)
          }
        case _ =>
          val (cw, cb)   = recomputeCaptures(s.game)
          val turn       = s.game.current.turn
          val wBot       = model.botFor(Color.White)
          val bBot       = model.botFor(Color.Black)
          val shouldFlip = (wBot.isDefined && bBot.isEmpty) ||
                           (wBot.isEmpty && bBot.isEmpty && turn == Color.Black)
          boardComponent.setFlipped(shouldFlip)
          s.change match
            case Change.Reset => boardComponent.reset(s.game)
            case _            => boardComponent.updateState(s.game)
          sidePanel.update(s.game, s.moveHistory, cw, cb)
          sidePanel.setPlayerNames(s.metadata.white, s.metadata.black)
          sidePanel.updateClock(s.clock)
          val clockFlagged = s.clock.flatMap(_.flagged).isDefined
          val isOver       = model.gameResult().isDefined || clockFlagged
          val isThinking   = !isOver && model.botFor(turn).isDefined
          boardComponent.setBoardEnabled(!isOver && !isThinking)
          if isThinking then sidePanel.showThinking()
          else               sidePanel.hideThinking()
          s.clock.flatMap(_.flagged).fold(
            model.gameResult().foreach(r => sidePanel.showResult(resultMessage(r)))
          ) { color => sidePanel.showResult(timeExpiredMessage(color)) }

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
      model.resign()
      sidePanel.showResult(loser + " resigned. " + winner + " wins.")
      boardComponent.setBoardEnabled(false)

    def doNewGame(): Unit =
      ChessDialog.showGameSetup(gui).foreach { setup =>
        model.newGame(setup.whiteBot, setup.blackBot, setup.whiteName, setup.blackName, setup.clockConfig)
      }

    def doPause(): Unit =
      if model.isPausedState then model.resume() else model.pause()

    lazy val shortcutMap: Map[Char, () => Unit] = Map(
      Keymap.newGame.key   -> (() => doNewGame()),
      Keymap.resign.key    -> (() => doResign()),
      Keymap.undo.key      -> (() => model.undo()),
      Keymap.redo.key      -> (() => model.redo()),
      Keymap.importFen.key -> (() => doImportFen()),
      Keymap.exportFen.key -> (() => doExportFen()),
      Keymap.importPgn.key -> (() => doImportPgn()),
      Keymap.exportPgn.key -> (() => doExportPgn()),
      Keymap.pause.key     -> (() => doPause())
    )

    lazy val boardComponent: BoardComponent = new BoardComponent(model.state.game, move => {
      val finalMove = move match
        case nm: NormalMove if nm.promotion.isDefined =>
          NormalMove(nm.from, nm.to, Some(PromotionDialog.show(gui)))
        case _ => move
      val _ = model.applyMove(finalMove)
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

  private def timeExpiredMessage(flagged: Color): String =
    if flagged == Color.White then "White's time expired. Black wins."
    else "Black's time expired. White wins."

@main def runGame(): Unit =
  java.util.logging.Logger.getLogger("com.sun.javafx").setLevel(java.util.logging.Level.OFF)
  java.util.logging.Logger.getLogger("com.sun.glass").setLevel(java.util.logging.Level.OFF)

  val ctrl  = new GameController(StandardRules)
  val model = new SharedGameModel(ctrl)

  javafx.application.Platform.setImplicitExit(false)
  val fxReady = new java.util.concurrent.CountDownLatch(1)
  javafx.application.Platform.startup(() =>
    val stage = new javafx.stage.Stage()
    FxUI.start(model, stage)
    fxReady.countDown()
  )
  fxReady.await()

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
