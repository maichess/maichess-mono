package org.maichess.mono.uifx

import javafx.geometry.{Insets, Pos}
import javafx.scene.control.{Label, ListView, Separator}
import javafx.scene.layout.{Priority, VBox}
import org.maichess.mono.engine.GameState
import org.maichess.mono.model.*
import org.maichess.mono.rules.StandardRules

/** Right-hand panel: Black player strip → move history → result area → White player strip. */
class FxSidePanel:

  val blackStrip  = new FxPlayerStrip("player-black")
  val whiteStrip  = new FxPlayerStrip("player-white")

  private val historyList  = new ListView[String]()
  private val resultLabel  = new Label()

  historyList.getStyleClass.add("history-list")
  resultLabel.setId("result-label")
  resultLabel.setWrapText(true)
  resultLabel.setMaxWidth(Double.MaxValue)
  resultLabel.setAlignment(Pos.CENTER)
  resultLabel.setVisible(false)

  VBox.setVgrow(historyList, Priority.ALWAYS)

  val vbox = new VBox()
  vbox.setId("side-panel")
  vbox.setMinWidth(280.0)
  val _ = vbox.getChildren.addAll(
    blackStrip.root,
    new Separator(),
    historyList,
    resultLabel,
    new Separator(),
    whiteStrip.root
  )

  def update(
    state:         GameState,
    history:       List[String],
    capturedWhite: List[Piece],
    capturedBlack: List[Piece]
  ): Unit =
    val isWhiteTurn = state.current.turn == Color.White
    whiteStrip.setActive(isWhiteTurn)
    blackStrip.setActive(!isWhiteTurn)
    whiteStrip.setCaptured(capturedWhite)
    blackStrip.setCaptured(capturedBlack)
    refreshHistory(history)

  def setPlayerNames(white: String, black: String): Unit =
    whiteStrip.setName(white)
    blackStrip.setName(black)

  def updateClock(clock: Option[ClockState]): Unit =
    whiteStrip.setClock(clock, Color.White)
    blackStrip.setClock(clock, Color.Black)

  def showResult(message: String): Unit =
    whiteStrip.setActive(false)
    blackStrip.setActive(false)
    resultLabel.setText(message)
    resultLabel.setVisible(true)

  def hideResult(): Unit =
    resultLabel.setVisible(false)

  private def refreshHistory(history: List[String]): Unit =
    historyList.getItems.clear()
    history.zipWithIndex.grouped(2).foreach { pair =>
      val head  = pair.headOption
      val white = head.fold("")(_._1)
      val black = pair.lift(1).fold("")(_._1)
      val num   = (head.fold(0)(_._2 / 2) + 1).toString + ".  "
      historyList.getItems.add(num + white + "    " + black)
    }
    if !historyList.getItems.isEmpty then
      historyList.scrollTo(historyList.getItems.size - 1)
