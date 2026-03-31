package org.maichess.mono.uifx

import javafx.scene.canvas.Canvas
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color as JColor
import javafx.scene.text.{Font, FontWeight, TextAlignment}
import org.maichess.mono.engine.GameState
import org.maichess.mono.model.*
import org.maichess.mono.rules.StandardRules

class FxBoard(initialState: GameState, squareSize: Double = 72.0):
  val canvas = new Canvas(squareSize * 8, squareSize * 8)
  private val gc = canvas.getGraphicsContext2D

  private var gameState  = initialState
  private var selected   = Option.empty[Square]
  private var legalMoves = List.empty[Move]
  private var enabled    = true
  private var flipped    = false
  var onMove: Move => Unit = _ => ()

  canvas.setOnMouseClicked((e: MouseEvent) => if enabled then handleClick(e))
  draw()

  def updateState(state: GameState): Unit =
    gameState  = state
    selected   = None
    legalMoves = Nil
    draw()

  def setBoardEnabled(flag: Boolean): Unit =
    enabled = flag

  def setFlipped(flag: Boolean): Unit =
    flipped = flag
    draw()

  private def toFile(pixelX: Double): Int =
    val raw = (pixelX / squareSize).toInt
    if flipped then 7 - raw else raw

  private def toRank(pixelY: Double): Int =
    val raw = (pixelY / squareSize).toInt
    if flipped then raw else 7 - raw

  private def handleClick(e: MouseEvent): Unit =
    val fi = toFile(e.getX)
    val ri = toRank(e.getY)
    for f <- File.fromInt(fi); r <- Rank.fromInt(ri) do
      clickSquare(Square(f, r))

  private def clickSquare(sq: Square): Unit =
    selected match
      case None =>
        val moves = StandardRules.legalMoves(gameState.current, sq).toList.distinctBy(_.to)
        if moves.nonEmpty then
          selected   = Some(sq)
          legalMoves = moves
          draw()
      case Some(from) =>
        legalMoves.find(_.to == sq) match
          case Some(move) =>
            selected   = None
            legalMoves = Nil
            draw()
            onMove(move)
          case None =>
            val moves = StandardRules.legalMoves(gameState.current, sq).toList.distinctBy(_.to)
            if sq == from || moves.isEmpty then
              selected   = None
              legalMoves = Nil
            else
              selected   = Some(sq)
              legalMoves = moves
            draw()

  private def sqX(fi: Int): Double = if flipped then (7 - fi) * squareSize else fi * squareSize
  private def sqY(ri: Int): Double = if flipped then ri * squareSize else (7 - ri) * squareSize

  private def draw(): Unit =
    gc.clearRect(0, 0, canvas.getWidth, canvas.getHeight)
    for ri <- 0 to 7; fi <- 0 to 7 do
      for f <- File.fromInt(fi); r <- Rank.fromInt(ri) do
        drawSquare(Square(f, r), fi, ri)
    drawLabels()

  private def drawSquare(sq: Square, fi: Int, ri: Int): Unit =
    val x       = sqX(fi)
    val y       = sqY(ri)
    val isLight = (fi + ri) % 2 != 0

    val bg = selected match
      case Some(s) if s == sq                    => JColor.rgb(130, 151, 105)
      case _ if legalMoves.exists(_.to == sq)    => JColor.rgb(100, 130, 180)
      case _ if isLight                          => JColor.rgb(240, 217, 181)
      case _                                     => JColor.rgb(181, 136,  99)

    gc.setFill(bg)
    gc.fillRect(x, y, squareSize, squareSize)

    gameState.current.board.pieceAt(sq).foreach { piece =>
      val fg  = if piece.color == Color.White then JColor.WHITE else JColor.BLACK
      val out = if piece.color == Color.White then JColor.rgb(80, 80, 80) else JColor.rgb(200, 200, 200)
      gc.setFont(Font.font("Serif", FontWeight.BOLD, squareSize * 0.72))
      gc.setTextAlign(TextAlignment.CENTER)
      gc.setStroke(out)
      gc.setLineWidth(1.0)
      gc.strokeText(pieceChar(piece).toString, x + squareSize / 2, y + squareSize * 0.80)
      gc.setFill(fg)
      gc.fillText(pieceChar(piece).toString, x + squareSize / 2, y + squareSize * 0.80)
    }

  private def isLightSquare(fi: Int, ri: Int): Boolean = (fi + ri) % 2 != 0

  private def drawLabels(): Unit =
    gc.setFont(Font.font("SansSerif", 10))
    gc.setTextAlign(TextAlignment.LEFT)
    for row <- 0 to 7 do
      val ri      = if flipped then row else 7 - row
      val label   = (ri + 1).toString
      val isLight = isLightSquare(0, ri)
      gc.setFill(if isLight then JColor.rgb(181, 136, 99) else JColor.rgb(240, 217, 181))
      gc.fillText(label, 2, row * squareSize + 12)
    for col <- 0 to 7 do
      val fi      = if flipped then 7 - col else col
      val label   = ('a' + fi).toChar.toString
      val isLight = isLightSquare(fi, 0)
      gc.setFill(if isLight then JColor.rgb(181, 136, 99) else JColor.rgb(240, 217, 181))
      gc.fillText(label, col * squareSize + squareSize - 10, canvas.getHeight - 3)

  private def pieceChar(piece: Piece): Char = piece.color match
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
