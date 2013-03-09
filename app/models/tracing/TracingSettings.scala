package models.tracing

import play.api.libs.json._
import play.api.libs.json.Json._
import models.basics.BasicSettings

import TracingSettings._

case class TracingSettings(
  allowedModes: List[String] = List(OXALIS, ARBITRARY), 
  branchPointsAllowed: Boolean = true,
  somaClickingAllowed: Boolean = true)

object TracingSettings {
  val OXALIS = "oxalis"
  val ARBITRARY = "arbitrary"

  val default = TracingSettings()

  implicit val TracingWrites: Writes[TracingSettings] = Json.writes[TracingSettings]
}