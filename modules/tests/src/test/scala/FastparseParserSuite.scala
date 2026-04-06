package org.maichess.mono.tests

import munit.FunSuite
import org.maichess.mono.model.*
import org.maichess.mono.rules.*
import org.maichess.mono.engine.*

/** Exercises all fastparse codec paths, mirroring the scenarios in FenSuite/PgnSuite. */
class FastparseParserSuite extends FunSuite:

  val ctrl     = GameController(StandardRules)
  val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
  val fpFen    = new FastparseFen()
  val fpSan    = new FastparseSan()
  val fpPgn    = new FastparsePgn(fpSan)

  def sq(alg: String): Square =
    Square.fromAlgebraic(alg).getOrElse(throw AssertionError(s"Invalid square: $alg"))
  def move(from: String, to: String): NormalMove = NormalMove(sq(from), sq(to))
  def afterMove(sit: Situation, m: Move): Situation =
    sit.copy(board = sit.board.applyMove(m), turn = sit.turn.opposite)

  // ── FastparseFen: encode ───────────────────────────────────────────────────

  test("fpFen.encode delegates to Fen"):
    assertEquals(fpFen.encode(Situation.standard), startFen)

  // ── FastparseFen: decode valid ─────────────────────────────────────────────

  test("FastparseFen decode: starting FEN"):
    val result = fpFen.decode(startFen)
    assert(result.isRight)
    result.foreach { sit =>
      assertEquals(sit.board,           Situation.standard.board)
      assertEquals(sit.turn,            Color.White)
      assertEquals(sit.castlingRights,  CastlingRights.all)
      assertEquals(sit.enPassantSquare, None)
      assertEquals(sit.halfMoveClock,   0)
      assertEquals(sit.fullMoveNumber,  1)
    }

  test("FastparseFen decode: black to move"):
    fpFen.decode("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
      .foreach(sit => assertEquals(sit.turn, Color.Black))

  test("FastparseFen decode: en passant square"):
    fpFen.decode("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
      .foreach(sit => assertEquals(sit.enPassantSquare, Some(sq("e3"))))

  test("FastparseFen decode: no castling rights"):
    fpFen.decode("8/8/4k3/8/8/4K3/8/8 w - - 0 1")
      .foreach(sit => assertEquals(sit.castlingRights, CastlingRights.none))

  test("FastparseFen decode: partial castling rights Kq"):
    fpFen.decode("r3k2r/8/8/8/8/8/8/R3K2R w Kq - 0 1").foreach { sit =>
      assertEquals(sit.castlingRights.whiteKingSide,  true)
      assertEquals(sit.castlingRights.whiteQueenSide, false)
      assertEquals(sit.castlingRights.blackKingSide,  false)
      assertEquals(sit.castlingRights.blackQueenSide, true)
    }

  test("FastparseFen decode: halfMoveClock"):
    fpFen.decode("8/8/8/8/8/8/8/8 w - - 17 1")
      .foreach(sit => assertEquals(sit.halfMoveClock, 17))

  test("FastparseFen decode: fullMoveNumber"):
    fpFen.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 42")
      .foreach(sit => assertEquals(sit.fullMoveNumber, 42))

  test("FastparseFen decode: round-trip preserves situation"):
    val encoded = fpFen.encode(Situation.standard)
    val decoded = fpFen.decode(encoded)
    assert(decoded.isRight)
    decoded.foreach(sit => assertEquals(sit, Situation.standard))

  test("FastparseFen decode: all piece types"):
    // every piece char (KQRBNPkqrbnp) must parse without error
    assert(fpFen.decode(startFen).isRight)

  test("FastparseFen decode: empty board"):
    assert(fpFen.decode("8/8/8/8/8/8/8/8 w - - 0 1").isRight)

  // ── FastparseFen: decode invalid ──────────────────────────────────────────

  test("FastparseFen decode: too few fields"):
    assert(fpFen.decode("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w").isLeft)

  test("FastparseFen decode: invalid turn char"):
    assert(fpFen.decode("8/8/8/8/8/8/8/8 x - - 0 1").isLeft)

  test("FastparseFen decode: invalid castling chars"):
    assert(fpFen.decode("8/8/8/8/8/8/8/8 w XY - 0 1").isLeft)

  test("FastparseFen decode: invalid en passant"):
    assert(fpFen.decode("8/8/8/8/8/8/8/8 w - z9 0 1").isLeft)

  test("FastparseFen decode: too few ranks"):
    assert(fpFen.decode("8/8/8 w - - 0 1").isLeft)

  test("FastparseFen decode: unknown piece char"):
    assert(fpFen.decode("xnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1").isLeft)

  test("FastparseFen decode: too many pieces in a rank"):
    assert(fpFen.decode("rnbqkbnrr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1").isLeft)

  // ── FastparseSan: encode ───────────────────────────────────────────────────

  test("fpSan.encode delegates to San"):
    val sit         = Situation.standard
    val m           = move("e2", "e4")
    val handwritten = new San()
    assertEquals(
      fpSan.encode(sit, m, afterMove(sit, m), StandardRules),
      handwritten.encode(sit, m, afterMove(sit, m), StandardRules)
    )

  // ── FastparseSan: decode ───────────────────────────────────────────────────

  test("FastparseSan decode: pawn push"):
    fpSan.decode(Situation.standard, "e4", StandardRules) match
      case Right(m) => assertEquals(m, move("e2", "e4"))
      case Left(e)  => fail(e)

  test("FastparseSan decode: pawn push with check suffix"):
    fpSan.decode(Situation.standard, "e4+", StandardRules) match
      case Right(m) => assertEquals(m, move("e2", "e4"))
      case Left(e)  => fail(e)

  test("FastparseSan decode: knight move"):
    fpSan.decode(Situation.standard, "Nf3", StandardRules) match
      case Right(m) => assertEquals(m, move("g1", "f3"))
      case Left(e)  => fail(e)

  test("FastparseSan decode: king move"):
    val sit = Situation(
      Board(Map(sq("e1") -> Piece(Color.White, PieceType.King), sq("e8") -> Piece(Color.Black, PieceType.King))),
      Color.White, CastlingRights.none, None, 0, 1
    )
    fpSan.decode(sit, "Kd2", StandardRules) match
      case Right(m) => assertEquals(m.to, sq("d2"))
      case Left(e)  => fail(e)

  test("FastparseSan decode: rook move"):
    val sit = Situation(
      Board(Map(sq("a1") -> Piece(Color.White, PieceType.Rook),
                sq("e1") -> Piece(Color.White, PieceType.King),
                sq("e8") -> Piece(Color.Black, PieceType.King))),
      Color.White, CastlingRights.none, None, 0, 1
    )
    fpSan.decode(sit, "Ra4", StandardRules) match
      case Right(m) => assertEquals(m.to, sq("a4"))
      case Left(e)  => fail(e)

  test("FastparseSan decode: king-side castling"):
    val sit = Situation(
      Board(Map(sq("e1") -> Piece(Color.White, PieceType.King),
                sq("h1") -> Piece(Color.White, PieceType.Rook),
                sq("e8") -> Piece(Color.Black, PieceType.King))),
      Color.White, CastlingRights.all, None, 0, 1
    )
    fpSan.decode(sit, "O-O", StandardRules) match
      case Right(_: CastlingMove) => ()
      case other                  => fail(s"Expected CastlingMove, got $other")

  test("FastparseSan decode: queen-side castling"):
    val sit = Situation(
      Board(Map(sq("e1") -> Piece(Color.White, PieceType.King),
                sq("a1") -> Piece(Color.White, PieceType.Rook),
                sq("e8") -> Piece(Color.Black, PieceType.King))),
      Color.White, CastlingRights.all, None, 0, 1
    )
    fpSan.decode(sit, "O-O-O", StandardRules) match
      case Right(_: CastlingMove) => ()
      case other                  => fail(s"Expected CastlingMove, got $other")

  test("FastparseSan decode: pawn capture"):
    val sit = Situation(
      Board(Map(sq("e4") -> Piece(Color.White, PieceType.Pawn),
                sq("d5") -> Piece(Color.Black, PieceType.Pawn),
                sq("e1") -> Piece(Color.White, PieceType.King),
                sq("e8") -> Piece(Color.Black, PieceType.King))),
      Color.White, CastlingRights.none, None, 0, 1
    )
    fpSan.decode(sit, "exd5", StandardRules) match
      case Right(m) => assertEquals(m.to, sq("d5"))
      case Left(e)  => fail(e)

  test("FastparseSan decode: en passant"):
    val sit = Situation(
      Board(Map(sq("e5") -> Piece(Color.White, PieceType.Pawn),
                sq("d5") -> Piece(Color.Black, PieceType.Pawn),
                sq("e1") -> Piece(Color.White, PieceType.King),
                sq("e8") -> Piece(Color.Black, PieceType.King))),
      Color.White, CastlingRights.none, Some(sq("d6")), 0, 1
    )
    fpSan.decode(sit, "exd6", StandardRules) match
      case Right(_: EnPassantMove) => ()
      case other                   => fail(s"Expected EnPassantMove, got $other")

  test("FastparseSan decode: pawn promotions"):
    val sit = Situation(
      Board(Map(sq("e7") -> Piece(Color.White, PieceType.Pawn),
                sq("e1") -> Piece(Color.White, PieceType.King),
                sq("a8") -> Piece(Color.Black, PieceType.King))),
      Color.White, CastlingRights.none, None, 0, 1
    )
    for (san, pt) <- List("e8=Q" -> PieceType.Queen, "e8=R" -> PieceType.Rook,
                          "e8=B" -> PieceType.Bishop, "e8=N" -> PieceType.Knight) do
      fpSan.decode(sit, san, StandardRules) match
        case Right(NormalMove(_, _, Some(p))) => assertEquals(p, pt)
        case other                             => fail(s"Expected promotion to $pt, got $other")

  test("FastparseSan decode: file disambiguation"):
    val sit = Situation(
      Board(Map(sq("b1") -> Piece(Color.White, PieceType.Knight),
                sq("f1") -> Piece(Color.White, PieceType.Knight),
                sq("e3") -> Piece(Color.White, PieceType.King),
                sq("e8") -> Piece(Color.Black, PieceType.King))),
      Color.White, CastlingRights.none, None, 0, 1
    )
    fpSan.decode(sit, "Nbd2", StandardRules) match
      case Right(m) => assertEquals(m.from, sq("b1"))
      case Left(e)  => fail(e)

  test("FastparseSan decode: rank disambiguation"):
    val sit = Situation(
      Board(Map(sq("a1") -> Piece(Color.White, PieceType.Rook),
                sq("a3") -> Piece(Color.White, PieceType.Rook),
                sq("e1") -> Piece(Color.White, PieceType.King),
                sq("e8") -> Piece(Color.Black, PieceType.King))),
      Color.White, CastlingRights.none, None, 0, 1
    )
    fpSan.decode(sit, "R1a2", StandardRules) match
      case Right(m) => assertEquals(m.from, sq("a1"))
      case Left(e)  => fail(e)

  test("FastparseSan decode: full-square disambiguation"):
    val sit = Situation(
      Board(Map(sq("a1") -> Piece(Color.White, PieceType.Queen),
                sq("a4") -> Piece(Color.White, PieceType.Queen),
                sq("d1") -> Piece(Color.White, PieceType.Queen),
                sq("h1") -> Piece(Color.White, PieceType.King),
                sq("h8") -> Piece(Color.Black, PieceType.King))),
      Color.White, CastlingRights.none, None, 0, 1
    )
    fpSan.decode(sit, "Qa1d4", StandardRules) match
      case Right(m) => assertEquals(m.from, sq("a1"))
      case Left(e)  => fail(e)

  test("FastparseSan decode: piece capture"):
    val sit = Situation(
      Board(Map(sq("e4") -> Piece(Color.White, PieceType.Knight),
                sq("d6") -> Piece(Color.Black, PieceType.Pawn),
                sq("e1") -> Piece(Color.White, PieceType.King),
                sq("e8") -> Piece(Color.Black, PieceType.King))),
      Color.White, CastlingRights.none, None, 0, 1
    )
    fpSan.decode(sit, "Nxd6", StandardRules) match
      case Right(m) => assertEquals(m.to, sq("d6"))
      case Left(e)  => fail(e)

  // ── FastparseSan: error paths ──────────────────────────────────────────────

  test("FastparseSan decode: invalid SAN returns Left"):
    assert(fpSan.decode(Situation.standard, "!!!", StandardRules).isLeft)

  test("FastparseSan decode: illegal pawn move returns Left"):
    assert(fpSan.decode(Situation.standard, "e5", StandardRules).isLeft)

  test("FastparseSan decode: ambiguous piece move returns Left"):
    val sit = Situation(
      Board(Map(sq("b1") -> Piece(Color.White, PieceType.Knight),
                sq("f1") -> Piece(Color.White, PieceType.Knight),
                sq("e3") -> Piece(Color.White, PieceType.King),
                sq("e8") -> Piece(Color.Black, PieceType.King))),
      Color.White, CastlingRights.none, None, 0, 1
    )
    assert(fpSan.decode(sit, "Nd2", StandardRules).isLeft)

  test("FastparseSan decode: no legal piece move returns Left"):
    assert(fpSan.decode(Situation.standard, "Na6", StandardRules).isLeft)

  test("FastparseSan decode: castling unavailable returns Left"):
    val sit = Situation(
      Board(Map(sq("e1") -> Piece(Color.White, PieceType.King),
                sq("e8") -> Piece(Color.Black, PieceType.King))),
      Color.White, CastlingRights.none, None, 0, 1
    )
    assert(fpSan.decode(sit, "O-O", StandardRules).isLeft)
    assert(fpSan.decode(sit, "O-O-O", StandardRules).isLeft)

  test("FastparseSan decode: invalid promotion letter returns Left"):
    val sit = Situation(
      Board(Map(sq("e7") -> Piece(Color.White, PieceType.Pawn),
                sq("e1") -> Piece(Color.White, PieceType.King),
                sq("a8") -> Piece(Color.Black, PieceType.King))),
      Color.White, CastlingRights.none, None, 0, 1
    )
    assert(fpSan.decode(sit, "e8=X", StandardRules).isLeft)

  // ── FastparsePgn: encode ───────────────────────────────────────────────────

  test("fpPgn.encode delegates to Pgn"):
    val state          = ctrl.newGame()
    val handwrittenPgn = new Pgn(new San())
    assertEquals(
      fpPgn.encode(state, StandardRules, PgnMetadata.default),
      handwrittenPgn.encode(state, StandardRules, PgnMetadata.default)
    )

  // ── FastparsePgn: decode ───────────────────────────────────────────────────

  test("FastparsePgn decode: Scholar's Mate"):
    val pgn = "1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# 1-0"
    fpPgn.decode(pgn, StandardRules) match
      case Right((state, _)) =>
        assertEquals(ctrl.gameResult(state), Some(GameResult.Checkmate(Color.White)))
      case Left(err) => fail(err)

  test("FastparsePgn decode: move count"):
    fpPgn.decode("1. e4 e5 2. d4 d5 *", StandardRules) match
      case Right((state, _)) => assertEquals(state.history.length, 4)
      case Left(err)         => fail(err)

  test("FastparsePgn decode: ignores comments"):
    fpPgn.decode("1. e4 {A good move} e5 *", StandardRules) match
      case Right((state, _)) => assertEquals(state.history.length, 2)
      case Left(err)         => fail(err)

  test("FastparsePgn decode: ignores NAGs"):
    fpPgn.decode("1. e4 $1 e5 $2 *", StandardRules) match
      case Right((state, _)) => assertEquals(state.history.length, 2)
      case Left(err)         => fail(err)

  test("FastparsePgn decode: ignores annotations (!?)"):
    fpPgn.decode("1. e4! e5? *", StandardRules) match
      case Right((state, _)) => assertEquals(state.history.length, 2)
      case Left(err)         => fail(err)

  test("FastparsePgn decode: with headers"):
    val pgn =
      """[Event "Test"]
        |[White "Alice"]
        |[Black "Bob"]
        |
        |1. e4 e5 *""".stripMargin
    fpPgn.decode(pgn, StandardRules) match
      case Right((state, meta)) =>
        assertEquals(state.history.length, 2)
        assertEquals(meta.event, "Test")
        assertEquals(meta.white, "Alice")
        assertEquals(meta.black, "Bob")
      case Left(err) => fail(err)

  test("FastparsePgn decode: all header tags preserved"):
    val pgn =
      """[Event "Test Tournament"]
        |[Site "localhost"]
        |[Date "2024.01.01"]
        |[Round "3"]
        |[White "Alice"]
        |[Black "Bob"]
        |
        |1. e4 *""".stripMargin
    fpPgn.decode(pgn, StandardRules) match
      case Right((_, meta)) =>
        assertEquals(meta.event, "Test Tournament")
        assertEquals(meta.site,  "localhost")
        assertEquals(meta.date,  "2024.01.01")
        assertEquals(meta.round, "3")
        assertEquals(meta.white, "Alice")
        assertEquals(meta.black, "Bob")
      case Left(err) => fail(err)

  test("FastparsePgn decode: move numbers without spaces"):
    fpPgn.decode("1.e4 e5 2.Nf3 Nc6 *", StandardRules) match
      case Right((state, _)) => assertEquals(state.history.length, 4)
      case Left(err)         => fail(err)

  test("FastparsePgn decode: round-trip preserves board"):
    val pgn = "1. e4 e5 2. Nf3 Nc6 3. Bb5 *"
    fpPgn.decode(pgn, StandardRules) match
      case Right((state, _)) =>
        val encoded = fpPgn.encode(state, StandardRules, PgnMetadata.default)
        fpPgn.decode(encoded, StandardRules) match
          case Right((state2, _)) => assertEquals(state2.current.board, state.current.board)
          case Left(err)          => fail(s"Re-decode failed: $err")
      case Left(err) => fail(err)

  test("FastparsePgn decode: missing headers uses defaults"):
    fpPgn.decode("1. e4 *", StandardRules) match
      case Right((_, meta)) =>
        assertEquals(meta.event, "?")
        assertEquals(meta.white, "White")
        assertEquals(meta.black, "Black")
      case Left(err) => fail(err)

  test("FastparsePgn decode: invalid move returns Left"):
    assert(fpPgn.decode("1. e5 *", StandardRules).isLeft)

  test("FastparsePgn decode: first error short-circuits"):
    assert(fpPgn.decode("1. e5 e5 2. d4 *", StandardRules).isLeft)
