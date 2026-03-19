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

## Module Structure

All files live in `modules/ui-text/src/main/scala/`:

| File | Responsibility |
|---|---|
| `LanternaUI.scala` | Entry point; sets up `Screen` + `MultiWindowTextGUI`; owns game state; runs event loop |
| `BoardComponent.scala` | Custom `AbstractInteractableComponent`; renders board; handles mouse + keyboard |
| `SidePanel.scala` | Turn indicator, captured pieces, scrollable move history |
| `PromotionDialog.scala` | Modal `DialogWindow` for pawn promotion piece selection |
| `MenuBar.scala` | New Game / Resign buttons |
| `UIState.scala` | Pure cursor/state logic extracted from current `TextUI.scala`; independently testable |

`TextUI.scala` is removed and replaced by the above.

## Section 1: Architecture

`LanternaUI` owns all mutable game state and pushes updates to components after each move. Components do not share mutable state with each other.

```
LanternaUI
  ├── GameState          (immutable, replaced on each move)
  ├── List[String]       move history (algebraic half-moves)
  ├── List[Piece]        captured by white
  ├── List[Piece]        captured by black
  └── CursorState        (owned by BoardComponent, returned on move)
```

The main window uses a `BorderLayout`:
- **Top**: `MenuBar`
- **Center**: `BoardComponent`
- **Right**: `SidePanel`

## Section 2: BoardComponent

A custom `AbstractInteractableComponent` that renders the 8×8 board.

**Square rendering:**
- Each square: 3 chars wide × 1 row tall (` ♟ `)
- Light squares: `RGB(240, 217, 181)`
- Dark squares: `RGB(181, 136, 99)`

**Highlight overlays (override square color):**
- Cursor: `RGB(247, 247, 105)` (yellow)
- Selected piece: `RGB(130, 151, 105)` (green)
- Target squares: `RGB(100, 130, 180)` (blue)

**Unicode pieces:**
- White: ♔ ♕ ♖ ♗ ♘ ♙
- Black: ♚ ♛ ♜ ♝ ♞ ♟

**Input:**
- Keyboard: arrow keys move cursor, Enter selects/confirms, Esc cancels
- Mouse: single click on a square triggers select-or-move (equivalent to keyboard Enter)

**State:** holds `CursorState` (Navigating / PieceSelected); notifies `LanternaUI` via callback when a move is confirmed or promotion is needed.

## Section 3: SidePanel

A vertical `Panel` with three sub-sections:

**Turn / check indicator** — `Label` showing `"White to move"` / `"Black to move"`, red with `" — CHECK!"` when in check.

**Captured pieces** — two `Label`s, one per color:
```
White captured: ♟♟♟ ♞
Black captured: ♙ ♗
```
Tracked by diffing board state before/after each move.

**Move history** — `ScrolledPanel` with one row per half-move pair:
```
1.  e2-e4    e7-e5
2.  g1-f3    b8-c6
```
Auto-scrolls to latest move after each turn.

## Section 4: PromotionDialog

A Lanterna `DialogWindow` shown modally when a pawn reaches the back rank.

```
┌─ Promote pawn to: ──────────┐
│                              │
│  [ Queen ] [ Rook ] [ Bishop ] [ Knight ]  │
│                              │
└──────────────────────────────┘
```

- Buttons are keyboard-focusable (Tab/Enter) and mouse-clickable
- Returns `PieceType` to `LanternaUI` before the promotion move is applied
- Uses the existing `PromotionMove` model type

## Section 5: MenuBar

A `Panel` at the top with two buttons:

- **New Game** — resets `GameState`, clears move history and captures, resets cursor to e1
- **Resign** — ends game immediately, shows `"White resigned. Black wins."` (or Black) in side panel

## Section 6: Data Flow

On each move:
1. `BoardComponent` emits a `Move` (+ optional `PieceType` from `PromotionDialog`)
2. `LanternaUI` calls `ctrl.applyMove(state, move)`
3. `Right(newState)`: diff boards for captures, append to move history, replace `GameState`, call `gui.updateScreen()`
4. `Left(_)`: illegal move — no state change, board re-renders unchanged

## Section 7: Testing

**`UIState.scala` (pure logic):**
- `cursorSquare`, `selectedSquare`, `targetSquares`, `moveCursorFree`, `moveCursorTargets`
- All existing `TextUISuite` tests migrate here unchanged

**Board rendering (pure):**
- `renderBoard(board, cursorState): Seq[TextCharacter]` extracted as a pure function
- Tests assert correct Unicode symbols, correct RGB colors for light/dark squares, correct highlight colors

**Side panel data (pure):**
- `capturedPieces(before: Board, after: Board): List[Piece]` — unit tested
- Move history formatting — unit tested

**Not unit tested:**
- `PromotionDialog` (thin Lanterna wrapper) — manual integration test only

## Task IDs

- Task #2: Implement UIState (pure cursor logic)
- Task #3: Implement BoardComponent
- Task #4: Implement SidePanel
- Task #5: Implement PromotionDialog
- Task #6: Implement MenuBar
- Task #7: Implement LanternaUI orchestrator
- Task #8: Update build.sbt and wire entry point
