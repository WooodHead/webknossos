package com.scalableminds.braingames.binary.helpers

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

import com.scalableminds.util.tools.{Fox, FoxImplicits}
import com.typesafe.scalalogging.LazyLogging
import net.liftweb.common.{Failure, Full}
import play.api.Play.current
import play.api.http.HeaderNames
import play.api.http.Status._
import play.api.libs.json._
import play.api.libs.ws._

import scala.concurrent.ExecutionContext.Implicits.global

class RPCRequest(val id: Int, val url: String) extends FoxImplicits with LazyLogging {

  var request: WSRequest = WS.url(url)

  def withQueryString(parameters: (String, String)*): RPCRequest = {
    request = request.withQueryString(parameters :_*)
    this
  }

  def get: Fox[WSResponse] = {
    request = request
      .withMethod("GET")
    performRequest
  }

  def getWithJsonResponse[T : Reads]: Fox[T] = {
    request = request
      .withMethod("GET")
    parseJsonResponse(performRequest)
  }

  def post(file: File): Fox[WSResponse] = {
    request = request
      .withBody(FileBody(file))
      .withMethod("POST")
    performRequest
  }

  def postWithJsonResponse[T : Reads](file: File): Fox[T] = {
    request = request
      .withBody(FileBody(file))
      .withMethod("POST")
    parseJsonResponse(performRequest)
  }

  def post[T : Writes](body: T = Json.obj()): Fox[WSResponse] = {
    request = request
      .withHeaders(HeaderNames.CONTENT_TYPE -> "application/json")
      .withBody(Json.toJson(body))
      .withMethod("POST")
    performRequest
  }

  def postWithJsonResponse[T : Writes, U : Reads](body: T = Json.obj()): Fox[U] = {
    request = request
      .withHeaders(HeaderNames.CONTENT_TYPE -> "application/json")
      .withBody(Json.toJson(body))
      .withMethod("POST")
    parseJsonResponse(performRequest)
  }

  private def performRequest: Fox[WSResponse] = {
    logger.debug(s"Sending WS request to $url (ID: $id). " +
      s"RequestBody: '${requestBodyPreview}'")
    request.execute().map(Full(_)).recover {
      case e =>
        val errorMsg = s"Error sending WS request to webknossos (ID: $id): " +
          s"${e.getMessage}\n${e.getStackTrace.mkString("\n    ")}"
        logger.error(errorMsg)
        Failure(errorMsg)
    }
  }

  private def parseJsonResponse[T : Reads](r: Fox[WSResponse]): Fox[T] = {
    r.flatMap { response =>
      if (response.status == OK) {
        logger.debug(s"Successful request (ID: $id). " +
          s"Body: '${response.body.take(100)}'")
      } else {
        logger.warn(s"Failed to send WS request to $url (ID: $id). " +
          s"RequestBody: '${requestBodyPreview}'. Status ${response.status}. " +
          s"ResponseBody: '${response.body.take(100)}'")
      }
      Json.parse(response.body).validate[T] match {
        case JsSuccess(value, _) =>
          Full(value)
        case JsError(e) =>
          Failure(s"Request returned invalid JSON (ID: $id): $e")
      }
    }
  }

  private def requestBodyPreview: String = {
    request.body match {
      case body: InMemoryBody => new String(body.bytes.take(100).map(_.toChar))
      case body: FileBody => s"<file: ${body.file.length} bytes>"
      case _ => ""
    }
  }
}

object RPC {

  private val requestCounter: AtomicInteger = new AtomicInteger()

  def apply(url: String): RPCRequest = {
    new RPCRequest(requestCounter.getAndIncrement(), url)
  }
}
