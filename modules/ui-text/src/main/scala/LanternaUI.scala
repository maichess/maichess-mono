package org.maichess.mono.ui

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.{BasicWindow, BorderLayout, DefaultWindowManager, EmptySpace, MultiWindowTextGUI, Panel, WindowBasedTextGUI}
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.swing.SwingTerminalFontConfiguration
import java.awt.Font
import org.maichess.mono.engine.{DrawReason, Fen, GameController, GameResult, GameState, Pgn}
import org.maichess.mono.model.*
import org.maichess.mono.rules.{Situation, StandardRules}

@SuppressWarnings(Array("org.wartremover.warts.Var"))
object LanternaUI:

  def start(ctrl: GameController, gui: WindowBasedTextGUI): Unit =
    var gameState:         GameState    = ctrl.newGame()
    var moveHistory:       List[String] = List.empty
    var futureMoveHistory: List[String] = List.empty
    var capturedWhite:     List[Piece]  = List.empty
    var capturedBlack:     List[Piece]  = List.empty

    val sidePanel = new SidePanel()

    def recomputeCaptures(state: GameState): (List[Piece], List[Piece]) =
      val sits = state.history.reverse :+ state.current
      sits.zip(sits.drop(1)).foldLeft((List.empty[Piece], List.empty[Piece])) {
        case ((cw, cb), (before, after)) =>
          val caps = SidePanel.capturedPieces(before.board, after.board, before.turn)
          if before.turn == Color.White then (cw ++ caps, cb) else (cw, cb ++ caps)
      }

    def refreshFromState(newState: GameState): Unit =
      gameState         = newState
      moveHistory       = List.empty
      futureMoveHistory = List.empty
      capturedWhite     = List.empty
      capturedBlack     = List.empty
      boardComponent.reset(newState)
      boardComponent.setBoardEnabled(true)
      sidePanel.update(newState, moveHistory, capturedWhite, capturedBlack)

    def doNewGame(): Unit = refreshFromState(ctrl.newGame())

    def doUndo(): Unit =
      ctrl.undo(gameState).foreach { newState =>
        futureMoveHistory = moveHistory.lastOption.toList ++ futureMoveHistory
        moveHistory       = moveHistory.dropRight(1)
        gameState         = newState
        val (cw, cb)      = recomputeCaptures(newState)
        capturedWhite     = cw
        capturedBlack     = cb
        boardComponent.updateState(newState)
        sidePanel.update(newState, moveHistory, capturedWhite, capturedBlack)
      }

    def doRedo(): Unit =
      ctrl.redo(gameState).foreach { newState =>
        futureMoveHistory match
          case head :: rest =>
            moveHistory       = moveHistory :+ head
            futureMoveHistory = rest
          case Nil => ()
        gameState         = newState
        val (cw, cb)      = recomputeCaptures(newState)
        capturedWhite     = cw
        capturedBlack     = cb
        boardComponent.updateState(newState)
        sidePanel.update(newState, moveHistory, capturedWhite, capturedBlack)
      }
    def doImportFen(): Unit =
      ChessDialog.showImport(gui, "Import FEN", "Paste a FEN string:").foreach { input =>
        Fen.decode(input.trim) match
          case Right(sit) => refreshFromState(GameState(Nil, sit))
          case Left(err)  => ChessDialog.showExport(gui, "FEN Error", err)
      }
    def doExportFen(): Unit =
      ChessDialog.showExport(gui, "Export FEN", Fen.encode(gameState.current))
    def doImportPgn(): Unit =
      ChessDialog.showImport(gui, "Import PGN", "Paste a PGN string:").foreach { input =>
        Pgn.decode(input.trim, StandardRules) match
          case Right(newState) => refreshFromState(newState)
          case Left(err)       => ChessDialog.showExport(gui, "PGN Error", err)
      }
    def doExportPgn(): Unit =
      ChessDialog.showExport(gui, "Export PGN", Pgn.encode(gameState, StandardRules))
    def doResign(): Unit =
      val resigning = if gameState.current.turn == Color.White then "White" else "Black"
      val winner    = if gameState.current.turn == Color.White then "Black" else "White"
      sidePanel.showResult(resigning + " resigned. " + winner + " wins.")
      boardComponent.setBoardEnabled(false)

    lazy val shortcutMap: Map[Char, () => Unit] = Map(
      'n' -> (() => doNewGame()),
      'r' -> (() => doResign()),
      'z' -> (() => doUndo()),
      'y' -> (() => doRedo()),
      'f' -> (() => doImportFen()),
      'e' -> (() => doExportFen()),
      'p' -> (() => doImportPgn()),
      'o' -> (() => doExportPgn())
    )

    lazy val boardComponent: BoardComponent = new BoardComponent(gameState, move => {
      val finalMove = move match
        case nm: NormalMove if nm.promotion.isDefined =>
          NormalMove(nm.from, nm.to, Some(PromotionDialog.show(gui)))
        case _ => move

      ctrl.applyMove(gameState, finalMove) match
        case Right(newState) =>
          val newCaptures = SidePanel.capturedPieces(
            gameState.current.board,
            newState.current.board,
            gameState.current.turn
          )
          if gameState.current.turn == Color.White then
            capturedWhite = capturedWhite ++ newCaptures
          else
            capturedBlack = capturedBlack ++ newCaptures
          moveHistory       = moveHistory :+ SidePanel.moveNotation(finalMove)
          futureMoveHistory = List.empty
          gameState         = newState
          boardComponent.updateState(newState)
          sidePanel.update(newState, moveHistory, capturedWhite, capturedBlack)
          ctrl.gameResult(newState).foreach { result =>
            sidePanel.showResult(resultMessage(result))
            boardComponent.setBoardEnabled(false)
          }
        case Left(_) => ()
    }, shortcutMap)

    val menuBar = new MenuBar(
      onNewGame   = () => doNewGame(),
      onResign    = () => doResign(),
      onUndo      = () => doUndo(),
      onRedo      = () => doRedo(),
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
  val ctrl      = new GameController(StandardRules)
  val factory   = new DefaultTerminalFactory()
  val isWindows = System.getProperty("os.name").toLowerCase.contains("win")
  if isWindows then
    val font = new Font(Font.MONOSPACED, Font.PLAIN, 24)
    factory.setTerminalEmulatorFontConfiguration(SwingTerminalFontConfiguration.newInstance(font))
  else
    factory.setForceTextTerminal(true)
  val terminal = factory.createTerminal()
  val screen   = new TerminalScreen(terminal)
  screen.startScreen()
  try
    val gui = new MultiWindowTextGUI(
      screen,
      new DefaultWindowManager(),
      new EmptySpace(TextColor.ANSI.BLACK)
    )
    LanternaUI.start(ctrl, gui)
  finally
    screen.stopScreen()
    terminal.close()
