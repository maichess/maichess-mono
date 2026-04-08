package org.maichess.mono.engine

import scala.util.parsing.combinator.RegexParsers
import org.maichess.mono.model.*
import org.maichess.mono.rules.{RuleSet, Situation}

/** SAN decoder backed by scala-parser-combinators. Encode delegates to [[San]]. */
class PcSan extends SanCodec with RegexParsers:

  override val skipWhitespace = false

  private val handwritten = new San()

  def encode(situation: Situation, move: Move, after: Situation, ruleSet: RuleSet): String =
    handwritten.encode(situation, move, after, ruleSet)

  def decode(situation: Situation, san: String, ruleSet: RuleSet): Either[String, Move] =
    val stripped = san.stripSuffix("#").stripSuffix("+")
    parseAll(sanSpec, stripped) match
      case Success(spec, _)          => findMove(situation, spec, ruleSet)
      case _: NoSuccess @unchecked   => Left("Invalid SAN: " + san)

  // ── Intermediate structure ─────────────────────────────────────────────────

  private enum SanSpec:
    case Castling(queenSide: Boolean)
    case PieceMove(pt: PieceType, disambig: String, isCapture: Boolean, to: Square, promo: Option[PieceType])
    case PawnMove(fromFile: Option[Char], isCapture: Boolean, to: Square, promo: Option[PieceType])

  // ── Shared primitives ──────────────────────────────────────────────────────

  private def square: Parser[Square] =
    ("[a-h][1-8]".r ^^ Square.fromAlgebraic) ^? (
      { case Some(sq) => sq },
      _ => "invalid square"
    )

  private def promoLetter: Parser[PieceType] =
    "[QRBN]".r ^^ {
      case "Q" => PieceType.Queen
      case "R" => PieceType.Rook
      case "B" => PieceType.Bishop
      case _   => PieceType.Knight
    }

  private def promotion: Parser[PieceType] = "=" ~> promoLetter

  // ── Castling ───────────────────────────────────────────────────────────────

  private def castlingSpec: Parser[SanSpec] =
    "O-O-O" ^^^ SanSpec.Castling(queenSide = true) |
    "O-O"   ^^^ SanSpec.Castling(queenSide = false)

  // ── Piece move ─────────────────────────────────────────────────────────────

  private def pieceTypeLetter: Parser[PieceType] =
    "[KQRBN]".r ^^ {
      case "Q" => PieceType.Queen
      case "R" => PieceType.Rook
      case "B" => PieceType.Bishop
      case "N" => PieceType.Knight
      case _   => PieceType.King
    }

  private def pieceMoveSpec: Parser[SanSpec] =
    (pieceTypeLetter ~ "[abcdefgh12345678x]*".r ~ opt(promotion)) ^? (
      Function.unlift { case pt ~ middle ~ promo =>
        val noCapture = middle.replace("x", "")
        if noCapture.length < 2 then None
        else
          Square.fromAlgebraic(noCapture.takeRight(2)).map { sq =>
            SanSpec.PieceMove(pt, noCapture.dropRight(2), middle.contains('x'), sq, promo)
          }
      },
      _ => "invalid piece move"
    )

  // ── Pawn move ──────────────────────────────────────────────────────────────

  private def pawnCapture: Parser[(Option[Char], Boolean, Square)] =
    ("[a-h]".r <~ "x") ~ square ^^ { case f ~ sq => (Some(f.head), true, sq) }

  private def pawnPush: Parser[(Option[Char], Boolean, Square)] =
    square ^^ (sq => (None, false, sq))

  private def pawnMoveSpec: Parser[SanSpec] =
    (pawnCapture | pawnPush) ~ opt(promotion) ^^ {
      case (fromFile, isCapture, to) ~ promo =>
        SanSpec.PawnMove(fromFile, isCapture, to, promo)
    }

  // ── Top-level ──────────────────────────────────────────────────────────────

  private def sanSpec: Parser[SanSpec] =
    castlingSpec | pieceMoveSpec | pawnMoveSpec

  // ── Move lookup ────────────────────────────────────────────────────────────

  private def findMove(situation: Situation, spec: SanSpec, ruleSet: RuleSet): Either[String, Move] =
    import SanSpec.*
    val legal = ruleSet.allLegalMoves(situation)
    spec match
      case Castling(queenSide) =>
        legal.collectFirst {
          case m: CastlingMove if queenSide == (m.rookFrom.file.toInt < m.from.file.toInt) => m
        }.toRight(if queenSide then "No queen-side castling available" else "No king-side castling available")

      case PieceMove(pt, disambig, _, to, _) =>
        val candidates = legal.filter { m =>
          m.to == to &&
          situation.board.pieceAt(m.from).exists(_.pieceType == pt) &&
          matchesDisambig(m.from, disambig)
        }
        candidates match
          case single :: Nil => Right(single)
          case Nil           => Left("No legal move matches '" + spec.toString + "'")
          case _             => Left("Ambiguous SAN '" + spec.toString + "'")

      case PawnMove(fromFile, isCapture, to, promoOpt) =>
        val candidates = legal.filter {
          case NormalMove(from, moveTo, promo) =>
            moveTo == to && promo == promoOpt &&
            situation.board.pieceAt(from).exists(_.pieceType == PieceType.Pawn) &&
            fromFile.forall(_ == from.file.toChar) &&
            (isCapture || from.file == moveTo.file)
          case ep: EnPassantMove =>
            ep.to == to && fromFile.forall(_ == ep.from.file.toChar)
          case _ => false
        }
        candidates match
          case Nil    => Left("No legal pawn move matches '" + spec.toString + "'")
          case m :: _ => Right(m)

  private def matchesDisambig(from: Square, disambig: String): Boolean =
    if disambig.isEmpty then true
    else if disambig.length == 1 then
      disambig.head.isLetter && disambig.head == from.file.toChar ||
      disambig.head.isDigit  && disambig.head == from.rank.toChar
    else Square.fromAlgebraic(disambig).contains(from)
