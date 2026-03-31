package org.maichess.mono.ui

import com.googlecode.lanterna.{TextCharacter, TextColor, TerminalPosition, TerminalSize}
import com.googlecode.lanterna.gui2.{AbstractInteractableComponent, Interactable, InteractableRenderer, TextGUIGraphics}
import com.googlecode.lanterna.input.{KeyStroke, KeyType, MouseAction, MouseActionType}
import org.maichess.mono.engine.GameState
import org.maichess.mono.model.*
import org.maichess.mono.rules.StandardRules

case class SquareHighlights(
  cursor:   Option[Square],
  selected: Option[Square],
  targets:  Set[Square]
)

object BoardComponent:

  def renderSquare(board: Board, sq: Square, hl: SquareHighlights): TextCharacter =
    val isLight = (sq.file.toInt + sq.rank.toInt) % 2 != 0
    val bg: TextColor.RGB =
      if hl.cursor.contains(sq)        then new TextColor.RGB(247, 247, 105)
      else if hl.selected.contains(sq) then new TextColor.RGB(130, 151, 105)
      else if hl.targets.contains(sq)  then new TextColor.RGB(100, 130, 180)
      else if isLight                  then new TextColor.RGB(240, 217, 181)
      else                                  new TextColor.RGB(181, 136, 99)
    val defaultFg: TextColor = TextColor.ANSI.DEFAULT
    val (ch, fg) = board.pieceAt(sq).fold((' ', defaultFg: TextColor))(p => (pieceChar(p), pieceFg(p)))
    new TextCharacter(ch, fg, bg)

  private[ui] def pieceChar(piece: Piece): Char = piece.color match
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

  private def pieceFg(piece: Piece): TextColor =
    if piece.color == Color.White then new TextColor.RGB(255, 255, 255)
    else                               new TextColor.RGB(0, 0, 0)

  private object Renderer extends InteractableRenderer[BoardComponent]:
    override def getPreferredSize(component: BoardComponent): TerminalSize =
      new TerminalSize(25, 9)   // rank label + 8 files × 3 chars wide, 8 ranks × 1 row tall + file label row

    override def getCursorLocation(component: BoardComponent): TerminalPosition =
      val sq  = UIState.cursorSquare(component.cursorStateSnapshot)
      val col = if component.flipped then 1 + (7 - sq.file.toInt) * 3 + 1
                else                      1 + sq.file.toInt * 3 + 1
      val row = if component.flipped then sq.rank.toInt
                else                      7 - sq.rank.toInt
      new TerminalPosition(col, row)

    override def drawComponent(graphics: TextGUIGraphics, component: BoardComponent): Unit =
      val board   = component.snapshot.current.board
      val hl      = component.currentHighlights
      val flipped = component.flipped
      val labelFg = TextColor.ANSI.WHITE
      val labelBg = TextColor.ANSI.DEFAULT
      for rowIdx <- 0 to 7 do
        val ri    = if flipped then rowIdx else 7 - rowIdx
        val rankC = ('1' + ri).toChar
        val _ = graphics.setCharacter(0, rowIdx, new TextCharacter(rankC, labelFg, labelBg))
        for colIdx <- 0 to 7 do
          val fi    = if flipped then 7 - colIdx else colIdx
          val col   = 1 + colIdx * 3
          val optSq = for f <- File.fromInt(fi); r <- Rank.fromInt(ri) yield Square(f, r)
          optSq.foreach { square =>
            val tc    = renderSquare(board, square, hl)
            val space = new TextCharacter(' ', tc.getForegroundColor, tc.getBackgroundColor)
            val _ = graphics.setCharacter(col,     rowIdx, space)
            val _ = graphics.setCharacter(col + 1, rowIdx, tc)
            val _ = graphics.setCharacter(col + 2, rowIdx, space)
          }
      for colIdx <- 0 to 7 do
        val fi    = if flipped then 7 - colIdx else colIdx
        val label = ('a' + fi).toChar
        val _ = graphics.setCharacter(1 + colIdx * 3 + 1, 8, new TextCharacter(label, labelFg, labelBg))

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class BoardComponent(
  initialState: GameState,
  onMove: Move => Unit,
  shortcuts: Map[Char, () => Unit]
) extends AbstractInteractableComponent[BoardComponent]:

  private var gameState:    GameState   = initialState
  private var cursorState:  CursorState = initialCursorState
  private var boardEnabled: Boolean     = true
  var flipped:              Boolean     = false

  private def initialCursorState: CursorState =
    (for f <- File.fromInt(4); r <- Rank.fromInt(0) yield Square(f, r))
      .map(sq => CursorState.Navigating(sq): CursorState)
      .getOrElse(CursorState.Navigating(Square.all(0)))

  def snapshot: GameState = gameState

  def cursorStateSnapshot: CursorState = cursorState

  def currentHighlights: SquareHighlights = SquareHighlights(
    cursor   = Some(UIState.cursorSquare(cursorState)),
    selected = UIState.selectedSquare(cursorState),
    targets  = UIState.targetSquares(cursorState)
  )

  def updateState(newState: GameState): Unit =
    gameState = newState
    invalidate()

  def reset(newState: GameState): Unit =
    gameState   = newState
    cursorState = initialCursorState
    invalidate()

  def setBoardEnabled(flag: Boolean): Unit =
    boardEnabled = flag

  def setFlipped(flag: Boolean): Unit =
    flipped = flag
    invalidate()

  override protected def createDefaultRenderer(): InteractableRenderer[BoardComponent] =
    BoardComponent.Renderer

  // handleInput is final in AbstractInteractableComponent; override handleKeyStroke instead.
  override protected def handleKeyStroke(key: KeyStroke): Interactable.Result =
    key match
      case ma: MouseAction             => if boardEnabled then handleMouse(ma) else Interactable.Result.UNHANDLED
      case _ if key.getKeyType == KeyType.Tab        => Interactable.Result.MOVE_FOCUS_NEXT
      case _ if key.getKeyType == KeyType.ReverseTab => Interactable.Result.MOVE_FOCUS_PREVIOUS
      case _ if key.getKeyType == KeyType.Character =>
        handleShortcut(key.getCharacter)
      case _ if boardEnabled           => handleKey(key)
      case _                           => Interactable.Result.UNHANDLED

  private def handleShortcut(ch: Char): Interactable.Result =
    shortcuts.get(ch.toLower) match
      case Some(action) =>
        action()
        Interactable.Result.HANDLED
      case None => Interactable.Result.UNHANDLED

  private def flipDir(dir: Direction): Direction =
    if flipped then dir match
      case Direction.Up    => Direction.Down
      case Direction.Down  => Direction.Up
      case Direction.Left  => Direction.Right
      case Direction.Right => Direction.Left
    else dir

  private def handleKey(key: KeyStroke): Interactable.Result =
    val optNewCs: Option[CursorState] = cursorState match
      case CursorState.Navigating(cursor) => key.getKeyType match
        case KeyType.ArrowUp    => Some(CursorState.Navigating(UIState.moveCursorFree(cursor, flipDir(Direction.Up))))
        case KeyType.ArrowDown  => Some(CursorState.Navigating(UIState.moveCursorFree(cursor, flipDir(Direction.Down))))
        case KeyType.ArrowLeft  => Some(CursorState.Navigating(UIState.moveCursorFree(cursor, flipDir(Direction.Left))))
        case KeyType.ArrowRight => Some(CursorState.Navigating(UIState.moveCursorFree(cursor, flipDir(Direction.Right))))
        case KeyType.Enter      => selectPiece(cursor)
        case _                  => None
      case CursorState.PieceSelected(from, index, targets) => key.getKeyType match
        case KeyType.ArrowUp | KeyType.ArrowLeft =>
          Some(CursorState.PieceSelected(from, UIState.moveCursorTargets(targets, index, Direction.Up), targets))
        case KeyType.ArrowDown | KeyType.ArrowRight =>
          Some(CursorState.PieceSelected(from, UIState.moveCursorTargets(targets, index, Direction.Down), targets))
        case KeyType.Enter =>
          onMove(targets(index))
          Some(CursorState.Navigating(targets(index).to))
        case KeyType.Escape => Some(CursorState.Navigating(from))
        case _              => None
    optNewCs match
      case Some(newCs) =>
        cursorState = newCs
        invalidate()
        Interactable.Result.HANDLED
      case None => Interactable.Result.UNHANDLED

  private def handleMouse(ma: MouseAction): Interactable.Result =
    if ma.getActionType == MouseActionType.CLICK_DOWN then
      val origin   = toGlobal(TerminalPosition.TOP_LEFT_CORNER)
      val pos      = ma.getPosition
      val boardCol = pos.getColumn - origin.getColumn - 1
      val rawFi    = boardCol / 3
      val rawRi    = pos.getRow - origin.getRow
      val fi       = if flipped then 7 - rawFi else rawFi
      val ri       = if flipped then rawRi else 7 - rawRi
      val optCs = for
        _ <- Option.when(boardCol >= 0)(())
        f <- File.fromInt(fi)
        r <- Rank.fromInt(ri)
      yield handleSquareClick(Square(f, r))
      optCs match
        case Some(cs) =>
          cursorState = cs
          invalidate()
          Interactable.Result.HANDLED
        case None => Interactable.Result.UNHANDLED
    else Interactable.Result.UNHANDLED

  private def selectPiece(sq: Square): Option[CursorState] =
    val raw     = StandardRules.legalMoves(gameState.current, sq)
    val deduped = raw.distinctBy(_.to).sortBy(m => m.to.rank.toInt * 8 + m.to.file.toInt)
    Option.when(deduped.nonEmpty)(CursorState.PieceSelected(sq, 0, deduped.toIndexedSeq))

  private def handleSquareClick(sq: Square): CursorState = cursorState match
    case cs @ CursorState.Navigating(_) =>
      selectPiece(sq).getOrElse(cs)
    case cs @ CursorState.PieceSelected(from, _, targets) =>
      targets.find(_.to == sq) match
        case Some(move) =>
          onMove(move)
          CursorState.Navigating(sq)
        case None =>
          if sq == from then CursorState.Navigating(from)
          else selectPiece(sq).getOrElse(cs)
