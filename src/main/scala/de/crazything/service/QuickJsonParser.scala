package de.crazything.service

import play.api.libs.json.{Format, Json, OFormat}

trait QuickJsonParser {

  def jsonString2T[T](str: String)(implicit format: OFormat[T]): T = Json.fromJson[T](Json.parse(str))
    .getOrElse(throw new RuntimeException(s"Could not parse String $str"))

  def t2JsonString[T](t: T)(implicit format: OFormat[T]): String = Json.toJson[T](t).toString()

}
