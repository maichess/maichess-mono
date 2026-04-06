package org.maichess.mono.engine

import fastparse.*
import fastparse.NoWhitespace.*
import org.maichess.mono.model.*
import org.maichess.mono.rules.{RuleSet, Situation}

/** SAN decoder backed by fastparse combinators. Encode delegates to [[San]]. */
class FastparseSan extends SanCodec:

  private val handwritten = new San()

  def encode(situation: Situation, move: Move, after: Situation, ruleSet: RuleSet): String =
    handwritten.encode(situation, move, after, ruleSet)

  def decode(situation: Situation, san: String, ruleSet: RuleSet): Either[String, Move] =
    val stripped = san.stripSuffix("#").stripSuffix("+")
    parse(stripped, sanSpec) match
      case Parsed.Success(spec, _) => findMove(situation, spec, ruleSet)
      case _: Parsed.Failure       => Left("Invalid SAN: " + san)

  // ── Intermediate structure ─────────────────────────────────────────────────

  private enum SanSpec:
    case Castling(queenSide: Boolean)
    case PieceMove(pt: PieceType, disambig: String, isCapture: Boolean, to: Square, promo: Option[PieceType])
    case PawnMove(fromFile: Option[Char], isCapture: Boolean, to: Square, promo: Option[PieceType])

  // ── Shared primitives ──────────────────────────────────────────────────────

  private def fileChar(implicit ctx: P[Any]): P[Char] = CharIn("a-h").!.map(_.head)
  private def rankChar(implicit ctx: P[Any]): P[Char] = CharIn("1-8").!.map(_.head)

  private def square(implicit ctx: P[Any]): P[Square] =
    (fileChar ~ rankChar).flatMap { case (f, r) =>
      Square.fromAlgebraic(f.toString + r.toString)
        .fold[P[Square]](Fail)(sq => Pass.map(_ => sq))
    }

  private def promoLetter(implicit ctx: P[Any]): P[PieceType] =
    CharIn("QRBN").!.map {
      case "Q" => PieceType.Queen
      case "R" => PieceType.Rook
      case "B" => PieceType.Bishop
      case _   => PieceType.Knight
    }

  private def promotion(implicit ctx: P[Any]): P[PieceType] = P("=") ~ promoLetter

  // ── Castling ───────────────────────────────────────────────────────────────

  private def castlingSpec(implicit ctx: P[Any]): P[SanSpec] =
    P("O-O-O").map(_ => SanSpec.Castling(queenSide = true)) |
    P("O-O").map(_ => SanSpec.Castling(queenSide = false))

  // ── Piece move ─────────────────────────────────────────────────────────────
  //
  // Piece letter, then any mix of [a-h1-8x] (disambiguation + optional capture
  // marker), then optional promotion. The last two non-'x' chars are the dest
  // square — same heuristic as the handwritten parser.

  private def pieceTypeLetter(implicit ctx: P[Any]): P[PieceType] =
    CharIn("KQRBN").!.map {
      case "Q" => PieceType.Queen
      case "R" => PieceType.Rook
      case "B" => PieceType.Bishop
      case "N" => PieceType.Knight
      case _   => PieceType.King
    }

  private def pieceMoveSpec(implicit ctx: P[Any]): P[SanSpec] =
    (pieceTypeLetter ~ CharsWhile("abcdefgh12345678x".contains(_), 0).! ~ promotion.?).flatMap {
      case (pt, middle, promo) =>
        val noCapture = middle.replace("x", "")
        val isCapture = middle.contains('x')
        if noCapture.length < 2 then Fail
        else
          val dest     = noCapture.takeRight(2)
          val disambig = noCapture.dropRight(2)
          Square.fromAlgebraic(dest)
            .fold[P[SanSpec]](Fail)(sq => Pass.map(_ => SanSpec.PieceMove(pt, disambig, isCapture, sq, promo)))
    }

  // ── Pawn move ──────────────────────────────────────────────────────────────

  private def pawnCapture(implicit ctx: P[Any]): P[(Option[Char], Boolean, Square)] =
    (fileChar ~ P("x") ~ square).map { case (f, sq) => (Some(f), true, sq) }

  private def pawnPush(implicit ctx: P[Any]): P[(Option[Char], Boolean, Square)] =
    square.map(sq => (None, false, sq))

  private def pawnMoveSpec(implicit ctx: P[Any]): P[SanSpec] =
    (pawnCapture | pawnPush).flatMap { case (fromFile, isCapture, to) =>
      promotion.?.map(promo => SanSpec.PawnMove(fromFile, isCapture, to, promo))
    }

  // ── Top-level ──────────────────────────────────────────────────────────────

  private def sanSpec(p: P[Any]): P[SanSpec] =
    given P[Any] = p
    (castlingSpec | pieceMoveSpec | pawnMoveSpec) ~ End

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
