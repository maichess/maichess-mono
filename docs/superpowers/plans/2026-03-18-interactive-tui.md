# Interactive TUI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace manual "e2e4" text entry with arrow-key cursor navigation and Enter to select/confirm moves, with ANSI color highlighting for cursor, selected piece, and legal targets.

**Architecture:** A two-phase state machine (`CursorState`) drives the interaction: free cursor movement in `Navigating`, then cycling among legal-move targets in `PieceSelected`. All logic except `gameLoop` is pure. JLine3 provides raw terminal input and ANSI rendering.

**Tech Stack:** Scala 3.8.2, sbt, JLine3 3.29.0 (`jline-terminal` + `jline-reader`), munit (existing test framework)

**Spec:** `docs/superpowers/specs/2026-03-18-interactive-tui-design.md`

---

## File Structure

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `build.sbt` | Add JLine3 deps to `uiText` |
| Rewrite | `modules/ui-text/src/main/scala/TextUI.scala` | All UI: enums, pure helpers, rendering, game loop |
| Create | `modules/tests/src/test/scala/TextUISuite.scala` | Tests for all pure TextUI functions |

---

### Task 1: Add JLine3 dependency

**Files:**
- Modify: `build.sbt`

- [ ] **Step 1: Add JLine3 to uiText in build.sbt**

Find the `uiText` project definition and add `libraryDependencies`:

```scala
lazy val uiText = (project in file("modules/ui-text"))
  .dependsOn(engine)
  .settings(moduleSettings("org.maichess.mono.ui"): _*)
  .settings(coverageEnabled := false)
  .settings(name := "maichess-ui-text")
  .settings(
    libraryDependencies ++= Seq(
      "org.jline" % "jline-terminal" % "3.29.0",
      "org.jline" % "jline-reader"   % "3.29.0"
    )
  )
```

- [ ] **Step 2: Verify it compiles**

```bash
sbt compile
```

Expected: `[success]` — JLine3 artifacts download and the project compiles.

- [ ] **Step 3: Commit**

```bash
git add build.sbt
git commit -m "build: add JLine3 dependency to uiText module"
```

---

### Task 2: Add Direction enum, CursorState enum, and cursor accessor helpers

The pure functions `cursorSquare`, `selectedSquare`, and `targetSquares` extract rendering
information from a `CursorState` value. Tests go in `TextUISuite.scala`; implementation in
`TextUI.scala`.

**Files:**
- Create: `modules/tests/src/test/scala/TextUISuite.scala`
- Modify: `modules/ui-text/src/main/scala/TextUI.scala`

> **WartRemover note:** `uiText` runs `Warts.unsafe` on compile. Avoid `.get` on `Option`
> (use `.getOrElse`), `var` declarations, and `null`. The `tests` module has WartRemover
> disabled in test scope, so `.get` is safe in test helpers.

- [ ] **Step 1: Write the failing tests**

Create `modules/tests/src/test/scala/TextUISuite.scala`:

```scala
package org.maichess.mono.tests

import munit.FunSuite
import org.maichess.mono.model.*
import org.maichess.mono.ui.{CursorState, Direction, TextUI}

class TextUISuite extends FunSuite:

  private def sq(s: String): Square  = Square.fromAlgebraic(s).get
  private def mv(f: String, t: String): Move = NormalMove(sq(f), sq(t))

  // ── cursorSquare ────────────────────────────────────────────────────────────

  test("cursorSquare returns cursor when Navigating"):
    assertEquals(TextUI.cursorSquare(CursorState.Navigating(sq("e2"))), sq("e2"))

  test("cursorSquare returns current target square when PieceSelected"):
    val targets = IndexedSeq(mv("e2", "e4"), mv("e2", "e3"))
    assertEquals(
      TextUI.cursorSquare(CursorState.PieceSelected(sq("e2"), 1, targets)),
      sq("e3")
    )

  // ── selectedSquare ──────────────────────────────────────────────────────────

  test("selectedSquare is None when Navigating"):
    assertEquals(TextUI.selectedSquare(CursorState.Navigating(sq("e2"))), None)

  test("selectedSquare is Some(from) when PieceSelected"):
    val targets = IndexedSeq(mv("e2", "e4"))
    assertEquals(
      TextUI.selectedSquare(CursorState.PieceSelected(sq("e2"), 0, targets)),
      Some(sq("e2"))
    )

  // ── targetSquares ───────────────────────────────────────────────────────────

  test("targetSquares is empty when Navigating"):
    assertEquals(TextUI.targetSquares(CursorState.Navigating(sq("e2"))), Set.empty[Square])

  test("targetSquares returns all target squares when PieceSelected"):
    val targets = IndexedSeq(mv("e2", "e4"), mv("e2", "e3"))
    assertEquals(
      TextUI.targetSquares(CursorState.PieceSelected(sq("e2"), 0, targets)),
      Set(sq("e4"), sq("e3"))
    )
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
sbt "testOnly *TextUISuite"
```

Expected: compilation error — `CursorState`, `Direction` not found.

- [ ] **Step 3: Add Direction and CursorState enums, and the three accessor functions to TextUI.scala**

Add at the top of `modules/ui-text/src/main/scala/TextUI.scala`, before `object TextUI`:

```scala
enum Direction:
  case Up, Down, Left, Right

enum CursorState:
  case Navigating(cursor: Square)
  case PieceSelected(from: Square, index: Int, targets: IndexedSeq[Move])
```

Add inside `object TextUI` (alongside the existing functions):

```scala
def cursorSquare(cs: CursorState): Square = cs match
  case CursorState.Navigating(cursor)               => cursor
  case CursorState.PieceSelected(_, index, targets) => targets(index).to

def selectedSquare(cs: CursorState): Option[Square] = cs match
  case CursorState.Navigating(_)             => None
  case CursorState.PieceSelected(from, _, _) => Some(from)

def targetSquares(cs: CursorState): Set[Square] = cs match
  case CursorState.Navigating(_)                => Set.empty
  case CursorState.PieceSelected(_, _, targets) => targets.map(_.to).toSet
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
sbt "testOnly *TextUISuite"
```

Expected: all 6 tests pass.

- [ ] **Step 5: Verify the project still compiles**

```bash
sbt compile
```

Expected: `[success]`

- [ ] **Step 6: Commit**

```bash
git add modules/ui-text/src/main/scala/TextUI.scala \
        modules/tests/src/test/scala/TextUISuite.scala
git commit -m "feat: add Direction enum, CursorState, and cursor accessor helpers"
```

---

### Task 3: Implement moveCursorFree

Moves the cursor one step in any direction, wrapping at board edges.

**Files:**
- Modify: `modules/tests/src/test/scala/TextUISuite.scala` (add tests)
- Modify: `modules/ui-text/src/main/scala/TextUI.scala` (add function)

- [ ] **Step 1: Write the failing tests**

Append to `TextUISuite.scala`:

```scala
  // ── moveCursorFree ──────────────────────────────────────────────────────────

  test("moveCursorFree Up increases rank"):
    assertEquals(TextUI.moveCursorFree(sq("e2"), Direction.Up), sq("e3"))

  test("moveCursorFree Down decreases rank"):
    assertEquals(TextUI.moveCursorFree(sq("e3"), Direction.Down), sq("e2"))

  test("moveCursorFree Right increases file"):
    assertEquals(TextUI.moveCursorFree(sq("e2"), Direction.Right), sq("f2"))

  test("moveCursorFree Left decreases file"):
    assertEquals(TextUI.moveCursorFree(sq("e2"), Direction.Left), sq("d2"))

  test("moveCursorFree Up wraps from rank 8 to rank 1"):
    assertEquals(TextUI.moveCursorFree(sq("e8"), Direction.Up), sq("e1"))

  test("moveCursorFree Down wraps from rank 1 to rank 8"):
    assertEquals(TextUI.moveCursorFree(sq("e1"), Direction.Down), sq("e8"))

  test("moveCursorFree Right wraps from h-file to a-file"):
    assertEquals(TextUI.moveCursorFree(sq("h4"), Direction.Right), sq("a4"))

  test("moveCursorFree Left wraps from a-file to h-file"):
    assertEquals(TextUI.moveCursorFree(sq("a4"), Direction.Left), sq("h4"))
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
sbt "testOnly *TextUISuite"
```

Expected: compilation error — `moveCursorFree` not found.

- [ ] **Step 3: Implement moveCursorFree**

Add inside `object TextUI`:

```scala
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
```

> **Note:** `File.fromInt` and `Rank.fromInt` both live in `org.maichess.mono.model` and are
> already imported. The modulo arithmetic guarantees values stay in 0–7, so `getOrElse(cursor)`
> is a safe fallback that should never be reached.

- [ ] **Step 4: Run tests to verify they pass**

```bash
sbt "testOnly *TextUISuite"
```

Expected: all tests pass (8 new + 6 from Task 2).

- [ ] **Step 5: Commit**

```bash
git add modules/ui-text/src/main/scala/TextUI.scala \
        modules/tests/src/test/scala/TextUISuite.scala
git commit -m "feat: implement moveCursorFree with wrap-around"
```

---

### Task 4: Implement moveCursorTargets

Cycles through an `IndexedSeq[Move]` by index. Up/Left = previous; Down/Right = next; both wrap.

**Files:**
- Modify: `modules/tests/src/test/scala/TextUISuite.scala`
- Modify: `modules/ui-text/src/main/scala/TextUI.scala`

- [ ] **Step 1: Write the failing tests**

Append to `TextUISuite.scala`:

```scala
  // ── moveCursorTargets ───────────────────────────────────────────────────────

  test("moveCursorTargets Down advances index"):
    val targets = IndexedSeq(mv("e2","e3"), mv("e2","e4"))
    assertEquals(TextUI.moveCursorTargets(targets, 0, Direction.Down), 1)

  test("moveCursorTargets Up retreats index"):
    val targets = IndexedSeq(mv("e2","e3"), mv("e2","e4"))
    assertEquals(TextUI.moveCursorTargets(targets, 1, Direction.Up), 0)

  test("moveCursorTargets Down wraps from last to first"):
    val targets = IndexedSeq(mv("e2","e3"), mv("e2","e4"))
    assertEquals(TextUI.moveCursorTargets(targets, 1, Direction.Down), 0)

  test("moveCursorTargets Up wraps from first to last"):
    val targets = IndexedSeq(mv("e2","e3"), mv("e2","e4"))
    assertEquals(TextUI.moveCursorTargets(targets, 0, Direction.Up), 1)

  test("moveCursorTargets Right advances index"):
    val targets = IndexedSeq(mv("e2","e3"), mv("e2","e4"))
    assertEquals(TextUI.moveCursorTargets(targets, 0, Direction.Right), 1)

  test("moveCursorTargets Left retreats index"):
    val targets = IndexedSeq(mv("e2","e3"), mv("e2","e4"))
    assertEquals(TextUI.moveCursorTargets(targets, 1, Direction.Left), 0)
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
sbt "testOnly *TextUISuite"
```

Expected: compilation error — `moveCursorTargets` not found.

- [ ] **Step 3: Implement moveCursorTargets**

Add inside `object TextUI`:

```scala
def moveCursorTargets(targets: IndexedSeq[Move], index: Int, dir: Direction): Int =
  val delta = dir match
    case Direction.Right | Direction.Down => 1
    case Direction.Left  | Direction.Up   => -1
  (index + delta + targets.length) % targets.length
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
sbt "testOnly *TextUISuite"
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add modules/ui-text/src/main/scala/TextUI.scala \
        modules/tests/src/test/scala/TextUISuite.scala
git commit -m "feat: implement moveCursorTargets with wrap-around cycling"
```

---

### Task 5: Update renderBoard with ANSI highlighting

Extend `renderBoard` to accept cursor, selected, and target squares, and apply ANSI background
colors. Introduce a private `renderCell` helper to keep each function under 15 lines.

ANSI codes used:
- `\u001b[43m` — yellow background (cursor)
- `\u001b[42m` — green background (selected piece)
- `\u001b[44m` — blue background (legal targets)
- `\u001b[0m`  — reset

**Files:**
- Modify: `modules/tests/src/test/scala/TextUISuite.scala`
- Modify: `modules/ui-text/src/main/scala/TextUI.scala`

- [ ] **Step 1: Write the failing tests**

Append to `TextUISuite.scala`:

```scala
  // ── renderBoard ─────────────────────────────────────────────────────────────

  test("renderBoard wraps cursor square in yellow ANSI background"):
    val out = TextUI.renderBoard(Board.standard, Color.White, Some(sq("e1")), None, Set.empty)
    assert(out.contains("\u001b[43m"), "expected yellow background for cursor")

  test("renderBoard wraps selected square in green ANSI background"):
    val out = TextUI.renderBoard(Board.standard, Color.White, Some(sq("e2")), Some(sq("e1")), Set.empty)
    assert(out.contains("\u001b[42m"), "expected green background for selected piece")

  test("renderBoard shows + with blue background for target squares"):
    val out = TextUI.renderBoard(Board.standard, Color.White, None, None, Set(sq("e4")))
    assert(out.contains("+"),          "expected + symbol for target square")
    assert(out.contains("\u001b[44m"), "expected blue background for target square")

  test("renderBoard resets ANSI after each highlighted square"):
    val out = TextUI.renderBoard(Board.standard, Color.White, Some(sq("e1")), None, Set.empty)
    assert(out.contains("\u001b[0m"), "expected ANSI reset after highlighted square")

  test("renderBoard still contains file labels"):
    val out = TextUI.renderBoard(Board.standard, Color.White, None, None, Set.empty)
    assert(out.contains("a b c d e f g h"))
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
sbt "testOnly *TextUISuite"
```

Expected: compilation error — `renderBoard` signature mismatch (currently takes 2 args).

- [ ] **Step 3: Replace renderBoard and add renderCell in TextUI.scala**

Replace the existing `renderBoard` function with the new signature, and add the private `renderCell` helper:

```scala
def renderBoard(
  board: Board,
  perspective: Color,
  cursor: Option[Square],
  selected: Option[Square],
  targets: Set[Square]
): String =
  val rankRange = if perspective == Color.White then (7 to 0 by -1) else (0 to 7)
  val rows = rankRange.map { ri =>
    val cells = (0 to 7).map { fi =>
      val sq = for f <- File.fromInt(fi); r <- Rank.fromInt(ri) yield Square(f, r)
      sq.fold(".")(renderCell(board, _, cursor, selected, targets))
    }
    (ri + 1).toString + " " + cells.mkString(" ")
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
    if cursor.contains(sq)   then "\u001b[43m"
    else if selected.contains(sq) then "\u001b[42m"
    else if targets.contains(sq)  then "\u001b[44m"
    else ""
  if ansiPrefix.isEmpty then symbol else s"${ansiPrefix}${symbol}\u001b[0m"
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
sbt "testOnly *TextUISuite"
```

Expected: all tests pass.

- [ ] **Step 5: Verify the project still compiles**

```bash
sbt compile
```

Expected: `[success]`. Fix any WartRemover errors before continuing.

> **WartRemover note:** If `StringPlusAny` fires on `(ri + 1).toString + " "`, change to
> `s"${ri + 1} "`. If `NonUnitStatements` fires anywhere, wrap the expression in `val _ = ...`.

- [ ] **Step 6: Commit**

```bash
git add modules/ui-text/src/main/scala/TextUI.scala \
        modules/tests/src/test/scala/TextUISuite.scala
git commit -m "feat: update renderBoard with ANSI color highlighting"
```

---

### Task 6: Rewrite gameLoop with JLine3 and update runGame

Replace `StdIn.readLine()` input with raw JLine3 key-event reading. Introduce `buildKeyMap`,
`handleKey`, `trySelectPiece`, `applySelectedMove`, and the tail-recursive `go` loop.

This task is impure and cannot be unit-tested. Verify manually by running the game.

**Files:**
- Modify: `modules/ui-text/src/main/scala/TextUI.scala`

- [ ] **Step 1: Add JLine3 imports to TextUI.scala**

Replace the `import scala.io.StdIn` line with:

```scala
import org.jline.keymap.{BindingReader, KeyMap}
import org.jline.terminal.{Terminal, TerminalBuilder}
import org.jline.utils.InfoCmp.Capability
```

- [ ] **Step 2: Add buildKeyMap**

Add as a private function inside `object TextUI`:

```scala
private def buildKeyMap(terminal: Terminal): KeyMap[String] =
  val km = new KeyMap[String]()
  val _ = km.bind("up",    KeyMap.key(terminal, Capability.key_up))
  val _ = km.bind("down",  KeyMap.key(terminal, Capability.key_down))
  val _ = km.bind("left",  KeyMap.key(terminal, Capability.key_left))
  val _ = km.bind("right", KeyMap.key(terminal, Capability.key_right))
  val _ = km.bind("enter", "\r", "\n")
  val _ = km.bind("esc",   KeyMap.esc())
  km
```

- [ ] **Step 3: Add trySelectPiece and applySelectedMove**

These two private helpers keep `handleKey` concise. Add inside `object TextUI`:

```scala
private def trySelectPiece(
  cursor: Square,
  state: GameState
): (GameState, CursorState) =
  val raw    = StandardRules.legalMoves(state.current, cursor)
  val deduped = raw
    .groupBy(_.to)
    .values
    .flatMap(_.headOption)
    .toIndexedSeq
    .sortBy(m => m.to.rank.toInt * 8 + m.to.file.toInt)
  if deduped.isEmpty then (state, CursorState.Navigating(cursor))
  else (state, CursorState.PieceSelected(cursor, 0, deduped))

private def applySelectedMove(
  move: Move,
  from: Square,
  state: GameState,
  ctrl: GameController
): (GameState, CursorState) =
  ctrl.applyMove(state, move) match
    case Right(newState) => (newState, CursorState.Navigating(move.to))
    case Left(_)         => (state, CursorState.Navigating(from))
```

> **Note on deduplication:** `groupBy(_.to)` groups all moves to the same destination.
> `flatMap(_.headOption)` keeps only the first move per destination. Because
> `StandardRules.legalMoves` returns Queen promotion first among promotion variants,
> this auto-selects Queen for pawn promotions. Deduplication happens **before** `sortBy`.

- [ ] **Step 4: Add handleKey**

```scala
private def handleKey(
  key: Option[String],
  state: GameState,
  cs: CursorState,
  ctrl: GameController
): (GameState, CursorState) =
  cs match
    case CursorState.Navigating(cursor) =>
      key match
        case Some("up")    => (state, CursorState.Navigating(moveCursorFree(cursor, Direction.Up)))
        case Some("down")  => (state, CursorState.Navigating(moveCursorFree(cursor, Direction.Down)))
        case Some("left")  => (state, CursorState.Navigating(moveCursorFree(cursor, Direction.Left)))
        case Some("right") => (state, CursorState.Navigating(moveCursorFree(cursor, Direction.Right)))
        case Some("enter") => trySelectPiece(cursor, state)
        case _             => (state, cs)
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
```

- [ ] **Step 5: Add the tail-recursive go loop**

```scala
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
  ctrl.gameResult(state) match
    case Some(result) =>
      val _ = terminal.writer().println(resultMessage(result))
    case None =>
      val turnLabel = if state.current.turn == Color.White then "White" else "Black"
      val checkNote = if StandardRules.isCheck(state.current) then " — CHECK!" else ""
      val _ = terminal.writer().println(s"$turnLabel to move$checkNote  (arrows to navigate, Enter to select/confirm, Esc to cancel)")
      val key = Option(bindingReader.readBinding(keyMap))
      val (newState, newCs): (GameState, CursorState) = handleKey(key, state, cs, ctrl)
      go(newState, newCs, ctrl, terminal, bindingReader, keyMap)
```

- [ ] **Step 6: Replace gameLoop and update runGame**

Replace the existing `gameLoop` function:

```scala
def gameLoop(state: GameState, ctrl: GameController, terminal: Terminal): Unit =
  val keyMap        = buildKeyMap(terminal)
  val bindingReader = BindingReader(terminal.reader())
  val initialCursor = Square.fromAlgebraic("e1").getOrElse(Square.all.head)
  try go(state, CursorState.Navigating(initialCursor), ctrl, terminal, bindingReader, keyMap)
  finally terminal.close()
```

Replace the `@main def runGame()` at the bottom of the file:

```scala
@main def runGame(): Unit =
  val ctrl     = GameController(StandardRules)
  val terminal = TerminalBuilder.terminal()
  val _        = terminal.writer().println("Welcome to MaiChess!")
  TextUI.gameLoop(ctrl.newGame(), ctrl, terminal)
```

Also remove the old `parseMove` function — it is no longer used.

- [ ] **Step 7: Compile and fix WartRemover errors**

```bash
sbt compile
```

Common WartRemover issues and fixes:
- **`NonUnitStatements`** — if `terminal.puts(...)` triggers it, the `val _ = ...` pattern handles it.
- **`StringPlusAny`** — ensure all `+` string concatenation has `String` on both sides. The `s"..."` interpolation is always safe.
- **`Var`** — there must be no `var` in the implementation; use the tail-recursive `go` instead.
- **`OptionPartial`** — no `.get` anywhere; `getOrElse` is used instead.

Fix any errors until `sbt compile` reports `[success]`.

- [ ] **Step 8: Run all tests**

```bash
sbt test
```

Expected: all tests pass, including the existing suites.

- [ ] **Step 9: Run the game manually and verify interactive navigation**

```bash
sbt run
```

Verify:
- Board renders with a cursor on e1 (yellow background)
- Arrow keys move the cursor around the board with wrap-around
- Enter on a white pawn (e.g. e2) highlights legal target squares in blue with `+`
- Only target squares are reachable after selecting a piece
- Enter on a target applies the move; the board updates
- Esc cancels piece selection and returns to free navigation
- Terminal restores to normal after the game ends (no stuck raw mode)

- [ ] **Step 10: Commit**

```bash
git add modules/ui-text/src/main/scala/TextUI.scala
git commit -m "feat: interactive arrow-key TUI with JLine3"
```
