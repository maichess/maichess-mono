package org.maichess.mono.uifx

import javafx.geometry.Insets
import javafx.scene.control.{Label, ListView}
import javafx.scene.layout.VBox
import org.maichess.mono.engine.GameState
import org.maichess.mono.model.*
import org.maichess.mono.rules.StandardRules

class FxSidePanel:
  private val statusLabel        = new Label("White to move")
  private val capturedWhiteLabel = new Label("White captured: ")
  private val capturedBlackLabel = new Label("Black captured: ")
  private val historyList        = new ListView[String]()

  val vbox = new VBox(8.0)
  vbox.setPadding(new Insets(10))
  vbox.setPrefWidth(180)
  vbox.getChildren.addAll(statusLabel, capturedWhiteLabel, capturedBlackLabel, historyList)

  def update(
    state:         GameState,
    history:       List[String],
    capturedWhite: List[Piece],
    capturedBlack: List[Piece]
  ): Unit =
    val checkNote = if StandardRules.isCheck(state.current) then " \u2014 CHECK!" else ""
    val turnText  = if state.current.turn == Color.White then "White to move" else "Black to move"
    statusLabel.setText(turnText + checkNote)
    capturedWhiteLabel.setText("White captured: " + capturedWhite.map(pieceSymbol).mkString)
    capturedBlackLabel.setText("Black captured: " + capturedBlack.map(pieceSymbol).mkString)
    historyList.getItems.clear()
    history.zipWithIndex.grouped(2).foreach { pair =>
      val head  = pair.headOption
      val white = head.fold("")(_._1)
      val black = pair.lift(1).fold("")(_._1)
      val num   = (head.fold(0)(_._2 / 2) + 1).toString + ".  "
      historyList.getItems.add(num + white + "    " + black)
    }

  def showResult(message: String): Unit =
    statusLabel.setText(message)

  private def pieceSymbol(piece: Piece): String = (piece.color match
    case Color.White => piece.pieceType match
      case PieceType.King   => '\u2654'
      case PieceType.Queen  => '\u2655'
      case PieceType.Rook   => '\u2656'
      case PieceType.Bishop => '\u2657'
      case PieceType.Knight => '\u2658'
      case PieceType.Pawn   => '\u2659'
    case Color.Black => piece.pieceType match
      case PieceType.King   => '\u265A'
      case PieceType.Queen  => '\u265B'
      case PieceType.Rook   => '\u265C'
      case PieceType.Bishop => '\u265D'
      case PieceType.Knight => '\u265E'
      case PieceType.Pawn   => '\u265F'
  ).toString
