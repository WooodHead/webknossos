package oxalis.annotation.handler

import net.liftweb.common.Box
import models.task.{TaskDAO, Task}
import play.api.i18n.Messages
import models.user.User
import models.annotation.{AnnotationRestrictions, TemporaryAnnotation}
import models.security.{RoleDAO, Role}
import models.tracing.skeleton.CompoundAnnotation
import braingames.reactivemongo.DBAccessContext
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import braingames.util.{FoxImplicits, Fox}

object TaskInformationHandler extends AnnotationInformationHandler with FoxImplicits {

  import braingames.mvc.BoxImplicits._

  type AType = TemporaryAnnotation

  def taskAnnotationRestrictions(task: Task) =
    new AnnotationRestrictions {
      override def allowAccess(user: Option[User]) =
        user.flatMap {
          user =>
            RoleDAO.Admin.map(user.hasRole)
        } getOrElse false
    }

  def provideAnnotation(taskId: String)(implicit ctx: DBAccessContext): Fox[TemporaryAnnotation] = {
    for {
      task <- TaskDAO.findOneById(taskId) ?~> Messages("task.notFound")
      annotation <- CompoundAnnotation.createFromTask(task) ?~> Messages("task.noAnnotations")
    } yield {
      annotation.copy(restrictions = taskAnnotationRestrictions(task))
    }
  }
}