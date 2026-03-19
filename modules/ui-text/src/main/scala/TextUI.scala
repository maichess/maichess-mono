package org.maichess.mono.ui

import org.maichess.mono.engine.*
import org.maichess.mono.model.*
import org.maichess.mono.rules.*

object TextUI:

  def cursorSquare(cs: CursorState): Square = cs match
    case CursorState.Navigating(cursor)               => cursor
    case CursorState.PieceSelected(_, index, targets) => targets(index).to

  def selectedSquare(cs: CursorState): Option[Square] = cs match
    case CursorState.Navigating(_)             => None
    case CursorState.PieceSelected(from, _, _) => Some(from)

  def targetSquares(cs: CursorState): Set[Square] = cs match
    case CursorState.Navigating(_)                => Set.empty
    case CursorState.PieceSelected(_, _, targets) => targets.map(_.to).toSet

  def moveCursorFree(cursor: Square, dir: Direction): Square =
    val df = dir match
      case Direction.Left  => -1
      case Direction.Right =>  1
      case _               =>  0
    val dr = dir match
      case Direction.Up   =>  1
      case Direction.Down => -1
      case _              =>  0
    val newFile = (cursor.file.toInt + df + 8) % 8
    val newRank = (cursor.rank.toInt + dr + 8) % 8
    (for f <- File.fromInt(newFile); r <- Rank.fromInt(newRank) yield Square(f, r))
      .getOrElse(cursor)

  def moveCursorTargets(targets: IndexedSeq[Move], index: Int, dir: Direction): Int =
    val delta = dir match
      case Direction.Right | Direction.Down => 1
      case Direction.Left  | Direction.Up   => -1
    (index + delta + targets.length) % targets.length

  def renderBoard(
    board: Board,
    perspective: Color,
    cursor: Option[Square],
    selected: Option[Square],
    targets: Set[Square]
  ): String =
    val rankRange = if perspective == Color.White then (7 to 0 by -1) else (0 to 7)
    val separator = "  +---+---+---+---+---+---+---+---+"
    val rows = rankRange.map { ri =>
      val cells: IndexedSeq[String] = (0 to 7).map { fi =>
        val sq = for f <- File.fromInt(fi); r <- Rank.fromInt(ri) yield Square(f, r)
        sq.fold(" . ")(renderCell(board, _, cursor, selected, targets))
      }
      val rankLabel: String = (ri + 1).toString
      rankLabel + " |" + cells.mkString("|") + "|"
    }
    val lines = separator +: rows.flatMap(row => Seq(row, separator))
    lines.mkString("\n") + "\n    a   b   c   d   e   f   g   h"

  private def renderCell(
    board: Board,
    sq: Square,
    cursor: Option[Square],
    selected: Option[Square],
    targets: Set[Square]
  ): String =
    val symbol =
      if targets.contains(sq) then "+"
      else board.pieceAt(sq).fold(".")(pieceSymbol)
    val padded: String = " " + symbol + " "
    val ansiPrefix =
      if cursor.contains(sq)        then "\u001b[43m"
      else if selected.contains(sq) then "\u001b[42m"
      else if targets.contains(sq)  then "\u001b[44m"
      else ""
    if ansiPrefix.isEmpty then padded else ansiPrefix + padded + "\u001b[0m"

  private def pieceSymbol(piece: Piece): String =
    val s = piece.pieceType match
      case PieceType.King   => "K"
      case PieceType.Queen  => "Q"
      case PieceType.Rook   => "R"
      case PieceType.Bishop => "B"
      case PieceType.Knight => "N"
      case PieceType.Pawn   => "P"
    if piece.color == Color.White then s else s.toLowerCase

  private def drawReasonName(reason: DrawReason): String = reason match
    case DrawReason.FiftyMoveRule         => "FiftyMoveRule"
    case DrawReason.InsufficientMaterial  => "InsufficientMaterial"
    case DrawReason.ThreefoldRepetition   => "ThreefoldRepetition"
    case DrawReason.Agreement             => "Agreement"

  private def resultMessage(result: GameResult): String = result match
    case GameResult.Checkmate(Color.White) => "Checkmate! White wins."
    case GameResult.Checkmate(Color.Black) => "Checkmate! Black wins."
    case GameResult.Stalemate              => "Stalemate — draw."
    case GameResult.Draw(reason)           => "Draw: " + drawReasonName(reason)
