package org.maichess.mono.uifx

import javafx.geometry.{Insets, Pos, VPos}
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.{Image, ImageView}
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

    // ── Player fields ───────────────────────────────────────────────────
    val whiteNameField = new TextField("White")
    val blackNameField = new TextField("Black")
    whiteNameField.setPrefWidth(180.0)
    blackNameField.setPrefWidth(180.0)

    val whiteCombo = buildBotCombo()
    val blackCombo = buildBotCombo()

    whiteCombo.setOnAction { _ =>
      val sel = whiteCombo.getValue
      if sel != null then whiteNameField.setText(sel)
    }
    blackCombo.setOnAction { _ =>
      val sel = blackCombo.getValue
      if sel != null then blackNameField.setText(sel)
    }

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
    minsSpinner.setPrefWidth(80.0)
    secsSpinner.setPrefWidth(80.0)
    incSpinner.setPrefWidth(80.0)

    val customRow = new HBox(8.0,
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

    // ── Player cards ─────────────────────────────────────────────────────
    val whiteCard = buildPlayerCard("\u2654", "WHITE", whiteNameField, whiteCombo, "white-card")
    val blackCard = buildPlayerCard("\u265a", "BLACK", blackNameField, blackCombo, "black-card")

    // ── Header (logo) ────────────────────────────────────────────────────
    val header = buildHeader()

    // ── Assembly ─────────────────────────────────────────────────────────
    val root = new VBox(16.0)
    root.setId("dialog-root")
    val _ = root.getChildren.addAll(
      header,
      new Separator(),
      sectionLabel("PLAYERS"),
      whiteCard,
      blackCard,
      new Separator(),
      sectionLabel("TIME CONTROL"),
      clockCombo,
      customRow,
      new Separator(),
      btnRow
    )
    root.setPadding(new Insets(20.0))

    dialog.setScene(new Scene(root, 460.0, Region.USE_COMPUTED_SIZE))
    dialog.setMinWidth(460.0)
    dialog

  private def buildHeader(): HBox =
    val titleLabel = new Label("New Game")
    titleLabel.getStyleClass.add("dialog-title")
    val header = new HBox(12.0)
    header.setAlignment(Pos.CENTER_LEFT)
    val logoStream = getClass.getResourceAsStream("/images/maiChess_logo.png")
    if logoStream != null then
      val logo = new ImageView(new Image(logoStream))
      logo.setFitHeight(40.0)
      logo.setPreserveRatio(true)
      val _ = header.getChildren.addAll(logo, titleLabel)
    else
      val _ = header.getChildren.add(titleLabel)
    header

  private def buildPlayerCard(
    pieceIcon:  String,
    colorLabel: String,
    nameField:  TextField,
    botCombo:   ComboBox[String],
    styleClass: String
  ): VBox =
    val iconLabel  = new Label(pieceIcon)
    iconLabel.getStyleClass.add("player-card-icon")
    val colorLbl   = new Label(colorLabel)
    colorLbl.getStyleClass.add("player-card-color")
    val headerRow  = new HBox(8.0, iconLabel, colorLbl)
    headerRow.setAlignment(Pos.CENTER_LEFT)

    HBox.setHgrow(nameField, Priority.ALWAYS)
    HBox.setHgrow(botCombo,  Priority.ALWAYS)
    val fieldsRow = new HBox(8.0, nameField, botCombo)
    fieldsRow.setAlignment(Pos.CENTER_LEFT)

    val card = new VBox(8.0, headerRow, fieldsRow)
    card.getStyleClass.addAll("player-card", styleClass)
    card.setPadding(new Insets(10.0, 14.0, 10.0, 14.0))
    card

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
