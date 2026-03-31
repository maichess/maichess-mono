package org.maichess.mono.uifx

import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.{Alert, Button, ButtonType, TextArea, ToolBar}
import javafx.scene.input.{Clipboard, ClipboardContent, KeyCode, KeyEvent}
import javafx.scene.layout.{BorderPane, HBox, VBox}
import javafx.stage.{Modality, Stage, WindowEvent}
import java.util.concurrent.atomic.AtomicReference
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

    val (cw0, cb0) = recomputeCaptures(init.game)
    side.update(init.game, init.moveHistory, cw0, cb0)

    model.addObserver { s =>
      try Platform.runLater(() => refresh(s))
      catch case _: Exception => ()
    }

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

    def doResign(): Unit =
      val turn   = model.state.game.current.turn
      val loser  = if turn == Color.White then "White" else "Black"
      val winner = if turn == Color.White then "Black" else "White"
      side.showResult(loser + " resigned. " + winner + " wins.")
      board.setBoardEnabled(false)

    def doImportFen(): Unit =
      showImportDialog("Import FEN", stage).foreach { input =>
        model.importFen(input.trim) match
          case Left(err) => showExportDialog("FEN Error", err, stage)
          case Right(()) => ()
      }

    def doExportFen(): Unit = showExportDialog("Export FEN", model.exportFen(), stage)

    def doImportPgn(): Unit =
      showImportDialog("Import PGN", stage).foreach { input =>
        model.importPgn(input.trim) match
          case Left(err) => showExportDialog("PGN Error", err, stage)
          case Right(()) => ()
      }

    def doExportPgn(): Unit = showExportDialog("Export PGN", model.exportPgn(), stage)

    def doNewGame(): Unit =
      val alert = new Alert(Alert.AlertType.CONFIRMATION)
      alert.setTitle("New Game")
      alert.setHeaderText("Choose game mode")
      alert.initOwner(stage)
      val hvh = new ButtonType("Human vs Human")
      val hva = new ButtonType("Human vs AI")
      alert.getButtonTypes.setAll(hvh, hva)
      alert.showAndWait().ifPresent { choice =>
        val mode = if choice == hva then PlayerMode.HumanVsAi else PlayerMode.HumanVsHuman
        model.newGameWithMode(mode)
      }

    val actions: List[(KeyBinding, () => Unit)] = List(
      (Keymap.newGame,   () => doNewGame()),
      (Keymap.undo,      () => model.undo()),
      (Keymap.redo,      () => model.redo()),
      (Keymap.importFen, () => doImportFen()),
      (Keymap.exportFen, () => doExportFen()),
      (Keymap.importPgn, () => doImportPgn()),
      (Keymap.exportPgn, () => doExportPgn()),
      (Keymap.resign,    () => doResign())
    )

    val toolbarButtons = actions.map { (kb, action) =>
      val b = new Button(kb.buttonLabel)
      b.setOnAction(_ => action())
      b
    }
    val toolbar = new ToolBar(toolbarButtons: _*)

    val root = new BorderPane()
    root.setTop(toolbar)
    root.setCenter(board.canvas)
    root.setRight(side.vbox)
    BorderPane.setMargin(board.canvas, new Insets(8))

    val kbMap: Map[KeyCode, () => Unit] = actions.flatMap { (kb, action) =>
      Option(KeyCode.getKeyCode(kb.key.toUpper.toString)).map(_ -> action)
    }.toMap

    val scene = new Scene(root)
    scene.addEventFilter(KeyEvent.KEY_PRESSED, (event: KeyEvent) => {
      if !event.isControlDown && !event.isAltDown && !event.isMetaDown then
        kbMap.get(event.getCode).foreach { action =>
          action()
          event.consume()
        }
    })

    stage.setTitle("MaiChess")
    stage.setScene(scene)
    stage.setOnCloseRequest { (_: WindowEvent) =>
      model.shutdown()
      Platform.exit()
    }
    stage.show()

  // ── helpers ────────────────────────────────────────────────────────────────

  private def showExportDialog(title: String, content: String, owner: Stage): Unit =
    val dialog = new Stage()
    dialog.initOwner(owner)
    dialog.initModality(Modality.WINDOW_MODAL)
    dialog.setTitle(title)
    val ta = new TextArea(content)
    ta.setEditable(false)
    ta.setWrapText(true)
    val copyBtn = new Button("Copy to Clipboard")
    copyBtn.setOnAction { _ =>
      val cc = new ClipboardContent()
      cc.putString(content)
      val _ = Clipboard.getSystemClipboard.setContent(cc)
    }
    val closeBtn = new Button("Close")
    closeBtn.setOnAction(_ => dialog.close())
    val buttons = new HBox(8.0)
    val _ = buttons.getChildren.addAll(copyBtn, closeBtn)
    val root = new VBox(8.0)
    val _ = root.getChildren.addAll(ta, buttons)
    root.setPadding(new Insets(10))
    dialog.setScene(new Scene(root, 500, 300))
    dialog.showAndWait()

  private def showImportDialog(title: String, owner: Stage): Option[String] =
    val result = new AtomicReference[Option[String]](None)
    val dialog = new Stage()
    dialog.initOwner(owner)
    dialog.initModality(Modality.WINDOW_MODAL)
    dialog.setTitle(title)
    val ta = new TextArea()
    ta.setWrapText(true)
    val pasteBtn = new Button("Paste from Clipboard")
    pasteBtn.setOnAction { _ =>
      Option(Clipboard.getSystemClipboard.getString).foreach(ta.setText)
    }
    val okBtn = new Button("OK")
    okBtn.setOnAction { _ =>
      result.set(Some(ta.getText))
      dialog.close()
    }
    val cancelBtn = new Button("Cancel")
    cancelBtn.setOnAction(_ => dialog.close())
    val buttons = new HBox(8.0)
    val _ = buttons.getChildren.addAll(pasteBtn, okBtn, cancelBtn)
    val root = new VBox(8.0)
    val _ = root.getChildren.addAll(ta, buttons)
    root.setPadding(new Insets(10))
    dialog.setScene(new Scene(root, 500, 300))
    dialog.showAndWait()
    result.get()

  private def capturedIn(before: Board, after: Board, movingColor: Color): List[Piece] =
    val opponentBefore = before.pieces.values.filter(_.color != movingColor).toList
    val opponentAfter  = after.pieces.values.filter(_.color != movingColor).toList
    opponentBefore diff opponentAfter

  private def moveNotation(move: Move): String = move match
    case NormalMove(from, to, None)         => from.toAlgebraic + "-" + to.toAlgebraic
    case NormalMove(from, to, Some(pt))     => from.toAlgebraic + "-" + to.toAlgebraic + "=" + promoLetter(pt)
    case CastlingMove(from, _, rookFrom, _) =>
      if rookFrom.file.toInt > from.file.toInt then "O-O" else "O-O-O"
    case EnPassantMove(from, to, _)         => from.toAlgebraic + "-" + to.toAlgebraic

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
