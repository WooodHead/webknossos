package models.annotation

import models.user.User
import models.task.Task
import models.annotation.AnnotationType._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import braingames.reactivemongo.DBAccessContext
import braingames.util.{FoxImplicits, Fox}
import reactivemongo.bson.BSONObjectID
import play.api.Logger
import models.tracing.skeleton.AnnotationStatistics
import oxalis.view.{ResourceActionCollection, ResourceAction}

/**
 * Company: scalableminds
 * User: tmbo
 * Date: 01.06.13
 * Time: 15:06
 */
trait AnnotationLike extends AnnotationStatistics{
  def _name: Option[String]

  def user: Future[Option[User]]

  def team: String

  def muta: AnnotationMutationsLike

  def content: Fox[AnnotationContent]

  def _user: BSONObjectID

  def id: String

  def typ: AnnotationType

  def task: Fox[Task]

  def state: AnnotationState

  def restrictions: AnnotationRestrictions

  def review: List[AnnotationReview] = Nil

  def version: Int

 // def incrementVersion: AnnotationLike

  def dataSetName = content.map(_.dataSetName) getOrElse ""


  def isTrainingsAnnotation =
    typ == AnnotationType.Training

  def annotationInfo(user: Option[User])(implicit ctx: DBAccessContext): Fox[JsObject] =
    AnnotationLike.annotationLikeInfoWrites(this, user)

  def actions(user: Option[User]): ResourceActionCollection
}

object AnnotationLike extends FoxImplicits {

  def annotationLikeInfoWrites(a: AnnotationLike, user: Option[User])(implicit ctx: DBAccessContext): Fox[JsObject] = {
    for {
      contentJs <- a.content.flatMap(AnnotationContent.writeAsJson(_))
      restrictionsJs <- AnnotationRestrictions.writeAsJson(a.restrictions, user).toFox
    } yield {
      val name = a._name.getOrElse("")
      Json.obj(
        "version" -> a.version,
        "id" -> a.id,
        "name" -> name,
        "typ" -> a.typ,
        "content" -> contentJs,
        "restrictions" -> restrictionsJs)
    }
  }

}