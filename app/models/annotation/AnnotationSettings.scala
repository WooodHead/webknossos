package models.annotation

import play.api.libs.json._
import play.api.libs.json.Json._
import models.basics.BasicSettings

import AnnotationSettings._

case class AnnotationSettings(allowedModes: List[String] = ALL_MODES,
                              branchPointsAllowed: Boolean = true,
                              somaClickingAllowed: Boolean = true
                             )

object AnnotationSettings {
  val OXALIS = "oxalis"
  val ARBITRARY = "arbitrary"

  val ALL_MODES = List(OXALIS, ARBITRARY)

  val default = AnnotationSettings()

  implicit val annotationSettingsFormat = Json.format[AnnotationSettings]
}