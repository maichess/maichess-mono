package chess.ui

import chess.engine.{GameController, GameResult, DrawReason, GameState, IllegalMove}
import chess.model.*
import chess.rules.{Board, Situation, StandardRules}

/** Text-based chess interface. All I/O is isolated to this object. */
object TextUI:

  // ── Board rendering ───────────────────────────────────────────────────────

  /**
   * Renders the board as a Unicode string with rank/file labels.
   *
   * @param board       The board to render.
   * @param perspective The color whose pieces appear at the bottom.
   */
  def renderBoard(board: Board, perspective: Color): String =
    val ranks = if perspective == Color.White then Rank.values.reverse else Rank.values
    val files  = if perspective == Color.White then File.values else File.values.reverse

    val rows = ranks.map: rank =>
      val cells = files.map: file =>
        board.pieceAt(Square(file, rank)).fold("·")(pieceGlyph)
      s"${rank.toInt} ${cells.mkString(" ")}"

    val fileRow = "  " + files.map(_.toChar).mkString(" ")
    (rows :+ fileRow).mkString("\n")

  private def pieceGlyph(piece: Piece): String =
    (piece.color, piece.pieceType) match
      case (Color.White, PieceType.King)   => "♔"
      case (Color.White, PieceType.Queen)  => "♕"
      case (Color.White, PieceType.Rook)   => "♖"
      case (Color.White, PieceType.Bishop) => "♗"
      case (Color.White, PieceType.Knight) => "♘"
      case (Color.White, PieceType.Pawn)   => "♙"
      case (Color.Black, PieceType.King)   => "♚"
      case (Color.Black, PieceType.Queen)  => "♛"
      case (Color.Black, PieceType.Rook)   => "♜"
      case (Color.Black, PieceType.Bishop) => "♝"
      case (Color.Black, PieceType.Knight) => "♞"
      case (Color.Black, PieceType.Pawn)   => "♟"

  // ── Move parsing ──────────────────────────────────────────────────────────

  /**
   * Parses a move in "e2e4" or "e2 e4" format.
   *
   * @return [[Right]] with the (from, to) pair on success,
   *         or [[Left]] with a human-readable error message.
   */
  def parseMove(input: String): Either[String, (Square, Square)] =
    val cleaned = input.trim.filter(_ != ' ')
    if cleaned.length != 4 then
      Left(s"Expected format 'e2e4', got: '$input'")
    else
      for
        from <- Square.fromAlgebraic(cleaned.take(2)).toRight(s"Bad square: ${cleaned.take(2)}")
        to   <- Square.fromAlgebraic(cleaned.drop(2)).toRight(s"Bad square: ${cleaned.drop(2)}")
      yield (from, to)

  /**
   * Finds the legal move matching the (from, to) pair in `situation`.
   * When multiple moves match (promotions), the queen promotion is preferred.
   */
  private def findLegalMove(situation: Situation, from: Square, to: Square): Option[Move] =
    val matches = StandardRules.allLegalMoves(situation).filter(m => m.from == from && m.to == to)
    matches match
      case Nil      => None
      case List(m)  => Some(m)
      case multiple =>
        multiple.find { case NormalMove(_, _, Some(PieceType.Queen)) => true; case _ => false }
          .orElse(multiple.headOption)

  // ── Game loop ─────────────────────────────────────────────────────────────

  /** Entry point: initialises a new game and begins the interactive loop. */
  @main def run(): Unit =
    val controller = GameController(StandardRules)
    println("Chess — enter moves as 'e2e4'. Type Ctrl-D to quit.")
    gameLoop(controller.newGame(), controller)

  @annotation.tailrec
  private def gameLoop(state: GameState, controller: GameController): Unit =
    println()
    println(renderBoard(state.current.board, state.current.turn))
    println(s"${state.current.turn}'s turn")
    if StandardRules.isCheck(state.current) then println("Check!")

    controller.gameResult(state) match
      case Some(result) =>
        println(resultMessage(result))
      case None =>
        val raw = scala.io.StdIn.readLine("Move: ")
        if raw == null then ()
        else
          parseMove(raw) match
            case Left(err) =>
              println(s"Parse error: $err")
              gameLoop(state, controller)
            case Right((from, to)) =>
              findLegalMove(state.current, from, to) match
                case None =>
                  println("Illegal move — try again.")
                  gameLoop(state, controller)
                case Some(move) =>
                  controller.applyMove(state, move) match
                    case Left(err)       =>
                      println(s"Rejected: ${err.reason}")
                      gameLoop(state, controller)
                    case Right(newState) =>
                      gameLoop(newState, controller)

  private def resultMessage(result: GameResult): String = result match
    case GameResult.Checkmate(winner) => s"Checkmate! $winner wins."
    case GameResult.Stalemate         => "Stalemate — draw."
    case GameResult.Draw(reason)      =>
      val why = reason match
        case DrawReason.FiftyMoveRule        => "fifty-move rule"
        case DrawReason.InsufficientMaterial => "insufficient material"
        case DrawReason.ThreefoldRepetition  => "threefold repetition"
        case DrawReason.Agreement            => "agreement"
      s"Draw by $why."
