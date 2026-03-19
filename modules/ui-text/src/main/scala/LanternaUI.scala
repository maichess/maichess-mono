package org.maichess.mono.ui

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.{BasicWindow, BorderLayout, DefaultWindowManager, EmptySpace, MultiWindowTextGUI, Panel, WindowBasedTextGUI}
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import org.maichess.mono.engine.{DrawReason, GameController, GameResult, GameState}
import org.maichess.mono.model.*
import org.maichess.mono.rules.StandardRules

@SuppressWarnings(Array("org.wartremover.warts.Var"))
object LanternaUI:

  def start(ctrl: GameController, gui: WindowBasedTextGUI): Unit =
    var gameState:     GameState    = ctrl.newGame()
    var moveHistory:   List[String] = List.empty
    var capturedWhite: List[Piece]  = List.empty
    var capturedBlack: List[Piece]  = List.empty

    val sidePanel = new SidePanel()

    // lazy val avoids forward reference: the lambda captures boardComponent
    // but is only called after boardComponent is fully initialised.
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
            capturedBlack = capturedBlack ++ newCaptures
          else
            capturedWhite = capturedWhite ++ newCaptures
          moveHistory = moveHistory :+ SidePanel.moveNotation(finalMove)
          gameState = newState
          boardComponent.updateState(newState)
          sidePanel.update(newState, moveHistory, capturedWhite, capturedBlack)
          ctrl.gameResult(newState).foreach { result =>
            sidePanel.showResult(resultMessage(result))
            boardComponent.setBoardEnabled(false)
          }
        case Left(_) => ()
    })

    val menuBar = new MenuBar(
      onNewGame = () => {
        val newState = ctrl.newGame()
        gameState     = newState
        moveHistory   = List.empty
        capturedWhite = List.empty
        capturedBlack = List.empty
        boardComponent.reset(newState)
        boardComponent.setBoardEnabled(true)
        sidePanel.update(newState, moveHistory, capturedWhite, capturedBlack)
      },
      onResign = () => {
        val resigning = if gameState.current.turn == Color.White then "White" else "Black"
        val winner    = if gameState.current.turn == Color.White then "Black" else "White"
        sidePanel.showResult(resigning + " resigned. " + winner + " wins.")
        boardComponent.setBoardEnabled(false)
      }
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
  val ctrl     = new GameController(StandardRules)
  val terminal = new DefaultTerminalFactory().createTerminal()
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
