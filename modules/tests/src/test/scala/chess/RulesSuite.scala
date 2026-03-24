package chess

import chess.model.*
import chess.rules.{Board, CastlingRights, Situation, StandardRules}
import munit.FunSuite

class RulesSuite extends FunSuite:

  private def sq(s: String): Square = Square.fromAlgebraic(s).get

  private def sit(
    pieces: Map[Square, Piece],
    turn: Color = Color.White,
    castling: CastlingRights = CastlingRights.none,
    ep: Option[Square] = None,
    halfClock: Int = 0,
  ): Situation =
    Situation(Board(pieces), turn, castling, ep, halfClock, 1)

  private def blackTurn(pieces: Map[Square, Piece], castling: CastlingRights = CastlingRights.none): Situation =
    sit(pieces, turn = Color.Black, castling = castling)

  // ── Pawn diagonal capture ──────────────────────────────────────────────────

  test("White pawn can capture diagonally"):
    val s = sit(Map(
      sq("d4") -> Piece(Color.White, PieceType.Pawn),
      sq("e5") -> Piece(Color.Black, PieceType.Knight),
    ))
    val targets = StandardRules.candidateMoves(s, sq("d4")).map(_.to).toSet
    assert(targets.contains(sq("e5")), "should be able to capture on e5")

  test("White pawn cannot capture friendly piece diagonally"):
    val s = sit(Map(
      sq("d4") -> Piece(Color.White, PieceType.Pawn),
      sq("e5") -> Piece(Color.White, PieceType.Knight),
    ))
    val targets = StandardRules.candidateMoves(s, sq("d4")).map(_.to).toSet
    assert(!targets.contains(sq("e5")))

  // ── Pawn promotion ─────────────────────────────────────────────────────────

  test("White pawn on rank 7 generates 4 push-promotion moves"):
    val s     = sit(Map(sq("e7") -> Piece(Color.White, PieceType.Pawn)))
    val moves = StandardRules.candidateMoves(s, sq("e7"))
    assertEquals(moves.size, 4)
    assert(moves.forall { case NormalMove(_, _, Some(_)) => true; case _ => false })
    val pieces = moves.collect { case NormalMove(_, _, Some(pt)) => pt }.toSet
    assertEquals(pieces, Set(PieceType.Queen, PieceType.Rook, PieceType.Bishop, PieceType.Knight))

  test("White pawn capture-promotion produces 4 moves"):
    val s = sit(Map(
      sq("d7") -> Piece(Color.White, PieceType.Pawn),
      sq("e8") -> Piece(Color.Black, PieceType.Rook),
    ))
    val captureMoves = StandardRules.candidateMoves(s, sq("d7"))
      .filter(m => m.to == sq("e8"))
    assertEquals(captureMoves.size, 4)
    assert(captureMoves.forall { case NormalMove(_, _, Some(_)) => true; case _ => false })

  test("Black pawn on rank 2 generates 4 push-promotion moves"):
    val s     = blackTurn(Map(sq("e2") -> Piece(Color.Black, PieceType.Pawn)))
    val moves = StandardRules.candidateMoves(s, sq("e2"))
    assertEquals(moves.size, 4)
    assert(moves.forall { case NormalMove(_, _, Some(_)) => true; case _ => false })

  // ── En passant square tracking ─────────────────────────────────────────────

  test("Applying an EnPassantMove clears the en passant square"):
    val s    = sit(
      Map(sq("e5") -> Piece(Color.White, PieceType.Pawn), sq("d5") -> Piece(Color.Black, PieceType.Pawn)),
      ep = Some(sq("d6")),
    )
    val next = StandardRules.applyMove(s, EnPassantMove(sq("e5"), sq("d6"), sq("d5")))
    assertEquals(next.enPassantSquare, None)

  test("Applying a CastlingMove clears the en passant square"):
    val s = sit(
      Map(sq("e1") -> Piece(Color.White, PieceType.King), sq("h1") -> Piece(Color.White, PieceType.Rook)),
      castling = CastlingRights.all,
      ep       = Some(sq("e3")),
    )
    val next = StandardRules.applyMove(s, CastlingMove(sq("e1"), sq("g1"), sq("h1"), sq("f1")))
    assertEquals(next.enPassantSquare, None)

  test("Applying a promotion NormalMove clears the en passant square"):
    val s    = sit(Map(sq("e7") -> Piece(Color.White, PieceType.Pawn)), ep = Some(sq("d6")))
    val next = StandardRules.applyMove(s, NormalMove(sq("e7"), sq("e8"), Some(PieceType.Queen)))
    assertEquals(next.enPassantSquare, None)

  // ── Castling rights: king moves ────────────────────────────────────────────

  test("White king move forfeits both white castling rights"):
    val s = sit(
      Map(sq("e1") -> Piece(Color.White, PieceType.King), sq("e8") -> Piece(Color.Black, PieceType.King)),
      castling = CastlingRights.all,
    )
    val next = StandardRules.applyMove(s, NormalMove(sq("e1"), sq("d1")))
    assertEquals(next.castlingRights.whiteKingSide, false)
    assertEquals(next.castlingRights.whiteQueenSide, false)
    assertEquals(next.castlingRights.blackKingSide, true)
    assertEquals(next.castlingRights.blackQueenSide, true)

  test("Black king move forfeits both black castling rights"):
    val s = blackTurn(
      Map(sq("e1") -> Piece(Color.White, PieceType.King), sq("e8") -> Piece(Color.Black, PieceType.King)),
      castling = CastlingRights.all,
    )
    val next = StandardRules.applyMove(s, NormalMove(sq("e8"), sq("d8")))
    assertEquals(next.castlingRights.blackKingSide, false)
    assertEquals(next.castlingRights.blackQueenSide, false)
    assertEquals(next.castlingRights.whiteKingSide, true)
    assertEquals(next.castlingRights.whiteQueenSide, true)

  // ── Castling rights: rook moves ────────────────────────────────────────────

  test("White a1 rook move forfeits white queen-side right"):
    val s    = sit(Map(sq("e1") -> Piece(Color.White, PieceType.King), sq("a1") -> Piece(Color.White, PieceType.Rook)), castling = CastlingRights.all)
    val next = StandardRules.applyMove(s, NormalMove(sq("a1"), sq("b1")))
    assertEquals(next.castlingRights.whiteQueenSide, false)
    assertEquals(next.castlingRights.whiteKingSide, true)

  test("White h1 rook move forfeits white king-side right"):
    val s    = sit(Map(sq("e1") -> Piece(Color.White, PieceType.King), sq("h1") -> Piece(Color.White, PieceType.Rook)), castling = CastlingRights.all)
    val next = StandardRules.applyMove(s, NormalMove(sq("h1"), sq("g1")))
    assertEquals(next.castlingRights.whiteKingSide, false)
    assertEquals(next.castlingRights.whiteQueenSide, true)

  test("White rook moving from non-corner rank-1 square keeps all rights"):
    val s    = sit(Map(sq("e1") -> Piece(Color.White, PieceType.King), sq("b1") -> Piece(Color.White, PieceType.Rook)), castling = CastlingRights.all)
    val next = StandardRules.applyMove(s, NormalMove(sq("b1"), sq("c1")))
    assertEquals(next.castlingRights.whiteKingSide, true)
    assertEquals(next.castlingRights.whiteQueenSide, true)

  test("Black a8 rook move forfeits black queen-side right"):
    val s    = blackTurn(Map(sq("e8") -> Piece(Color.Black, PieceType.King), sq("a8") -> Piece(Color.Black, PieceType.Rook)), castling = CastlingRights.all)
    val next = StandardRules.applyMove(s, NormalMove(sq("a8"), sq("b8")))
    assertEquals(next.castlingRights.blackQueenSide, false)
    assertEquals(next.castlingRights.blackKingSide, true)

  test("Black h8 rook move forfeits black king-side right"):
    val s    = blackTurn(Map(sq("e8") -> Piece(Color.Black, PieceType.King), sq("h8") -> Piece(Color.Black, PieceType.Rook)), castling = CastlingRights.all)
    val next = StandardRules.applyMove(s, NormalMove(sq("h8"), sq("g8")))
    assertEquals(next.castlingRights.blackKingSide, false)
    assertEquals(next.castlingRights.blackQueenSide, true)

  test("Black rook moving from non-corner rank-8 square keeps all rights"):
    val s    = blackTurn(Map(sq("e8") -> Piece(Color.Black, PieceType.King), sq("c8") -> Piece(Color.Black, PieceType.Rook)), castling = CastlingRights.all)
    val next = StandardRules.applyMove(s, NormalMove(sq("c8"), sq("d8")))
    assertEquals(next.castlingRights.blackKingSide, true)
    assertEquals(next.castlingRights.blackQueenSide, true)

  // ── Castling rights: rook captured on home square ──────────────────────────

  test("Capturing white rook on a1 forfeits white queen-side right"):
    val s = sit(
      Map(sq("e1") -> Piece(Color.White, PieceType.King), sq("a1") -> Piece(Color.White, PieceType.Rook),
          sq("e8") -> Piece(Color.Black, PieceType.King), sq("b3") -> Piece(Color.Black, PieceType.Bishop)),
      castling = CastlingRights.all,
    )
    val next = StandardRules.applyMove(s, NormalMove(sq("b3"), sq("a1")))
    assertEquals(next.castlingRights.whiteQueenSide, false)

  test("Capturing white rook on h1 forfeits white king-side right"):
    val s = sit(
      Map(sq("e1") -> Piece(Color.White, PieceType.King), sq("h1") -> Piece(Color.White, PieceType.Rook),
          sq("e8") -> Piece(Color.Black, PieceType.King), sq("g3") -> Piece(Color.Black, PieceType.Bishop)),
      castling = CastlingRights.all,
    )
    val next = StandardRules.applyMove(s, NormalMove(sq("g3"), sq("h1")))
    assertEquals(next.castlingRights.whiteKingSide, false)

  test("Capturing black rook on a8 forfeits black queen-side right"):
    val s = sit(
      Map(sq("e1") -> Piece(Color.White, PieceType.King), sq("b6") -> Piece(Color.White, PieceType.Bishop),
          sq("e8") -> Piece(Color.Black, PieceType.King), sq("a8") -> Piece(Color.Black, PieceType.Rook)),
      castling = CastlingRights.all,
    )
    val next = StandardRules.applyMove(s, NormalMove(sq("b6"), sq("a8")))  // white captures
    assertEquals(next.castlingRights.blackQueenSide, false)

  test("Capturing black rook on h8 forfeits black king-side right"):
    val s = sit(
      Map(sq("e1") -> Piece(Color.White, PieceType.King), sq("g6") -> Piece(Color.White, PieceType.Bishop),
          sq("e8") -> Piece(Color.Black, PieceType.King), sq("h8") -> Piece(Color.Black, PieceType.Rook)),
      castling = CastlingRights.all,
    )
    val next = StandardRules.applyMove(s, NormalMove(sq("g6"), sq("h8")))
    assertEquals(next.castlingRights.blackKingSide, false)

  test("Capturing a rook on a non-corner non-back-rank square keeps all rights"):
    val s = sit(
      Map(sq("e1") -> Piece(Color.White, PieceType.King), sq("d4") -> Piece(Color.White, PieceType.Bishop),
          sq("e8") -> Piece(Color.Black, PieceType.King), sq("c5") -> Piece(Color.Black, PieceType.Rook)),
      castling = CastlingRights.all,
    )
    val next = StandardRules.applyMove(s, NormalMove(sq("d4"), sq("c5")))
    assertEquals(next.castlingRights, CastlingRights.all.copy(blackKingSide = true, blackQueenSide = true))

  test("Capturing a black rook on non-corner rank-8 square keeps black rights"):
    val s = sit(
      Map(sq("e1") -> Piece(Color.White, PieceType.King), sq("d6") -> Piece(Color.White, PieceType.Bishop),
          sq("e8") -> Piece(Color.Black, PieceType.King), sq("c8") -> Piece(Color.Black, PieceType.Rook)),
      castling = CastlingRights.all,
    )
    val next = StandardRules.applyMove(s, NormalMove(sq("d6"), sq("c8")))
    assertEquals(next.castlingRights.blackKingSide, true)
    assertEquals(next.castlingRights.blackQueenSide, true)

  // ── Checkmate ──────────────────────────────────────────────────────────────

  test("Scholar's-mate position is checkmate"):
    val s = sit(
      Map(
        sq("e8") -> Piece(Color.Black, PieceType.King),
        sq("d8") -> Piece(Color.Black, PieceType.Queen),
        sq("c8") -> Piece(Color.Black, PieceType.Bishop),
        sq("b8") -> Piece(Color.Black, PieceType.Knight),
        sq("a8") -> Piece(Color.Black, PieceType.Rook),
        sq("g8") -> Piece(Color.Black, PieceType.Knight),
        sq("h8") -> Piece(Color.Black, PieceType.Rook),
        sq("a7") -> Piece(Color.Black, PieceType.Pawn),
        sq("b7") -> Piece(Color.Black, PieceType.Pawn),
        sq("c7") -> Piece(Color.Black, PieceType.Pawn),
        sq("d7") -> Piece(Color.Black, PieceType.Pawn),
        sq("f7") -> Piece(Color.White, PieceType.Queen),
        sq("g7") -> Piece(Color.Black, PieceType.Pawn),
        sq("h7") -> Piece(Color.Black, PieceType.Pawn),
        sq("c6") -> Piece(Color.Black, PieceType.Knight),
        sq("f6") -> Piece(Color.Black, PieceType.Knight),
        sq("c4") -> Piece(Color.White, PieceType.Bishop),
        sq("e1") -> Piece(Color.White, PieceType.King),
      ),
      turn = Color.Black,
    )
    assert(StandardRules.isCheckmate(s))

  test("isCheckmate returns false when not in check"):
    assert(!StandardRules.isCheckmate(Situation.standard))

  // ── Stalemate ──────────────────────────────────────────────────────────────

  test("Stalemate position is detected"):
    val s = blackTurn(Map(
      sq("a8") -> Piece(Color.Black, PieceType.King),
      sq("b6") -> Piece(Color.White, PieceType.Queen),
      sq("c7") -> Piece(Color.White, PieceType.King),
    ))
    assert(StandardRules.isStalemate(s))

  test("isStalemate returns false for starting position"):
    assert(!StandardRules.isStalemate(Situation.standard))

  test("isStalemate returns false when player is in check"):
    val s = blackTurn(Map(
      sq("a8") -> Piece(Color.Black, PieceType.King),
      sq("a1") -> Piece(Color.White, PieceType.Rook),
      sq("c7") -> Piece(Color.White, PieceType.King),
    ))
    assert(!StandardRules.isStalemate(s))

  // ── Fifty-move rule ────────────────────────────────────────────────────────

  test("Fifty-move rule not triggered at halfMoveClock = 99"):
    assert(!StandardRules.isFiftyMoveRule(sit(Map(sq("e1") -> Piece(Color.White, PieceType.King)), halfClock = 99)))

  test("Fifty-move rule triggered at halfMoveClock = 100"):
    assert(StandardRules.isFiftyMoveRule(sit(Map(sq("e1") -> Piece(Color.White, PieceType.King)), halfClock = 100)))

  test("halfMoveClock resets to 0 after pawn move"):
    val next = StandardRules.applyMove(Situation.standard.copy(halfMoveClock = 40), NormalMove(sq("e2"), sq("e4")))
    assertEquals(next.halfMoveClock, 0)

  test("halfMoveClock increments after non-pawn non-capture move"):
    val next = StandardRules.applyMove(Situation.standard.copy(halfMoveClock = 5), NormalMove(sq("g1"), sq("f3")))
    assertEquals(next.halfMoveClock, 6)

  // ── Check detection ────────────────────────────────────────────────────────

  test("isCheck detects rook check"):
    val s = sit(Map(sq("e1") -> Piece(Color.White, PieceType.King), sq("e8") -> Piece(Color.Black, PieceType.Rook)))
    assert(StandardRules.isCheck(s))

  test("isCheck returns false when not in check"):
    assert(!StandardRules.isCheck(Situation.standard))

  // ── Insufficient material ──────────────────────────────────────────────────

  test("King vs king is insufficient material"):
    val s = sit(Map(sq("e1") -> Piece(Color.White, PieceType.King), sq("e8") -> Piece(Color.Black, PieceType.King)))
    assert(StandardRules.isInsufficientMaterial(s))

  test("King+bishop vs king is insufficient material"):
    val s = sit(Map(sq("e1") -> Piece(Color.White, PieceType.King), sq("c4") -> Piece(Color.White, PieceType.Bishop), sq("e8") -> Piece(Color.Black, PieceType.King)))
    assert(StandardRules.isInsufficientMaterial(s))

  test("King+knight vs king is insufficient material"):
    val s = sit(Map(sq("e1") -> Piece(Color.White, PieceType.King), sq("c4") -> Piece(Color.White, PieceType.Knight), sq("e8") -> Piece(Color.Black, PieceType.King)))
    assert(StandardRules.isInsufficientMaterial(s))

  test("King+queen vs king is NOT insufficient material"):
    val s = sit(Map(sq("e1") -> Piece(Color.White, PieceType.King), sq("d1") -> Piece(Color.White, PieceType.Queen), sq("e8") -> Piece(Color.Black, PieceType.King)))
    assert(!StandardRules.isInsufficientMaterial(s))
