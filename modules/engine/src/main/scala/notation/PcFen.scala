package org.maichess.mono.engine

import scala.util.parsing.combinator.RegexParsers
import org.maichess.mono.model.*
import org.maichess.mono.rules.{CastlingRights, Situation}

/** FEN decoder backed by scala-parser-combinators. Encode delegates to [[Fen]]. */
class PcFen extends FenCodec with RegexParsers:

  override val skipWhitespace = false

  private val handwritten = new Fen()

  def encode(situation: Situation): String = handwritten.encode(situation)

  def decode(fen: String): Either[String, Situation] =
    parseAll(fenParser, fen) match
      case Success(sit, _)    => Right(sit)
      case err: NoSuccess @unchecked => Left("Invalid FEN: " + err.msg)

  // ── Board ──────────────────────────────────────────────────────────────────

  private def pieceCell: Parser[Seq[Option[Piece]]] =
    "[KQRBNPkqrbnp]".r ^^ (s => Seq(Some(charToPiece(s.head))))

  private def emptyRun: Parser[Seq[Option[Piece]]] =
    "[1-8]".r ^^ (s => Seq.fill(s.head.asDigit)(None))

  private def rankCells: Parser[Seq[Option[Piece]]] =
    rep1(pieceCell | emptyRun) ^? (
      { case cells if cells.flatten.size == 8 => cells.flatten },
      _ => "rank must have exactly 8 cells"
    )

  private def board: Parser[Board] =
    repsep(rankCells, "/") ^? (
      { case ranks if ranks.size == 8 => buildBoard(ranks) },
      _ => "board must have exactly 8 ranks"
    )

  // ── Fields ─────────────────────────────────────────────────────────────────

  private def turn: Parser[Color] =
    "[wb]".r ^^ (s => if s == "w" then Color.White else Color.Black)

  private def castlingRights: Parser[CastlingRights] =
    "-" ^^^ CastlingRights.none |
    "[KQkq]+".r ^^ (s => CastlingRights(
      whiteKingSide  = s.contains('K'),
      whiteQueenSide = s.contains('Q'),
      blackKingSide  = s.contains('k'),
      blackQueenSide = s.contains('q')
    ))

  private def square: Parser[Square] =
    ("[a-h][1-8]".r ^^ Square.fromAlgebraic) ^? (
      { case Some(sq) => sq },
      _ => "invalid square"
    )

  private def enPassant: Parser[Option[Square]] =
    "-" ^^^ None | square ^^ Some.apply

  private def nat: Parser[Int] = """\d+""".r ^^ (_.toInt)

  // ── Top-level ──────────────────────────────────────────────────────────────

  private def fenParser: Parser[Situation] =
    board ~ " " ~ turn ~ " " ~ castlingRights ~ " " ~ enPassant ~ " " ~ nat ~ " " ~ nat ^^ {
      case b ~ _ ~ t ~ _ ~ cr ~ _ ~ ep ~ _ ~ half ~ _ ~ full =>
        Situation(b, t, cr, ep, half, full)
    }

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
