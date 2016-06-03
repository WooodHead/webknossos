package models.annotation

import models.annotation.AnnotationSettings._
import play.api.data.validation.ValidationError
import play.api.libs.json._

case class AnnotationSettings(
  allowedModes: List[String] = SKELETON_MODES,
  preferredMode: Option[String] = None,
  branchPointsAllowed: Boolean = true,
  somaClickingAllowed: Boolean = true,
  advancedOptionsAllowed: Boolean = true
)

object AnnotationSettings {
  val ORTHOGONAL = "orthogonal"
  val OBLIQUE = "oblique"
  val FLIGHT = "flight"
  val VOLUME = "volume"

  val SKELETON_MODES = List(ORTHOGONAL, OBLIQUE, FLIGHT)
  val VOLUME_MODES = List(VOLUME)
  val ALL_MODES = (SKELETON_MODES ::: VOLUME_MODES).toSet

  val default = AnnotationSettings()
  val skeletonDefault = AnnotationSettings(allowedModes = SKELETON_MODES)
  val volumeDefault = AnnotationSettings(allowedModes = VOLUME_MODES)

  implicit val annotationSettingsWrites = Json.writes[AnnotationSettings]

  implicit val annotationSettingsReads =
    Json
    .reads[AnnotationSettings]
    .filter(ValidationError("annotation.preferedMode.invalid")) { a =>
      a.preferredMode.forall(ALL_MODES.contains) }
    .filter(ValidationError("annotation.mode.invalid")) { a =>
      a.allowedModes.forall(ALL_MODES.contains) }
}
