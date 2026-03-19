package org.maichess.mono.ui

import com.googlecode.lanterna.gui2.{Button, LinearLayout, Panel}
import com.googlecode.lanterna.gui2.{Direction => LDirection}

class MenuBar(onNewGame: () => Unit, onResign: () => Unit)
    extends Panel(new LinearLayout(LDirection.HORIZONTAL)):

  private val _ = addComponent(new Button("New Game", new Runnable:
    def run(): Unit = onNewGame()
  ))
  private val _ = addComponent(new Button("Resign", new Runnable:
    def run(): Unit = onResign()
  ))
