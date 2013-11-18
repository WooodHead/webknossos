package oxalis.annotation.handler

import net.liftweb.common.Box
import play.api.i18n.Messages
import braingames.util.TextUtils._
import models.annotation.{AnnotationDAO, AnnotationLike, Annotation}
import braingames.reactivemongo.DBAccessContext
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import braingames.util.{FoxImplicits, Fox}

object SavedTracingInformationHandler extends AnnotationInformationHandler with FoxImplicits{

  import braingames.mvc.BoxImplicits._

  type AType = Annotation

  override val cache = false

  override def nameForAnnotation(a: AnnotationLike): Future[String] = a match {
    case annotation: Annotation =>
      for{
        user <- annotation.user
      } yield {
        val userName = user.map(_.abreviatedName) getOrElse ""
        val task = annotation.task.map(_.id) getOrElse ("explorational")
        val id = oxalis.view.helpers.formatHash(annotation.id)
        normalize(s"${annotation.dataSetName}__${task}__${userName}__${id}")
      }
    case a =>
      Future.successful(a.id)
  }

  def provideAnnotation(annotationId: String)(implicit ctx: DBAccessContext): Fox[Annotation] = {
    Future.successful(
      for {
        annotation <- AnnotationDAO.findOneById(annotationId) ?~ Messages("annotation.notFound")
      } yield {
        annotation
      })
  }

}