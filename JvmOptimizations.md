# JVM Optimisations — MaiChess UCI Engine

This document explains the architectural decisions in the engine foundation
(`modules/bots/src/main/scala/engine/`) and how they produce maximum
nodes-per-second (NPS) throughput on the JVM.

---

## 1. Opaque types compile to primitives

```scala
opaque type Square    = Int
opaque type Piece     = Int
opaque type Bitboard  = Long
```

Opaque types in Scala 3 are erased at the bytecode level.  A `Square` value
in a local variable compiles to a JVM `int`; a `Bitboard` compiles to a `long`.
There is **zero heap allocation** and **zero boxing** for these values.
By contrast, a `case class Square(val v: Int)` would allocate an object per
value, causing gigabytes of garbage during a long search.

---

## 2. `inline` methods → zero-overhead abstractions

Every method in `BB`, `Zobrist`, and `Attacks` is annotated `inline`.
The Scala 3 compiler expands them at the call site before generating bytecode,
so the JIT sees a single flat expression with no virtual dispatch, no closure
allocation, and no stack frame overhead.

```scala
inline def lsb(bb: Long): Square =
  Square(java.lang.Long.numberOfTrailingZeros(bb))
```

`numberOfTrailingZeros` is a JDK intrinsic; on x86-64 the JIT emits a single
`BSF` (Bit Scan Forward) or `TZCNT` instruction.  Similarly `bitCount` emits
`POPCNT`.  These are single-cycle operations on all modern CPUs.

---

## 3. 4-bit piece encoding — array indexing instead of pattern matching

Pieces are stored as a 4-bit integer: `(color << 3) | pieceType`.

| Bits | Meaning |
|------|---------|
| 3    | 0 = White, 1 = Black |
| 0–2  | Piece type (0=Pawn … 5=King) |

This means a piece index is a direct array subscript: `pieces(piece.toInt)`.
No `match` expression, no branch prediction miss.  The `byColor` and
`occupied` aggregates are kept in sync and consulted for fast
intersection tests without scanning `pieces`.

---

## 4. Mailbox for O(1) piece-type lookup

Bitboards excel at "which squares does piece X attack?" but are slow for
"what piece is on square S?".  The `mailbox(64)` array answers the latter
in a single load — critical during capture handling in `makeMove`.

Both structures are kept in perfect sync by `putPiece` / `removePiece` /
`movePiece`.  The Raw variants used in `unmakeMove` skip the Zobrist XOR
because the saved hash is restored directly, avoiding redundant work.

---

## 5. In-place Make/Unmake — no copying

Many engines copy the board before each recursive call.  On the JVM this
allocates objects and triggers the GC at search depth — a 100 ms GC pause
at depth 12 costs millions of nodes.

Instead, `Position` uses **parallel undo stacks**:

```scala
val undoState:    Array[Int]  = new Array[Int](1024)   // packed state
val undoCapPiece: Array[Int]  = new Array[Int](1024)   // captured piece
val undoHash:     Array[Long] = new Array[Long](1024)  // full Zobrist hash
```

`makeMove` pushes to these arrays before mutating and `unmakeMove` pops and
reverses.  The stack depth of 1024 covers any reachable game tree.  **No
heap objects are allocated in the hot path.**

### Packed undo state

`undoState` encodes three fields into a single `Int`:

| Bits  | Field            |
|-------|------------------|
| 0–10  | halfMoveClock    |
| 11–17 | epSquare + 1     |
| 18–21 | castlingRights   |

One load restores three fields simultaneously.

---

## 6. Castling rights via O(1) mask table

```scala
castlingRights &= Attacks.castlingMask(from.toInt) & Attacks.castlingMask(to.toInt)
```

Two array lookups and two `AND` operations update castling rights after any
move, regardless of piece type.  Squares not involved in castling store `15`
(all bits set), which is a no-op AND.

---

## 7. Zobrist hashing: incremental XOR

The hash is never recomputed from scratch during search.  Each `makeMove`
XORs at most 6 `Long` values (piece-square entries + castling + ep + side).
`unmakeMove` simply restores the saved `Long` from the undo stack — zero XOR
operations on the reverse path.

---

## 8. Directional shift masks prevent wrap-around

```scala
inline def shiftE(bb: Long): Long = (bb & NotFileH) << 1
```

Without the mask, a piece on H-file would "wrap" to A-file after a left
shift.  Masking before the shift is a single `AND` on a compile-time
constant, which the JIT constant-folds into the shift operand on most
architectures.

---

## 9. What remains outside the hot path

- `recomputeHash()`: O(64) scan used only after FEN setup, not during search.
- `BB.prettyPrint()`: uses `StringBuilder`; debug only.
- `UciBot.chooseMove()`: currently a stub; search will be added next.

All of the above are deliberately excluded from the performance-critical path.
