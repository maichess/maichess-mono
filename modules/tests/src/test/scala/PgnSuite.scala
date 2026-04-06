package org.maichess.mono.tests

import munit.FunSuite
import org.maichess.mono.model.*
import org.maichess.mono.rules.*
import org.maichess.mono.engine.*

class PgnSuite extends FunSuite:

  val ctrl = GameController(StandardRules)
  val san  = new San()
  val pgn  = new Pgn(san)

  def sq(alg: String): Square =
    Square.fromAlgebraic(alg).getOrElse(throw new AssertionError(s"Invalid square: $alg"))
  def move(from: String, to: String): NormalMove = NormalMove(sq(from), sq(to))
  def after(sit: Situation, m: Move): Situation =
    sit.copy(board = sit.board.applyMove(m), turn = sit.turn.opposite)

  // ── SAN encoding ──────────────────────────────────────────────────────────

  test("SAN encode: pawn advance"):
    val sit = Situation.standard
    val m   = move("e2", "e4")
    assertEquals(san.encode(sit, m, after(sit, m), StandardRules), "e4")

  test("SAN encode: pawn capture"):
    val pieces = Map(
      sq("e4") -> Piece(Color.White, PieceType.Pawn),
      sq("d5") -> Piece(Color.Black, PieceType.Pawn),
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    val m   = move("e4", "d5")
    assertEquals(san.encode(sit, m, after(sit, m), StandardRules), "exd5")

  test("SAN encode: knight move"):
    val sit = Situation.standard
    val m   = move("g1", "f3")
    assertEquals(san.encode(sit, m, after(sit, m), StandardRules), "Nf3")

  test("SAN encode: king move"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    val m   = move("e1", "d2")
    assertEquals(san.encode(sit, m, after(sit, m), StandardRules), "Kd2")

  test("SAN encode: rook move"):
    val pieces = Map(
      sq("a1") -> Piece(Color.White, PieceType.Rook),
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    val m   = move("a1", "a4")
    assertEquals(san.encode(sit, m, after(sit, m), StandardRules), "Ra4")

  test("SAN encode: piece captures"):
    val pieces = Map(
      sq("e4") -> Piece(Color.White, PieceType.Knight),
      sq("d6") -> Piece(Color.Black, PieceType.Pawn),
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    val m   = move("e4", "d6")
    assertEquals(san.encode(sit, m, after(sit, m), StandardRules), "Nxd6+")

  test("SAN encode: king-side castling"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("h1") -> Piece(Color.White, PieceType.Rook),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.all, None, 0, 1)
    val m   = CastlingMove(sq("e1"), sq("g1"), sq("h1"), sq("f1"))
    assertEquals(san.encode(sit, m, after(sit, m), StandardRules), "O-O")

  test("SAN encode: queen-side castling"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("a1") -> Piece(Color.White, PieceType.Rook),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.all, None, 0, 1)
    val m   = CastlingMove(sq("e1"), sq("c1"), sq("a1"), sq("d1"))
    assertEquals(san.encode(sit, m, after(sit, m), StandardRules), "O-O-O")

  test("SAN encode: en passant"):
    val pieces = Map(
      sq("e5") -> Piece(Color.White, PieceType.Pawn),
      sq("d5") -> Piece(Color.Black, PieceType.Pawn),
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, Some(sq("d6")), 0, 1)
    val m   = EnPassantMove(sq("e5"), sq("d6"), sq("d5"))
    assertEquals(san.encode(sit, m, after(sit, m), StandardRules), "exd6")

  test("SAN encode: pawn promotion with all piece types"):
    val pieces = Map(
      sq("e7") -> Piece(Color.White, PieceType.Pawn),
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("h8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    // Queen promotion (wildcard case in promotionLetter)
    val mq = NormalMove(sq("e7"), sq("e8"), Some(PieceType.Queen))
    assert(san.encode(sit, mq, after(sit, mq), StandardRules).startsWith("e8=Q"))
    // Rook promotion
    val mr = NormalMove(sq("e7"), sq("e8"), Some(PieceType.Rook))
    assert(san.encode(sit, mr, after(sit, mr), StandardRules).startsWith("e8=R"))
    // Bishop promotion
    val mb = NormalMove(sq("e7"), sq("e8"), Some(PieceType.Bishop))
    assert(san.encode(sit, mb, after(sit, mb), StandardRules).startsWith("e8=B"))
    // Knight promotion
    val mn = NormalMove(sq("e7"), sq("e8"), Some(PieceType.Knight))
    assert(san.encode(sit, mn, after(sit, mn), StandardRules).startsWith("e8=N"))

  test("SAN encode: check suffix"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King),
      sq("d1") -> Piece(Color.White, PieceType.Queen)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    val m   = move("d1", "d8")
    val notation = san.encode(sit, m, after(sit, m), StandardRules)
    assert(notation.endsWith("+"), s"Expected check suffix, got '$notation'")

  test("SAN encode: checkmate suffix"):
    // Fool's Mate: 1. f3 e5 2. g4 Qh4#
    val moves = List(move("f2", "f3"), move("e7", "e5"), move("g2", "g4"), move("d8", "h4"))
    val states = moves.scanLeft(ctrl.newGame()) { (s, m) =>
      ctrl.applyMove(s, m).getOrElse(fail(s"illegal move $m"))
    }
    val before = states(3).current
    val aft    = states(4).current
    val notation = san.encode(before, moves(3), aft, StandardRules)
    assert(notation.endsWith("#"), s"Expected checkmate suffix, got '$notation'")

  test("SAN encode: no piece at source returns destination"):
    val sit = Situation(Board.empty, Color.White, CastlingRights.none, None, 0, 1)
    val m   = move("e2", "e4")
    assertEquals(san.encode(sit, m, sit, StandardRules), "e4")

  test("SAN encode: file disambiguation for two knights"):
    // Two white knights on b1 and f1 both can reach d2
    val pieces = Map(
      sq("b1") -> Piece(Color.White, PieceType.Knight),
      sq("f1") -> Piece(Color.White, PieceType.Knight),
      sq("e3") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    val m   = move("b1", "d2")
    assertEquals(san.encode(sit, m, after(sit, m), StandardRules), "Nbd2")

  test("SAN encode: rank disambiguation for two rooks"):
    // Two white rooks on a1 and a3 both can reach a2
    val pieces = Map(
      sq("a1") -> Piece(Color.White, PieceType.Rook),
      sq("a3") -> Piece(Color.White, PieceType.Rook),
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    val m   = move("a1", "a2")
    assertEquals(san.encode(sit, m, after(sit, m), StandardRules), "R1a2")

  test("SAN encode: full square disambiguation for three queens"):
    // Three queens: a1, a4, d1. a1 and a4 share file 'a', a1 and d1 share rank '1'.
    val pieces = Map(
      sq("a1") -> Piece(Color.White, PieceType.Queen),
      sq("a4") -> Piece(Color.White, PieceType.Queen),
      sq("d1") -> Piece(Color.White, PieceType.Queen),
      sq("h1") -> Piece(Color.White, PieceType.King),
      sq("h8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    // Queen on a1 goes to d4; queen on a4 and d1 can also reach d4
    val m   = move("a1", "d4")
    assertEquals(san.encode(sit, m, after(sit, m), StandardRules), "Qa1d4+")

  // ── SAN decoding ──────────────────────────────────────────────────────────

  test("SAN decode: pawn advance"):
    san.decode(Situation.standard, "e4", StandardRules) match
      case Right(m) => assertEquals(m, move("e2", "e4"))
      case Left(e)  => fail(e)

  test("SAN decode: knight move"):
    san.decode(Situation.standard, "Nf3", StandardRules) match
      case Right(m) => assertEquals(m, move("g1", "f3"))
      case Left(e)  => fail(e)

  test("SAN decode: king move"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    san.decode(sit, "Kd2", StandardRules) match
      case Right(m) => assertEquals(m.to, sq("d2"))
      case Left(e)  => fail(e)

  test("SAN decode: rook move"):
    val pieces = Map(
      sq("a1") -> Piece(Color.White, PieceType.Rook),
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    san.decode(sit, "Ra4", StandardRules) match
      case Right(m) => assertEquals(m.to, sq("a4"))
      case Left(e)  => fail(e)

  test("SAN decode: king-side castling"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("h1") -> Piece(Color.White, PieceType.Rook),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.all, None, 0, 1)
    san.decode(sit, "O-O", StandardRules) match
      case Right(_: CastlingMove) => ()
      case other                  => fail(s"Expected CastlingMove, got $other")

  test("SAN decode: queen-side castling"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("a1") -> Piece(Color.White, PieceType.Rook),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.all, None, 0, 1)
    san.decode(sit, "O-O-O", StandardRules) match
      case Right(_: CastlingMove) => ()
      case other                  => fail(s"Expected CastlingMove, got $other")

  test("SAN decode: pawn capture"):
    val pieces = Map(
      sq("e4") -> Piece(Color.White, PieceType.Pawn),
      sq("d5") -> Piece(Color.Black, PieceType.Pawn),
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    san.decode(sit, "exd5", StandardRules) match
      case Right(m) => assertEquals(m.to, sq("d5"))
      case Left(e)  => fail(e)

  test("SAN decode: en passant"):
    val pieces = Map(
      sq("e5") -> Piece(Color.White, PieceType.Pawn),
      sq("d5") -> Piece(Color.Black, PieceType.Pawn),
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, Some(sq("d6")), 0, 1)
    san.decode(sit, "exd6", StandardRules) match
      case Right(_: EnPassantMove) => ()
      case other                   => fail(s"Expected EnPassantMove, got $other")

  test("SAN decode: promotion"):
    val pieces = Map(
      sq("e7") -> Piece(Color.White, PieceType.Pawn),
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("a8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    // Queen
    san.decode(sit, "e8=Q", StandardRules) match
      case Right(NormalMove(_, _, Some(PieceType.Queen))) => ()
      case other => fail(s"Expected queen promotion, got $other")
    // Rook
    san.decode(sit, "e8=R", StandardRules) match
      case Right(NormalMove(_, _, Some(PieceType.Rook))) => ()
      case other => fail(s"Expected rook promotion, got $other")
    // Bishop
    san.decode(sit, "e8=B", StandardRules) match
      case Right(NormalMove(_, _, Some(PieceType.Bishop))) => ()
      case other => fail(s"Expected bishop promotion, got $other")
    // Knight
    san.decode(sit, "e8=N", StandardRules) match
      case Right(NormalMove(_, _, Some(PieceType.Knight))) => ()
      case other => fail(s"Expected knight promotion, got $other")
    // Invalid promotion letter falls back to no match
    assert(san.decode(sit, "e8=X", StandardRules).isLeft)

  test("SAN decode: file disambiguation"):
    val pieces = Map(
      sq("b1") -> Piece(Color.White, PieceType.Knight),
      sq("f1") -> Piece(Color.White, PieceType.Knight),
      sq("e3") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    san.decode(sit, "Nbd2", StandardRules) match
      case Right(m) => assertEquals(m.from, sq("b1"))
      case Left(e)  => fail(e)

  test("SAN decode: rank disambiguation"):
    val pieces = Map(
      sq("a1") -> Piece(Color.White, PieceType.Rook),
      sq("a3") -> Piece(Color.White, PieceType.Rook),
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    san.decode(sit, "R1a2", StandardRules) match
      case Right(m) => assertEquals(m.from, sq("a1"))
      case Left(e)  => fail(e)

  test("SAN decode: full square disambiguation"):
    val pieces = Map(
      sq("a1") -> Piece(Color.White, PieceType.Queen),
      sq("a4") -> Piece(Color.White, PieceType.Queen),
      sq("d1") -> Piece(Color.White, PieceType.Queen),
      sq("h1") -> Piece(Color.White, PieceType.King),
      sq("h8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    san.decode(sit, "Qa1d4", StandardRules) match
      case Right(m) => assertEquals(m.from, sq("a1"))
      case Left(e)  => fail(e)

  // ── SAN decode: error paths ───────────────────────────────────────────────

  test("SAN decode: invalid pawn move returns Left"):
    assert(san.decode(Situation.standard, "e5", StandardRules).isLeft)

  test("SAN decode: unknown piece letter returns Left"):
    assert(san.decode(Situation.standard, "Xe4", StandardRules).isLeft)

  test("SAN decode: invalid piece destination returns Left"):
    assert(san.decode(Situation.standard, "Nz9", StandardRules).isLeft)

  test("SAN decode: no legal piece move returns Left"):
    // No knight can reach a6 from standard position
    assert(san.decode(Situation.standard, "Na6", StandardRules).isLeft)

  test("SAN decode: ambiguous piece move returns Left"):
    val pieces = Map(
      sq("b1") -> Piece(Color.White, PieceType.Knight),
      sq("f1") -> Piece(Color.White, PieceType.Knight),
      sq("e3") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    // Both knights can reach d2 — no disambiguation given
    assert(san.decode(sit, "Nd2", StandardRules).isLeft)

  test("SAN decode: invalid pawn destination returns Left"):
    assert(san.decode(Situation.standard, "z9", StandardRules).isLeft)

  test("SAN decode: king-side castling not available returns Left"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    assert(san.decode(sit, "O-O", StandardRules).isLeft)

  test("SAN decode: queen-side castling not available returns Left"):
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    assert(san.decode(sit, "O-O-O", StandardRules).isLeft)

  test("SAN decode: pawn push does not match capture"):
    // Pawn on e3 can push to e4, pawn on d3 can capture on e4
    val pieces = Map(
      sq("e3") -> Piece(Color.White, PieceType.Pawn),
      sq("d3") -> Piece(Color.White, PieceType.Pawn),
      sq("e4") -> Piece(Color.Black, PieceType.Pawn),
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    // "dxe4" should match only the d3 pawn capturing
    san.decode(sit, "dxe4", StandardRules) match
      case Right(m) => assertEquals(m.from, sq("d3"))
      case Left(e)  => fail(e)

  test("SAN decode: pawn move with castling available exercises non-pawn filter"):
    // Position where castling is possible and a pawn can move
    val pieces = Map(
      sq("e2") -> Piece(Color.White, PieceType.Pawn),
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("h1") -> Piece(Color.White, PieceType.Rook),
      sq("a1") -> Piece(Color.White, PieceType.Rook),
      sq("a8") -> Piece(Color.Black, PieceType.King)
    )
    val sit = Situation(Board(pieces), Color.White, CastlingRights.all, None, 0, 1)
    san.decode(sit, "e4", StandardRules) match
      case Right(m) => assertEquals(m.to, sq("e4"))
      case Left(e)  => fail(e)

  // ── PGN encode ────────────────────────────────────────────────────────────

  test("PGN encode: ongoing game has result *"):
    val state   = ctrl.newGame()
    val encoded = pgn.encode(state, StandardRules, PgnMetadata.default)
    assert(encoded.contains("[Result \"*\"]"))

  test("PGN encode: contains move numbers"):
    val s1      = ctrl.applyMove(ctrl.newGame(), move("e2", "e4")).getOrElse(fail("move 1"))
    val s2      = ctrl.applyMove(s1, move("e7", "e5")).getOrElse(fail("move 2"))
    val encoded = pgn.encode(s2, StandardRules, PgnMetadata.default)
    assert(encoded.contains("1."))

  test("PGN encode: white checkmate has result 1-0"):
    // Scholar's Mate
    val moves = List(move("e2","e4"), move("e7","e5"), move("d1","h5"),
                     move("b8","c6"), move("f1","c4"), move("g8","f6"), move("h5","f7"))
    val state = moves.foldLeft(ctrl.newGame()) { (s, m) =>
      ctrl.applyMove(s, m).getOrElse(fail(s"illegal move $m"))
    }
    val encoded = pgn.encode(state, StandardRules, PgnMetadata.default)
    assert(encoded.contains("[Result \"1-0\"]"))

  test("PGN encode: black checkmate has result 0-1"):
    // Fool's Mate: 1. f3 e5 2. g4 Qh4#
    val moves = List(move("f2","f3"), move("e7","e5"), move("g2","g4"), move("d8","h4"))
    val state = moves.foldLeft(ctrl.newGame()) { (s, m) =>
      ctrl.applyMove(s, m).getOrElse(fail(s"illegal move $m"))
    }
    val encoded = pgn.encode(state, StandardRules, PgnMetadata.default)
    assert(encoded.contains("[Result \"0-1\"]"))

  test("PGN encode: draw has result 1/2-1/2"):
    // K vs K — insufficient material
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King)
    )
    val state = GameState(Nil, Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1))
    val encoded = pgn.encode(state, StandardRules, PgnMetadata.default)
    assert(encoded.contains("[Result \"1/2-1/2\"]"))

  // ── PGN decode ────────────────────────────────────────────────────────────

  test("PGN decode: Scholar's Mate"):
    val pgnStr = "1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# 1-0"
    pgn.decode(pgnStr, StandardRules) match
      case Right((state, _)) =>
        assertEquals(ctrl.gameResult(state), Some(GameResult.Checkmate(Color.White)))
      case Left(err) => fail(err)

  test("PGN decode then encode round-trip preserves final board"):
    val pgnStr = "1. e4 e5 2. Nf3 Nc6 3. Bb5 *"
    pgn.decode(pgnStr, StandardRules) match
      case Right((state, _)) =>
        val encoded = pgn.encode(state, StandardRules, PgnMetadata.default)
        pgn.decode(encoded, StandardRules) match
          case Right((state2, _)) => assertEquals(state2.current.board, state.current.board)
          case Left(err)          => fail(s"Re-decode failed: $err")
      case Left(err) => fail(err)

  test("PGN decode: move count is correct"):
    pgn.decode("1. e4 e5 2. d4 d5 *", StandardRules) match
      case Right((state, _)) => assertEquals(state.history.length, 4)
      case Left(err)         => fail(err)

  test("PGN decode: ignores comments"):
    pgn.decode("1. e4 {A good move} e5 *", StandardRules) match
      case Right((state, _)) => assertEquals(state.history.length, 2)
      case Left(err)         => fail(err)

  test("PGN decode: invalid move returns Left"):
    assert(pgn.decode("1. e5 *", StandardRules).isLeft)

  test("PGN decode: first invalid move short-circuits remaining tokens"):
    val result = pgn.decode("1. e5 e5 2. d4 *", StandardRules)
    assert(result.isLeft)

  test("PGN decode: strips headers before parsing"):
    val pgnStr =
      """[Event "Test"]
        |[White "A"]
        |[Black "B"]
        |
        |1. e4 e5 *""".stripMargin
    pgn.decode(pgnStr, StandardRules) match
      case Right((state, _)) => assertEquals(state.history.length, 2)
      case Left(err)         => fail(err)

  test("PGN decode: move numbers without spaces (e.g. 1.e4 c5)"):
    val pgnStr =
      """[Event "ch-UZB 1st League 2014"]
        |[White "Abdusattorov,Nodirbek"]
        |[Black "Alikulov,Elbek"]
        |[Result "1-0"]
        |
        |1.e4 c5 2.Nf3 a6 3.d3 g6 4.g3 Bg7 5.Bg2 b5 6.O-O Bb7 7.c3 e5 8.a3 Ne7 9.b4 d6
        |10.Nbd2 O-O 11.Nb3 Nd7 12.Be3 Rc8 13.Rc1 h6 14.Nfd2 f5 15.f4 Kh7 16.Qe2 cxb4
        |17.axb4 exf4 18.Bxf4 Rxc3 19.Rxc3 Bxc3 20.Bxd6 Qb6+ 21.Bc5 Nxc5 22.bxc5 Qe6
        |23.d4 Rd8 24.Qd3 Bxd2 25.Nxd2 fxe4 26.Nxe4 Nf5 27.d5 Qe5 28.g4 Ne7 29.Rf7+ Kg8
        |30.Qf1 Nxd5 31.Rxb7 Qd4+ 32.Kh1 Rf8 33.Qg1 Ne3 34.Re7 a5 35.c6 a4 36.Qxe3 Qxe3
        |37.Nf6+ Rxf6 38.Rxe3 Rd6 39.h4 Rd1+ 40.Kh2 b4 41.c7 1-0""".stripMargin
    pgn.decode(pgnStr, StandardRules) match
      case Right((state, _)) => assertEquals(state.history.length, 81)
      case Left(err)         => fail(err)

  // ── PGN metadata ─────────────────────────────────────────────────────────

  test("PGN decode: header tags are preserved in metadata"):
    val pgnStr =
      """[Event "Test Tournament"]
        |[Site "localhost"]
        |[Date "2024.01.01"]
        |[Round "3"]
        |[White "Alice"]
        |[Black "Bob"]
        |
        |1. e4 *""".stripMargin
    pgn.decode(pgnStr, StandardRules) match
      case Right((_, meta)) =>
        assertEquals(meta.event, "Test Tournament")
        assertEquals(meta.site,  "localhost")
        assertEquals(meta.date,  "2024.01.01")
        assertEquals(meta.round, "3")
        assertEquals(meta.white, "Alice")
        assertEquals(meta.black, "Bob")
      case Left(err) => fail(err)

  test("PGN encode: uses provided player names in header"):
    val state   = ctrl.newGame()
    val meta    = PgnMetadata.fromNames("Alice", "Bob")
    val encoded = pgn.encode(state, StandardRules, meta)
    assert(encoded.contains("[White \"Alice\"]"))
    assert(encoded.contains("[Black \"Bob\"]"))
    assert(encoded.contains("[Event \"Alice vs Bob\"]"))

  test("PgnMetadata.fromNames sets white and black names"):
    val meta = PgnMetadata.fromNames("Alice", "Bob")
    assertEquals(meta.white, "Alice")
    assertEquals(meta.black, "Bob")
    assertEquals(meta.event, "Alice vs Bob")
    assertEquals(meta.round, "1")

  test("PgnMetadata.default has placeholder values"):
    assertEquals(PgnMetadata.default.white, "White")
    assertEquals(PgnMetadata.default.black, "Black")
    assertEquals(PgnMetadata.default.event, "?")
