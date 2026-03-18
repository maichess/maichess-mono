package org.maichess.mono.tests

import munit.FunSuite
import org.maichess.mono.model.*
import org.maichess.mono.rules.*

class MoveGeneratorSuite extends FunSuite:

  def sq(alg: String): Square =
    Square.fromAlgebraic(alg).getOrElse(throw new AssertionError(s"Invalid square: $alg"))

  def sit(
    pieces: Map[Square, Piece],
    turn: Color = Color.White,
    cr: CastlingRights = CastlingRights.none,
    ep: Option[Square] = None
  ): Situation = Situation(Board(pieces), turn, cr, ep, 0, 1)

  test("White pawn on e2 can advance to e3"):
    val moves = StandardRules.legalMoves(sit(Map(sq("e2") -> Piece(Color.White, PieceType.Pawn))), sq("e2"))
    assert(moves.map(_.to).contains(sq("e3")))

  test("White pawn on e2 can double-push to e4"):
    val moves = StandardRules.legalMoves(sit(Map(sq("e2") -> Piece(Color.White, PieceType.Pawn))), sq("e2"))
    assert(moves.map(_.to).contains(sq("e4")))

  test("White pawn on e2 has exactly 2 moves from starting square"):
    val moves = StandardRules.legalMoves(sit(Map(sq("e2") -> Piece(Color.White, PieceType.Pawn))), sq("e2"))
    assertEquals(moves.size, 2)

  test("Black pawn on e7 can advance to e6"):
    val moves = StandardRules.legalMoves(sit(Map(sq("e7") -> Piece(Color.Black, PieceType.Pawn)), Color.Black), sq("e7"))
    assert(moves.map(_.to).contains(sq("e6")))

  test("Black pawn on e7 can double-push to e5"):
    val moves = StandardRules.legalMoves(sit(Map(sq("e7") -> Piece(Color.Black, PieceType.Pawn)), Color.Black), sq("e7"))
    assert(moves.map(_.to).contains(sq("e5")))

  test("Knight on e4 has 8 moves in open board"):
    val moves = StandardRules.candidateMoves(
      sit(Map(sq("e4") -> Piece(Color.White, PieceType.Knight))), sq("e4"))
    assertEquals(moves.size, 8)

  test("Knight on a1 has only 2 moves (corner)"):
    val moves = StandardRules.candidateMoves(
      sit(Map(sq("a1") -> Piece(Color.White, PieceType.Knight))), sq("a1"))
    assertEquals(moves.size, 2)

  test("King cannot move into check"):
    // White king on e1, black rook on e8 — king cannot move to e2
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.Rook)
    )
    val moves = StandardRules.legalMoves(sit(pieces), sq("e1"))
    assert(!moves.map(_.to).contains(sq("e2")))

  test("En passant move available after double pawn push"):
    // White pawn on e5, black pawn just moved d7-d5 → en passant square is d6
    val pieces = Map(
      sq("e5") -> Piece(Color.White, PieceType.Pawn),
      sq("d5") -> Piece(Color.Black, PieceType.Pawn)
    )
    val s = sit(pieces, Color.White, CastlingRights.none, Some(sq("d6")))
    val moves = StandardRules.legalMoves(s, sq("e5"))
    assert(moves.exists(_.isInstanceOf[EnPassantMove]))

  test("Castling legal when path clear and no prior moves"):
    // White king e1, white rook h1, nothing in between
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("h1") -> Piece(Color.White, PieceType.Rook)
    )
    val s = sit(pieces, Color.White, CastlingRights.all)
    val moves = StandardRules.legalMoves(s, sq("e1"))
    assert(moves.exists(_.isInstanceOf[CastlingMove]))

  test("Castling illegal when king is in check"):
    // White king e1 in check from black rook e8, rook h1 present
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("h1") -> Piece(Color.White, PieceType.Rook),
      sq("e8") -> Piece(Color.Black, PieceType.Rook)
    )
    val s = sit(pieces, Color.White, CastlingRights.all)
    val moves = StandardRules.legalMoves(s, sq("e1"))
    assert(!moves.exists(_.isInstanceOf[CastlingMove]))

  test("Castling illegal when king passes through attacked square"):
    // White king e1, rook h1, black rook attacks f1
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("h1") -> Piece(Color.White, PieceType.Rook),
      sq("f8") -> Piece(Color.Black, PieceType.Rook)
    )
    val s = sit(pieces, Color.White, CastlingRights.all)
    val moves = StandardRules.legalMoves(s, sq("e1"))
    assert(!moves.exists(m => m.isInstanceOf[CastlingMove] && m.to == sq("g1")))

  test("Pawn on 7th rank generates 4 promotion moves via push"):
    // White pawn on e7, nothing blocking e8
    val s     = sit(Map(sq("e7") -> Piece(Color.White, PieceType.Pawn)))
    val moves = StandardRules.legalMoves(s, sq("e7"))
    val promos = moves.collect { case NormalMove(_, to, Some(pt)) => pt }
    assertEquals(promos.toSet, Set(PieceType.Queen, PieceType.Rook, PieceType.Bishop, PieceType.Knight))

  test("Pawn diagonal capture on promotion rank generates 4 promotion captures"):
    // White pawn on e7, black rook on f8 to capture
    val pieces = Map(
      sq("e7") -> Piece(Color.White, PieceType.Pawn),
      sq("f8") -> Piece(Color.Black, PieceType.Rook)
    )
    val s     = sit(pieces)
    val moves = StandardRules.legalMoves(s, sq("e7"))
    val capPromos = moves.collect { case NormalMove(_, to, Some(pt)) if to == sq("f8") => pt }
    assertEquals(capPromos.toSet, Set(PieceType.Queen, PieceType.Rook, PieceType.Bishop, PieceType.Knight))

  test("Pawn diagonal capture (non-promotion) is included in legal moves"):
    // White pawn on e4, black pawn on d5
    val pieces = Map(
      sq("e4") -> Piece(Color.White, PieceType.Pawn),
      sq("d5") -> Piece(Color.Black, PieceType.Pawn)
    )
    val s     = sit(pieces)
    val moves = StandardRules.legalMoves(s, sq("e4"))
    assert(moves.exists(m => m.to == sq("d5")))
