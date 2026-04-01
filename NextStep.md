# Next Step — Move Generation

## What was built (foundation)

| File | Purpose |
|------|---------|
| `engine/Types.scala`   | Opaque types, piece/square/move constants |
| `engine/BB.scala`      | Inline bitboard utilities, directional shifts |
| `engine/Zobrist.scala` | XorShift64-seeded Zobrist tables |
| `engine/Attacks.scala` | Leaper attack tables + castling mask |
| `engine/Position.scala`| 12 bitboards, mailbox, Make/Unmake, incremental hash |
| `engine/UciBot.scala`  | Bot-interface stub (returns None) |

## What comes next: Move Generation

Move generation is the innermost loop of the engine — everything else is
overhead around it.  The plan is:

### Step 1 — Magic Bitboards for sliding pieces

1. Add `engine/Magics.scala` with pre-computed magic numbers, occupancy
   masks and attack tables for bishops and rooks (size: 2 × 4096 `Long` arrays).
2. Implement `slidingAttacks(piece, sq, occupied)` using the magic formula:
   ```
   index = ((occupied & mask(sq)) * magic(sq)) >>> shift(sq)
   attacks = attackTable(sq)(index)
   ```
3. Queen attacks = bishop attacks | rook attacks.

### Step 2 — `MoveGen` object

Add `engine/MoveGen.scala`:

```scala
object MoveGen:
  // Fill pre-allocated array; return move count (avoids List allocation)
  def generate(pos: Position, moves: Array[Int]): Int
  def generateCaptures(pos: Position, moves: Array[Int]): Int  // for QSearch
```

Implement in this order (simplest → most complex):
1. Pawn pushes (single + double) using `BB.shiftN/S`
2. Pawn captures using `Attacks.pawnAttacks`
3. En-passant
4. Knight moves using `Attacks.knightAttacks`
5. King moves using `Attacks.kingAttacks`
6. Castling (check rook presence + empty squares + no check on king path)
7. Bishop + Rook + Queen via Magic Bitboards
8. Promotions (split from push/capture generation)

### Step 3 — Legality filter

Add `isInCheck(pos, color)` and `isLegal(pos, move)`.  Prefer a
pseudo-legal generator + legality filter over fully legal generation, since
the filter only executes `makeMove` + `isInCheck(king)` + `unmakeMove`.

---

## Claude Code prompt for Move Generation

> **Role & Goal:** Continue building the world-class UCI chess engine in
> `modules/bots/src/main/scala/engine/`.  The foundation (Types, BB, Zobrist,
> Attacks, Position with Make/Unmake) is complete.  Now add **Magic Bitboards**
> and **pseudo-legal move generation**.
>
> **Technical constraints** (same as before):
> - Scala 3, inline functions, primitive arrays, no allocation in hot path.
> - No standard Scala collections (`List`, `Option`, etc.) in generation code.
> - WartRemover and coverage are excluded for the `engine` subpackage.
>
> **Your task:**
>
> 1. **`engine/Magics.scala`** — Static magic-bitboard tables for rooks and
>    bishops.  Use the well-known fixed magic numbers (copy from any open-source
>    C engine such as Stockfish or Ethereal).  Store occupancy masks, magic
>    numbers, shifts, and flattened attack tables in `Array[Long]`.  Provide
>    `rookAttacks(sq, occupied)` and `bishopAttacks(sq, occupied)` as `inline`
>    methods.
>
> 2. **`engine/MoveGen.scala`** — Object with two methods:
>    - `generate(pos: Position, moves: Array[Int]): Int` — all pseudo-legal moves
>    - `generateCaptures(pos: Position, moves: Array[Int]): Int` — captures only (for Quiescence Search)
>
>    Generate moves in this order: pawn pushes, pawn captures, en-passant,
>    knight, king, castling, bishop, rook, queen, promotions.
>    Encode each move with `Move.encode(from, to, flag)`.
>    Return the count of moves written into `moves`.
>
> 3. **`engine/LegalCheck.scala`** — `isInCheck(pos: Position, color: Int): Boolean`
>    using attack tables and `pos.occupied`.  Also `isLegal(pos: Position, move: Int): Boolean`
>    that calls makeMove, isInCheck, unmakeMove.
>
> 4. **Update `engine/UciBot.scala`** — wire up the move generator so
>    `chooseMove` picks a random legal move (temporary placeholder for search).
>
> Keep all files under ~150 lines.  Do not modify any file outside
> `modules/bots/src/main/scala/engine/` and `modules/bots/src/main/scala/BotRegistry.scala`.
> Do NOT add UciBot to BotRegistry yet — it is still a work-in-progress.
