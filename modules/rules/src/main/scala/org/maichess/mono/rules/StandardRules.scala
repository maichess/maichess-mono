package org.maichess.mono.rules

import org.maichess.mono.model.*

object StandardRules extends RuleSet:

  // ── Directions ────────────────────────────────────────────────────────────
  private val rookDirs:   List[(Int, Int)] = List((1,0),(-1,0),(0,1),(0,-1))
  private val bishopDirs: List[(Int, Int)] = List((1,1),(1,-1),(-1,1),(-1,-1))
  private val queenDirs:  List[(Int, Int)] = rookDirs ++ bishopDirs
  private val knightJumps: List[(Int, Int)] =
    List((2,1),(2,-1),(-2,1),(-2,-1),(1,2),(1,-2),(-1,2),(-1,-2))

  // ── Public API ────────────────────────────────────────────────────────────
  def candidateMoves(sit: Situation, sq: Square): List[Move] =
    sit.board.pieceAt(sq).fold(List.empty[Move]) { piece =>
      if piece.color != sit.turn then List.empty[Move]
      else piece.pieceType match
        case PieceType.Pawn   => pawnCandidates(sit, sq, piece.color)
        case PieceType.Knight => knightCandidates(sit, sq, piece.color)
        case PieceType.Bishop => slidingMoves(sit, sq, piece.color, bishopDirs)
        case PieceType.Rook   => slidingMoves(sit, sq, piece.color, rookDirs)
        case PieceType.Queen  => slidingMoves(sit, sq, piece.color, queenDirs)
        case PieceType.King   => kingCandidates(sit, sq, piece.color)
    }

  def legalMoves(sit: Situation, sq: Square): List[Move] =
    candidateMoves(sit, sq).filter { move =>
      !leavesKingInCheck(sit, move) && !castlesThroughCheck(sit, move)
    }

  def allLegalMoves(sit: Situation): List[Move] =
    Square.all.toList.flatMap(sq => legalMoves(sit, sq))

  def isCheck(sit: Situation): Boolean =
    kingSquare(sit.board, sit.turn)
      .fold(false)(sq => isAttackedBy(sit.board, sq, sit.turn.opposite))

  def isCheckmate(sit: Situation): Boolean =
    isCheck(sit) && allLegalMoves(sit).isEmpty

  def isStalemate(sit: Situation): Boolean =
    !isCheck(sit) && allLegalMoves(sit).isEmpty

  def isInsufficientMaterial(sit: Situation): Boolean =
    insufficientMaterial(sit.board)

  def isFiftyMoveRule(sit: Situation): Boolean =
    sit.halfMoveClock >= 100

  // ── Sliding pieces (Bishop, Rook, Queen) ─────────────────────────────────
  private def slidingMoves(sit: Situation, from: Square, color: Color, dirs: List[(Int, Int)]): List[Move] =
    dirs.flatMap(dir => castRay(sit.board, from, color, dir))

  private def castRay(board: Board, from: Square, color: Color, dir: (Int, Int)): List[Move] =
    def loop(sq: Square, acc: List[Move]): List[Move] =
      sq.offset(dir._1, dir._2) match
        case None       => acc
        case Some(next) =>
          board.pieceAt(next) match
            case None                        => loop(next, NormalMove(from, next) :: acc)
            case Some(p) if p.color != color => NormalMove(from, next) :: acc
            case Some(_)                     => acc
    loop(from, Nil).reverse

  // ── Knight ────────────────────────────────────────────────────────────────
  private def knightCandidates(sit: Situation, from: Square, color: Color): List[Move] =
    knightJumps.flatMap { (df, dr) =>
      from.offset(df, dr).flatMap { to =>
        sit.board.pieceAt(to) match
          case Some(p) if p.color == color => None
          case _                           => Some(NormalMove(from, to))
      }
    }

  // ── King ──────────────────────────────────────────────────────────────────
  private def kingCandidates(sit: Situation, from: Square, color: Color): List[Move] =
    val steps = queenDirs.flatMap { (df, dr) =>
      from.offset(df, dr).flatMap { to =>
        sit.board.pieceAt(to) match
          case Some(p) if p.color == color => None
          case _                           => Some(NormalMove(from, to))
      }
    }
    steps ++ castlingCandidates(sit, from, color)

  // ── Pawn ──────────────────────────────────────────────────────────────────
  private def pawnCandidates(sit: Situation, from: Square, color: Color): List[Move] =
    val fwd       = if color == Color.White then 1 else -1
    val startRank = if color == Color.White then 1 else 6
    val promoRank = if color == Color.White then 7 else 0

    val single = from.offset(0, fwd).filter(to => sit.board.pieceAt(to).isEmpty)

    val double = Option.when(from.rank.toInt == startRank) {
      from.offset(0, fwd).flatMap { mid =>
        Option.when(sit.board.pieceAt(mid).isEmpty) {
          from.offset(0, fwd * 2).filter(to => sit.board.pieceAt(to).isEmpty)
        }.flatten
      }
    }.flatten

    val diagonalCaptures = List(-1, 1).flatMap { df =>
      from.offset(df, fwd).flatMap { to =>
        sit.board.pieceAt(to).filter(_.color != color).map(_ => to)
      }
    }

    val epCaptures: List[EnPassantMove] = sit.enPassantSquare.toList.flatMap { epSq =>
      List(-1, 1).flatMap { df =>
        from.offset(df, fwd).filter(_ == epSq).map { to =>
          EnPassantMove(from, to, Square(to.file, from.rank))
        }
      }
    }

    def toMoves(dest: Square): List[NormalMove] =
      if dest.rank.toInt == promoRank then
        List(PieceType.Queen, PieceType.Rook, PieceType.Bishop, PieceType.Knight)
          .map(pt => NormalMove(from, dest, Some(pt)))
      else List(NormalMove(from, dest))

    val stepSquares  = single.toList ++ double.toList
    val stepMoves    = stepSquares.flatMap(toMoves)
    val captureMoves = diagonalCaptures.flatMap(toMoves)
    stepMoves ++ captureMoves ++ epCaptures

  // ── Castling ──────────────────────────────────────────────────────────────
  private def castlingCandidates(sit: Situation, from: Square, color: Color): List[CastlingMove] =
    color match
      case Color.White => whiteCastles(sit, from)
      case Color.Black => blackCastles(sit, from)

  private def squaresEmpty(board: Board, squares: List[String]): Boolean =
    squares.forall(alg => Square.fromAlgebraic(alg).fold(false)(sq => board.pieceAt(sq).isEmpty))

  private def castleMoves(
    sit: Situation,
    from: Square,
    kingSideRight: Boolean,
    queenSideRight: Boolean,
    kingSquareAlg: String,
    kingSideSquares: List[String],
    queenSideSquares: List[String],
    kingSideCoords: (String, String, String, String),
    queenSideCoords: (String, String, String, String)
  ): List[CastlingMove] =
    val expected = Square.fromAlgebraic(kingSquareAlg).getOrElse(from)

    def makeCastle(rights: Boolean, clearSquares: List[String], coords: (String, String, String, String)): Option[CastlingMove] =
      for
        _ <- Option.when(rights && from == expected && squaresEmpty(sit.board, clearSquares))(())
        kf <- Square.fromAlgebraic(coords._1)
        kt <- Square.fromAlgebraic(coords._2)
        rf <- Square.fromAlgebraic(coords._3)
        rt <- Square.fromAlgebraic(coords._4)
      yield CastlingMove(kf, kt, rf, rt)

    List(
      makeCastle(kingSideRight,  kingSideSquares,  kingSideCoords),
      makeCastle(queenSideRight, queenSideSquares, queenSideCoords)
    ).flatten

  private def whiteCastles(sit: Situation, from: Square): List[CastlingMove] =
    castleMoves(sit, from,
      sit.castlingRights.whiteKingSide, sit.castlingRights.whiteQueenSide,
      "e1",
      List("f1", "g1"), List("b1", "c1", "d1"),
      ("e1", "g1", "h1", "f1"), ("e1", "c1", "a1", "d1")
    )

  private def blackCastles(sit: Situation, from: Square): List[CastlingMove] =
    castleMoves(sit, from,
      sit.castlingRights.blackKingSide, sit.castlingRights.blackQueenSide,
      "e8",
      List("f8", "g8"), List("b8", "c8", "d8"),
      ("e8", "g8", "h8", "f8"), ("e8", "c8", "a8", "d8")
    )

  // ── Check detection ───────────────────────────────────────────────────────
  private def kingSquare(board: Board, color: Color): Option[Square] =
    Square.all.find(sq => board.pieceAt(sq).contains(Piece(color, PieceType.King)))

  private def isAttackedBy(board: Board, target: Square, attacker: Color): Boolean =
    Square.all.exists { sq =>
      board.pieceAt(sq).fold(false) { p =>
        p.color == attacker && squareAttacks(board, sq, p, target)
      }
    }

  private def squareAttacks(board: Board, from: Square, piece: Piece, target: Square): Boolean =
    val fwd = if piece.color == Color.White then 1 else -1
    piece.pieceType match
      case PieceType.Pawn   =>
        from.offset(-1, fwd).contains(target) || from.offset(1, fwd).contains(target)
      case PieceType.Knight =>
        knightJumps.exists { (df, dr) => from.offset(df, dr).contains(target) }
      case PieceType.Bishop => rayReaches(board, from, bishopDirs, target)
      case PieceType.Rook   => rayReaches(board, from, rookDirs, target)
      case PieceType.Queen  => rayReaches(board, from, queenDirs, target)
      case PieceType.King   =>
        queenDirs.exists { (df, dr) => from.offset(df, dr).contains(target) }

  private def rayReaches(board: Board, from: Square, dirs: List[(Int, Int)], target: Square): Boolean =
    dirs.exists { dir =>
      def loop(sq: Square): Boolean = sq.offset(dir._1, dir._2) match
        case None                                      => false
        case Some(next) if next == target              => true
        case Some(next) if board.pieceAt(next).isEmpty => loop(next)
        case Some(_)                                   => false
      loop(from)
    }

  private def leavesKingInCheck(sit: Situation, move: Move): Boolean =
    val nextBoard = sit.board.applyMove(move)
    kingSquare(nextBoard, sit.turn).fold(false) { sq =>
      isAttackedBy(nextBoard, sq, sit.turn.opposite)
    }

  private def castlesThroughCheck(sit: Situation, move: Move): Boolean = move match
    case CastlingMove(from, to, _, _) =>
      val passSq = if to.file.toInt > from.file.toInt
        then from.offset(1, 0)
        else from.offset(-1, 0)
      isCheck(sit) || passSq.fold(false)(sq => isAttackedBy(sit.board, sq, sit.turn.opposite))
    case _ => false

  // ── Insufficient material ─────────────────────────────────────────────────
  private def insufficientMaterial(board: Board): Boolean =
    val nonKings = board.pieces.values.iterator.toList.filter(_.pieceType != PieceType.King)
    nonKings match
      case Nil => true
      case List(p) if p.pieceType == PieceType.Bishop || p.pieceType == PieceType.Knight => true
      case List(p1, p2)
        if p1.pieceType == PieceType.Bishop && p2.pieceType == PieceType.Bishop
           && p1.color != p2.color => true
      case _ => false
