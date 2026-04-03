package org.maichess.mono.ui

import com.googlecode.lanterna.gui2.{Direction => LDirection, Label, LinearLayout, Panel}
import org.maichess.mono.engine.GameState
import org.maichess.mono.model.*
import org.maichess.mono.rules.StandardRules
import org.maichess.mono.uifx.{ClockState, SharedGameModel, *}

object SidePanel:

  def capturedPieces(before: Board, after: Board, movingColor: Color): List[Piece] =
    val opponentBefore = before.pieces.values.filter(_.color != movingColor).toList
    val opponentAfter  = after.pieces.values.filter(_.color != movingColor).toList
    opponentBefore diff opponentAfter

  def pieceSymbol(piece: Piece): String =
    BoardComponent.pieceChar(piece).toString

class SidePanel extends Panel(new LinearLayout(LDirection.VERTICAL)):

  private val blackClockLabel    = new Label("Black: \u221e")
  private val blackNameLabel     = new Label("Black")
  private val statusLabel        = new Label("White to move")
  private val thinkingLabel      = new Label("")
  private val capturedWhiteLabel = new Label("White captured: ")
  private val capturedBlackLabel = new Label("Black captured: ")
  private val historyPanel       = new Panel(new LinearLayout(LDirection.VERTICAL))
  private val whiteNameLabel     = new Label("White")
  private val whiteClockLabel    = new Label("White: \u221e")

  private val _ = addComponent(blackNameLabel)
  private val _ = addComponent(blackClockLabel)
  private val _ = addComponent(new com.googlecode.lanterna.gui2.Separator(LDirection.HORIZONTAL))
  private val _ = addComponent(statusLabel)
  private val _ = addComponent(thinkingLabel)
  private val _ = addComponent(capturedWhiteLabel)
  private val _ = addComponent(capturedBlackLabel)
  private val _ = addComponent(historyPanel)
  private val _ = addComponent(new com.googlecode.lanterna.gui2.Separator(LDirection.HORIZONTAL))
  private val _ = addComponent(whiteNameLabel)
  private val _ = addComponent(whiteClockLabel)

  def update(
    state:         GameState,
    history:       List[String],
    capturedWhite: List[Piece],
    capturedBlack: List[Piece]
  ): Unit =
    val checkNote = if StandardRules.isCheck(state.current) then " \u2014 CHECK!" else ""
    val turnText  = if state.current.turn == Color.White then "White to move" else "Black to move"
    statusLabel.setText(turnText + checkNote)
    capturedWhiteLabel.setText("White captured: " + capturedWhite.map(SidePanel.pieceSymbol).mkString)
    capturedBlackLabel.setText("Black captured: " + capturedBlack.map(SidePanel.pieceSymbol).mkString)
    refreshHistory(history)

  def setPlayerNames(white: String, black: String): Unit =
    whiteNameLabel.setText(white)
    blackNameLabel.setText(black)

  def updateClock(clock: Option[ClockState]): Unit =
    clock match
      case None =>
        whiteClockLabel.setText("White: \u221e")
        blackClockLabel.setText("Black: \u221e")
      case Some(s) =>
        whiteClockLabel.setText("White: " + s.formatted(Color.White))
        blackClockLabel.setText("Black: " + s.formatted(Color.Black))

  def showResult(message: String): Unit =
    statusLabel.setText(message)
    thinkingLabel.setText("")

  def showThinking(): Unit  = thinkingLabel.setText("\u265e AI thinking...")
  def hideThinking(): Unit  = thinkingLabel.setText("")

  private def refreshHistory(history: List[String]): Unit =
    val _ = historyPanel.removeAllComponents()
    history.zipWithIndex.grouped(2).foreach { pair =>
      val head  = pair.headOption
      val white = head.fold("")(_._1)
      val black = pair.lift(1).fold("")(_._1)
      val num   = (head.fold(0)(_._2 / 2) + 1).toString + ".  "
      val _ = historyPanel.addComponent(new Label(num + white + "    " + black))
    }
