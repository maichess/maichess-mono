package org.maichess.mono.ui

import org.maichess.mono.engine.*
import org.maichess.mono.model.*
import org.maichess.mono.rules.*

import org.jline.keymap.{BindingReader, KeyMap}
import org.jline.terminal.{Attributes, Terminal, TerminalBuilder}
import org.jline.utils.InfoCmp.Capability

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

  private def buildKeyMap(terminal: Terminal): KeyMap[String] =
    val km = new KeyMap[String]()
    val _ = km.bind("up",    "\u001b[A", KeyMap.key(terminal, Capability.key_up))
    val _ = km.bind("down",  "\u001b[B", KeyMap.key(terminal, Capability.key_down))
    val _ = km.bind("right", "\u001b[C", KeyMap.key(terminal, Capability.key_right))
    val _ = km.bind("left",  "\u001b[D", KeyMap.key(terminal, Capability.key_left))
    val _ = km.bind("enter", "\r", "\n")
    val _ = km.bind("esc",   KeyMap.esc())
    km

  private def trySelectPiece(
    cursor: Square,
    state: GameState
  ): (GameState, CursorState) =
    val raw    = StandardRules.legalMoves(state.current, cursor)
    val deduped = raw
      .distinctBy(_.to)
      .sortBy(m => m.to.rank.toInt * 8 + m.to.file.toInt)
    if deduped.isEmpty then (state, CursorState.Navigating(cursor))
    else (state, CursorState.PieceSelected(cursor, 0, deduped.toIndexedSeq))

  private def applySelectedMove(
    move: Move,
    from: Square,
    state: GameState,
    ctrl: GameController
  ): (GameState, CursorState) =
    ctrl.applyMove(state, move) match
      case Right(newState) => (newState, CursorState.Navigating(move.to))
      case Left(_)         => (state, CursorState.Navigating(from))

  private def moveCursorByKey(cursor: Square, key: String): Square = key match
    case "up"    => moveCursorFree(cursor, Direction.Up)
    case "down"  => moveCursorFree(cursor, Direction.Down)
    case "left"  => moveCursorFree(cursor, Direction.Left)
    case "right" => moveCursorFree(cursor, Direction.Right)
    case _       => cursor

  private def handleKey(
    key: Option[String],
    state: GameState,
    cs: CursorState,
    ctrl: GameController
  ): (GameState, CursorState) =
    cs match
      case CursorState.Navigating(cursor) =>
        key match
          case Some("enter") => trySelectPiece(cursor, state)
          case Some(k)       => (state, CursorState.Navigating(moveCursorByKey(cursor, k)))
          case None          => (state, cs)
      case CursorState.PieceSelected(from, index, targets) =>
        key match
          case Some("up") | Some("left") =>
            (state, CursorState.PieceSelected(from, moveCursorTargets(targets, index, Direction.Up), targets))
          case Some("down") | Some("right") =>
            (state, CursorState.PieceSelected(from, moveCursorTargets(targets, index, Direction.Down), targets))
          case Some("enter") =>
            applySelectedMove(targets(index), from, state, ctrl)
          case Some("esc") =>
            (state, CursorState.Navigating(from))
          case _ =>
            (state, cs)

  @annotation.tailrec
  private def go(
    state: GameState,
    cs: CursorState,
    ctrl: GameController,
    terminal: Terminal,
    bindingReader: BindingReader,
    keyMap: KeyMap[String]
  ): Unit =
    val _ = terminal.puts(Capability.clear_screen)
    val _ = terminal.writer().println(
      renderBoard(state.current.board, Color.White, Some(cursorSquare(cs)), selectedSquare(cs), targetSquares(cs))
    )
    val _ = terminal.flush()
    ctrl.gameResult(state) match
      case Some(result) =>
        val _ = terminal.writer().println(resultMessage(result))
        val _ = terminal.flush()
      case None =>
        val turnLabel: String = if state.current.turn == Color.White then "White" else "Black"
        val checkNote: String = if StandardRules.isCheck(state.current) then " — CHECK!" else ""
        val prompt: String = turnLabel + " to move" + checkNote + "  (arrows to navigate, Enter to select/confirm, Esc to cancel)"
        val _ = terminal.writer().println(prompt)
        val _ = terminal.flush()
        val key = Option(bindingReader.readBinding(keyMap))
        val (newState, newCs): (GameState, CursorState) = handleKey(key, state, cs, ctrl)
        go(newState, newCs, ctrl, terminal, bindingReader, keyMap)

  def gameLoop(state: GameState, ctrl: GameController, terminal: Terminal): Unit =
    val keyMap        = buildKeyMap(terminal)
    val bindingReader = BindingReader(terminal.reader())
    val initialCursor = Square.fromAlgebraic("e1").getOrElse(Square.all(0))
    val prevAttrs: Attributes = terminal.enterRawMode()
    try go(state, CursorState.Navigating(initialCursor), ctrl, terminal, bindingReader, keyMap)
    finally
      terminal.setAttributes(prevAttrs)
      terminal.close()

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

@main def runGame(): Unit =
  val ctrl     = GameController(StandardRules)
  val terminal = TerminalBuilder.terminal()
  val _        = terminal.writer().println("Welcome to MaiChess!")
  TextUI.gameLoop(ctrl.newGame(), ctrl, terminal)
