# Interactive TUI — Design Spec

**Date:** 2026-03-18
**Status:** Approved

## Overview

Replace the current text-input move entry (`e2e4`) in `TextUI.scala` with a cursor-based interactive board where the player navigates using arrow keys and confirms selections with Enter.

## Architecture

The `uiText` module gains a JLine3 dependency for raw terminal input and ANSI rendering. `TextUI.scala` is restructured around three concerns:

1. **Rendering** — `renderBoard` is extended to accept a cursor position, an optional selected square, and a set of legal target squares. These are highlighted using ANSI background colors.
2. **Cursor state** — a sealed trait `CursorState` with two cases:
   - `Navigating(cursor: Square)` — free navigation over all 64 squares
   - `PieceSelected(from: Square, cursor: Square, targets: IndexedSeq[Square])` — cursor locked to legal targets
3. **Input loop** — `gameLoop` uses JLine3's `Terminal` to read raw key events (arrow keys, Enter, Escape) and drives the state machine, re-rendering on each event.

Pure functions remain pure. Only `gameLoop` is impure.

## Dependencies

Add to `uiText` in `build.sbt`:

```scala
"org.jline" % "jline-terminal" % "3.29.0",
"org.jline" % "jline-reader"   % "3.29.0"
```

## Interaction Model

### Navigation phase (`Navigating`)

- Arrow keys move the cursor across all 64 squares with wrap-around:
  - Right from h-file → a-file (same rank)
  - Left from a-file → h-file (same rank)
  - Up from rank 8 → rank 1 (same file)
  - Down from rank 1 → rank 8 (same file)
- Enter on a square with a piece that has legal moves → transitions to `PieceSelected`
- Enter on an empty square or a piece with no legal moves → no-op

### Selection phase (`PieceSelected`)

- Arrow keys cycle through legal target squares only (in board order: rank-major, a–h within each rank)
- Right/Down → next target; Left/Up → previous target; both wrap
- Enter on current target → applies the move, returns to `Navigating` for the new game state
- Escape → cancels, returns to `Navigating` with cursor restored to the from-square

## Rendering

| Element               | Visual                          |
|-----------------------|---------------------------------|
| Cursor square         | Yellow ANSI background          |
| Selected piece square | Green ANSI background           |
| Legal target square   | `+` symbol, blue ANSI background |
| Normal piece          | Letter symbol, no background    |
| Empty square          | `.`                             |

The screen is cleared and the board redrawn on every key event.

## Components

### Changed functions

- `renderBoard(board, perspective, cursor, selected, targets)` — pure; replaces current two-argument signature
- `gameLoop(state, ctrl, terminal)` — impure; owns the JLine3 terminal and key-event loop

### New functions

- `moveCursorFree(cursor, direction)` — pure; wraps across all 64 squares
- `moveCursorTargets(targets, current, direction)` — pure; cycles through a target list

## Error Handling & Cleanup

JLine3's `Terminal` is `AutoCloseable`. The `gameLoop` wraps the terminal in `try/finally` to restore the terminal to normal (`cooked`) mode on exit — whether the game ends normally, the user quits, or an exception is thrown.

## Out of Scope

- Pawn promotion choice — auto-promote to Queen (existing engine behaviour)
- Mouse input
- Multiplayer / networked play
