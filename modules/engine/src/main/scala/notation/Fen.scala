package org.maichess.mono.engine

import org.maichess.mono.model.*
import org.maichess.mono.rules.{CastlingRights, Situation}

object Fen:

  def encode(situation: Situation): String =
    val board    = encodeBoard(situation.board)
    val turn     = if situation.turn == Color.White then "w" else "b"
    val castling = encodeCastling(situation.castlingRights)
    val ep       = situation.enPassantSquare.fold("-")(_.toAlgebraic)
    val half     = situation.halfMoveClock.toString
    val full     = situation.fullMoveNumber.toString
    board + " " + turn + " " + castling + " " + ep + " " + half + " " + full

  def decode(fen: String): Either[String, Situation] =
    fen.split(' ').toList match
      case boardStr :: turnStr :: castlingStr :: epStr :: halfStr :: fullStr :: Nil =>
        for
          board    <- decodeBoard(boardStr)
          turn     <- decodeTurn(turnStr)
          castling <- decodeCastling(castlingStr)
          ep       <- decodeEp(epStr)
          half     <- decodeInt(halfStr, "half-move clock")
          full     <- decodeInt(fullStr, "full-move number")
        yield Situation(board, turn, castling, ep, half, full)
      case _ => Left("FEN must have 6 space-separated fields, got: " + fen)

  private def encodeBoard(board: Board): String =
    (7 to 0 by -1).map { rankIdx =>
      val row = (0 to 7).map { fileIdx =>
        val optSq = for f <- File.fromInt(fileIdx); r <- Rank.fromInt(rankIdx) yield Square(f, r)
        optSq.flatMap(board.pieceAt)
      }
      encodeRow(row.toList)
    }.mkString("/")

  private def encodeRow(cells: List[Option[Piece]]): String =
    val (result, empties) = cells.foldLeft(("", 0)) {
      case ((acc, n), None)        => (acc, n + 1)
      case ((acc, n), Some(piece)) =>
        val prefix = if n > 0 then n.toString else ""
        (acc + prefix + pieceChar(piece).toString, 0)
    }
    if empties > 0 then result + empties.toString else result

  private def pieceChar(piece: Piece): Char =
    val c = piece.pieceType match
      case PieceType.King   => 'k'
      case PieceType.Queen  => 'q'
      case PieceType.Rook   => 'r'
      case PieceType.Bishop => 'b'
      case PieceType.Knight => 'n'
      case PieceType.Pawn   => 'p'
    if piece.color == Color.White then c.toUpper else c

  private def encodeCastling(cr: CastlingRights): String =
    val s = (if cr.whiteKingSide  then "K" else "") +
            (if cr.whiteQueenSide then "Q" else "") +
            (if cr.blackKingSide  then "k" else "") +
            (if cr.blackQueenSide then "q" else "")
    if s.isEmpty then "-" else s

  private def decodeBoard(s: String): Either[String, Board] =
    val ranks = s.split('/').toList
    if ranks.size != 8 then Left("Board must have 8 ranks separated by '/', got: " + s)
    else
      val pairs: List[Either[String, (Square, Piece)]] =
        ranks.zipWithIndex.flatMap { case (rankStr, rankFromTop) =>
          val rankIdx = 7 - rankFromTop
          decodeRank(rankStr, rankIdx)
        }
      pairs.foldLeft(Right(Map.empty[Square, Piece]): Either[String, Map[Square, Piece]]) {
        case (Right(map), Right(pair)) => Right(map + pair)
        case (Left(e),    _)           => Left(e)
        case (_,          Left(e))     => Left(e)
      }.map(Board(_))

  private def decodeRank(s: String, rankIdx: Int): List[Either[String, (Square, Piece)]] =
    val (pairs, _, errs) = s.foldLeft((List.empty[(Square, Piece)], 0, List.empty[String])) {
      case ((acc, file, errs), c) if c.isDigit =>
        (acc, file + c.asDigit, errs)
      case ((acc, file, errs), c) =>
        charToPiece(c) match
          case None => (acc, file + 1, errs :+ ("Unknown piece char: " + c.toString))
          case Some(piece) =>
            val optSq = for f <- File.fromInt(file); r <- Rank.fromInt(rankIdx) yield Square(f, r)
            optSq match
              case None     => (acc, file + 1, errs :+ ("Invalid square: file=" + file.toString + " rank=" + rankIdx.toString))
              case Some(sq) => (acc :+ (sq -> piece), file + 1, errs)
    }
    if errs.nonEmpty then List(Left(errs.mkString(", ")))
    else pairs.map(Right(_))

  private def charToPiece(c: Char): Option[Piece] =
    val pt = c.toLower match
      case 'k' => Some(PieceType.King)
      case 'q' => Some(PieceType.Queen)
      case 'r' => Some(PieceType.Rook)
      case 'b' => Some(PieceType.Bishop)
      case 'n' => Some(PieceType.Knight)
      case 'p' => Some(PieceType.Pawn)
      case _   => None
    val color = if c.isUpper then Color.White else Color.Black
    pt.map(Piece(color, _))

  private def decodeTurn(s: String): Either[String, Color] = s match
    case "w" => Right(Color.White)
    case "b" => Right(Color.Black)
    case _   => Left("Invalid active color '" + s + "', expected 'w' or 'b'")

  private def decodeCastling(s: String): Either[String, CastlingRights] =
    if s == "-" then Right(CastlingRights.none)
    else if s.forall("KQkq-".contains) then
      Right(CastlingRights(
        whiteKingSide  = s.contains('K'),
        whiteQueenSide = s.contains('Q'),
        blackKingSide  = s.contains('k'),
        blackQueenSide = s.contains('q')
      ))
    else Left("Invalid castling field '" + s + "'")

  private def decodeEp(s: String): Either[String, Option[Square]] =
    if s == "-" then Right(None)
    else Square.fromAlgebraic(s).fold(Left("Invalid en-passant square '" + s + "'"): Either[String, Option[Square]])(sq => Right(Some(sq)))

  private def decodeInt(s: String, name: String): Either[String, Int] =
    s.toIntOption.toRight("Invalid " + name + " '" + s + "'")
