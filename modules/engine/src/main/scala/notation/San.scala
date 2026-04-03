package org.maichess.mono.engine

import org.maichess.mono.model.*
import org.maichess.mono.rules.{RuleSet, Situation}

/** Standard Algebraic Notation encoder/decoder. */
object San:

  /** Encodes a move as SAN given the situation *before* the move and the resulting situation. */
  def encode(situation: Situation, move: Move, after: Situation, ruleSet: RuleSet): String =
    val base = move match
      case CastlingMove(from, _, rookFrom, _) =>
        if rookFrom.file.toInt > from.file.toInt then "O-O" else "O-O-O"
      case EnPassantMove(from, to, _) =>
        from.file.toChar.toString + "x" + to.toAlgebraic
      case NormalMove(from, to, promo) =>
        situation.board.pieceAt(from) match
          case Some(p) if p.pieceType == PieceType.Pawn => encodePawnMove(situation, from, to, promo)
          case Some(p) => encodePieceMove(situation, p.pieceType, from, to, ruleSet)
          case None    => to.toAlgebraic
    val suffix =
      if ruleSet.isCheckmate(after) then "#"
      else if ruleSet.isCheck(after) then "+"
      else ""
    base + suffix

  /** Decodes a SAN string into a Move given the current situation. Returns Left on failure. */
  def decode(situation: Situation, san: String, ruleSet: RuleSet): Either[String, Move] =
    val stripped = san.stripSuffix("#").stripSuffix("+")
    if stripped == "O-O-O" then
      findCastle(situation, ruleSet, queenSide = true)
    else if stripped == "O-O" then
      findCastle(situation, ruleSet, queenSide = false)
    else
      decodeNormal(situation, stripped, ruleSet)

  private def encodePawnMove(situation: Situation, from: Square, to: Square, promo: Option[PieceType]): String =
    val isCapture = situation.board.pieceAt(to).isDefined || from.file != to.file
    val capture   = if isCapture then from.file.toChar.toString + "x" else ""
    val promoStr  = promo.fold("")(pt => "=" + promotionLetter(pt).toString)
    capture + to.toAlgebraic + promoStr

  private def encodePieceMove(situation: Situation, pt: PieceType, from: Square, to: Square, ruleSet: RuleSet): String =
    val letter    = pieceLetter(pt).toString
    val isCapture = situation.board.pieceAt(to).isDefined
    val capture   = if isCapture then "x" else ""
    val ambig     = disambiguate(situation, pt, from, to, ruleSet)
    letter + ambig + capture + to.toAlgebraic

  private def disambiguate(situation: Situation, pt: PieceType, from: Square, to: Square, ruleSet: RuleSet): String =
    val others = ruleSet.allLegalMoves(situation)
      .filter(m => m.to == to && m.from != from && situation.board.pieceAt(m.from).exists(_.pieceType == pt))
    if others.isEmpty then ""
    else if others.forall(_.from.file != from.file) then from.file.toChar.toString
    else if others.forall(_.from.rank != from.rank) then from.rank.toChar.toString
    else from.toAlgebraic

  /** Letter for non-pawn pieces in SAN. Wildcard covers King — the only remaining case. */
  private def pieceLetter(pt: PieceType): Char = pt match
    case PieceType.Queen  => 'Q'
    case PieceType.Rook   => 'R'
    case PieceType.Bishop => 'B'
    case PieceType.Knight => 'N'
    case _                => 'K'

  /** Letter for promotion pieces. Wildcard covers Queen — the most common promotion. */
  private def promotionLetter(pt: PieceType): Char = pt match
    case PieceType.Rook   => 'R'
    case PieceType.Bishop => 'B'
    case PieceType.Knight => 'N'
    case _                => 'Q'

  private def findCastle(situation: Situation, ruleSet: RuleSet, queenSide: Boolean): Either[String, Move] =
    ruleSet.allLegalMoves(situation).collectFirst {
      case m: CastlingMove if queenSide == (m.rookFrom.file.toInt < m.from.file.toInt) => m
    }.toRight(if queenSide then "No queen-side castling available" else "No king-side castling available")

  private def decodeNormal(situation: Situation, san: String, ruleSet: RuleSet): Either[String, Move] =
    val all     = ruleSet.allLegalMoves(situation)
    val isPiece = san.nonEmpty && san.head.isUpper && san.head != 'O'
    if isPiece then decodePieceMove(situation, san, all)
    else decodePawnMove(situation, san, all)

  private def decodePieceMove(situation: Situation, san: String, legal: List[Move]): Either[String, Move] =
    val pt: Option[PieceType] = san.head match
      case 'K' => Some(PieceType.King)
      case 'Q' => Some(PieceType.Queen)
      case 'R' => Some(PieceType.Rook)
      case 'B' => Some(PieceType.Bishop)
      case 'N' => Some(PieceType.Knight)
      case _   => None
    pt match
      case None => Left("Unknown piece letter: " + san.head.toString)
      case Some(pieceType) =>
        val rest     = san.drop(1).replace("x", "")
        val dest     = rest.takeRight(2)
        val disambig = rest.dropRight(2)
        Square.fromAlgebraic(dest) match
          case None => Left("Invalid destination square '" + dest + "' in '" + san + "'")
          case Some(to) =>
            val candidates = legal.filter { m =>
              m.to == to && situation.board.pieceAt(m.from).exists(_.pieceType == pieceType) &&
              matchesDisambig(m.from, disambig)
            }
            candidates match
              case single :: Nil => Right(single)
              case Nil           => Left("No legal move matches '" + san + "'")
              case _             => Left("Ambiguous SAN '" + san + "'")

  private def decodePawnMove(situation: Situation, san: String, legal: List[Move]): Either[String, Move] =
    val promoOpt = if san.contains('=') then
      san.dropWhile(_ != '=').drop(1).headOption.flatMap(c => charToPieceType(c.toLower))
    else None
    val withoutPromo = san.takeWhile(_ != '=')
    val isCapture    = withoutPromo.contains('x')
    val dest         = withoutPromo.takeRight(2)
    val srcFile      = if isCapture then Some(withoutPromo.head) else None
    Square.fromAlgebraic(dest) match
      case None => Left("Invalid destination '" + dest + "' in pawn move '" + san + "'")
      case Some(to) =>
        val candidates = legal.filter {
          case NormalMove(from, moveTo, promo) =>
            moveTo == to && promo == promoOpt &&
            situation.board.pieceAt(from).exists(_.pieceType == PieceType.Pawn) &&
            srcFile.forall(_ == from.file.toChar) &&
            (isCapture || from.file == moveTo.file)
          case ep: EnPassantMove =>
            ep.to == to && srcFile.forall(_ == ep.from.file.toChar)
          case _ => false
        }
        candidates match
          case Nil    => Left("No legal pawn move matches '" + san + "'")
          case single :: _ => Right(single)

  private def matchesDisambig(from: Square, disambig: String): Boolean =
    if disambig.isEmpty then true
    else if disambig.length == 1 then
      disambig.head.isLetter && disambig.head == from.file.toChar ||
      disambig.head.isDigit  && disambig.head == from.rank.toChar
    else Square.fromAlgebraic(disambig).contains(from)

  private def charToPieceType(c: Char): Option[PieceType] = c match
    case 'q' => Some(PieceType.Queen)
    case 'r' => Some(PieceType.Rook)
    case 'b' => Some(PieceType.Bishop)
    case 'n' => Some(PieceType.Knight)
    case _   => None
