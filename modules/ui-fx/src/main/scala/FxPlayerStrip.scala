package org.maichess.mono.uifx

import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.{HBox, Priority, Region}
import org.maichess.mono.model.{Color, Piece, PieceType}

/** One player row: name label, spacer, clock, and captured-pieces symbols.
 *  Two of these (Black at top, White at bottom) form the side panel's player area. */
class FxPlayerStrip(colorClass: String):

  private val nameLabel     = new Label("—")
  private val clockLabel    = new Label("∞")
  private val capturedLabel = new Label()
  private val spacer        = new Region()

  nameLabel.getStyleClass.add("player-name")
  clockLabel.getStyleClass.add("clock-label")
  capturedLabel.getStyleClass.add("captured-label")
  HBox.setHgrow(spacer, Priority.ALWAYS)

  val root = new HBox(8.0, nameLabel, spacer, capturedLabel, clockLabel)
  root.setAlignment(Pos.CENTER_LEFT)
  root.getStyleClass.addAll("player-strip", "player-inactive", colorClass)

  def setName(name: String): Unit = nameLabel.setText(name)

  def setClock(clock: Option[ClockState], color: Color): Unit =
    clock match
      case None =>
        clockLabel.setText("∞")
        clockLabel.getStyleClass.removeAll("clock-low", "clock-zero")
      case Some(s) =>
        clockLabel.setText(s.formatted(color))
        toggleClass(clockLabel, "clock-low",  s.isLow(color))
        toggleClass(clockLabel, "clock-zero", s.flagged.contains(color))

  def setCaptured(pieces: List[Piece]): Unit =
    capturedLabel.setText(pieces.map(pieceSymbol).mkString)

  def setActive(active: Boolean): Unit =
    if active then
      root.getStyleClass.remove("player-inactive")
      if !root.getStyleClass.contains("player-active") then
        root.getStyleClass.add("player-active")
    else
      root.getStyleClass.remove("player-active")
      if !root.getStyleClass.contains("player-inactive") then
        root.getStyleClass.add("player-inactive")

  private def toggleClass(node: javafx.scene.Node, cls: String, on: Boolean): Unit =
    if on  && !node.getStyleClass.contains(cls) then node.getStyleClass.add(cls)
    if !on &&  node.getStyleClass.contains(cls) then node.getStyleClass.remove(cls)

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
