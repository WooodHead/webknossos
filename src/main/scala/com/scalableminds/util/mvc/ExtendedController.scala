/*
* Copyright (C) 20011-2014 Scalable minds UG (haftungsbeschränkt) & Co. KG. <http://scm.io>
*/
package com.scalableminds.util.mvc

import net.liftweb.common._
import play.api.http.Status._
import play.api.i18n.{I18nSupport, Messages}
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import play.api.http.{HeaderNames, Writeable}
import play.api.http.Status
import play.twirl.api._
import net.liftweb.common.Full
import play.api.mvc.{Request, Result, ResponseHeader}
import play.api.libs.json.JsObject
import com.scalableminds.util.tools.{FoxImplicits, Fox}

trait ResultBox extends I18nSupport{

  def asResult[T <: Result](b: Box[T]): Result = b match {
    case Full(result) =>
      result
    case ParamFailure(msg, _, _, statusCode: Int) =>
      new JsonResult(statusCode)(Messages(msg))
    case Failure(msg, _, _) =>
      new JsonResult(BAD_REQUEST)(Messages(msg))
    case Empty =>
      new JsonResult(NOT_FOUND)("Couldn't find the requested resource.")
  }
}

trait ResultImplicits extends ResultBox with I18nSupport{
  implicit def fox2FutureResult[T <: Result](b: Fox[T])(implicit ec: ExecutionContext): Future[Result] =
    b.futureBox.map(asResult)

  implicit def futureBox2Result[T <: Result](b: Box[Future[T]])(implicit ec: ExecutionContext): Future[Result] = {
    b match {
      case Full(f) =>
        f.map(value => asResult(Full(value)))
      case Empty =>
        Future.successful(asResult(Empty))
      case f: Failure =>
        Future.successful(asResult(f))
    }
  }

  implicit def boxFuture2Result[T <: Result](f: Future[Box[T]])(implicit ec: ExecutionContext): Future[Result] = {
    f.map {
      b =>
        asResult(b)
    }
  }

  implicit def box2Result[T <: Result](b: Box[T]): Result =
    asResult(b)

//  implicit def box2ResultBox[T <: Result](b: Box[T]) = new ResultBox(b)
}

trait BoxImplicits {
  implicit def option2Box[T](in: Option[T]): Box[T] = Box(in)
}

object BoxImplicits extends BoxImplicits

class JsonResult(status: Int) extends Result(header = ResponseHeader(status), body = Enumerator(Array[Byte]())) with JsonResultAttribues {

  val isSuccess = List(OK) contains status

  def createResult(content: JsObject)(implicit writeable: Writeable[JsObject]) =
    Result(
      header = ResponseHeader(status, writeable.contentType.map(ct => Map(HeaderNames.CONTENT_TYPE -> ct)).getOrElse(Map.empty)),
      body = Enumerator(writeable.transform(content)))

  def messageTypeFromStatus =
    if (isSuccess)
      jsonSuccess
    else
      jsonError

  def apply(json: JsObject) =
    createResult(json)

  def apply(json: JsObject, messages: Seq[(String, String)]) =
    createResult(json ++ jsonMessages(messages))

  def apply(messages: Seq[(String, String)]): Result =
    apply(Json.obj(), messages)

  def apply(html: Html, json: JsObject, messages: Seq[(String, String)]): Result =
    apply(json ++ jsonHTMLResult(html), messages)

  def apply(html: Html, json: JsObject, message: String): Result =
    apply(json ++ jsonHTMLResult(html), Seq(messageTypeFromStatus -> message))

  def apply(json: JsObject, message: String): Result =
    apply(json, Seq(messageTypeFromStatus -> message))

  def apply(html: Html, messages: Seq[(String, String)]): Result =
    apply(html, Json.obj(), messages)

  def apply(html: Html, message: String): Result =
    apply(html, Seq(messageTypeFromStatus -> message))

  def apply(html: Html): Result =
    apply(html, Seq.empty)

  def apply(message: String): Result =
    apply(Html(""), message)

  def jsonHTMLResult(html: Html) = {
    val htmlJson = html.body match {
      case "" =>
        Json.obj()
      case body =>
        Json.obj("html" -> body)
    }

    htmlJson
  }

  def jsonMessages(messages: Seq[(String, String)]) =
    Json.obj(
      "messages" -> messages.map(m => Json.obj(m._1 -> m._2)))
}

trait JsonResults extends JsonResultAttribues {
  val JsonOk = new JsonResult(OK)
  val JsonBadRequest = new JsonResult(BAD_REQUEST)
}

trait JsonResultAttribues {
  val jsonSuccess = "success"
  val jsonError = "error"
}

trait PostRequestHelpers {
  def postParameter(parameter: String)(implicit request: Request[Map[String, Seq[String]]]) =
    request.body.get(parameter).flatMap(_.headOption)

  def postParameterList(parameter: String)(implicit request: Request[Map[String, Seq[String]]]) =
    request.body.get(parameter)
}

trait ExtendedController
  extends JsonResults
  with BoxImplicits
  with FoxImplicits
  with ResultImplicits
  with Status
  with WithHighlightableResult
  with PostRequestHelpers
  with WithFilters
  with I18nSupport
