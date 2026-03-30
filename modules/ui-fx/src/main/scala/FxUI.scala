package org.maichess.mono.uifx

import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.{Alert, Button, ButtonType, ToolBar}
import javafx.scene.layout.BorderPane
import javafx.stage.{Stage, WindowEvent}
import org.maichess.mono.engine.{DrawReason, GameResult, GameState}
import org.maichess.mono.model.*

object FxUI:

  def start(model: SharedGameModel, stage: Stage): Unit =
    val init  = model.state
    val board = new FxBoard(init.game)
    val side  = new FxSidePanel()

    def recomputeCaptures(state: GameState): (List[Piece], List[Piece]) =
      val sits = state.history.reverse :+ state.current
      sits.zip(sits.drop(1)).foldLeft((List.empty[Piece], List.empty[Piece])) {
        case ((cw, cb), (before, after)) =>
          val caps = capturedIn(before.board, after.board, before.turn)
          if before.turn == Color.White then (cw ++ caps, cb) else (cw, cb ++ caps)
      }

    def refresh(s: SharedGameModel.State): Unit =
      board.updateState(s.game)
      val (cw, cb) = recomputeCaptures(s.game)
      side.update(s.game, s.moveHistory, cw, cb)
      model.gameResult().foreach { result =>
        side.showResult(resultMessage(result))
        board.setBoardEnabled(false)
      }

    // Initial render
    val (cw0, cb0) = recomputeCaptures(init.game)
    side.update(init.game, init.moveHistory, cw0, cb0)

    // Observer: called from any thread after model changes
    model.addObserver { s =>
      try Platform.runLater(() => refresh(s))
      catch case _: Exception => ()
    }

    // Pawn promotion dialog (runs on JAT since called from canvas click handler)
    def askPromotion(): PieceType =
      val alert = new Alert(Alert.AlertType.CONFIRMATION)
      alert.setTitle("Pawn Promotion")
      alert.setHeaderText("Choose promotion piece")
      alert.initOwner(stage)
      val queen  = new ButtonType("Queen")
      val rook   = new ButtonType("Rook")
      val bishop = new ButtonType("Bishop")
      val knight = new ButtonType("Knight")
      alert.getButtonTypes.setAll(queen, rook, bishop, knight)
      alert.showAndWait().map[PieceType] {
        case t if t == rook   => PieceType.Rook
        case t if t == bishop => PieceType.Bishop
        case t if t == knight => PieceType.Knight
        case _                => PieceType.Queen
      }.orElse(PieceType.Queen)

    board.onMove = { move =>
      val finalMove = move match
        case nm: NormalMove if nm.promotion.isDefined =>
          nm.copy(promotion = Some(askPromotion()))
        case _ => move
      model.applyMove(finalMove, moveNotation(finalMove))
      ()
    }

    def btn(label: String)(action: => Unit): Button =
      val b = new Button(label)
      b.setOnAction(_ => action)
      b

    val toolbar = new ToolBar(
      btn("New")    { model.newGame() },
      btn("Undo")   { model.undo() },
      btn("Redo")   { model.redo() },
      btn("Resign") {
        val turn   = model.state.game.current.turn
        val loser  = if turn == Color.White then "White" else "Black"
        val winner = if turn == Color.White then "Black" else "White"
        side.showResult(loser + " resigned. " + winner + " wins.")
        board.setBoardEnabled(false)
      }
    )

    val root = new BorderPane()
    root.setTop(toolbar)
    root.setCenter(board.canvas)
    root.setRight(side.vbox)
    BorderPane.setMargin(board.canvas, new Insets(8))

    stage.setTitle("MaiChess")
    stage.setScene(new Scene(root))
    stage.setOnCloseRequest { (_: WindowEvent) =>
      model.shutdown()
      Platform.exit()
    }
    stage.show()

  // ── helpers ────────────────────────────────────────────────────────────────

  private def capturedIn(before: Board, after: Board, movingColor: Color): List[Piece] =
    val opponentBefore = before.pieces.values.filter(_.color != movingColor).toList
    val opponentAfter  = after.pieces.values.filter(_.color != movingColor).toList
    opponentBefore diff opponentAfter

  private def moveNotation(move: Move): String = move match
    case NormalMove(from, to, None)     => from.toAlgebraic + "-" + to.toAlgebraic
    case NormalMove(from, to, Some(pt)) => from.toAlgebraic + "-" + to.toAlgebraic + "=" + promoLetter(pt)
    case CastlingMove(from, _, rookFrom, _) =>
      if rookFrom.file.toInt > from.file.toInt then "O-O" else "O-O-O"
    case EnPassantMove(from, to, _) => from.toAlgebraic + "-" + to.toAlgebraic

  private def promoLetter(pt: PieceType): String = pt match
    case PieceType.Queen  => "Q"
    case PieceType.Rook   => "R"
    case PieceType.Bishop => "B"
    case PieceType.Knight => "N"
    case PieceType.King   => "K"
    case PieceType.Pawn   => "P"

  private def resultMessage(result: GameResult): String = result match
    case GameResult.Checkmate(Color.White)                => "Checkmate \u2014 White wins."
    case GameResult.Checkmate(Color.Black)                => "Checkmate \u2014 Black wins."
    case GameResult.Stalemate                             => "Stalemate \u2014 draw."
    case GameResult.Draw(DrawReason.FiftyMoveRule)        => "Draw: 50-move rule."
    case GameResult.Draw(DrawReason.InsufficientMaterial) => "Draw: insufficient material."
    case GameResult.Draw(DrawReason.ThreefoldRepetition)  => "Draw: threefold repetition."
    case GameResult.Draw(DrawReason.Agreement)            => "Draw by agreement."
