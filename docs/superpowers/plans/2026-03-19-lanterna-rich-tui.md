# Lanterna Rich TUI Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers-extended-cc:subagent-driven-development (if subagents available) or superpowers-extended-cc:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the JLine-based text UI with a Lanterna-backed rich terminal UI featuring Unicode chess pieces, truecolor board squares, mouse support, a side panel with move history and captures, a pawn promotion dialog, and a New Game / Resign menu.

**Architecture:** Six focused files replace `TextUI.scala`: `UIState` (pure cursor logic), `BoardComponent` (custom Lanterna component), `SidePanel` (status + history), `PromotionDialog` (modal), `MenuBar` (actions), and `LanternaUI` (orchestrator). All mutable state is confined to `LanternaUI` and `BoardComponent`, each annotated with `@SuppressWarnings(Array("org.wartremover.warts.Var"))`. Pure helper functions (`renderSquare`, `capturedPieces`, `moveNotation`) are extracted and unit-tested in the existing `tests` module.

**Tech Stack:** Scala 3.8.2, sbt 1.12.6, Lanterna 3.1.2, munit 1.0.4, WartRemover (`Warts.unsafe` as errors)

---

## File Map

| Action | Path |
|---|---|
| **Create** | `modules/ui-text/src/main/scala/UIState.scala` |
| **Create** | `modules/ui-text/src/main/scala/BoardComponent.scala` |
| **Create** | `modules/ui-text/src/main/scala/SidePanel.scala` |
| **Create** | `modules/ui-text/src/main/scala/PromotionDialog.scala` |
| **Create** | `modules/ui-text/src/main/scala/MenuBar.scala` |
| **Create** | `modules/ui-text/src/main/scala/LanternaUI.scala` |
| **Modify** | `build.sbt` — swap JLine for Lanterna (**done first in Task 2**) |
| **Delete** | `modules/ui-text/src/main/scala/TextUI.scala` (Task 8) |
| **Modify** | `modules/tests/src/test/scala/TextUISuite.scala` — update imports + replace ANSI tests |

---

## Key Lanterna 3.1.2 API Reference

```scala
// Root package
import com.googlecode.lanterna.{TextCharacter, TextColor, TerminalSize}

// GUI — all in com.googlecode.lanterna.gui2
import com.googlecode.lanterna.gui2.{
  AbstractInteractableComponent, BasicWindow, BorderLayout, Button,
  ComponentRenderer, DefaultWindowManager, EmptySpace, Interactable,
  Label, LinearLayout, MultiWindowTextGUI, Panel, ScrolledPanel,
  WindowBasedTextGUI
}
import com.googlecode.lanterna.gui2.{Direction => LDirection}  // HORIZONTAL / VERTICAL

// Input — com.googlecode.lanterna.input
import com.googlecode.lanterna.input.{KeyStroke, KeyType, MouseAction, MouseActionType}
// MouseAction extends KeyStroke in Lanterna 3.1.2

// Graphics
import com.googlecode.lanterna.graphics.TextGraphics

// Screen / Terminal
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
```

**Critical Lanterna 3.1.2 method names:**
- Input handling override: `handleInput(key: KeyStroke): Interactable.Result` (NOT `handleKeyStroke` or `handleMouseEvent`)
- Mouse events arrive as `MouseAction extends KeyStroke` — detect with `case ma: MouseAction =>` in a pattern match
- Result constants: `Interactable.Result.HANDLED`, `Interactable.Result.UNHANDLED`
- Component renderer override: `createDefaultRenderer(): ComponentRenderer[T]`
- Preferred size override: `getPreferredSize(component: T): TerminalSize`
- Draw override: `drawComponent(graphics: TextGraphics, component: T): Unit`
- GUI method: `gui.addWindowAndWait(window)` — blocks until window closes (nested event loop, safe from callbacks)

---

## WartRemover Rules for This Codebase

- `var` banned (`Warts.Var`) — only `LanternaUI` and `BoardComponent` may use `var`, annotated with `@SuppressWarnings(Array("org.wartremover.warts.Var"))`
- `return` banned — use `if/else` expressions
- `asInstanceOf`/`isInstanceOf` banned — use `match` with subtype patterns (`case ma: MouseAction =>` is fine, it is NOT `isInstanceOf`)
- `While` banned — use `for` comprehensions
- String interpolation with `Any` banned — use `+` concatenation
- Discarded values: use `val _ = expr` for any expression returning non-Unit that is discarded (including fluent API calls like `setLayoutManager(...)`, `addComponent(...)`)
- `AtomicReference[T]` is the WartRemover-safe way to hold one mutable value inside a method without `var`

---

## Task 2: Build Setup + UIState

**Files:**
- Modify: `build.sbt`
- Create: `modules/ui-text/src/main/scala/UIState.scala`
- Modify: `modules/tests/src/test/scala/TextUISuite.scala`

> **Build setup is done first** so that all subsequent compile checks in Tasks 3–7 succeed.

### Step 2.1: Update build.sbt — swap JLine for Lanterna

In `build.sbt`, find the `uiText` project definition and make two changes:

**Change 1** — remove `run / connectInput := true`:
```scala
// Before:
.settings(run / fork := true, run / connectInput := true)

// After:
.settings(run / fork := true)
```

**Change 2** — replace JLine deps with Lanterna:
```scala
// Before:
libraryDependencies ++= Seq(
  "org.jline" % "jline-terminal" % "3.29.0",
  "org.jline" % "jline-reader"   % "3.29.0"
)

// After:
libraryDependencies ++= Seq(
  "com.googlecode.lanterna" % "lanterna" % "3.1.2"
)
```

- [ ] Update build.sbt

### Step 2.2: Verify build resolves

```bash
sbt update
```

Expected: Lanterna 3.1.2 downloaded successfully, no missing dependencies.

- [ ] Confirm `sbt update` succeeds

### Step 2.3: Create UIState.scala

```scala
package org.maichess.mono.ui

import org.maichess.mono.model.*

enum Direction:
  case Up, Down, Left, Right

enum CursorState:
  case Navigating(cursor: Square)
  case PieceSelected(from: Square, index: Int, targets: IndexedSeq[Move])

object UIState:

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
```

- [ ] Write `modules/ui-text/src/main/scala/UIState.scala`

### Step 2.4: Update TextUISuite imports

In `modules/tests/src/test/scala/TextUISuite.scala`, change line 5:

```scala
// Before:
import org.maichess.mono.ui.{CursorState, Direction, TextUI}

// After:
import org.maichess.mono.ui.{CursorState, Direction, UIState}
```

Replace every `TextUI.cursorSquare` → `UIState.cursorSquare`, `TextUI.selectedSquare` → `UIState.selectedSquare`, `TextUI.targetSquares` → `UIState.targetSquares`, `TextUI.moveCursorFree` → `UIState.moveCursorFree`, `TextUI.moveCursorTargets` → `UIState.moveCursorTargets`.

- [ ] Update the import and 5 call sites

### Step 2.5: Run cursor logic tests

```bash
sbt "testOnly *TextUISuite"
```

Expected: all cursor tests pass (17 tests). The five `renderBoard` ANSI tests still call `TextUI.renderBoard` and will fail — that is expected and will be fixed in Task 3.

- [ ] Confirm cursor tests pass, renderBoard tests fail (expected)

### Step 2.6: Commit

```bash
git add build.sbt \
        modules/ui-text/src/main/scala/UIState.scala \
        modules/tests/src/test/scala/TextUISuite.scala
git commit -m "refactor: add Lanterna dep and extract UIState from TextUI"
```

- [ ] Commit

---

## Task 3: BoardComponent — Rendering + Input

**Files:**
- Create: `modules/ui-text/src/main/scala/BoardComponent.scala`
- Modify: `modules/tests/src/test/scala/TextUISuite.scala`

### Step 3.1: Write failing renderSquare tests

Add these imports to `TextUISuite.scala`:

```scala
import com.googlecode.lanterna.TextColor
import org.maichess.mono.ui.{BoardComponent, SquareHighlights}
```

Replace the five ANSI `renderBoard` tests with:

```scala
// ── renderSquare ─────────────────────────────────────────────────────────────

private def noHl: SquareHighlights = SquareHighlights(None, None, Set.empty)

test("renderSquare: a1 is dark (file=0 + rank=0 = 0, even)"):
  val tc = BoardComponent.renderSquare(Board.empty, sq("a1"), noHl)
  assertEquals(tc.getBackgroundColor, new TextColor.RGB(181, 136, 99))

test("renderSquare: b1 is light (file=1 + rank=0 = 1, odd)"):
  val tc = BoardComponent.renderSquare(Board.empty, sq("b1"), noHl)
  assertEquals(tc.getBackgroundColor, new TextColor.RGB(240, 217, 181))

test("renderSquare: cursor square has yellow background"):
  val hl = SquareHighlights(cursor = Some(sq("e4")), None, Set.empty)
  val tc = BoardComponent.renderSquare(Board.empty, sq("e4"), hl)
  assertEquals(tc.getBackgroundColor, new TextColor.RGB(247, 247, 105))

test("renderSquare: selected square has green background"):
  val hl = SquareHighlights(None, selected = Some(sq("e2")), Set.empty)
  val tc = BoardComponent.renderSquare(Board.empty, sq("e2"), hl)
  assertEquals(tc.getBackgroundColor, new TextColor.RGB(130, 151, 105))

test("renderSquare: target square has blue background"):
  val hl = SquareHighlights(None, None, targets = Set(sq("e4")))
  val tc = BoardComponent.renderSquare(Board.empty, sq("e4"), hl)
  assertEquals(tc.getBackgroundColor, new TextColor.RGB(100, 130, 180))

test("renderSquare: white king on e1 shows \u2654"):
  val tc = BoardComponent.renderSquare(Board.standard, sq("e1"), noHl)
  assertEquals(tc.getCharacter, '\u2654')

test("renderSquare: black queen on d8 shows \u265B"):
  val tc = BoardComponent.renderSquare(Board.standard, sq("d8"), noHl)
  assertEquals(tc.getCharacter, '\u265B')

test("renderSquare: empty square shows space"):
  val tc = BoardComponent.renderSquare(Board.empty, sq("e4"), noHl)
  assertEquals(tc.getCharacter, ' ')
```

- [ ] Replace ANSI tests with the 8 tests above (keep all cursor tests)

### Step 3.2: Run tests to confirm they fail

```bash
sbt "testOnly *TextUISuite"
```

Expected: 8 new tests fail — "object BoardComponent is not a member of package org.maichess.mono.ui"

- [ ] Confirm tests fail for the right reason

### Step 3.3: Create BoardComponent.scala

```scala
package org.maichess.mono.ui

import com.googlecode.lanterna.{TextCharacter, TextColor, TerminalSize}
import com.googlecode.lanterna.graphics.TextGraphics
import com.googlecode.lanterna.gui2.{AbstractInteractableComponent, ComponentRenderer, Interactable}
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

  // Light squares: (file.toInt + rank.toInt) is odd
  def renderSquare(board: Board, sq: Square, hl: SquareHighlights): TextCharacter =
    val isLight = (sq.file.toInt + sq.rank.toInt) % 2 != 0
    val bg: TextColor.RGB =
      if hl.cursor.contains(sq)        then new TextColor.RGB(247, 247, 105)
      else if hl.selected.contains(sq) then new TextColor.RGB(130, 151, 105)
      else if hl.targets.contains(sq)  then new TextColor.RGB(100, 130, 180)
      else if isLight                  then new TextColor.RGB(240, 217, 181)
      else                                  new TextColor.RGB(181, 136, 99)
    val (pieceChar, fg) = board.pieceAt(sq).fold((' ', TextColor.ANSI.DEFAULT: TextColor))(pieceCharAndColor)
    new TextCharacter(pieceChar, fg, bg)

  private def pieceCharAndColor(piece: Piece): (Char, TextColor) =
    val ch = piece.color match
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
    val fg: TextColor =
      if piece.color == Color.White then new TextColor.RGB(255, 255, 255)
      else                               new TextColor.RGB(0, 0, 0)
    (ch, fg)

  private object Renderer extends ComponentRenderer[BoardComponent]:
    override def getPreferredSize(component: BoardComponent): TerminalSize =
      new TerminalSize(24, 8)   // 8 files × 3 chars wide, 8 ranks × 1 row tall

    override def drawComponent(graphics: TextGraphics, component: BoardComponent): Unit =
      val board = component.snapshot.current.board
      val hl    = component.currentHighlights
      for ri <- 7 to 0 by -1 do
        val row = 7 - ri
        for fi <- 0 to 7 do
          val col = fi * 3
          val optSq = for f <- File.fromInt(fi); r <- Rank.fromInt(ri) yield Square(f, r)
          optSq.foreach { square =>
            val tc    = renderSquare(board, square, hl)
            val space = new TextCharacter(' ', tc.getForegroundColor, tc.getBackgroundColor)
            val _ = graphics.setCharacter(col,     row, space)
            val _ = graphics.setCharacter(col + 1, row, tc)
            val _ = graphics.setCharacter(col + 2, row, space)
          }

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class BoardComponent(
  initialState: GameState,
  onMove: Move => Unit
) extends AbstractInteractableComponent[BoardComponent]:

  private var gameState:   GameState   = initialState
  private var cursorState: CursorState =
    CursorState.Navigating(Square.fromAlgebraic("e1").getOrElse(Square.all.head))
  private var enabled: Boolean = true

  def snapshot: GameState = gameState

  def currentHighlights: SquareHighlights = SquareHighlights(
    cursor   = Some(UIState.cursorSquare(cursorState)),
    selected = UIState.selectedSquare(cursorState),
    targets  = UIState.targetSquares(cursorState)
  )

  def updateState(newState: GameState): Unit =
    gameState = newState
    invalidate()

  def reset(newState: GameState): Unit =
    gameState = newState
    cursorState = CursorState.Navigating(Square.fromAlgebraic("e1").getOrElse(Square.all.head))
    invalidate()

  def setEnabled(flag: Boolean): Unit =
    enabled = flag

  override protected def createDefaultRenderer(): ComponentRenderer[BoardComponent] =
    BoardComponent.Renderer

  // Mouse events arrive as MouseAction (subtype of KeyStroke).
  // handleInput is the single entry point in Lanterna 3.1.2.
  override def handleInput(key: KeyStroke): Interactable.Result =
    if enabled then
      key match
        case ma: MouseAction => handleMouse(ma)
        case _               => handleKey(key)
    else Interactable.Result.UNHANDLED

  private def handleKey(key: KeyStroke): Interactable.Result =
    val newCs = cursorState match
      case CursorState.Navigating(cursor) => key.getKeyType match
        case KeyType.ArrowUp    => CursorState.Navigating(UIState.moveCursorFree(cursor, Direction.Up))
        case KeyType.ArrowDown  => CursorState.Navigating(UIState.moveCursorFree(cursor, Direction.Down))
        case KeyType.ArrowLeft  => CursorState.Navigating(UIState.moveCursorFree(cursor, Direction.Left))
        case KeyType.ArrowRight => CursorState.Navigating(UIState.moveCursorFree(cursor, Direction.Right))
        case KeyType.Enter      => selectPiece(cursor)
        case _                  => cursorState
      case CursorState.PieceSelected(from, index, targets) => key.getKeyType match
        case KeyType.ArrowUp | KeyType.ArrowLeft =>
          CursorState.PieceSelected(from, UIState.moveCursorTargets(targets, index, Direction.Up), targets)
        case KeyType.ArrowDown | KeyType.ArrowRight =>
          CursorState.PieceSelected(from, UIState.moveCursorTargets(targets, index, Direction.Down), targets)
        case KeyType.Enter =>
          onMove(targets(index))
          CursorState.Navigating(targets(index).to)
        case KeyType.Escape => CursorState.Navigating(from)
        case _              => cursorState
    cursorState = newCs
    invalidate()
    Interactable.Result.HANDLED

  private def handleMouse(ma: MouseAction): Interactable.Result =
    if ma.getActionType == MouseActionType.CLICK_DOWN then
      val pos = ma.getPosition
      val fi  = pos.getColumn / 3
      val ri  = 7 - pos.getRow
      val optCs = for f <- File.fromInt(fi); r <- Rank.fromInt(ri)
        yield handleSquareClick(Square(f, r))
      optCs.foreach { cs => cursorState = cs }
      invalidate()
      Interactable.Result.HANDLED
    else Interactable.Result.UNHANDLED

  private def selectPiece(cursor: Square): CursorState =
    val raw     = StandardRules.legalMoves(gameState.current, cursor)
    val deduped = raw.distinctBy(_.to).sortBy(m => m.to.rank.toInt * 8 + m.to.file.toInt)
    if deduped.isEmpty then CursorState.Navigating(cursor)
    else CursorState.PieceSelected(cursor, 0, deduped.toIndexedSeq)

  private def handleSquareClick(sq: Square): CursorState = cursorState match
    case CursorState.Navigating(_) =>
      selectPiece(sq)
    case CursorState.PieceSelected(from, _, targets) =>
      targets.find(_.to == sq) match
        case Some(move) =>
          onMove(move)
          CursorState.Navigating(sq)
        case None =>
          if sq == from then CursorState.Navigating(from)
          else selectPiece(sq)
```

- [ ] Write `modules/ui-text/src/main/scala/BoardComponent.scala`

### Step 3.4: Run renderSquare tests

```bash
sbt "testOnly *TextUISuite"
```

Expected: all 8 new `renderSquare` tests pass; all cursor tests pass. ~25 total.

- [ ] Confirm all tests pass

### Step 3.5: Compile check

```bash
sbt "uiText/compile"
```

Expected: clean compile, no WartRemover errors.

- [ ] Confirm clean compile

### Step 3.6: Commit

```bash
git add modules/ui-text/src/main/scala/BoardComponent.scala \
        modules/tests/src/test/scala/TextUISuite.scala
git commit -m "feat: add BoardComponent with renderSquare, keyboard, and mouse input"
```

- [ ] Commit

---

## Task 4: SidePanel — Status, Captures, History

**Files:**
- Create: `modules/ui-text/src/main/scala/SidePanel.scala`
- Modify: `modules/tests/src/test/scala/TextUISuite.scala`

### Step 4.1: Write failing pure-function tests

Add to `TextUISuite.scala`:

```scala
import org.maichess.mono.ui.SidePanel
import org.maichess.mono.model.{NormalMove, CastlingMove, EnPassantMove, PieceType}

// ── capturedPieces ────────────────────────────────────────────────────────────

test("capturedPieces: normal capture returns the captured opponent piece"):
  val blackPawn = Piece(Color.Black, PieceType.Pawn)
  val before    = Board(Map(sq("e5") -> blackPawn))
  val after     = Board(Map.empty)
  assertEquals(SidePanel.capturedPieces(before, after, Color.White), List(blackPawn))

test("capturedPieces: moving player own piece not included"):
  val whitePawn = Piece(Color.White, PieceType.Pawn)
  val before    = Board(Map(sq("e2") -> whitePawn))
  val after     = Board(Map(sq("e4") -> whitePawn))
  assertEquals(SidePanel.capturedPieces(before, after, Color.White), List.empty)

test("capturedPieces: promotion-capture returns captured piece, not moving pawn"):
  val whitePawn  = Piece(Color.White, PieceType.Pawn)
  val blackRook  = Piece(Color.Black, PieceType.Rook)
  val whiteQueen = Piece(Color.White, PieceType.Queen)
  val before = Board(Map(sq("e7") -> whitePawn, sq("d8") -> blackRook))
  val after  = Board(Map(sq("d8") -> whiteQueen))
  assertEquals(SidePanel.capturedPieces(before, after, Color.White), List(blackRook))

test("capturedPieces: en passant capture returns the captured pawn"):
  val whitePawn = Piece(Color.White, PieceType.Pawn)
  val blackPawn = Piece(Color.Black, PieceType.Pawn)
  val before    = Board(Map(sq("e5") -> whitePawn, sq("d5") -> blackPawn))
  val after     = Board(Map(sq("d6") -> whitePawn))
  assertEquals(SidePanel.capturedPieces(before, after, Color.White), List(blackPawn))

test("capturedPieces: promotion without capture returns empty"):
  val whitePawn  = Piece(Color.White, PieceType.Pawn)
  val whiteQueen = Piece(Color.White, PieceType.Queen)
  val before = Board(Map(sq("e7") -> whitePawn))
  val after  = Board(Map(sq("e8") -> whiteQueen))
  assertEquals(SidePanel.capturedPieces(before, after, Color.White), List.empty)

// ── moveNotation ──────────────────────────────────────────────────────────────

test("moveNotation: normal move"):
  assertEquals(SidePanel.moveNotation(NormalMove(sq("e2"), sq("e4"), None)), "e2-e4")

test("moveNotation: promotion appends piece letter"):
  assertEquals(
    SidePanel.moveNotation(NormalMove(sq("e7"), sq("e8"), Some(PieceType.Queen))),
    "e7-e8=Q"
  )

test("moveNotation: kingside castling"):
  // rookFrom(h1) file=7 > from(e1) file=4
  assertEquals(
    SidePanel.moveNotation(CastlingMove(sq("e1"), sq("g1"), sq("h1"), sq("f1"))),
    "O-O"
  )

test("moveNotation: queenside castling"):
  // rookFrom(a1) file=0 < from(e1) file=4
  assertEquals(
    SidePanel.moveNotation(CastlingMove(sq("e1"), sq("c1"), sq("a1"), sq("d1"))),
    "O-O-O"
  )

test("moveNotation: en passant uses same file-rank format"):
  assertEquals(
    SidePanel.moveNotation(EnPassantMove(sq("e5"), sq("d6"), sq("d5"))),
    "e5-d6"
  )
```

- [ ] Add the 10 tests above to `TextUISuite.scala`

### Step 4.2: Run tests to confirm they fail

```bash
sbt "testOnly *TextUISuite"
```

Expected: 10 new tests fail — "object SidePanel is not a member..."

- [ ] Confirm tests fail

### Step 4.3: Create SidePanel.scala

```scala
package org.maichess.mono.ui

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.{Direction => LDirection, Label, LinearLayout, Panel, ScrolledPanel}
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

  def pieceSymbol(piece: Piece): String = piece.color match
    case Color.White => piece.pieceType match
      case PieceType.King   => "\u2654"
      case PieceType.Queen  => "\u2655"
      case PieceType.Rook   => "\u2656"
      case PieceType.Bishop => "\u2657"
      case PieceType.Knight => "\u2658"
      case PieceType.Pawn   => "\u2659"
    case Color.Black => piece.pieceType match
      case PieceType.King   => "\u265A"
      case PieceType.Queen  => "\u265B"
      case PieceType.Rook   => "\u265C"
      case PieceType.Bishop => "\u265D"
      case PieceType.Knight => "\u265E"
      case PieceType.Pawn   => "\u265F"

class SidePanel extends Panel:
  private val _ = setLayoutManager(new LinearLayout(LDirection.VERTICAL))

  private val statusLabel        = new Label("White to move")
  private val capturedWhiteLabel = new Label("White captured: ")
  private val capturedBlackLabel = new Label("Black captured: ")
  private val historyInner       = new Panel(new LinearLayout(LDirection.VERTICAL))
  private val historyScroll      = new ScrolledPanel(historyInner, new TerminalSize(22, 12))

  private val _ = addComponent(statusLabel)
  private val _ = addComponent(capturedWhiteLabel)
  private val _ = addComponent(capturedBlackLabel)
  private val _ = addComponent(historyScroll)

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
    historyInner.removeAllComponents()
    history.zipWithIndex.grouped(2).foreach { pair =>
      val white = pair.headOption.fold("")(_._1)
      val black = pair.lift(1).fold("")(_._1)
      val num   = (pair.headOption.fold(0)(_._2 / 2) + 1).toString + ".  "
      val _ = historyInner.addComponent(new Label(num + white + "    " + black))
    }
```

**Note on `refreshHistory`:** `zipWithIndex.grouped(2)` groups into pairs of `(halfMove, globalIndex)`. Each group is a `List[(String, Int)]`. `headOption` and `lift(1)` on `List` are safe (not banned by WartRemover — only `head`, `last`, `tail` without the `Option` wrapper are banned). `fold("")` avoids any partial access.

- [ ] Write `modules/ui-text/src/main/scala/SidePanel.scala`

### Step 4.4: Run SidePanel tests

```bash
sbt "testOnly *TextUISuite"
```

Expected: all 10 new tests pass plus all prior tests. ~35 total.

- [ ] Confirm all tests pass

### Step 4.5: Compile check

```bash
sbt "uiText/compile"
```

- [ ] Confirm clean compile

### Step 4.6: Commit

```bash
git add modules/ui-text/src/main/scala/SidePanel.scala \
        modules/tests/src/test/scala/TextUISuite.scala
git commit -m "feat: add SidePanel with capturedPieces and moveNotation helpers"
```

- [ ] Commit

---

## Task 5: PromotionDialog

**Files:**
- Create: `modules/ui-text/src/main/scala/PromotionDialog.scala`

No unit tests — thin Lanterna wrapper verified during integration.

### Step 5.1: Create PromotionDialog.scala

`AtomicReference` is used instead of `var` to capture the button result safely.

```scala
package org.maichess.mono.ui

import com.googlecode.lanterna.gui2.{BasicWindow, Button, LinearLayout, Panel, WindowBasedTextGUI}
import com.googlecode.lanterna.gui2.{Direction => LDirection}
import java.util.concurrent.atomic.AtomicReference
import org.maichess.mono.model.PieceType

object PromotionDialog:

  def show(gui: WindowBasedTextGUI): PieceType =
    val result = new AtomicReference[PieceType](PieceType.Queen)
    val window = new BasicWindow("Promote pawn to:")
    val panel  = new Panel(new LinearLayout(LDirection.HORIZONTAL))

    def makeButton(label: String, pt: PieceType): Button =
      new Button(label, new Runnable:
        def run(): Unit =
          val _ = result.set(pt)
          window.close()
      )

    val _ = panel.addComponent(makeButton("Queen",  PieceType.Queen))
    val _ = panel.addComponent(makeButton("Rook",   PieceType.Rook))
    val _ = panel.addComponent(makeButton("Bishop", PieceType.Bishop))
    val _ = panel.addComponent(makeButton("Knight", PieceType.Knight))

    window.setComponent(panel)
    gui.addWindowAndWait(window)
    result.get()
```

- [ ] Write `modules/ui-text/src/main/scala/PromotionDialog.scala`

### Step 5.2: Compile check

```bash
sbt "uiText/compile"
```

- [ ] Confirm clean compile

### Step 5.3: Commit

```bash
git add modules/ui-text/src/main/scala/PromotionDialog.scala
git commit -m "feat: add PromotionDialog for pawn promotion piece selection"
```

- [ ] Commit

---

## Task 6: MenuBar

**Files:**
- Create: `modules/ui-text/src/main/scala/MenuBar.scala`

No unit tests.

### Step 6.1: Create MenuBar.scala

```scala
package org.maichess.mono.ui

import com.googlecode.lanterna.gui2.{Button, LinearLayout, Panel}
import com.googlecode.lanterna.gui2.{Direction => LDirection}

class MenuBar(onNewGame: () => Unit, onResign: () => Unit) extends Panel:
  private val _ = setLayoutManager(new LinearLayout(LDirection.HORIZONTAL))

  private val _ = addComponent(new Button("New Game", new Runnable:
    def run(): Unit = onNewGame()
  ))
  private val _ = addComponent(new Button("Resign", new Runnable:
    def run(): Unit = onResign()
  ))
```

- [ ] Write `modules/ui-text/src/main/scala/MenuBar.scala`

### Step 6.2: Compile check

```bash
sbt "uiText/compile"
```

- [ ] Confirm clean compile

### Step 6.3: Commit

```bash
git add modules/ui-text/src/main/scala/MenuBar.scala
git commit -m "feat: add MenuBar with New Game and Resign actions"
```

- [ ] Commit

---

## Task 7: LanternaUI — Orchestrator

**Files:**
- Create: `modules/ui-text/src/main/scala/LanternaUI.scala`

No unit tests.

### Step 7.1: Create LanternaUI.scala

**Key fix:** Promotion is detected by pattern-matching the confirmed `Move` against `NormalMove` with `promotion.isDefined`. The `Move` sealed trait has no `promotion` field — only `NormalMove` does.

**Key fix:** `lazy val boardComponent` avoids the forward-reference problem: the `onMove` callback references `boardComponent`, which is safe because the lambda is not called until after `boardComponent` is fully initialised.

```scala
package org.maichess.mono.ui

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.{BasicWindow, BorderLayout, DefaultWindowManager, EmptySpace, MultiWindowTextGUI, Panel, WindowBasedTextGUI}
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import org.maichess.mono.engine.{DrawReason, GameController, GameResult, GameState}
import org.maichess.mono.model.*
import org.maichess.mono.rules.StandardRules

@SuppressWarnings(Array("org.wartremover.warts.Var"))
object LanternaUI:

  def start(ctrl: GameController, gui: WindowBasedTextGUI): Unit =
    var gameState:     GameState    = ctrl.newGame()
    var moveHistory:   List[String] = List.empty
    var capturedWhite: List[Piece]  = List.empty
    var capturedBlack: List[Piece]  = List.empty

    val sidePanel = new SidePanel()

    // lazy val avoids forward reference: the lambda captures boardComponent
    // but it is only called after boardComponent is fully initialised.
    lazy val boardComponent: BoardComponent = new BoardComponent(gameState, move => {
      val finalMove = move match
        case nm: NormalMove if nm.promotion.isDefined =>
          NormalMove(nm.from, nm.to, Some(PromotionDialog.show(gui)))
        case _ => move

      ctrl.applyMove(gameState, finalMove) match
        case Right(newState) =>
          val newCaptures = SidePanel.capturedPieces(
            gameState.current.board,
            newState.current.board,
            gameState.current.turn
          )
          if gameState.current.turn == Color.White then
            capturedBlack = capturedBlack ++ newCaptures
          else
            capturedWhite = capturedWhite ++ newCaptures
          moveHistory = moveHistory :+ SidePanel.moveNotation(finalMove)
          gameState = newState
          boardComponent.updateState(newState)
          sidePanel.update(newState, moveHistory, capturedWhite, capturedBlack)
          ctrl.gameResult(newState).foreach { result =>
            sidePanel.showResult(resultMessage(result))
            boardComponent.setEnabled(false)
          }
        case Left(_) => ()
    })

    val menuBar = new MenuBar(
      onNewGame = () => {
        val newState = ctrl.newGame()
        gameState     = newState
        moveHistory   = List.empty
        capturedWhite = List.empty
        capturedBlack = List.empty
        boardComponent.reset(newState)
        boardComponent.setEnabled(true)
        sidePanel.update(newState, moveHistory, capturedWhite, capturedBlack)
      },
      onResign = () => {
        val resigning = if gameState.current.turn == Color.White then "White" else "Black"
        val winner    = if gameState.current.turn == Color.White then "Black" else "White"
        sidePanel.showResult(resigning + " resigned. " + winner + " wins.")
        boardComponent.setEnabled(false)
      }
    )

    val mainPanel = new Panel(new BorderLayout())
    val _ = mainPanel.addComponent(menuBar,        BorderLayout.Location.TOP)
    val _ = mainPanel.addComponent(boardComponent, BorderLayout.Location.CENTER)
    val _ = mainPanel.addComponent(sidePanel,      BorderLayout.Location.RIGHT)

    val window = new BasicWindow("MaiChess")
    window.setComponent(mainPanel)
    val _ = gui.addWindowAndWait(window)

  private def resultMessage(result: GameResult): String = result match
    case GameResult.Checkmate(Color.White)                => "Checkmate \u2014 White wins."
    case GameResult.Checkmate(Color.Black)                => "Checkmate \u2014 Black wins."
    case GameResult.Stalemate                             => "Stalemate \u2014 draw."
    case GameResult.Draw(DrawReason.FiftyMoveRule)        => "Draw: 50-move rule."
    case GameResult.Draw(DrawReason.InsufficientMaterial) => "Draw: insufficient material."
    case GameResult.Draw(DrawReason.ThreefoldRepetition)  => "Draw: threefold repetition."
    case GameResult.Draw(DrawReason.Agreement)            => "Draw by agreement."

@main def runGame(): Unit =
  val ctrl     = new GameController(StandardRules)
  val terminal = new DefaultTerminalFactory().createTerminal()
  val screen   = new TerminalScreen(terminal)
  val _        = screen.startScreen()
  try
    val gui = new MultiWindowTextGUI(
      screen,
      new DefaultWindowManager(),
      new EmptySpace(TextColor.ANSI.BLACK)
    )
    LanternaUI.start(ctrl, gui)
  finally
    val _ = screen.stopScreen()
    terminal.close()
```

- [ ] Write `modules/ui-text/src/main/scala/LanternaUI.scala`

### Step 7.2: Compile check

```bash
sbt "uiText/compile"
```

If WartRemover flags `nm: NormalMove` in the match (it should not — subtype patterns are not `isInstanceOf`), add `@unchecked`. If it flags the `lazy val`, it should not — `Warts.Var` only bans `var`, not `lazy val`.

- [ ] Confirm clean compile

### Step 7.3: Commit

```bash
git add modules/ui-text/src/main/scala/LanternaUI.scala
git commit -m "feat: add LanternaUI orchestrator and entry point"
```

- [ ] Commit

---

## Task 8: Wire — Delete TextUI.scala, Final Verification

**Files:**
- Delete: `modules/ui-text/src/main/scala/TextUI.scala`

### Step 8.1: Delete TextUI.scala

```bash
rm modules/ui-text/src/main/scala/TextUI.scala
```

- [ ] Delete TextUI.scala

### Step 8.2: Full compile

```bash
sbt compile
```

Expected: all modules compile cleanly with no errors.

- [ ] Confirm clean compile

### Step 8.3: Run all tests

```bash
sbt test
```

Expected: all tests pass.

- [ ] Confirm all tests pass

### Step 8.4: Smoke-test the game

```bash
sbt run
```

Verify in the terminal:
- [ ] Board renders with Unicode chess pieces (♔♕♖♗♘♙ / ♚♛♜♝♞♟) on colored squares
- [ ] Arrow keys move the yellow cursor square
- [ ] Enter on a piece highlights legal target squares in blue
- [ ] Arrow keys cycle through targets; Enter confirms a move
- [ ] Mouse click on a piece selects it; click on a target moves it
- [ ] Side panel shows turn, captured pieces, and move history
- [ ] Promotion dialog appears when a pawn reaches the back rank
- [ ] New Game button resets the board and side panel
- [ ] Resign button shows result message and disables board input

### Step 8.5: Final commit

```bash
git add -u
git commit -m "feat: remove legacy TextUI, complete Lanterna rich TUI"
```

- [ ] Commit
