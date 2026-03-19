package org.maichess.mono.ui

import com.googlecode.lanterna.gui2.{Direction => LDirection, Label, LinearLayout, Panel}
import org.maichess.mono.engine.GameState
import org.maichess.mono.model.*
import org.maichess.mono.rules.StandardRules

object SidePanel:

  def capturedPieces(before: Board, after: Board, movingColor: Color): List[Piece] =
    val opponentBefore = before.pieces.values.filter(_.color != movingColor).toList
    val opponentAfter  = after.pieces.values.filter(_.color != movingColor).toList
    opponentBefore diff opponentAfter

  def moveNotation(move: Move): String = move match
    case NormalMove(from, to, None)     => from.toAlgebraic + "-" + to.toAlgebraic
    case NormalMove(from, to, Some(pt)) => from.toAlgebraic + "-" + to.toAlgebraic + "=" + promotionLetter(pt)
    case CastlingMove(from, _, rookFrom, _) =>
      if rookFrom.file.toInt > from.file.toInt then "O-O" else "O-O-O"
    case EnPassantMove(from, to, _) => from.toAlgebraic + "-" + to.toAlgebraic

  private def promotionLetter(pt: PieceType): String = pt match
    case PieceType.Queen  => "Q"
    case PieceType.Rook   => "R"
    case PieceType.Bishop => "B"
    case PieceType.Knight => "N"
    case PieceType.King   => "K"
    case PieceType.Pawn   => "P"

  def pieceSymbol(piece: Piece): String =
    BoardComponent.pieceChar(piece).toString

class SidePanel extends Panel(new LinearLayout(LDirection.VERTICAL)):

  private val statusLabel        = new Label("White to move")
  private val capturedWhiteLabel = new Label("White captured: ")
  private val capturedBlackLabel = new Label("Black captured: ")
  private val historyPanel       = new Panel(new LinearLayout(LDirection.VERTICAL))

  private val _ = addComponent(statusLabel)
  private val _ = addComponent(capturedWhiteLabel)
  private val _ = addComponent(capturedBlackLabel)
  private val _ = addComponent(historyPanel)

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

  def showResult(message: String): Unit =
    statusLabel.setText(message)

  private def refreshHistory(history: List[String]): Unit =
    val _ = historyPanel.removeAllComponents()
    history.zipWithIndex.grouped(2).foreach { pair =>
      val head  = pair.headOption
      val white = head.fold("")(_._1)
      val black = pair.lift(1).fold("")(_._1)
      val num   = (head.fold(0)(_._2 / 2) + 1).toString + ".  "
      val _ = historyPanel.addComponent(new Label(num + white + "    " + black))
    }
