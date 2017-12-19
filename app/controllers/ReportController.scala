/*
 * Copyright (C) 2011-2017 scalable minds UG (haftungsbeschränkt) & Co. KG. <http://scm.io>
 */
package controllers

import javax.inject.Inject

import com.scalableminds.util.reactivemongo.{DBAccessContext, GlobalAccessContext}
import com.scalableminds.util.tools.{Fox, FoxImplicits}
import models.annotation.{AnnotationDAO, AnnotationType}
import models.project.{Project, ProjectDAO}
import models.task._
import models.team.TeamDAO
import models.user.{Experience, User, UserDAO}
import oxalis.security.WebknossosSilhouette.SecuredAction
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID

import scala.concurrent.duration._


case class OpenTasksEntry(user: String, totalAssignments: Int, assignmentsByProjects: Map[String, Int])
object OpenTasksEntry { implicit val jsonFormat = Json.format[OpenTasksEntry] }

case class ProjectProgressEntry(projectName: String, totalTasks: Int, totalInstances: Int, openInstances: Int,
                                finishedInstances: Int, inProgressInstances: Int)
object ProjectProgressEntry { implicit val jsonFormat = Json.format[ProjectProgressEntry] }

class ReportController @Inject()(val messagesApi: MessagesApi) extends Controller with FoxImplicits {

  def projectProgressOverview(teamId: String) = SecuredAction.async { implicit request =>
    for {
      team <- TeamDAO.findOneById(teamId)(GlobalAccessContext) ?~> "team.notFound"
      t0 = System.nanoTime()
      users <- UserDAO.findByTeams(List(Some(team.name), team.parent).flatten, false)
      projects <- ProjectDAO.findAllByTeamNames(List(Some(team.name), team.parent).flatten)(GlobalAccessContext)
      t1 = System.nanoTime()
      _ = println("collecting team, users, projects: " + (t1 - t0)/1000000 )
      i <- Fox.sequence(projects.map(p => progressOfProject(p, users)(GlobalAccessContext)))
      t2 = System.nanoTime()
      _ = println("processing them: " + (t2 - t1)/1000000 )
      x: List[ProjectProgressEntry] = i.flatten
    } yield {
      val k = x
      Ok(Json.toJson(x))
    }
  }

  def progressOfProject(project: Project, users: List[User])(implicit ctx: DBAccessContext): Fox[ProjectProgressEntry] = {
    for {
      _ <- Fox.successful(println(project.name + " ## 1 attempting..."))
      taskIds <- TaskDAO.findAllByProjectReturnOnlyIds(project.name)
      _ <- Fox.successful(println(project.name + " ## 2 got through find tasks"))
      totalTasks = taskIds.length
      firstTask <- TaskDAO.findOneByProject(project.name)
      totalInstances <- TaskDAO.sumInstancesByProject(project.name)
      finishedInstances <- AnnotationDAO.countFinishedByTaskIdsAndType(taskIds, AnnotationType.Task)
      _ <- Fox.successful(println(project.name + " ## 3 got count finished for " + project.name))
      inProgressInstances <- AnnotationDAO.countUnfinishedByTaskIdsAndType(taskIds, AnnotationType.Task)
      _ <- Fox.successful(println(project.name + " ## 4 got through count unfinished"))
      openInstances = totalInstances - finishedInstances - inProgressInstances
      _ <- assertNotPaused(project, finishedInstances, inProgressInstances)
      _ <- Fox.successful(println(project.name + " ## 5 passed NotPaused"))
      _ <- assertExpDomain(firstTask, inProgressInstances, users)
      _ <- Fox.successful(println(project.name + " ## 6 passed ExpDomain"))
      _ <- assertAge(taskIds, inProgressInstances, openInstances)
      _ <- Fox.successful(println(project.name + " ## 7 passed Age"))
    } yield {
      val e = ProjectProgressEntry(project.name, totalTasks, totalInstances, openInstances, finishedInstances, inProgressInstances)
      println(project.name + " ## 8 got a project progress entry")
      println(Json.toJson(e))
      e
    }
  }

  def assertNotPaused(project: Project, finishedInstances: Int, inProgressInstances: Int) = {
    if (project.paused && finishedInstances == 0 && inProgressInstances == 0) {
      Fox.failure("assertB")
    } else Fox.successful(())
  }

  def assertExpDomain(firstTask: Task, inProgressInstances: Int, users: List[User])(implicit ctx: DBAccessContext) = {
    if (inProgressInstances > 0) Fox.successful(())
    else assertMatchesAnyUserOfTeam(firstTask.neededExperience, users)
  }

  def assertMatchesAnyUserOfTeam(experience: Experience, users: List[User])(implicit ctx: DBAccessContext) = {
    for {
      _ <- users.exists(user => user.experiences.contains(experience.domain) && user.experiences(experience.domain) >= experience.value)
    } yield {
      ()
    }
  }

  def assertAge(taskIds: List[BSONObjectID], inProgressInstances: Int, openInstances: Int)(implicit ctx: DBAccessContext) = {
    if (inProgressInstances > 0 || openInstances > 0) Fox.successful(())
    else {
      assertRecentlyModified(taskIds)
    }
  }

  def assertRecentlyModified(taskIds: List[BSONObjectID])(implicit ctx: DBAccessContext) = {
    for {
      count <- AnnotationDAO.countRecentlyModifiedByTaskIdsAndType(taskIds, AnnotationType.Task, System.currentTimeMillis - (30 days).toMillis)
      _ <- count > 0
    } yield {
      ()
    }
  }


  /**
    * assumes that (a) there is only one OpenAssignment per Task
    * and (b) that all tasks of a project have the same required experience
    */
  def openTasksOverview(id: String) = SecuredAction.async { implicit request =>
    for {
      team <- TeamDAO.findOneById(id)(GlobalAccessContext)
      users <- UserDAO.findByTeams(List(team.name), true)(GlobalAccessContext)
      entries: List[OpenTasksEntry] <- getAllAvailableTaskCountsAndProjects(users)(GlobalAccessContext)
    } yield {
      Ok(Json.toJson(entries))
    }
  }


  def getAllAvailableTaskCountsAndProjects(users: Seq[User])(implicit ctx: DBAccessContext): Fox[List[OpenTasksEntry]] = {
    val foxes = users.map { user =>
      for {
        projects <- OpenAssignmentDAO.findByUserReturnOnlyProject(user).toFox
        assignmentCountsByProject <- getAssignmentsByProjectsFor(projects, user)
      } yield {
        OpenTasksEntry(user.name, assignmentCountsByProject.values.sum, assignmentCountsByProject)
      }
    }
    Fox.combined(foxes.toList)
  }

  def getAssignmentsByProjectsFor(projects: Seq[String], user: User)(implicit ctx: DBAccessContext): Fox[Map[String, Int]] = {
    val projectsGrouped = projects.groupBy(identity).mapValues(_.size)
    val foxes: Iterable[Fox[(String, Int)]] = projectsGrouped.keys.map {
      project =>
        Fox(for {
          tasksIds <- TaskDAO.findAllByProjectReturnOnlyIds(project)
          doneTasks <- AnnotationDAO.countFinishedByTaskIdsAndUserIdAndType(tasksIds, user._id, AnnotationType.Task)
          firstTask <- TaskDAO.findOneByProject(project)
        } yield {
          (project + "/" + firstTask.neededExperience.toString, projectsGrouped(project) - doneTasks)
        })
    }
    for {
      list <- Fox.combined(foxes.toList)
    } yield {
      list.toMap
    }
  }

}
