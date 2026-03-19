package org.maichess.mono.ui

import com.googlecode.lanterna.gui2.{BasicWindow, Button, LinearLayout, Panel, WindowBasedTextGUI}
import com.googlecode.lanterna.gui2.{Direction => LDirection}
import java.util.concurrent.atomic.AtomicReference
import org.maichess.mono.model.PieceType

object PromotionDialog:

  def show(gui: WindowBasedTextGUI): PieceType =
    val result = new AtomicReference[PieceType](PieceType.Queen)
    val window = new BasicWindow("Promote pawn to:")
    val panel  = new Panel(new LinearLayout(LDirection.HORIZONTAL))

    def makeButton(label: String, pt: PieceType): Button =
      new Button(label, new Runnable:
        def run(): Unit =
          result.set(pt)
          window.close()
      )

    val _ = panel.addComponent(makeButton("Queen",  PieceType.Queen))
    val _ = panel.addComponent(makeButton("Rook",   PieceType.Rook))
    val _ = panel.addComponent(makeButton("Bishop", PieceType.Bishop))
    val _ = panel.addComponent(makeButton("Knight", PieceType.Knight))

    window.setComponent(panel)
    gui.addWindowAndWait(window)
    result.get()
