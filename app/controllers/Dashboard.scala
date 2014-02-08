package controllers

import models.user.User
import models.annotation.{AnnotationService, Annotation}
import models.task.Task
import models.user.time.TimeTracking._
import models.binary.DataSet
import models.user.time.TimeTrackingService
import models.binary.DataSetDAO
import braingames.util.ExtendedTypes.ExtendedList
import play.api.libs.concurrent.Execution.Implicits._
import braingames.reactivemongo.DBAccessContext
import play.api.Logger
import braingames.util.Fox
import play.api.libs.json._

/**
 * Company: scalableminds
 * User: tmbo
 * Date: 07.11.13
 * Time: 20:58
 */
case class DashboardInfo(
                          user: User,
                          exploratory: List[Annotation],
                          tasks: List[(Task, Annotation)],
                          loggedTime: LoggedPerPaymentInterval,
                          dataSets: List[DataSet],
                          hasAnOpenTask: Boolean
                        )

object DashboardInfo {
  // implicit val dashboardInfoFormat = Json.format[DashboardInfo]
}

trait Dashboard {
  private def userWithTasks(user: User)(implicit ctx: DBAccessContext) = {
    AnnotationService.findTasksOf(user).flatMap{ taskAnnotations =>
      Fox.sequence(taskAnnotations.map(a => a.task.map(_ -> a))).map(_.flatten)
    }
  }

  private def exploratorySortedByTime(exploratoryAnnotations: List[Annotation]) =
    exploratoryAnnotations.futureSort(_.content.map(-_.timestamp).getOrElse(0L))

  private def hasOpenTask(tasksAndAnnotations: List[(Task, Annotation)]) =
    tasksAndAnnotations.exists { case (_, annotation) => !annotation.state.isFinished }

  def dashboardInfo(user: User)(implicit ctx: DBAccessContext) = {
    for {
      exploratoryAnnotations <- AnnotationService.findExploratoryOf(user)
      dataSets <- DataSetDAO.findAll
      loggedTime <- TimeTrackingService.loggedTime(user)
      exploratoryAnnotations <- exploratorySortedByTime(exploratoryAnnotations)
      userTasks <- userWithTasks(user)
    } yield {
      DashboardInfo(
        user,
        exploratoryAnnotations,
        userTasks,
        loggedTime,
        dataSets,
        hasOpenTask(userTasks))
    }
  }
}