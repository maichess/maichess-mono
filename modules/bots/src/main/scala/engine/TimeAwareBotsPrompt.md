# Time-Aware Bot Move Selection

## Context

Currently all bots receive only `GameState` and return `Option[Move]`. The chess clock runs concurrently and
deducts time from the active player during their thinking period. Bots do not yet consult the clock, so they
always think for a fixed depth regardless of how much time remains.

## Problem

A bot playing with 1 minute on the clock should not spend 30 seconds on a single move. It also should not
flag itself by computing indefinitely. Time pressure should compress search depth.

## Proposed Interface Change

Extend the `Bot` trait to accept an optional time budget:

```scala
trait Bot:
  def name: String
  def chooseMove(state: GameState, timeBudgetMs: Option[Long] = None): Option[Move]
```

`timeBudgetMs` is the remaining clock time for the active player in milliseconds. Bots that do not need time
awareness can ignore it (the default `None` keeps backwards compatibility).

## Bot Caller Change

`SharedGameModel.runBotMove` currently calls `b.chooseMove(game)`. Change it to pass the clock snapshot:

```scala
private def runBotMove(b: Bot, game: GameState): Unit =
  val budgetMs = synchronized { current.clock }.map(_.remainingFor(game.current.turn))
  b.chooseMove(game, budgetMs).foreach { move =>
    ...
  }
```

## Bot Implementation Guide

A time-adaptive iterative-deepening bot should:

1. Start a `System.currentTimeMillis()` baseline at entry.
2. Pick a target search time, e.g. `min(budgetMs / 20, 5000)` to reserve ~20 moves of budget.
3. After each depth iteration check `elapsed >= targetMs`; if so, return the best move found so far.
4. Never exceed `budgetMs * 0.9` to leave margin for move application latency.

Example sketch:

```scala
def chooseMove(state: GameState, timeBudgetMs: Option[Long]): Option[Move] =
  val targetMs  = timeBudgetMs.fold(defaultThinkMs)(b => math.min(b / 20L, defaultThinkMs))
  val deadline  = System.currentTimeMillis() + targetMs
  var best: Option[Move] = None
  var depth = 1
  while System.currentTimeMillis() < deadline && depth <= maxDepth do
    best = searchAtDepth(state, depth)
    depth += 1
  best
```

## Clock State API

`ClockState` (in `ui-fx`) exposes:

```scala
def remainingFor(color: Color): Long  // milliseconds left for that player
```

Since `ClockState` lives in `ui-fx` and `bots` must not depend on `ui-fx`, pass the value as a plain
`Long` (milliseconds) rather than passing the `ClockState` object.

## Coverage Note

Any changes to `Bot` in the `bots` module must maintain 100% statement coverage. Add or update tests in
`modules/tests/src/test/scala/` to cover the new default parameter path and the time-budget path.
