package org.maichess.mono.engine

import fastparse.*
import fastparse.NoWhitespace.*
import org.maichess.mono.model.*
import org.maichess.mono.rules.{CastlingRights, Situation}

/** FEN decoder backed by fastparse combinators. Encode delegates to [[Fen]]. */
class FastparseFen extends FenCodec:

  private val handwritten = new Fen()

  def encode(situation: Situation): String = handwritten.encode(situation)

  def decode(fen: String): Either[String, Situation] =
    parse(fen, fenParser) match
      case Parsed.Success(sit, _) => Right(sit)
      case f: Parsed.Failure      => Left("Invalid FEN: " + f.msg)

  // ── Board ──────────────────────────────────────────────────────────────────

  private def pieceCell(implicit ctx: P[Any]): P[Seq[Option[Piece]]] =
    CharIn("KQRBNPkqrbnp").!.map(s => Seq(Some(charToPiece(s.head))))

  private def emptyRun(implicit ctx: P[Any]): P[Seq[Option[Piece]]] =
    CharIn("1-8").!.map(s => Seq.fill(s.head.asDigit)(None))

  private def rankCells(implicit ctx: P[Any]): P[Seq[Option[Piece]]] =
    (pieceCell | emptyRun).rep(1).map(_.flatten.toSeq).flatMap { cells =>
      if cells.length == 8 then Pass.map(_ => cells) else Fail
    }

  private def board(implicit ctx: P[Any]): P[Board] =
    rankCells.rep(sep = P("/"), exactly = 8).map(ranks => buildBoard(ranks.toSeq))

  // ── Fields ─────────────────────────────────────────────────────────────────

  private def turn(implicit ctx: P[Any]): P[Color] =
    CharIn("wb").!.map(s => if s == "w" then Color.White else Color.Black)

  private def castlingRights(implicit ctx: P[Any]): P[CastlingRights] =
    P("-").map(_ => CastlingRights.none) |
    CharIn("KQkq").rep(1).!.map(s => CastlingRights(
      whiteKingSide  = s.contains('K'),
      whiteQueenSide = s.contains('Q'),
      blackKingSide  = s.contains('k'),
      blackQueenSide = s.contains('q')
    ))

  private def fileChar(implicit ctx: P[Any]): P[Char] = CharIn("a-h").!.map(_.head)
  private def rankChar(implicit ctx: P[Any]): P[Char] = CharIn("1-8").!.map(_.head)

  private def square(implicit ctx: P[Any]): P[Square] =
    (fileChar ~ rankChar).flatMap { case (f, r) =>
      Square.fromAlgebraic(f.toString + r.toString)
        .fold[P[Square]](Fail)(sq => Pass.map(_ => sq))
    }

  private def enPassant(implicit ctx: P[Any]): P[Option[Square]] =
    P("-").map(_ => None) | square.map(Some(_))

  private def nat(implicit ctx: P[Any]): P[Int] =
    CharsWhile(_.isDigit).!.map(_.toInt)

  // ── Top-level ──────────────────────────────────────────────────────────────

  private def fenParser(p: P[Any]): P[Situation] =
    given P[Any] = p
    (board ~ " " ~ turn ~ " " ~ castlingRights ~ " " ~ enPassant ~ " " ~ nat ~ " " ~ nat ~ End)
      .map { case (b, t, cr, ep, half, full) => Situation(b, t, cr, ep, half, full) }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private def charToPiece(c: Char): Piece =
    val color = if c.isUpper then Color.White else Color.Black
    val pt    = c.toLower match
      case 'k' => PieceType.King
      case 'q' => PieceType.Queen
      case 'r' => PieceType.Rook
      case 'b' => PieceType.Bishop
      case 'n' => PieceType.Knight
      case _   => PieceType.Pawn
    Piece(color, pt)

  private def buildBoard(ranks: Seq[Seq[Option[Piece]]]): Board =
    val pieces = for
      (cells, rankFromTop) <- ranks.zipWithIndex
      rankIdx               = 7 - rankFromTop
      (optPiece, fileIdx)  <- cells.zipWithIndex
      piece                <- optPiece
      file                 <- File.fromInt(fileIdx)
      rank                 <- Rank.fromInt(rankIdx)
    yield Square(file, rank) -> piece
    Board(pieces.toMap)
