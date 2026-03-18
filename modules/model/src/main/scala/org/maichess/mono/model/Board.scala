package org.maichess.mono.model

/** Immutable board state: a mapping from squares to pieces. */
case class Board(pieces: Map[Square, Piece]):

  /** Returns the piece at `square`, or None if empty. */
  def pieceAt(square: Square): Option[Piece] =
    pieces.get(square)

  /** Returns a new Board with the move applied. Never mutates. */
  def applyMove(move: Move): Board = move match
    case NormalMove(from, to, promotion) =>
      val movedPiece = pieces.get(from).map { p =>
        promotion.fold(p)(pt => p.copy(pieceType = pt))
      }
      val withoutFrom = pieces.removed(from)
      movedPiece.fold(Board(withoutFrom))(p => Board(withoutFrom.updated(to, p)))

    case CastlingMove(from, to, rookFrom, rookTo) =>
      val king = pieces.get(from)
      val rook = pieces.get(rookFrom)
      val base = pieces.removed(from).removed(rookFrom)
      val withKing = king.fold(base)(k => base.updated(to, k))
      val withRook = rook.fold(withKing)(r => withKing.updated(rookTo, r))
      Board(withRook)

    case EnPassantMove(from, to, captured) =>
      val pawn = pieces.get(from)
      val base = pieces.removed(from).removed(captured)
      Board(pawn.fold(base)(p => base.updated(to, p)))

object Board:
  val empty: Board = Board(Map.empty)

  /** Standard chess starting position with all 32 pieces. */
  def standard: Board =
    // Explicitly enumerate all 32 squares — WartRemover-safe (no .get calls)
    val positions: List[(String, Piece)] = List(
      ("a1", Piece(Color.White, PieceType.Rook)),
      ("b1", Piece(Color.White, PieceType.Knight)),
      ("c1", Piece(Color.White, PieceType.Bishop)),
      ("d1", Piece(Color.White, PieceType.Queen)),
      ("e1", Piece(Color.White, PieceType.King)),
      ("f1", Piece(Color.White, PieceType.Bishop)),
      ("g1", Piece(Color.White, PieceType.Knight)),
      ("h1", Piece(Color.White, PieceType.Rook)),
      ("a2", Piece(Color.White, PieceType.Pawn)),
      ("b2", Piece(Color.White, PieceType.Pawn)),
      ("c2", Piece(Color.White, PieceType.Pawn)),
      ("d2", Piece(Color.White, PieceType.Pawn)),
      ("e2", Piece(Color.White, PieceType.Pawn)),
      ("f2", Piece(Color.White, PieceType.Pawn)),
      ("g2", Piece(Color.White, PieceType.Pawn)),
      ("h2", Piece(Color.White, PieceType.Pawn)),
      ("a7", Piece(Color.Black, PieceType.Pawn)),
      ("b7", Piece(Color.Black, PieceType.Pawn)),
      ("c7", Piece(Color.Black, PieceType.Pawn)),
      ("d7", Piece(Color.Black, PieceType.Pawn)),
      ("e7", Piece(Color.Black, PieceType.Pawn)),
      ("f7", Piece(Color.Black, PieceType.Pawn)),
      ("g7", Piece(Color.Black, PieceType.Pawn)),
      ("h7", Piece(Color.Black, PieceType.Pawn)),
      ("a8", Piece(Color.Black, PieceType.Rook)),
      ("b8", Piece(Color.Black, PieceType.Knight)),
      ("c8", Piece(Color.Black, PieceType.Bishop)),
      ("d8", Piece(Color.Black, PieceType.Queen)),
      ("e8", Piece(Color.Black, PieceType.King)),
      ("f8", Piece(Color.Black, PieceType.Bishop)),
      ("g8", Piece(Color.Black, PieceType.Knight)),
      ("h8", Piece(Color.Black, PieceType.Rook))
    )
    Board(positions.flatMap { (alg, piece) =>
      Square.fromAlgebraic(alg).map(_ -> piece)
    }.toMap)
