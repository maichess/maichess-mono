# Lanterna Rich Text UI — Design Spec

**Date:** 2026-03-19
**Status:** Approved

## Overview

Replace the JLine-based `TextUI.scala` with a Lanterna-backed rich terminal UI. The new UI adds Unicode chess pieces, truecolor chessboard squares, mouse support, a scrollable side panel (move history, captured pieces, turn indicator), a pawn promotion modal, and a menu bar (New Game / Resign).

Game logic modules (`GameController`, `GameState`, `StandardRules`, model, engine) are untouched.

## Dependencies

Add to `build.sbt`:
```
"com.googlecode.lanterna" % "lanterna" % "3.1.2"
```

## Model Reference

**Move subtypes:**
- `NormalMove(from, to, promotion: Option[PieceType])` — standard move; promotion is `Some(pt)` when a pawn reaches the back rank. There is no separate `PromotionMove` type.
- `CastlingMove(from, to, rookFrom, rookTo)`
- `EnPassantMove(from, to, captured)` — `captured` is the en-passant-captured pawn square (not `to`)

**Board:** `Board(pieces: Map[Square, Piece])` — `pieces` is a public field. Use `board.pieces.values` to iterate all pieces. There is no `allPieces` method.

**GameState:** `GameState(history: List[Situation], current: Situation)`. The board is accessed as `state.current.board: Board`. The side to move is `state.current.turn: Color`.

**GameResult:** `Checkmate(winner: Color)`, `Stalemate`, `Draw(reason: DrawReason)`. There is no `Resign` variant — resign is handled as a UI-level terminal state only.

**GameController:** `ctrl.newGame(): GameState` and `ctrl.applyMove(state, move): Either[IllegalMove, GameState]` and `ctrl.gameResult(state): Option[GameResult]` all exist.

## Module Structure

All files live in `modules/ui-text/src/main/scala/`:

| File | Responsibility |
|---|---|
| `LanternaUI.scala` | Entry point; sets up `Screen` + `MultiWindowTextGUI`; owns game state; runs event loop |
| `BoardComponent.scala` | Custom `AbstractInteractableComponent`; renders board; handles mouse + keyboard |
| `SidePanel.scala` | Turn indicator, captured pieces, scrollable move history |
| `PromotionDialog.scala` | Modal `DialogWindow` for pawn promotion piece selection |
| `MenuBar.scala` | New Game / Resign buttons |
| `UIState.scala` | Pure cursor/state logic and `Direction` enum; independently testable |

`TextUI.scala` is removed and replaced by the above.

## Section 1: Architecture

`LanternaUI` owns all mutable game state. `BoardComponent` owns `CursorState` internally. Components communicate with `LanternaUI` via callbacks; they do not share mutable state with each other.

```
LanternaUI  (mutable vars, @SuppressWarnings Var)
  ├── GameState          (immutable, replaced on each move)
  ├── List[String]       move history (algebraic half-moves)
  ├── List[Piece]        captured by white
  ├── List[Piece]        captured by black
  └── Option[String]     terminal message (None = game in progress)

BoardComponent (internal, mutable var for CursorState)
  └── CursorState        (Navigating | PieceSelected)
```

**WartRemover:** `LanternaUI` and `BoardComponent` each use `var` for their internal state fields and are annotated with `@SuppressWarnings(Array("org.wartremover.warts.Var"))`. All other files remain `var`-free. Lanterna `Label` widgets are held as `val` references; calling `.setText(...)` on them is not a `var` and needs no suppression.

The main window uses a `BorderLayout`:
- **Top**: `MenuBar`
- **Center**: `BoardComponent`
- **Right**: `SidePanel`

**Component communication:**
- `BoardComponent` → `LanternaUI`: `onMove: Move => Unit` when user confirms a move (always sends the deduped `targets(index)` move as-is, including whatever `promotion` value it has)
- `MenuBar` → `LanternaUI`: `onNewGame: () => Unit` and `onResign: () => Unit`
- `LanternaUI` → `BoardComponent`: `reset(newState: GameState): Unit` — resets `CursorState` to `Navigating(e1)` (hardcoded starting cursor for the standard position) and triggers redraw; `setEnabled(Boolean): Unit` — disables/re-enables input
- `LanternaUI` → `SidePanel`: `update(state: GameState, history: List[String], capturedWhite: List[Piece], capturedBlack: List[Piece]): Unit` for normal updates; `showResult(message: String): Unit` to display a terminal message (replaces the turn indicator label text)

## Section 2: BoardComponent

A custom `AbstractInteractableComponent` that renders the 8×8 board.

**Square rendering:**
- Each square: 3 chars wide × 1 row tall (` ♟ `)
- Board orientation: White perspective always (rank 8 at top, rank 1 at bottom)
- Light squares: `RGB(240, 217, 181)`
- Dark squares: `RGB(181, 136, 99)`

**Highlight overlays (override square color):**
- Cursor: `RGB(247, 247, 105)` (yellow)
- Selected piece: `RGB(130, 151, 105)` (green)
- Target squares: `RGB(100, 130, 180)` (blue)

**Unicode pieces:**
- White: ♔ ♕ ♖ ♗ ♘ ♙
- Black: ♚ ♛ ♜ ♝ ♞ ♟
- Empty square: space character

**Target deduplication:**
`legalMoves` returns up to four `NormalMove`s to the same `.to` square for promotion. Targets are deduplicated with `.distinctBy(_.to)` before being stored in `CursorState.PieceSelected`. The deduped promotion move retains whatever `promotion = Some(someType)` the first matching move has — this field is used by `LanternaUI` to detect promotion (see Section 6).

**Keyboard input:**
- In `Navigating` state: arrow keys move cursor (wraps at board edge); Enter selects piece (calls `onMove` after target selection); Esc is ignored.
- In `PieceSelected` state: Up/Left cycles targets backward (index − 1 mod n, wraps); Down/Right cycles targets forward (index + 1 mod n, wraps); Enter confirms the current target (calls `onMove(targets(index))`); Esc cancels and returns to `Navigating(from)`.

**Mouse input:**
- In `Navigating` state: click on a square containing a friendly piece that has legal moves → select it. Click on an empty square, enemy piece, or friendly piece with no legal moves → ignore (no visual feedback).
- In `PieceSelected` state: click on a target square → confirm that move (calls `onMove`). Click on `from` square → cancel (return to `Navigating(from)`). Click on a different friendly piece that has legal moves → re-select that piece. Click on a friendly piece with no legal moves or any other square → ignore.

**Note:** `BoardComponent` does not detect or handle promotion. It always calls `onMove(targets(index))` and lets `LanternaUI` inspect the move and show the dialog if needed.

## Section 3: SidePanel

A vertical `Panel` with three sub-sections. All `Label` widgets are held as `val` references; their text is updated by calling `.setText(...)`.

**Turn / check indicator** — a `val statusLabel: Label`. Shows `"White to move"` / `"Black to move"`. Appends `" — CHECK!"` when `StandardRules.isCheck(state.current)` is true. `showResult(message)` calls `statusLabel.setText(message)`, replacing the turn text.

**Captured pieces** — two `val` label references, one per color:
```
White captured: ♟♟♟ ♞
Black captured: ♙ ♗
```
Computed by `capturedPieces(before: Board, after: Board, movingColor: Color): List[Piece]`:
```scala
val opponentPiecesBefore = before.pieces.values.filter(_.color != movingColor).toList
val opponentPiecesAfter  = after.pieces.values.filter(_.color != movingColor).toList
opponentPiecesBefore diff opponentPiecesAfter
```
This correctly handles normal captures, en passant, and promotion-captures without returning the moving player's own pawn as a "capture."

**Move history** — a `ScrolledPanel` with one row per half-move pair:
```
1.  e2-e4    e7-e5
2.  g1-f3    b8-c6
```
Auto-scrolls to latest move after each turn.

**Move notation format (`moveNotation(move: Move): String`):**
- `NormalMove` without promotion: `"file+rank-file+rank"`, e.g. `"e2-e4"`
- `NormalMove` with promotion: `"file+rank-file+rank=X"`, e.g. `"e7-e8=Q"` (letter from piece type initial)
- `CastlingMove`: `"O-O"` if `rookFrom.file.toInt > from.file.toInt` (kingside), `"O-O-O"` otherwise (queenside)
- `EnPassantMove`: `"file+rank-file+rank"`, same format as normal move, e.g. `"e5-d6"`

## Section 4: PromotionDialog

A Lanterna `DialogWindow` shown modally by `LanternaUI` when promotion is detected. Lanterna's `showDialog(gui)` / `addWindowAndWait` runs a nested event loop and is safe to call from within action callbacks on the GUI thread — this is the standard Lanterna modal dialog pattern.

```
┌─ Promote pawn to: ──────────────────┐
│                                      │
│  [ Queen ] [ Rook ] [ Bishop ] [ Knight ] │
│                                      │
└──────────────────────────────────────┘
```

- Buttons are keyboard-focusable (Tab cycles, Enter confirms) and mouse-clickable
- Returns `PieceType` synchronously to the caller
- Closing the dialog without choosing defaults to `PieceType.Queen`

## Section 5: MenuBar

A `Panel` at the top with two `Button`s, each wired to a callback in `LanternaUI`:

- **New Game** — `LanternaUI` calls `ctrl.newGame()`, clears move history and captures, clears terminal message, calls `boardComponent.setEnabled(true)` and `boardComponent.reset(newState)`
- **Resign** — `LanternaUI` builds `"<Color> resigned. <Opponent> wins."` based on `state.current.turn`, calls `sidePanel.showResult(message)` and `boardComponent.setEnabled(false)`

## Section 6: Data Flow

**Normal move:**
1. `BoardComponent` calls `onMove(targets(index))`
2. `LanternaUI` inspects the move:
   - If `move.promotion.isDefined`: show `PromotionDialog`, get chosen `PieceType`, replace move with `NormalMove(move.from, move.to, Some(chosenType))`
   - Otherwise: use move as-is
3. `LanternaUI` calls `ctrl.applyMove(state, move)`
4. `Right(newState)`: compute `capturedPieces(state.current.board, newState.current.board, state.current.turn)`, append to captured list, append `moveNotation(move)` to history, replace `GameState`, call `sidePanel.update(...)`, call `gui.updateScreen()`
5. `Left(_)`: illegal move — no state change, board re-renders unchanged

**Promotion (continuation of step 2 above):**
- `LanternaUI` shows `PromotionDialog` from within the `onMove` callback (GUI thread), which runs a nested Lanterna event loop until the user picks a piece type
- The callback returns synchronously with the chosen `PieceType`
- Processing continues at step 3 with the corrected move

**Game over (checkmate / stalemate / draw):**
- After each `Right(newState)`, `LanternaUI` checks `ctrl.gameResult(newState)`
- If `Some(result)`: build result message (e.g. `"Checkmate — White wins."`, `"Stalemate — draw."`), call `sidePanel.showResult(message)`, call `boardComponent.setEnabled(false)`
- New Game re-enables board: `boardComponent.setEnabled(true)` and `boardComponent.reset(newState)`

## Section 7: Testing

**`UIState.scala` (pure logic):**
Extracts `Direction` enum and functions `cursorSquare`, `selectedSquare`, `targetSquares`, `moveCursorFree`, `moveCursorTargets`. The existing tests in `modules/tests/src/test/scala/TextUISuite.scala` are updated with a sanctioned structural rename: import changes from `org.maichess.mono.ui.{CursorState, Direction, TextUI}` to `org.maichess.mono.ui.{CursorState, Direction, UIState}`, and `TextUI.foo` calls become `UIState.foo`. All test assertions remain unchanged — this is a rename, not a logic change.

**Square rendering (pure):**
`renderSquare(board: Board, sq: Square, highlights: SquareHighlights): TextCharacter` is a pure function in `BoardComponent`'s companion object. Tests assert per-square:
- Non-highlighted light square: background `RGB(240, 217, 181)`
- Non-highlighted dark square: background `RGB(181, 136, 99)`
- Cursor square: background `RGB(247, 247, 105)`
- Selected square: background `RGB(130, 151, 105)`
- Target square: background `RGB(100, 130, 180)`
- Piece character matches the Unicode symbol for each piece type/color

The existing ANSI-string assertions in `TextUISuite` (`contains("\u001b[43m")` etc.) are **replaced** by these new `TextCharacter` assertions.

**Side panel data (pure):**
- `capturedPieces(before: Board, after: Board, movingColor: Color): List[Piece]` — unit tested for normal captures, en passant, promotion-captures, and promotion-without-capture
- `moveNotation(move: Move): String` — unit tested for `NormalMove`, `CastlingMove` (kingside and queenside), `EnPassantMove`, and promotion notation

**Not unit tested:**
- `PromotionDialog`, `MenuBar`, `LanternaUI` event loop — manual integration test only

## Task IDs

- Task #2: Implement UIState (Direction + pure cursor logic) and migrate TextUISuite
- Task #3: Implement BoardComponent (renderSquare + keyboard + mouse)
- Task #4: Implement SidePanel and pure helpers (capturedPieces, moveNotation)
- Task #5: Implement PromotionDialog
- Task #6: Implement MenuBar
- Task #7: Implement LanternaUI orchestrator and game loop
- Task #8: Update build.sbt, remove JLine dependency, remove TextUI.scala, wire entry point
