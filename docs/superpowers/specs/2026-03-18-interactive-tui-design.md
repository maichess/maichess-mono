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
   - `PieceSelected(from: Square, cursor: Square, targets: IndexedSeq[Move])` — cursor locked to legal target moves. `targets` holds full `Move` objects (not just squares) so `ctrl.applyMove` can be called directly without a lookup step.
3. **Input loop** — `gameLoop` uses JLine3's `org.jline.terminal.Terminal` to read raw key events (arrow keys, Enter, Escape) and drives the state machine, re-rendering on each event.

Pure functions remain pure. Only `gameLoop` is impure.

## Dependencies

Add to `uiText` in `build.sbt`:

```scala
"org.jline" % "jline-terminal" % "3.29.0",
"org.jline" % "jline-reader"   % "3.29.0"
```

Arrow key escape sequences (`ESC [ A/B/C/D`) are parsed via `jline-reader`'s `KeyMap` infrastructure rather than raw byte assembly.

## Interaction Model

### Navigation phase (`Navigating`)

- Arrow keys move the cursor across all 64 squares with wrap-around:
  - **Up** = rank + 1 (toward rank 8, the top of the board for White). Wraps: rank 8 → rank 1.
  - **Down** = rank − 1. Wraps: rank 1 → rank 8.
  - **Right** = file + 1. Wraps: h-file → a-file (same rank).
  - **Left** = file − 1. Wraps: a-file → h-file (same rank).
- Board perspective is always White (rank 8 at top), matching existing behaviour.
- Enter on a square whose piece has legal moves → call `StandardRules.legalMoves(state.current, cursor)`, then deduplicate by `.to` square keeping the first `Move` per destination. Deduplication must be applied **before** sorting. `StandardRules.legalMoves` returns Queen promotion first among promotion variants for the same destination, so keeping the first move per `.to` silently auto-promotes to Queen — consistent with the "Out of Scope" clause. Store the result as `IndexedSeq[Move]` sorted in board order (rank-major, file a→h), transition to `PieceSelected`.
- Enter on an empty square or a piece with no legal moves → no-op.

### Selection phase (`PieceSelected`)

- Arrow keys cycle through legal target moves only:
  - **Right** or **Down** → next move in the `targets` sequence (wraps).
  - **Left** or **Up** → previous move (wraps).
- The cursor square is `targets(index).to` for the currently focused index.
- Enter → call `ctrl.applyMove(state, targets(index))`, return to `Navigating` with cursor at the destination square.
- Escape → cancel, return to `Navigating` with cursor on the from-square.

## Rendering

| Element               | Visual                           |
|-----------------------|----------------------------------|
| Cursor square         | Yellow ANSI background           |
| Selected piece square | Green ANSI background            |
| Legal target square   | `+` symbol, blue ANSI background |
| Normal piece          | Letter symbol, no background     |
| Empty square          | `.`                              |

The screen is cleared on every key event using `Terminal.puts(Capability.clear_screen)` followed by a full board redraw.

### `renderBoard` signature

```scala
def renderBoard(
  board: Board,
  perspective: Color,
  cursor: Option[Square],
  selected: Option[Square],
  targets: Set[Square]
): String
```

When called for the end-of-game result display (no cursor), pass `cursor = None`, `selected = None`, `targets = Set.empty`.

## Components

### Changed functions

- `renderBoard(board, perspective, cursor, selected, targets)` — pure; replaces current two-argument signature (see above for migration).
- `gameLoop(state, ctrl, terminal: org.jline.terminal.Terminal)` — impure; owns the key-event loop. Always passes `Color.White` as the `perspective` argument to `renderBoard` (matching existing behaviour). The caller (`runGame`) creates the terminal via `TerminalBuilder.terminal()` and passes it in; `gameLoop` wraps its body in `try/finally { terminal.close() }` to restore cooked mode on exit.

### New functions

- `moveCursorFree(cursor: Square, dir: Direction): Square` — pure; wraps across all 64 squares per the direction convention above.
- `moveCursorTargets(targets: IndexedSeq[Move], index: Int, dir: Direction): Int` — pure; returns the new index. `Direction` is a sealed ADT with cases `Up`, `Down`, `Left`, `Right`; Up/Left = previous (wrapping), Down/Right = next (wrapping).

### `Direction` ADT

```scala
enum Direction:
  case Up, Down, Left, Right
```

## Error Handling & Cleanup

JLine3's `Terminal` is `AutoCloseable`. `runGame` (the `@main` entry point) creates the terminal and passes it to `gameLoop`, which wraps the entire loop in `try/finally { terminal.close() }` to restore the terminal to cooked mode whether the game ends normally or an exception is thrown.

## Out of Scope

- Pawn promotion choice — auto-promote to Queen (handled by deduplication in Navigation phase).
- Mouse input.
- Multiplayer / networked play.
- Black-perspective rendering (board always shown from White's view).
