package org.maichess.mono.uifx

import javafx.animation.{Animation, KeyFrame, Timeline}
import javafx.application.Platform
import javafx.geometry.{Insets, Pos}
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.{Clipboard, ClipboardContent, KeyCode, KeyEvent}
import javafx.scene.layout.*
import javafx.stage.{Modality, Stage, WindowEvent}
import javafx.util.Duration
import java.util.concurrent.atomic.AtomicReference
import org.maichess.mono.bots.{Bot, BotRegistry}
import org.maichess.mono.engine.{DrawReason, GameResult, GameState}
import org.maichess.mono.model.*

object FxUI:

  def start(model: SharedGameModel, stage: Stage): Unit =
    val init  = model.state
    val board = new FxBoard(init.game, ThemeManager.board)
    val side  = new FxSidePanel()

    // ── Initial render ───────────────────────────────────────────────────
    val (cw0, cb0) = recomputeCaptures(init.game)
    side.update(init.game, init.moveHistory, cw0, cb0)
    side.setPlayerNames(init.metadata.white, init.metadata.black)
    side.updateClock(init.clock)

    // ── Thinking indicator ───────────────────────────────────────────────
    val thinkingPieces = Array("\u265e", "\u265d", "\u265c", "\u265b", "\u265a", "\u265f")
    var thinkingIdx    = 0
    val thinkingLabel  = new Label("AI thinking...  \u265e")
    thinkingLabel.setId("thinking-label")
    thinkingLabel.setVisible(false)
    val thinkingAnim   = new Timeline(new KeyFrame(Duration.millis(350), _ => {
      thinkingIdx = (thinkingIdx + 1) % thinkingPieces.length
      thinkingLabel.setText("AI thinking...  " + thinkingPieces(thinkingIdx))
    }))
    thinkingAnim.setCycleCount(Animation.INDEFINITE)

    // ── Pause button ─────────────────────────────────────────────────────
    val pauseBtn = new Button("\u23f8  Pause")
    pauseBtn.setId("pause-btn")
    pauseBtn.getStyleClass.add("toolbar-btn")

    def syncPauseButton(): Unit =
      if model.isPausedState then
        pauseBtn.setText("\u25b6  Resume")
        pauseBtn.getStyleClass.add("paused")
      else
        pauseBtn.setText("\u23f8  Pause")
        pauseBtn.getStyleClass.remove("paused")

    def doPause(): Unit =
      if model.isPausedState then model.resume() else model.pause()
      syncPauseButton()

    // ── Refresh logic ────────────────────────────────────────────────────
    def refresh(s: SharedGameModel.State): Unit =
      s.change match
        case Change.ClockTick =>
          side.updateClock(s.clock)
          handleClockFlag(s)
        case _ =>
          fullRefresh(s)

    def fullRefresh(s: SharedGameModel.State): Unit =
      val turn       = s.game.current.turn
      val wBot       = model.botFor(Color.White)
      val bBot       = model.botFor(Color.Black)
      val shouldFlip = (wBot.isDefined && bBot.isEmpty) ||
                       (wBot.isEmpty && bBot.isEmpty && turn == Color.Black)
      board.setFlipped(shouldFlip)
      board.updateState(s.game)
      val (cw, cb)   = recomputeCaptures(s.game)
      side.update(s.game, s.moveHistory, cw, cb)
      side.setPlayerNames(s.metadata.white, s.metadata.black)
      side.updateClock(s.clock)
      handleClockFlag(s)
      val clockFlagged = s.clock.flatMap(_.flagged).isDefined
      val isOver       = model.gameResult().isDefined || clockFlagged
      val isThinking   = !isOver && model.botFor(turn).isDefined
      model.gameResult().foreach(r => side.showResult(resultMessage(r)))
      board.setBoardEnabled(!isOver && !isThinking)
      if isThinking then
        thinkingLabel.setVisible(true)
        if thinkingAnim.getStatus != Animation.Status.RUNNING then thinkingAnim.play()
      else
        thinkingLabel.setVisible(false)
        thinkingAnim.stop()

    def handleClockFlag(s: SharedGameModel.State): Unit =
      s.clock.flatMap(_.flagged).foreach { color =>
        side.showResult(timeExpiredMessage(color))
        board.setBoardEnabled(false)
        thinkingLabel.setVisible(false)
        thinkingAnim.stop()
      }

    model.addObserver(s => try Platform.runLater(() => refresh(s)) catch case _: Exception => ())

    // ── Move handling ────────────────────────────────────────────────────
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
      val _ = model.applyMove(finalMove, SharedGameModel.moveNotation(finalMove))
    }

    // ── Game actions ─────────────────────────────────────────────────────
    def doNewGame(): Unit =
      NewGameDialog.show(stage).foreach { setup =>
        model.newGame(setup.whiteBot, setup.blackBot, setup.whiteName, setup.blackName, setup.clockConfig)
        syncPauseButton()
      }

    def doResign(): Unit =
      val turn   = model.state.game.current.turn
      val loser  = if turn == Color.White then "White" else "Black"
      val winner = if turn == Color.White then "Black" else "White"
      model.resign()
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

    var sceneRef: Scene = null  // set after scene is created; only called on JavaFX thread

    def doThemeNext(): Unit =
      ThemeManager.next()
      if sceneRef != null then ThemeManager.apply(sceneRef)
      board.updateTheme(ThemeManager.board)

    // ── Toolbar ──────────────────────────────────────────────────────────
    val kbActions: List[(KeyBinding, () => Unit)] = List(
      Keymap.newGame   -> (() => doNewGame()),
      Keymap.resign    -> (() => doResign()),
      Keymap.undo      -> (() => model.undo()),
      Keymap.redo      -> (() => model.redo()),
      Keymap.importFen -> (() => doImportFen()),
      Keymap.exportFen -> (() => doExportFen()),
      Keymap.importPgn -> (() => doImportPgn()),
      Keymap.exportPgn -> (() => doExportPgn()),
      Keymap.pause     -> (() => doPause()),
      Keymap.themeNext -> (() => doThemeNext())
    )

    def toolbarBtn(kb: KeyBinding, action: () => Unit): Button =
      val b = new Button(kb.buttonLabel)
      b.getStyleClass.add("toolbar-btn")
      b.setOnAction(_ => action())
      b

    val fenMenu = new MenuButton("FEN \u25be")
    fenMenu.getStyleClass.add("toolbar-btn")
    val _ = fenMenu.getItems.addAll(
      menuItem("Import FEN (F)", () => doImportFen()),
      menuItem("Export FEN (E)", () => doExportFen())
    )

    val pgnMenu = new MenuButton("PGN \u25be")
    pgnMenu.getStyleClass.add("toolbar-btn")
    val _ = pgnMenu.getItems.addAll(
      menuItem("Import PGN (P)", () => doImportPgn()),
      menuItem("Export PGN (O)", () => doExportPgn())
    )

    pauseBtn.setOnAction(_ => doPause())
    val themeBtn = toolbarBtn(Keymap.themeNext, () => doThemeNext())

    val toolbar = new ToolBar(
      toolbarBtn(Keymap.newGame, () => doNewGame()),
      toolbarBtn(Keymap.resign,  () => doResign()),
      new Separator(),
      toolbarBtn(Keymap.undo, () => model.undo()),
      toolbarBtn(Keymap.redo, () => model.redo()),
      new Separator(),
      pauseBtn,
      new Separator(),
      fenMenu, pgnMenu,
      new Separator(),
      themeBtn
    )

    // ── Layout ───────────────────────────────────────────────────────────
    val boardStack = new StackPane(board.canvas, thinkingLabel)
    StackPane.setAlignment(thinkingLabel, Pos.CENTER)

    val root = new BorderPane()
    root.setTop(toolbar)
    root.setCenter(boardStack)
    root.setRight(side.vbox)
    BorderPane.setMargin(boardStack, new Insets(8))

    // ── Scene & keyboard shortcuts ────────────────────────────────────────
    val scene = new Scene(root)
    sceneRef = scene
    ThemeManager.apply(scene)

    val kbMap: Map[KeyCode, () => Unit] = kbActions.flatMap { (kb, action) =>
      Option(KeyCode.getKeyCode(kb.key.toUpper.toString)).map(_ -> action)
    }.toMap

    scene.addEventFilter(KeyEvent.KEY_PRESSED, (event: KeyEvent) => {
      if !event.isControlDown && !event.isAltDown && !event.isMetaDown then
        val handled = kbMap.get(event.getCode).fold(false) { action => action(); true }
        if handled then event.consume()
    })

    stage.setTitle("MaiChess")
    stage.setMinWidth(830.0)
    stage.setMinHeight(660.0)
    stage.setScene(scene)
    stage.setOnCloseRequest { (_: WindowEvent) =>
      model.shutdown()
      Platform.exit()
    }
    stage.show()

  // ── Helpers ──────────────────────────────────────────────────────────────

  private def menuItem(label: String, action: () => Unit): MenuItem =
    val item = new MenuItem(label)
    item.setOnAction(_ => action())
    item

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
    val buttons = new HBox(8.0, copyBtn, closeBtn)
    val root    = new VBox(8.0, ta, buttons)
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
    val buttons = new HBox(8.0, pasteBtn, okBtn, cancelBtn)
    val root    = new VBox(8.0, ta, buttons)
    root.setPadding(new Insets(10))
    dialog.setScene(new Scene(root, 500, 300))
    dialog.showAndWait()
    result.get()

  private def recomputeCaptures(state: GameState): (List[Piece], List[Piece]) =
    val sits = state.history.reverse :+ state.current
    sits.zip(sits.drop(1)).foldLeft((List.empty[Piece], List.empty[Piece])) {
      case ((cw, cb), (before, after)) =>
        val caps = before.board.pieces.values.filter(_.color != before.turn).toList diff
                   after.board.pieces.values.filter(_.color != before.turn).toList
        if before.turn == Color.White then (cw ++ caps, cb) else (cw, cb ++ caps)
    }

  private def resultMessage(result: GameResult): String = result match
    case GameResult.Checkmate(Color.White)                => "Checkmate \u2014 White wins."
    case GameResult.Checkmate(Color.Black)                => "Checkmate \u2014 Black wins."
    case GameResult.Stalemate                             => "Stalemate \u2014 draw."
    case GameResult.Draw(DrawReason.FiftyMoveRule)        => "Draw: 50-move rule."
    case GameResult.Draw(DrawReason.InsufficientMaterial) => "Draw: insufficient material."
    case GameResult.Draw(DrawReason.ThreefoldRepetition)  => "Draw: threefold repetition."
    case GameResult.Draw(DrawReason.Agreement)            => "Draw by agreement."

  private def timeExpiredMessage(flagged: Color): String =
    if flagged == Color.White then "White's time expired. Black wins."
    else "Black's time expired. White wins."
