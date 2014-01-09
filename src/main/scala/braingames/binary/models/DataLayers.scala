package braingames.binary.models

import braingames.geometry.Point3D
import braingames.geometry.Vector3D
import braingames.util.Interpolator
import play.api.libs.json._
import play.api.libs.functional.syntax._
import braingames.binary.models._
import braingames.geometry.BoundingBox
import java.io.File

trait DataLayerLike {
  val sections: List[DataLayerSectionLike]
  val elementClass: String
  val typ: String

  val elementSize = elementClassToSize(elementClass)
  val bytesPerElement = elementSize / 8

  def isCompatibleWith(other: DataLayerLike) =
    this.bytesPerElement == other.bytesPerElement

  def elementClassToSize(elementClass: String): Int = elementClass match {
    case "uint8" => 8
    case "uint16" => 16
    case "uint24" => 24
    case "uint32" => 32
    case "uint64" => 64
    case _ => throw new IllegalArgumentException(s"illegal element class ($elementClass) for DataLayer")
  }
}

case class DataLayer(
  typ: String,
  baseDir: String,
  flags: Option[List[String]],
  elementClass: String = "uint8",
  fallback: Option[String] = None,
  sections: List[DataLayerSection] = Nil) extends DataLayerLike {

  def relativeBaseDir(binaryBase: String) = baseDir.replace(binaryBase, "")

  val interpolator = DataLayer.interpolationFromString(typ)

  val resolutions = sections.flatMap(_.resolutions).distinct

  val maxCoordinates = BoundingBox.hull(sections.map(_.bboxBig))
}


case class DataLayerType(name: String, interpolation: Interpolation, defaultElementClass: String = "uint8")

object DataLayer extends Function6[String, String, Option[List[String]], String, Option[String], List[DataLayerSection], DataLayer]{
  val COLOR =
    DataLayerType("color", TrilerpInterpolation)
  val SEGMENTATION =
    DataLayerType("segmentation", NearestNeighborInterpolation)
  val CLASSIFICATION =
    DataLayerType("classification", NearestNeighborInterpolation)

  implicit val dataLayerFormat = Json.format[DataLayer]

  val supportedLayers = List(
    COLOR, SEGMENTATION, CLASSIFICATION
  )

  val defaultInterpolation = NearestNeighborInterpolation

  def interpolationFromString(layerType: String) = {
    supportedLayers
      .find(_.name == layerType)
      .map(_.interpolation)
      .getOrElse {
      System.err.println(s"Invalid interpolation string: $layerType. Using default interpolation")
      defaultInterpolation
    }
  }
}
