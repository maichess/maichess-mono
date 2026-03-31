# CLAUDE.md

## Commands

```bash
sbt compile              # Compile all modules
sbt test                 # Run all tests (via the tests module)
sbt "testOnly *Foo"      # Run a single suite by name pattern
sbt scalafix             # Run Scalafix linter/rewriter
sbt "uiText/assembly"    # Build the fat JAR (required before running)
play.bat                 # Build fat JAR + launch the app (Windows)
```

`sbt run` does not work — the entry point is `@main def runGame()` in `modules/ui-text/src/main/scala/LanternaUI.scala`, assembled into `modules/ui-text/target/scala-3.8.2/maichess.jar`.

## Module structure

```
model ← rules ← engine ← ui-fx ← ui-text ← tests
```

All tests live in the `tests` module (munit). Test files go in `modules/tests/src/test/scala/`.

## Linters & coverage

- **WartRemover** (`Warts.unsafe` as errors) applies to `model`, `rules`, `engine` only — disabled on `ui-fx` and `ui-text`.
- **100% statement coverage** is enforced on `model`, `rules`, `engine` — disabled on `ui-fx`, `ui-text`, and `tests`.
- Every change to `model`/`rules`/`engine` must be accompanied by tests maintaining 100% coverage.

## Architecture decisions

- **`SharedGameModel`** (`ui-fx`) is the shared mutable bridge between both UIs. Both `FxUI` (JavaFX) and `LanternaUI` (Lanterna TUI) observe it via callbacks.
- **`Keymap`** (`ui-fx`) is the single source of truth for all keyboard shortcuts and button labels. Both UIs derive their shortcut maps and button text from `Keymap` — never hardcode key chars or labels elsewhere.
- **`GameState.history`** (list of `Situation`, most-recent-first) is the authoritative move history used for undo/redo. `SharedGameModel.moveHistory` (list of notation strings) is a parallel display list that must be kept in sync — see `reconstructMoveHistory` for the PGN-import case.

## Code style

- Functional programming throughout — immutable data, pure functions.
- Max ~15–20 lines per function. If you need "and" to describe it, split it.
- No comments unless explaining a non-obvious algorithm. Names carry intent.
- Scan for duplicated logic before finishing; extract it.

## Tests

- Do not change tests to make them pass — only change tests when the requirement they cover changes.
