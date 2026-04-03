package org.maichess.mono.uifx

import javafx.scene.Scene
import javafx.scene.paint.Color as JColor

/** Colors used to paint the board canvas — these cannot be set via CSS since Canvas uses the graphics API. */
case class BoardTheme(
  light:        JColor,
  dark:         JColor,
  selected:     JColor,
  legal:        JColor,
  labelOnLight: JColor,
  labelOnDark:  JColor
)

private case class CssTheme(name: String, cssPath: String, board: BoardTheme)

/** Manages the three built-in visual themes.
 *  Call next() to cycle, apply(scene) to swap stylesheets, board to get canvas colors. */
object ThemeManager:

  private val themes: List[CssTheme] = List(
    CssTheme("Dark", "/themes/dark.css", BoardTheme(
      light        = JColor.rgb(108, 110, 130),
      dark         = JColor.rgb(60,  62,  80),
      selected     = JColor.rgb(130, 151, 105),
      legal        = JColor.rgb(100, 130, 180),
      labelOnLight = JColor.rgb(60,  62,  80),
      labelOnDark  = JColor.rgb(108, 110, 130)
    )),
    CssTheme("Light", "/themes/light.css", BoardTheme(
      light        = JColor.rgb(240, 217, 181),
      dark         = JColor.rgb(181, 136, 99),
      selected     = JColor.rgb(130, 151, 105),
      legal        = JColor.rgb(100, 130, 180),
      labelOnLight = JColor.rgb(181, 136, 99),
      labelOnDark  = JColor.rgb(240, 217, 181)
    )),
    CssTheme("Wood", "/themes/wood.css", BoardTheme(
      light        = JColor.rgb(210, 180, 140),
      dark         = JColor.rgb(139, 90,  43),
      selected     = JColor.rgb(160, 130, 80),
      legal        = JColor.rgb(100, 160, 80),
      labelOnLight = JColor.rgb(139, 90,  43),
      labelOnDark  = JColor.rgb(210, 180, 140)
    ))
  )

  private var activeIdx = 0
  private var listeners: List[() => Unit] = Nil

  def activeName: String   = themes(activeIdx).name
  def board: BoardTheme    = themes(activeIdx).board

  def next(): Unit =
    activeIdx = (activeIdx + 1) % themes.length
    listeners.foreach(_())

  /** Replaces the scene's stylesheet with the active theme CSS. */
  def apply(scene: Scene): Unit =
    val url = Option(getClass.getResource(themes(activeIdx).cssPath))
    url.foreach(u => scene.getStylesheets.setAll(u.toExternalForm))

  def addChangeListener(f: () => Unit): Unit =
    listeners = f :: listeners
