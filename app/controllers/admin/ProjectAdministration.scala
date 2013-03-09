package controllers.admin;

import braingames.mvc.Controller
import brainflight.security.Secured
import models.security.Role
import views._
import models.task.Project
import play.api.data.Form._
import play.api.data.Form
import play.api.data.Forms._
import models.user.User
import play.api.i18n.Messages

object ProjectAdministration extends Controller with Secured {

  override val DefaultAccessRole = Role.Admin

  val projectForm = Form(tuple(
    "projectName" -> nonEmptyText(1, 100)
      .verifying("project.nameAlreadyInUse", name => Project.findOneByName(name).isEmpty),
    "owner" -> nonEmptyText(1, 100)
      .verifying("user.notFound", userId => User.findOneById(userId).isDefined)))

  def list = Authenticated { implicit request =>
    Ok(html.admin.project.projectList(
      Project.findAll,
      projectForm.fill("", request.user.id),
      User.findAll.sortBy(_.name)))
  }

  def delete(projectName: String) = Authenticated { implicit reuqest =>
    for {
      project <- Project.findOneByName(projectName) ?~ Messages("project.notFound")
    } yield {
      Project.remove(project)
      JsonOk(Messages("project.removed"))
    }
  }

  def create = Authenticated(parser = parse.urlFormEncoded) { implicit request =>
    projectForm.bindFromRequest.fold(
      formWithErrors => BadRequest(html.admin.project.projectList(Project.findAll, formWithErrors, User.findAll)),
      {
        case (name, ownerId) =>
          for {
            owner <- User.findOneById(ownerId) ?~ Messages("user.notFound")
          } yield {
            Project.insertOne(Project(name, owner._id))
            Redirect(routes.ProjectAdministration.list).flashing(
              FlashSuccess(Messages("project.createSuccess")))
          }
      })
  }
}