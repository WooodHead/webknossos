package controllers

import play.api._
import play.api.mvc.{SimpleResult, WebSocket, AsyncResult}
import play.api.Play.current
import play.api.libs.iteratee._
import Input.EOF
import play.api.libs.concurrent._
import play.api.libs.json.JsValue
import play.libs.Akka._
import _root_.models.security.{RoleDAO, Role}
import models.binary._
import oxalis.security.{UserAwareRequest, AuthenticatedRequest, Secured}
import scala.concurrent.Future
import braingames.geometry.Point3D
import akka.pattern.AskTimeoutException
import play.api.libs.iteratee.Concurrent.Channel
import scala.collection.mutable.ArrayBuffer
import akka.routing.RoundRobinRouter
import scala.concurrent.Future
import play.api.i18n.Messages
import braingames.image._
import java.awt.image.BufferedImage
import braingames.image.JPEGWriter
import braingames.binary.models._
import braingames.binary._
import oxalis.binary.BinaryDataService
import net.liftweb.common._
import braingames.util.ExtendedTypes.ExtendedFutureBox
import braingames.util.ExtendedTypes.ExtendedArraySeq
import braingames.binary.ParsedRequest
import oxalis.security.AuthenticatedRequest
import braingames.binary.models.DataLayerId
import scala.Some
import braingames.binary.DataRequestSettings
import braingames.image.ImageCreatorParameters
import braingames.binary.ParsedRequestCollection
import braingames.reactivemongo.DBAccessContext
import braingames.util.Fox

object BinaryData extends Controller with Secured {
  override val DefaultAccessRole = RoleDAO.User

  val conf = Play.configuration

  implicit val dispatcher = Akka.system.dispatcher
  val scaleFactors = Array(1, 1, 1)

  def createDataRequestCollection(dataSet: DataSet, dataLayerName: String, cubeSize: Int, parsedRequest: ParsedRequestCollection) = {
    val dataLayerId = DataLayerId(dataLayerName)
    val dataRequests = parsedRequest.requests.map(r =>
      BinaryDataService.createDataRequest(dataSet, dataLayerId, cubeSize, r))
    DataRequestCollection(dataRequests)
  }


  def requestData(
                   dataSetName: String,
                   dataLayerName: String,
                   cubeSize: Int,
                   parsedRequest: ParsedRequestCollection
                 )(implicit ctx: DBAccessContext): Fox[Array[Byte]] = {
    for {
      dataSet <- DataSetDAO.findOneByName(dataSetName) ?~> Messages("dataSet.notFound")
      dataRequestCollection = createDataRequestCollection(dataSet, dataLayerName, cubeSize, parsedRequest)
      data <- BinaryDataService.handleDataRequest(dataRequestCollection) ?~> "Data request couldn't get handled"
    } yield {
      data
    }
  }

  def requestData(
                   dataSetName: String,
                   dataLayerName: String,
                   position: Point3D,
                   width: Int,
                   height: Int,
                   depth: Int,
                   resolutionExponent: Int,
                   settings: DataRequestSettings
                 )(implicit ctx: DBAccessContext): Fox[Array[Byte]] = {
    for {
      dataSet <- DataSetDAO.findOneByName(dataSetName) ?~> Messages("dataSet.notFound")
      dataRequestCollection = BinaryDataService.createDataRequest(
        dataSet,
        DataLayerId(dataLayerName),
        width,
        height,
        depth,
        position,
        resolutionExponent,
        settings)
      data <- BinaryDataService.handleDataRequest(dataRequestCollection) ?~> "Data request couldn't get handled"
    } yield {
      data
    }
  }

  def requestViaAjaxDebug(dataSetName: String, dataLayerName: String, cubeSize: Int, x: Int, y: Int, z: Int, resolution: Int) = Authenticated().async {
    implicit request =>
      val dataRequests = ParsedRequestCollection(Array(ParsedRequest(resolution, Point3D(x, y, z), false)))
      for {
        data <- requestData(dataSetName, dataLayerName, cubeSize, dataRequests)
      } yield {
        Ok(data)
      }
  }

  /**
   * Handles a request for binary data via a HTTP POST. The content of the
   * POST body is specified in the BinaryProtokoll.parseAjax functions.
   */
  def requestViaAjax(dataSetName: String, dataLayerName: String, cubeSize: Int) = UserAwareAction.async(parse.raw) {
    implicit request =>
      for {
        payload <- request.body.asBytes() ?~> Messages("binary.payload.notSupplied")
        requests <- BinaryProtocol.parse(payload, containsHandle = false) ?~> Messages("binary.payload.invalid")
        data <- requestData(dataSetName, dataLayerName, cubeSize, requests) ?~> Messages("binary.data.notFound")
      } yield {
        Ok(data)
      }
  }

  def respondWithSpriteSheet(dataSetName: String, dataLayerName: String, width: Int, height: Int, depth: Int, imagesPerRow: Int, x: Int, y: Int, z: Int, resolution: Int)(implicit request: UserAwareRequest[_]): Future[SimpleResult] = {
    val settings = DataRequestSettings(useHalfByte = false, skipInterpolation = false)
    for {
      dataSet <- DataSetDAO.findOneByName(dataSetName) ?~> Messages("dataSet.notFound")
      dataLayer <- dataSet.dataLayer(dataLayerName) ?~> Messages("dataLayer.notFound")
      params = ImageCreatorParameters(dataLayer.bytesPerElement, width, height, imagesPerRow)
      data <- requestData(dataSetName, dataLayerName, Point3D(x, y, z), width, height, depth, resolution, settings) ?~> Messages("binary.data.notFound")
      spriteSheet <- ImageCreator.spriteSheetFor(data, params) ?~> Messages("image.create.failed")
      firstSheet <- spriteSheet.pages.headOption ?~> "Couldn'T create spritesheet"
    } yield {
      val file = new JPEGWriter().writeToFile(firstSheet.image)
      Ok.sendFile(file, true, _ => "test.jpg").withHeaders(
        CONTENT_TYPE -> "image/jpeg")
    }
  }

  def respondWithImage(dataSetName: String, dataLayerName: String, width: Int, height: Int, x: Int, y: Int, z: Int, resolution: Int)(implicit request: UserAwareRequest[_]) = {
    respondWithSpriteSheet(dataSetName, dataLayerName, width, height, 1, 1, x, y, z, resolution)
  }

  def requestSpriteSheet(dataSetName: String, dataLayerName: String, cubeSize: Int, imagesPerRow: Int, x: Int, y: Int, z: Int, resolution: Int) = UserAwareAction.async(parse.raw) {
    implicit request =>
      respondWithSpriteSheet(dataSetName, dataLayerName, cubeSize, cubeSize, cubeSize, imagesPerRow, x, y, z, resolution)
  }

  def requestImage(dataSetName: String, dataLayerName: String, width: Int, height: Int, x: Int, y: Int, z: Int, resolution: Int) = UserAwareAction.async(parse.raw) {
    implicit request =>
      respondWithImage(dataSetName, dataLayerName, width, height, x, y, z, resolution)
  }
}