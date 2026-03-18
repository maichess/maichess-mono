package org.maichess.mono.ui

import org.maichess.mono.engine.*
import org.maichess.mono.model.*
import org.maichess.mono.rules.*

import scala.io.StdIn

enum Direction:
  case Up, Down, Left, Right

enum CursorState:
  case Navigating(cursor: Square)
  case PieceSelected(from: Square, index: Int, targets: IndexedSeq[Move])

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
    val rows = rankRange.map { ri =>
      val cells: IndexedSeq[String] = (0 to 7).map { fi =>
        val sq = for f <- File.fromInt(fi); r <- Rank.fromInt(ri) yield Square(f, r)
        sq.fold(".")(renderCell(board, _, cursor, selected, targets))
      }
      val cellsStr: String = cells.mkString(" ")
      val rankLabel: String = (ri + 1).toString
      rankLabel + " " + cellsStr
    }
    rows.mkString("\n") + "\n  a b c d e f g h"

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
    val ansiPrefix =
      if cursor.contains(sq)        then "\u001b[43m"
      else if selected.contains(sq) then "\u001b[42m"
      else if targets.contains(sq)  then "\u001b[44m"
      else ""
    if ansiPrefix.isEmpty then symbol else ansiPrefix + symbol + "\u001b[0m"

  /** Parses "e2e4" or "e2 e4" into (from, to) squares. */
  def parseMove(input: String): Either[String, (Square, Square)] =
    val clean = input.trim.replace(" ", "")
    if clean.length < 4 then Left("Invalid input: " + input)
    else
      for
        from <- Square.fromAlgebraic(clean.take(2)).toRight("Invalid square: " + clean.take(2))
        to   <- Square.fromAlgebraic(clean.slice(2, 4)).toRight("Invalid square: " + clean.slice(2, 4))
      yield (from, to)

  /** Runs the interactive game loop. The only impure function in the project. */
  def gameLoop(state: GameState, ctrl: GameController): Unit =
    println(renderBoard(state.current.board, Color.White, None, None, Set.empty))
    ctrl.gameResult(state) match
      case Some(result) =>
        println(resultMessage(result))
      case None =>
        val turnLabel = if state.current.turn == Color.White then "White" else "Black"
        val checkNote = if StandardRules.isCheck(state.current) then " — CHECK!" else ""
        println(turnLabel + " to move" + checkNote + ". Enter move (e.g. e2e4):")
        val line = StdIn.readLine()
        parseMove(line) match
          case Left(err)         =>
            println("Parse error: " + err)
            gameLoop(state, ctrl)
          case Right((from, to)) =>
            val chosen = StandardRules.legalMoves(state.current, from).find(_.to == to)
            chosen match
              case None       =>
                println("Illegal move — try again.")
                gameLoop(state, ctrl)
              case Some(move) =>
                ctrl.applyMove(state, move) match
                  case Left(err)       =>
                    println("Error: " + err.reason)
                    gameLoop(state, ctrl)
                  case Right(newState) =>
                    gameLoop(newState, ctrl)

  private def pieceSymbol(piece: Piece): String =
    val s = piece.pieceType match
      case PieceType.King   => "K"
      case PieceType.Queen  => "Q"
      case PieceType.Rook   => "R"
      case PieceType.Bishop => "B"
      case PieceType.Knight => "N"
      case PieceType.Pawn   => "P"
    if piece.color == Color.White then s else s.toLowerCase

  private def resultMessage(result: GameResult): String = result match
    case GameResult.Checkmate(Color.White) => "Checkmate! White wins."
    case GameResult.Checkmate(Color.Black) => "Checkmate! Black wins."
    case GameResult.Stalemate              => "Stalemate — draw."
    case GameResult.Draw(reason)           => "Draw: " + reason.toString

@main def runGame(): Unit =
  val ctrl = GameController(StandardRules)
  println("Welcome to MaiChess!")
  TextUI.gameLoop(ctrl.newGame(), ctrl)
