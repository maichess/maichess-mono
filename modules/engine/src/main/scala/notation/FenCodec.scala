package org.maichess.mono.engine

import org.maichess.mono.rules.Situation

trait FenCodec:
  def encode(situation: Situation): String
  def decode(fen: String): Either[String, Situation]
