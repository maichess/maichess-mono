package org.maichess.mono.uifx

import javafx.application.Application
import javafx.stage.Stage

object ChessApp:
  var sharedModel: SharedGameModel = null  // set before Application.launch()

class ChessApp extends Application:
  override def start(stage: Stage): Unit =
    FxUI.start(ChessApp.sharedModel, stage)
