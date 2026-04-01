# Functional Patterns in MaiChess

## Immutable data & case classes

- `Situation`, `GameState`, `Board`, `CastlingRights`, `Piece`, `Square`, `NormalMove`, `CastlingMove`, `EnPassantMove` — all `case class` or `case object`, zero mutation.
- State transitions produce new values via `.copy(...)` rather than mutating fields.

## Sealed trait hierarchy

- `Move` is a sealed trait with subtypes `NormalMove`, `CastlingMove`, `EnPassantMove`; exhaustive pattern matching is enforced by the compiler.
- `Color`, `PieceType`, `DrawReason`, `GameResult`, `Change`, `PlayerMode` — all sealed enums/traits.

## Option for absence

- `Board.pieceAt`, `Square.fromAlgebraic`, `Square.offset`, `GameController.undo/redo`, `Ai.bestMove` — all return `Option[T]` instead of `null`.
- `Situation.enPassantSquare: Option[Square]` — absence of en passant target expressed in the type.

## Either for recoverable errors — Two-Track (Railway-Oriented) Programming

Every parse/decode boundary uses `Either[E, T]` to split execution into a **success track** (Right) and a **failure track** (Left). Once a computation lands on the Left track it stays there; no try/catch, no null checks, no early returns.

### `for`-comprehension chaining — `Fen.decode`

The purest example. Six independent sub-decoders each return `Either[String, T]`; the `for` sequences them so the first `Left` short-circuits the whole FEN parse without any explicit error propagation:

```scala
for
  board    <- decodeBoard(boardStr)
  turn     <- decodeTurn(turnStr)
  castling <- decodeCastling(castlingStr)
  ep       <- decodeEp(epStr)
  half     <- decodeInt(halfStr, "half-move clock")
  full     <- decodeInt(fullStr, "full-move number")
yield Situation(board, turn, castling, ep, half, full)
```

### `foldLeft` over `Either` — `Fen.decodeBoard`, `Pgn.parseTokens`

Accumulates results across a collection, sticking to `Left` once any element fails:

```scala
pairs.foldLeft(Right(Map.empty): Either[String, Map[Square, Piece]]) {
  case (Right(map), Right(pair)) => Right(map + pair)
  case (Left(e),    _)           => Left(e)
  case (_,          Left(e))     => Left(e)
}.map(Board(_))
```

`Pgn.parseTokens` uses the same shape: threads `(Situation, List[Move])` on the right track, short-circuits to `Left(error)` on the first bad SAN token.

### `Option → Either` promotion — `San.findCastle`, `Fen.decodeEp`, `Fen.decodeInt`

`Option` absence is promoted to the failure track with `.toRight(message)`, unifying the two idioms:

```scala
collectFirst { case m: CastlingMove if ... => m }
  .toRight("No queen-side castling available")   // San.scala

s.toIntOption.toRight("Invalid " + name + " '" + s + "'")  // Fen.scala
```

### Nested match cascade — `San.decodePieceMove`, `San.decodePawnMove`

Multiple validation steps each fork into Right/Left before passing to the next. The structure makes it impossible for an intermediate result to be silently ignored:

```scala
pt match
  case None => Left("Unknown piece letter")
  case Some(pieceType) =>
    Square.fromAlgebraic(dest) match
      case None     => Left("Invalid destination square")
      case Some(to) =>
        candidates match
          case single :: Nil => Right(single)
          case Nil           => Left("No legal move matches")
          case _             => Left("Ambiguous SAN")
```

### Track collapse at the UI boundary — `SharedGameModel`

`applyMove`, `importFen`, and `importPgn` all return `Either` to the UI layer, which is the only place that pattern-matches Right/Left and surfaces an error string to the user. Every layer below stays pure and testable.

## Higher-order functions

- `Ai.materialEval(pieceValues: Map[PieceType, Int]): Evaluator` — takes a value map, returns a scoring function (closure over `pieceValues`).
- `Ai.bestMove(ruleSet)(scorer)(depth): MoveProvider` — factory that captures configuration and returns a `GameState => Option[Move]` function.
- `StandardRules.slidingMoves` / `castRay` — direction list is a first-class value, `dirs.flatMap(castRay(...))` applies the function to each direction.
- `SharedGameModel.addObserver(f: State => Unit)` — observer stored as a function value; notifications call `obs.foreach(_(s))`.

## Currying & partial application

- `Ai.bestMove` is curried in three stages: `(ruleSet: RuleSet)(scorer: Evaluator)(depth: Int)` — fix the rule set and evaluator once at startup; vary depth per call site.
- `StandardRules.pawnForward`, `pawnStartRank`, `pawnPromoRank` — fix color, return the pawn-specific constant; used throughout `pawnCandidates` and `squareAttacks`.

## Closures

- `Ai.materialEval` returns an anonymous function that closes over `pieceValues`.
- `Ai.bestMove` returns a `MoveProvider` that closes over `ruleSet`, `scorer`, and `depth`.
- Each UI action button closes over the relevant `model` method (e.g. `() => model.undo()`).

## Tail-recursive functions (`@tailrec`)

- `StandardRules.castRay` — inner `loop(sq, acc)` accumulates moves along a ray; `@tailrec` guarantees stack safety.
- `StandardRules.rayReaches` — inner `loop(sq)` walks a ray to the target; `@tailrec`.
- `Ai.pickBest` — inner `loop(remaining, best)` folds over a scored-move list; `@tailrec`.
- `Ai.searchMax` / `Ai.searchMin` — iterate through move lists with alpha-beta pruning; both `@tailrec`.

## Function types as type aliases

- `type Evaluator = (Situation, RuleSet) => Int` — a named function type; passed as a first-class value through minimax.
- `type MoveProvider = GameState => Option[Move]` — captures the signature of any player (human input or AI search).

## Recursion replacing loops

- `Ai.minimax` calls `searchMax`/`searchMin` which recurse through the move list and call `minimax` at the next ply — no `while` or `for` loops in the search kernel.
- `StandardRules.castRay` and `rayReaches` replace explicit index loops with tail-recursive square walking.

## Map / flatMap / filter chaining

- `StandardRules.pawnCandidates` chains `filter`, `flatMap`, and `map` on `Option` and `List` to build the legal pawn move set without intermediate mutable collections.
- `Pgn.decode` threads the game state through `foldLeft` over token lists, propagating `Either` failures without early returns.

## foldLeft for aggregation

- `Ai.materialEval` — `board.pieces.values.foldLeft(0)` accumulates material score.
- `Ai.pickBest` — uses `foldLeft`-style tail recursion to find the best scored move.
- `GameController.threefoldRepetition` — `count` via `foldLeft` equivalent (`filter` + `length`).

## Observer / reactive pattern (functional style)

- `SharedGameModel` stores observers as `List[State => Unit]`; `commit` fans out to each observer via `obs.foreach(_(s))`.
- Both UIs register lambdas; neither UI holds a reference to the other.

## Self-constructed monad — `Ply[A]`

`Ply[A]` (`engine/Ply.scala`) is a purpose-built monad for chess game progression. Its underlying representation is:

```
opaque type Ply[A] = GameState => Ply.Step[A]
```

where `Step[A]` is either `Active(value: A, state: GameState)` (game ongoing) or `Terminal(result: GameResult, state: GameState)` (game ended). This makes it a concrete **StateT[Either[GameResult, \*], GameState, A]** — a state-threading monad whose "failure" track is a terminal chess result rather than an error.

### Monad structure

| Operation | Signature | Meaning |
|---|---|---|
| `pure(a)` | `A => Ply[A]` | Lift a value; leave state untouched |
| `get` | `Ply[GameState]` | Read current state as the result |
| `set(s)` | `Ply[Unit]` | Replace current state |
| `end(r)` | `Ply[A]` | Terminate with a `GameResult` |
| `map(f)` | `(A => B) => Ply[B]` | Transform the value; pass Terminal through |
| `flatMap(f)` | `(A => Ply[B]) => Ply[B]` | Sequence; skip `f` if already Terminal |
| `finalState` | `GameState => GameState` | Run and extract state regardless of outcome |

Monad laws hold: left identity, right identity, and associativity are all satisfied by the function-composition semantics of `flatMap`.

### Usage sites

**`GameController.step(move)`** — advances the game by one move then immediately checks for checkmate/stalemate/draw. Returns a `Ply[Unit]` so callers can chain further steps without manually threading state or repeating game-over checks.

**`GameController.replay(moves)`** — sequences an arbitrary list of moves via `foldLeft` over `flatMap`. The chain stops at the first terminal position; subsequent moves in the list are never executed.

**`Pgn.decode`** — refactored to a clean two-phase pipeline:
1. `parseTokens`: tokens → `Either[String, List[Move]]` (SAN parse, needs current `Situation` for disambiguation)
2. `ctrl.replay(moves).finalState(initial)`: play phase using `Ply`

### Advantages over the previous manual approach

| Concern | Before `Ply` | After `Ply` |
|---|---|---|
| **Game-end detection in PGN import** | Absent — all tokens were applied even past checkmate | Automatic — `step` detects it; `replay` stops there |
| **State threading** | Manual `Either[String, GameState]` foldLeft, advancing `GameState` inside each fold step | `replay` owns the threading; callers compose with `flatMap` |
| **Separation of concerns** | Parse and play were one combined fold | `parseTokens` (text → moves) and `replay` (moves → state) are independent, separately testable |
| **Legibility of sequences** | `moves.foldLeft(Right(state)) { case (Right(s), m) => Right(ctrl.advance(s, m)) … }` | `ctrl.replay(moves).finalState(initial)` |
| **Game-over semantics** | Implicit — callers must call `gameResult()` after every state-changing operation | Structural — `Ply.Step.Terminal` is part of the type; the compiler forces callers to handle it |

The core tradeoff the monad makes explicit: in chess, *sequencing* and *termination detection* are the same concern. Encoding both in `flatMap` means that invariant is enforced once, at the definition site, rather than scattered across every call site.
