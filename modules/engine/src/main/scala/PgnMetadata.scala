package org.maichess.mono.engine

import java.net.InetAddress
import java.time.LocalDate
import scala.util.Try

/** PGN seven-tag roster metadata.
 *  Preserved across import/export so a round-tripped file keeps its original headers. */
case class PgnMetadata(
  event: String,
  site:  String,
  date:  String,
  round: String,
  white: String,
  black: String
)

object PgnMetadata:

  /** Fallback used when no real metadata is available (e.g. after a raw FEN import). */
  val default: PgnMetadata = PgnMetadata(
    event = "?",
    site  = "?",
    date  = "????.??.??",
    round = "1",
    white = "White",
    black = "Black"
  )

  private def pad2(n: Int): String = if n < 10 then "0" + n.toString else n.toString
  private def pad4(n: Int): String = n.toString.reverse.padTo(4, '0').reverse

  /** Builds metadata from player display names, resolving the current date and local hostname. */
  def fromNames(white: String, black: String): PgnMetadata =
    val today    = LocalDate.now()
    val dateStr  = pad4(today.getYear) + "." + pad2(today.getMonthValue) + "." + pad2(today.getDayOfMonth)
    val hostname = Try(InetAddress.getLocalHost.getHostName).getOrElse("?")
    PgnMetadata(
      event = white + " vs " + black,
      site  = hostname,
      date  = dateStr,
      round = "1",
      white = white,
      black = black
    )
