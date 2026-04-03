package org.maichess.mono.uifx

import javafx.geometry.{HPos, Insets, Pos, VPos}
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.stage.{Modality, Stage}
import java.util.concurrent.atomic.AtomicReference
import org.maichess.mono.bots.{Bot, BotRegistry}

/** A single, combined dialog for new-game setup: player names, bot selection, and time control. */
object NewGameDialog:

  private case class Preset(label: String, ms: Long, incMs: Long)

  private val presets: List[Preset] = List(
    Preset("Bullet 1+0",      60_000L,    0L),
    Preset("Bullet 2+1",     120_000L,    1_000L),
    Preset("Blitz 3+2",      180_000L,    2_000L),
    Preset("Blitz 5+0",      300_000L,    0L),
    Preset("Blitz 5+3",      300_000L,    3_000L),
    Preset("Rapid 10+0",     600_000L,    0L),
    Preset("Rapid 15+10",    900_000L,   10_000L),
    Preset("Classical 30+0", 1_800_000L,  0L),
    Preset("Classical 60+0", 3_600_000L,  0L)
  )

  private val presetLabels: List[String] =
    "Unlimited" :: presets.map(_.label) ::: List("Custom...")

  def show(owner: Stage): Option[NewGameSetup] =
    val result = new AtomicReference[Option[NewGameSetup]](None)
    val dialog = buildDialog(owner, result)
    dialog.showAndWait()
    result.get()

  private def buildDialog(owner: Stage, result: AtomicReference[Option[NewGameSetup]]): Stage =
    val dialog = new Stage()
    dialog.initOwner(owner)
    dialog.initModality(Modality.WINDOW_MODAL)
    dialog.setTitle("New Game")
    dialog.setResizable(false)

    // ── Player section ──────────────────────────────────────────────────
    val playerGrid = new GridPane()
    playerGrid.setHgap(10.0)
    playerGrid.setVgap(8.0)

    val whiteNameField = new TextField("White")
    val blackNameField = new TextField("Black")
    whiteNameField.setPrefWidth(160.0)
    blackNameField.setPrefWidth(160.0)

    val whiteCombo = buildBotCombo()
    val blackCombo = buildBotCombo()

    // Auto-fill name when bot is selected
    whiteCombo.setOnAction { _ =>
      val sel = whiteCombo.getValue
      if sel != null then whiteNameField.setText(sel)
    }
    blackCombo.setOnAction { _ =>
      val sel = blackCombo.getValue
      if sel != null then blackNameField.setText(sel)
    }

    addRow(playerGrid, 0, sectionLabel("WHITE PLAYER"), whiteNameField, whiteCombo)
    addRow(playerGrid, 1, sectionLabel("BLACK PLAYER"), blackNameField, blackCombo)

    // ── Time-control section ────────────────────────────────────────────
    val clockCombo = new ComboBox[String]()
    presetLabels.foreach(clockCombo.getItems.add)
    clockCombo.getSelectionModel.selectFirst()
    clockCombo.setMaxWidth(Double.MaxValue)

    val minsSpinner = new Spinner[Integer](0, 180, 5)
    val secsSpinner = new Spinner[Integer](0,  59, 0)
    val incSpinner  = new Spinner[Integer](0,  60, 0)
    minsSpinner.setEditable(true)
    secsSpinner.setEditable(true)
    incSpinner.setEditable(true)
    minsSpinner.setPrefWidth(70.0)
    secsSpinner.setPrefWidth(70.0)
    incSpinner.setPrefWidth(70.0)

    val customRow = new HBox(6.0,
      fieldLabel("Min"), minsSpinner,
      fieldLabel("Sec"), secsSpinner,
      fieldLabel("+Inc"), incSpinner
    )
    customRow.setAlignment(Pos.CENTER_LEFT)
    customRow.setVisible(false)
    customRow.setManaged(false)

    clockCombo.setOnAction { _ =>
      val isCustom = clockCombo.getValue == "Custom..."
      customRow.setVisible(isCustom)
      customRow.setManaged(isCustom)
      if !isCustom then dialog.sizeToScene()
    }

    // ── Buttons ─────────────────────────────────────────────────────────
    val cancelBtn = new Button("Cancel")
    val startBtn  = new Button("Start Game")
    cancelBtn.getStyleClass.add("secondary-button")
    startBtn.getStyleClass.add("primary-button")
    startBtn.setDefaultButton(true)

    cancelBtn.setOnAction(_ => dialog.close())
    startBtn.setOnAction { _ =>
      result.set(Some(buildSetup(
        whiteNameField, blackNameField, whiteCombo, blackCombo,
        clockCombo, minsSpinner, secsSpinner, incSpinner
      )))
      dialog.close()
    }

    val btnRow = new HBox(8.0, cancelBtn, startBtn)
    btnRow.setAlignment(Pos.CENTER_RIGHT)

    // ── Assembly ────────────────────────────────────────────────────────
    val root = new VBox(14.0)
    root.setId("dialog-root")
    val title = new Label("New Game")
    title.getStyleClass.add("dialog-title")
    val _ = root.getChildren.addAll(
      title,
      sectionLabel("PLAYERS"),
      playerGrid,
      new Separator(),
      sectionLabel("TIME CONTROL"),
      clockCombo,
      customRow,
      new Separator(),
      btnRow
    )
    root.setPadding(new Insets(20.0))

    dialog.setScene(new Scene(root, 440.0, Region.USE_COMPUTED_SIZE))
    dialog.setMinWidth(440.0)
    dialog

  private def buildBotCombo(): ComboBox[String] =
    val combo = new ComboBox[String]()
    combo.getItems.add("Human")
    BotRegistry.all.foreach(b => combo.getItems.add(b.name))
    combo.getSelectionModel.selectFirst()
    combo.setMaxWidth(Double.MaxValue)
    combo

  private def buildSetup(
    wName:    TextField,
    bName:    TextField,
    wCombo:   ComboBox[String],
    bCombo:   ComboBox[String],
    clkCombo: ComboBox[String],
    mins:     Spinner[Integer],
    secs:     Spinner[Integer],
    inc:      Spinner[Integer]
  ): NewGameSetup =
    val whiteName  = wName.getText.trim.pipe(s => if s.isEmpty then "White" else s)
    val blackName  = bName.getText.trim.pipe(s => if s.isEmpty then "Black" else s)
    val whiteBot   = BotRegistry.all.find(_.name == wCombo.getValue)
    val blackBot   = BotRegistry.all.find(_.name == bCombo.getValue)
    val clockCfg   = buildClock(clkCombo.getValue, mins, secs, inc)
    NewGameSetup(whiteName, blackName, whiteBot, blackBot, clockCfg)

  private def buildClock(
    presetLabel: String,
    mins: Spinner[Integer],
    secs: Spinner[Integer],
    inc:  Spinner[Integer]
  ): Option[ClockConfig] =
    presetLabel match
      case "Unlimited" => None
      case "Custom..." =>
        val ms    = (mins.getValue.toLong * 60L + secs.getValue.toLong) * 1000L
        val incMs = inc.getValue.toLong * 1000L
        if ms <= 0L then None else Some(ClockConfig(ms, incMs))
      case label =>
        presets.find(_.label == label).map(p => ClockConfig(p.ms, p.incMs))

  private def addRow(grid: GridPane, row: Int, label: Label, field: TextField, combo: ComboBox[String]): Unit =
    val _ = grid.add(label, 0, row)
    val _ = grid.add(field, 1, row)
    val _ = grid.add(combo, 2, row)
    GridPane.setValignment(label, VPos.CENTER)

  private def sectionLabel(text: String): Label =
    val l = new Label(text)
    l.getStyleClass.add("section-label")
    l

  private def fieldLabel(text: String): Label =
    val l = new Label(text)
    l.getStyleClass.add("dialog-field-label")
    l

  extension [A](a: A)
    private def pipe[B](f: A => B): B = f(a)
