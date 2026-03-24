package chess.rules

import chess.model.*

/** Standard (orthodox) chess rules. */
object StandardRules extends RuleSet:

  // ── Direction tables ──────────────────────────────────────────────────────

  private val rookDirs: List[(Int, Int)]   = List((0, 1), (0, -1), (1, 0), (-1, 0))
  private val bishopDirs: List[(Int, Int)] = List((1, 1), (1, -1), (-1, 1), (-1, -1))
  private val queenDirs: List[(Int, Int)]  = rookDirs ++ bishopDirs
  private val kingDirs: List[(Int, Int)]   = queenDirs
  private val knightLeaps: List[(Int, Int)] =
    List((1, 2), (2, 1), (2, -1), (1, -2), (-1, -2), (-2, -1), (-2, 1), (-1, 2))

  // ── RuleSet implementation ────────────────────────────────────────────────

  def candidateMoves(situation: Situation, square: Square): List[Move] =
    situation.board.pieceAt(square) match
      case None                                        => Nil
      case Some(Piece(c, _)) if c != situation.turn   => Nil
      case Some(Piece(color, pt))                      => movesFor(situation, square, color, pt)

  def legalMoves(situation: Situation, square: Square): List[Move] =
    candidateMoves(situation, square).filterNot(leavesOwnKingInCheck(situation, _))

  def allLegalMoves(situation: Situation): List[Move] =
    Square.all.toList.flatMap(legalMoves(situation, _))

  def isCheck(situation: Situation): Boolean =
    kingSquare(situation.board, situation.turn)
      .exists(sq => attackedBy(situation.board, sq, situation.turn.opposite))

  def isCheckmate(situation: Situation): Boolean =
    isCheck(situation) && allLegalMoves(situation).isEmpty

  def isStalemate(situation: Situation): Boolean =
    !isCheck(situation) && allLegalMoves(situation).isEmpty

  def isInsufficientMaterial(situation: Situation): Boolean =
    val nonKings = situation.board.pieces.values.filterNot(_.pieceType == PieceType.King).toList
    nonKings match
      case Nil                              => true
      case List(Piece(_, PieceType.Bishop)) => true
      case List(Piece(_, PieceType.Knight)) => true
      case _                                => false

  def isFiftyMoveRule(situation: Situation): Boolean =
    situation.halfMoveClock >= 100

  def applyMove(situation: Situation, move: Move): Situation =
    val newBoard     = situation.board.applyMove(move)
    val isCapture    = situation.board.pieceAt(move.to).isDefined || move.isInstanceOf[EnPassantMove]
    val isPawnMove   = situation.board.pieceAt(move.from).exists(_.pieceType == PieceType.Pawn)
    val newHalfClock = if isPawnMove || isCapture then 0 else situation.halfMoveClock + 1
    val newFullMove  =
      if situation.turn == Color.Black then situation.fullMoveNumber + 1
      else situation.fullMoveNumber

    Situation(
      board           = newBoard,
      turn            = situation.turn.opposite,
      castlingRights  = updatedCastlingRights(situation, move),
      enPassantSquare = enPassantAfter(situation, move),
      halfMoveClock   = newHalfClock,
      fullMoveNumber  = newFullMove,
    )

  // ── Move generation ───────────────────────────────────────────────────────

  private def movesFor(sit: Situation, sq: Square, color: Color, pt: PieceType): List[Move] =
    pt match
      case PieceType.Pawn   => pawnMoves(sit, sq, color)
      case PieceType.Knight => jumps(sit.board, sq, color, knightLeaps)
      case PieceType.Bishop => slides(sit.board, sq, color, bishopDirs)
      case PieceType.Rook   => slides(sit.board, sq, color, rookDirs)
      case PieceType.Queen  => slides(sit.board, sq, color, queenDirs)
      case PieceType.King   => kingMoves(sit, sq, color)

  /** Sliding piece moves: walk a ray until blocked or capturing. */
  private def slides(board: Board, from: Square, color: Color, dirs: List[(Int, Int)]): List[Move] =
    dirs.flatMap(ray(board, from, color, _, _))

  private def ray(board: Board, from: Square, color: Color, df: Int, dr: Int): List[Move] =
    def loop(f: Int, r: Int): List[Move] =
      val (nf, nr) = (f + df, r + dr)
      if nf < 0 || nf > 7 || nr < 0 || nr > 7 then Nil
      else
        val to = Square(File.values(nf), Rank.values(nr))
        board.pieceAt(to) match
          case Some(Piece(c, _)) if c == color => Nil
          case Some(_)                          => List(NormalMove(from, to))
          case None                             => NormalMove(from, to) :: loop(nf, nr)
    loop(from.file.index, from.rank.index)

  /** Jump moves (knight / king step): each offset tried once. */
  private def jumps(board: Board, from: Square, color: Color, offsets: List[(Int, Int)]): List[Move] =
    offsets.flatMap: (df, dr) =>
      val (nf, nr) = (from.file.index + df, from.rank.index + dr)
      if nf < 0 || nf > 7 || nr < 0 || nr > 7 then None
      else
        val to = Square(File.values(nf), Rank.values(nr))
        board.pieceAt(to) match
          case Some(Piece(c, _)) if c == color => None
          case _                               => Some(NormalMove(from, to))

  private def kingMoves(sit: Situation, from: Square, color: Color): List[Move] =
    jumps(sit.board, from, color, kingDirs) ++ castlingMoves(sit, from, color)

  // ── Pawn moves ────────────────────────────────────────────────────────────

  private def pawnMoves(sit: Situation, from: Square, color: Color): List[Move] =
    val dir        = if color == Color.White then 1 else -1
    val startRankI = if color == Color.White then 1 else 6
    val promoRankI = if color == Color.White then 7 else 0
    val fi         = from.file.index
    val ri         = from.rank.index

    val pushes = pawnPushes(sit.board, from, fi, ri, dir, startRankI, promoRankI)
    val caps   = pawnCaptures(sit.board, from, fi, ri, dir, promoRankI, color)
    val ep     = enPassantMoves(sit, from, fi, ri, dir, color)
    pushes ++ caps ++ ep

  private def pawnPushes(
    board: Board,
    from: Square,
    fi: Int, ri: Int,
    dir: Int,
    startRankI: Int,
    promoRankI: Int,
  ): List[Move] =
    val nr = ri + dir
    if nr < 0 || nr > 7 then Nil
    else
      val to = Square(File.values(fi), Rank.values(nr))
      if board.pieceAt(to).isDefined then Nil
      else if nr == promoRankI then promotions(from, to)
      else
        val single = NormalMove(from, to)
        val double =
          if ri != startRankI then Nil
          else
            val nr2 = ri + 2 * dir
            val to2 = Square(File.values(fi), Rank.values(nr2))
            if board.pieceAt(to2).isDefined then Nil
            else List(NormalMove(from, to2))
        single :: double

  private def pawnCaptures(
    board: Board,
    from: Square,
    fi: Int, ri: Int,
    dir: Int,
    promoRankI: Int,
    color: Color,
  ): List[Move] =
    List(-1, 1).flatMap: df =>
      val (nf, nr) = (fi + df, ri + dir)
      if nf < 0 || nf > 7 || nr < 0 || nr > 7 then Nil
      else
        val to = Square(File.values(nf), Rank.values(nr))
        board.pieceAt(to) match
          case Some(Piece(c, _)) if c != color =>
            if nr == promoRankI then promotions(from, to)
            else List(NormalMove(from, to))
          case _ => Nil

  private def enPassantMoves(sit: Situation, from: Square, fi: Int, ri: Int, dir: Int, color: Color): List[Move] =
    sit.enPassantSquare.toList.flatMap: eps =>
      val epFi = eps.file.index
      val epRi = eps.rank.index
      if epRi == ri + dir && math.abs(epFi - fi) == 1 then
        val captured = Square(eps.file, Rank.values(ri))
        List(EnPassantMove(from, eps, captured))
      else Nil

  private def promotions(from: Square, to: Square): List[NormalMove] =
    List(PieceType.Queen, PieceType.Rook, PieceType.Bishop, PieceType.Knight)
      .map(pt => NormalMove(from, to, Some(pt)))

  // ── Castling ──────────────────────────────────────────────────────────────

  private def castlingMoves(sit: Situation, kingFrom: Square, color: Color): List[Move] =
    if attackedBy(sit.board, kingFrom, color.opposite) then Nil
    else List(kingSideCastle(sit, kingFrom, color), queenSideCastle(sit, kingFrom, color)).flatten

  private def kingSideCastle(sit: Situation, kingFrom: Square, color: Color): Option[CastlingMove] =
    val hasRight = if color == Color.White then sit.castlingRights.whiteKingSide
                   else sit.castlingRights.blackKingSide
    if !hasRight then None
    else
      val rank     = if color == Color.White then Rank.values(0) else Rank.values(7)
      val rookFrom = Square(File.values(7), rank)
      val kingTo   = Square(File.values(6), rank)
      val rookTo   = Square(File.values(5), rank)
      val transit  = List(rookTo, kingTo)
      val ok =
        transit.forall(sq => sit.board.pieceAt(sq).isEmpty) &&
        sit.board.pieceAt(rookFrom).exists(p => p.color == color && p.pieceType == PieceType.Rook) &&
        transit.forall(sq => !attackedBy(sit.board, sq, color.opposite))
      Option.when(ok)(CastlingMove(kingFrom, kingTo, rookFrom, rookTo))

  private def queenSideCastle(sit: Situation, kingFrom: Square, color: Color): Option[CastlingMove] =
    val hasRight = if color == Color.White then sit.castlingRights.whiteQueenSide
                   else sit.castlingRights.blackQueenSide
    if !hasRight then None
    else
      val rank     = if color == Color.White then Rank.values(0) else Rank.values(7)
      val rookFrom = Square(File.values(0), rank)
      val kingTo   = Square(File.values(2), rank)
      val rookTo   = Square(File.values(3), rank)
      // b-file must be empty but is not on the king's path
      val mustBeEmpty  = List(File.values(1), File.values(2), File.values(3)).map(f => Square(f, rank))
      val kingPath     = List(File.values(2), File.values(3)).map(f => Square(f, rank))
      val ok =
        mustBeEmpty.forall(sq => sit.board.pieceAt(sq).isEmpty) &&
        sit.board.pieceAt(rookFrom).exists(p => p.color == color && p.pieceType == PieceType.Rook) &&
        kingPath.forall(sq => !attackedBy(sit.board, sq, color.opposite))
      Option.when(ok)(CastlingMove(kingFrom, kingTo, rookFrom, rookTo))

  // ── Attack detection ──────────────────────────────────────────────────────

  /**
   * Returns true if `square` is attacked by any piece of `byColor` on `board`.
   * Used for check detection and castling legality.
   */
  def attackedBy(board: Board, square: Square, byColor: Color): Boolean =
    pawnAttacks(board, square, byColor) ||
    knightAttacks(board, square, byColor) ||
    kingAttacks(board, square, byColor) ||
    rayAttacks(board, square, byColor, rookDirs, Set(PieceType.Rook, PieceType.Queen)) ||
    rayAttacks(board, square, byColor, bishopDirs, Set(PieceType.Bishop, PieceType.Queen))

  private def pawnAttacks(board: Board, square: Square, byColor: Color): Boolean =
    // Pawns of byColor attack from one rank behind them relative to their march direction
    val pawnDir = if byColor == Color.White then -1 else 1
    List(-1, 1).exists: df =>
      val (nf, nr) = (square.file.index + df, square.rank.index + pawnDir)
      if nf < 0 || nf > 7 || nr < 0 || nr > 7 then false
      else
        board.pieceAt(Square(File.values(nf), Rank.values(nr)))
          .exists(p => p.color == byColor && p.pieceType == PieceType.Pawn)

  private def knightAttacks(board: Board, square: Square, byColor: Color): Boolean =
    knightLeaps.exists: (df, dr) =>
      val (nf, nr) = (square.file.index + df, square.rank.index + dr)
      if nf < 0 || nf > 7 || nr < 0 || nr > 7 then false
      else
        board.pieceAt(Square(File.values(nf), Rank.values(nr)))
          .exists(p => p.color == byColor && p.pieceType == PieceType.Knight)

  private def kingAttacks(board: Board, square: Square, byColor: Color): Boolean =
    kingDirs.exists: (df, dr) =>
      val (nf, nr) = (square.file.index + df, square.rank.index + dr)
      if nf < 0 || nf > 7 || nr < 0 || nr > 7 then false
      else
        board.pieceAt(Square(File.values(nf), Rank.values(nr)))
          .exists(p => p.color == byColor && p.pieceType == PieceType.King)

  private def rayAttacks(
    board: Board,
    square: Square,
    byColor: Color,
    dirs: List[(Int, Int)],
    roles: Set[PieceType],
  ): Boolean =
    dirs.exists: (df, dr) =>
      def loop(f: Int, r: Int): Boolean =
        val (nf, nr) = (f + df, r + dr)
        if nf < 0 || nf > 7 || nr < 0 || nr > 7 then false
        else
          board.pieceAt(Square(File.values(nf), Rank.values(nr))) match
            case None                      => loop(nf, nr)
            case Some(Piece(c, pt))        => c == byColor && roles.contains(pt)
      loop(square.file.index, square.rank.index)

  // ── Legality filter ───────────────────────────────────────────────────────

  private def leavesOwnKingInCheck(sit: Situation, move: Move): Boolean =
    val newBoard = sit.board.applyMove(move)
    kingSquare(newBoard, sit.turn).exists(sq => attackedBy(newBoard, sq, sit.turn.opposite))

  private def kingSquare(board: Board, color: Color): Option[Square] =
    board.pieces.find { case (_, Piece(c, PieceType.King)) => c == color; case _ => false }
      .map(_._1)

  // ── Post-move state updates ───────────────────────────────────────────────

  private def enPassantAfter(sit: Situation, move: Move): Option[Square] =
    move match
      case NormalMove(from, to, None) =>
        sit.board.pieceAt(from).flatMap: piece =>
          val isDouble = piece.pieceType == PieceType.Pawn &&
            math.abs(to.rank.index - from.rank.index) == 2
          if isDouble then
            val epRankIdx = (from.rank.index + to.rank.index) / 2
            Some(Square(from.file, Rank.values(epRankIdx)))
          else None
      case _ => None

  private def updatedCastlingRights(sit: Situation, move: Move): CastlingRights =
    val cr = sit.castlingRights

    val afterKingMove = sit.board.pieceAt(move.from) match
      case Some(Piece(Color.White, PieceType.King)) =>
        cr.copy(whiteKingSide = false, whiteQueenSide = false)
      case Some(Piece(Color.Black, PieceType.King)) =>
        cr.copy(blackKingSide = false, blackQueenSide = false)
      case _ => cr

    val afterRookMove = sit.board.pieceAt(move.from) match
      case Some(Piece(Color.White, PieceType.Rook)) =>
        rookRightLoss(move.from, afterKingMove, isWhite = true)
      case Some(Piece(Color.Black, PieceType.Rook)) =>
        rookRightLoss(move.from, afterKingMove, isWhite = false)
      case _ => afterKingMove

    // Rook captured on its home square also forfeits castling rights
    sit.board.pieceAt(move.to) match
      case Some(Piece(Color.White, PieceType.Rook)) =>
        rookRightLoss(move.to, afterRookMove, isWhite = true)
      case Some(Piece(Color.Black, PieceType.Rook)) =>
        rookRightLoss(move.to, afterRookMove, isWhite = false)
      case _ => afterRookMove

  private def rookRightLoss(square: Square, cr: CastlingRights, isWhite: Boolean): CastlingRights =
    val fi = square.file.index
    val ri = square.rank.index
    if isWhite && ri == 0 then
      if fi == 0 then cr.copy(whiteQueenSide = false)
      else if fi == 7 then cr.copy(whiteKingSide = false)
      else cr
    else if !isWhite && ri == 7 then
      if fi == 0 then cr.copy(blackQueenSide = false)
      else if fi == 7 then cr.copy(blackKingSide = false)
      else cr
    else cr
