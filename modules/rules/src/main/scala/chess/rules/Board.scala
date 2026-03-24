package chess.rules

import chess.model.*

/** The chess board: an immutable mapping of squares to pieces. */
case class Board(pieces: Map[Square, Piece]):

  /** Returns the piece on `square`, or [[None]] if the square is empty. */
  def pieceAt(square: Square): Option[Piece] = pieces.get(square)

  /**
   * Returns a new [[Board]] after applying `move`.
   * Assumes the move is legal; does not validate.
   */
  def applyMove(move: Move): Board = move match
    case NormalMove(from, to, promotion) =>
      pieces.get(from).fold(this): movingPiece =>
        val arriving = promotion.fold(movingPiece)(pt => movingPiece.copy(pieceType = pt))
        Board(pieces - from + (to -> arriving))

    case CastlingMove(from, to, rookFrom, rookTo) =>
      val result = for
        king <- pieces.get(from)
        rook <- pieces.get(rookFrom)
      yield Board(pieces - from - rookFrom + (to -> king) + (rookTo -> rook))
      result.getOrElse(this)

    case EnPassantMove(from, to, captured) =>
      pieces.get(from).fold(this): movingPiece =>
        Board(pieces - from - captured + (to -> movingPiece))

object Board:
  /** An empty board with no pieces. */
  val empty: Board = Board(Map.empty)

  /** A board in the standard chess starting position. */
  val standard: Board = buildStandard

  private def buildStandard: Board =
    import Color.*
    import PieceType.*

    def sq(f: Char, r: Int): Square = Square.fromAlgebraic(s"$f$r").get

    val backRow = List(Rook, Knight, Bishop, Queen, King, Bishop, Knight, Rook)
    val files   = List('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h')

    val whitePieces = files.zip(backRow).map((f, pt) => sq(f, 1) -> Piece(White, pt))
    val whitePawns  = files.map(f => sq(f, 2) -> Piece(White, Pawn))
    val blackPieces = files.zip(backRow).map((f, pt) => sq(f, 8) -> Piece(Black, pt))
    val blackPawns  = files.map(f => sq(f, 7) -> Piece(Black, Pawn))

    Board((whitePieces ++ whitePawns ++ blackPieces ++ blackPawns).toMap)
