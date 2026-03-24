package chess

import chess.model.*
import chess.rules.{Board, CastlingRights, Situation, StandardRules}
import munit.FunSuite

class MoveGeneratorSuite extends FunSuite:

  private def sq(s: String): Square   = Square.fromAlgebraic(s).get
  private def sqSet(ss: String*): Set[Square] = ss.map(sq).toSet

  private def situation(
    pieces: Map[Square, Piece],
    turn: Color = Color.White,
    castling: CastlingRights = CastlingRights.none,
    ep: Option[Square] = None,
  ): Situation =
    Situation(Board(pieces), turn, castling, ep, 0, 1)

  // ── Pawn moves ─────────────────────────────────────────────────────────────

  test("White pawn on e2 can move to e3 and e4 from standard position"):
    val targets = StandardRules.legalMoves(Situation.standard, sq("e2")).map(_.to).toSet
    assert(targets.contains(sq("e3")), "should include e3")
    assert(targets.contains(sq("e4")), "should include e4")

  test("White pawn on e2 has exactly 2 legal moves in standard position"):
    assertEquals(StandardRules.legalMoves(Situation.standard, sq("e2")).size, 2)

  test("Black pawn on e7 can move to e6 and e5 from standard position (black's turn)"):
    val blackStart = Situation.standard.copy(turn = Color.Black)
    val targets    = StandardRules.legalMoves(blackStart, sq("e7")).map(_.to).toSet
    assert(targets.contains(sq("e6")), "should include e6")
    assert(targets.contains(sq("e5")), "should include e5")

  test("White pawn on rank 8 cannot move off the board"):
    val sit = situation(Map(sq("a8") -> Piece(Color.White, PieceType.Pawn)))
    assertEquals(StandardRules.candidateMoves(sit, sq("a8")), Nil)

  test("White pawn blocked by piece in front cannot push"):
    val sit = situation(Map(
      sq("e4") -> Piece(Color.White, PieceType.Pawn),
      sq("e5") -> Piece(Color.Black, PieceType.Pawn),
    ))
    val moves = StandardRules.candidateMoves(sit, sq("e4")).map(_.to).toSet
    assert(!moves.contains(sq("e5")))
    assert(!moves.contains(sq("e6")))

  test("White pawn on starting rank blocked in front cannot double-push"):
    val sit = situation(Map(
      sq("e2") -> Piece(Color.White, PieceType.Pawn),
      sq("e3") -> Piece(Color.Black, PieceType.Pawn),
    ))
    assertEquals(StandardRules.candidateMoves(sit, sq("e2")), Nil)

  // ── Knight moves ───────────────────────────────────────────────────────────

  test("Lone white knight on e4 has 8 candidate moves"):
    val sit = situation(Map(sq("e4") -> Piece(Color.White, PieceType.Knight)))
    assertEquals(StandardRules.candidateMoves(sit, sq("e4")).size, 8)

  test("White knight on a1 has only 2 candidate moves"):
    val sit = situation(Map(sq("a1") -> Piece(Color.White, PieceType.Knight)))
    assertEquals(StandardRules.candidateMoves(sit, sq("a1")).size, 2)

  test("Knight targets from e4 are correct"):
    val sit     = situation(Map(sq("e4") -> Piece(Color.White, PieceType.Knight)))
    val targets = StandardRules.candidateMoves(sit, sq("e4")).map(_.to).toSet
    assertEquals(targets, sqSet("f6", "g5", "g3", "f2", "d2", "c3", "c5", "d6"))

  // ── King safety ────────────────────────────────────────────────────────────

  test("King cannot move into check"):
    // White king on e1, black rook on e8 controls entire e-file
    val sit = situation(Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.Rook),
    ))
    val targets = StandardRules.legalMoves(sit, sq("e1")).map(_.to).toSet
    assert(!targets.contains(sq("e2")), "e2 attacked by rook on e8")

  test("King can move away from check"):
    val sit = situation(Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.Rook),
    ))
    val targets = StandardRules.legalMoves(sit, sq("e1")).map(_.to).toSet
    assert(targets.nonEmpty, "king must have at least one escape")
    assert(targets.forall(_.file.toChar != 'e'), "no e-file squares allowed")

  // ── En passant ─────────────────────────────────────────────────────────────

  test("En passant is available after double pawn push"):
    // White plays e2-e4, then situation has ep square e3
    val afterE4 = StandardRules.applyMove(Situation.standard, NormalMove(sq("e2"), sq("e4")))
    assertEquals(afterE4.enPassantSquare, Some(sq("e3")))

  test("Black can capture en passant on the correct square"):
    // Black pawn on d4, white just played c2-c4 → ep on c3
    val sit = situation(
      Map(
        sq("d4") -> Piece(Color.Black, PieceType.Pawn),
        sq("c4") -> Piece(Color.White, PieceType.Pawn),
        sq("e1") -> Piece(Color.White, PieceType.King),  // keep kings to avoid issues
        sq("e8") -> Piece(Color.Black, PieceType.King),
      ),
      turn     = Color.Black,
      castling = CastlingRights.none,
      ep       = Some(sq("c3")),
    )
    val epMoves = StandardRules.legalMoves(sit, sq("d4")).collect { case e: EnPassantMove => e }
    assertEquals(epMoves.size, 1)
    assertEquals(epMoves.head.to, sq("c3"))
    assertEquals(epMoves.head.captured, sq("c4"))

  test("En passant square is cleared after the next move"):
    val afterE4 = StandardRules.applyMove(Situation.standard, NormalMove(sq("e2"), sq("e4")))
    val afterD5 = StandardRules.applyMove(afterE4, NormalMove(sq("d7"), sq("d5")))
    // Black played d7-d5 so ep should now be d6, not e3
    assertEquals(afterD5.enPassantSquare, Some(sq("d6")))

  // ── Castling ───────────────────────────────────────────────────────────────

  test("White can castle king-side when path is clear and rights exist"):
    val sit = situation(
      Map(
        sq("e1") -> Piece(Color.White, PieceType.King),
        sq("h1") -> Piece(Color.White, PieceType.Rook),
        sq("e8") -> Piece(Color.Black, PieceType.King),
      ),
      castling = CastlingRights.all,
    )
    val castles = StandardRules.legalMoves(sit, sq("e1")).collect { case c: CastlingMove => c }
    assert(castles.exists(_.to == sq("g1")), "king-side castle to g1 should be legal")

  test("White can castle queen-side when path is clear and rights exist"):
    val sit = situation(
      Map(
        sq("e1") -> Piece(Color.White, PieceType.King),
        sq("a1") -> Piece(Color.White, PieceType.Rook),
        sq("e8") -> Piece(Color.Black, PieceType.King),
      ),
      castling = CastlingRights.all,
    )
    val castles = StandardRules.legalMoves(sit, sq("e1")).collect { case c: CastlingMove => c }
    assert(castles.exists(_.to == sq("c1")), "queen-side castle to c1 should be legal")

  test("Castling is illegal when king is in check"):
    val sit = situation(
      Map(
        sq("e1") -> Piece(Color.White, PieceType.King),
        sq("h1") -> Piece(Color.White, PieceType.Rook),
        sq("e8") -> Piece(Color.Black, PieceType.Rook),  // attacks e1
      ),
      castling = CastlingRights.all,
    )
    val castles = StandardRules.legalMoves(sit, sq("e1")).collect { case c: CastlingMove => c }
    assert(castles.isEmpty, "cannot castle while in check")

  test("Castling is illegal when king passes through an attacked square"):
    val sit = situation(
      Map(
        sq("e1") -> Piece(Color.White, PieceType.King),
        sq("h1") -> Piece(Color.White, PieceType.Rook),
        sq("f8") -> Piece(Color.Black, PieceType.Rook),  // attacks f1 (king passes through)
      ),
      castling = CastlingRights.all,
    )
    val castles = StandardRules.legalMoves(sit, sq("e1")).collect { case c: CastlingMove => c }
    assert(!castles.exists(_.to == sq("g1")), "cannot castle through f1 (attacked)")

  test("Castling is illegal when castling rights are absent"):
    val sit = situation(
      Map(
        sq("e1") -> Piece(Color.White, PieceType.King),
        sq("h1") -> Piece(Color.White, PieceType.Rook),
        sq("e8") -> Piece(Color.Black, PieceType.King),
      ),
      castling = CastlingRights.none,
    )
    val castles = StandardRules.legalMoves(sit, sq("e1")).collect { case c: CastlingMove => c }
    assert(castles.isEmpty, "no castling rights → no castling moves")
