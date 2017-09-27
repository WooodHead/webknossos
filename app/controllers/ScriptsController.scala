package controllers

import javax.inject.Inject

import scala.concurrent.Future

import com.scalableminds.util.tools.FoxImplicits
import models.task.{Script, _}
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.twirl.api.Html
import oxalis.security.silhouetteOxalis.{UserAwareAction, UserAwareRequest, SecuredRequest, SecuredAction}


class ScriptsController @Inject()(val messagesApi: MessagesApi) extends Controller with FoxImplicits {

  val scriptPublicReads =
    ((__ \ 'name).read[String](minLength[String](2) or maxLength[String](50)) and
      (__ \ 'gist).read[String] and
      (__ \ 'ownerId).read[String]) (Script.fromForm _)

  def empty(id: String) = SecuredAction { implicit request =>
    Ok(views.html.main()(Html("")))
  }

  def create = SecuredAction.async(parse.json) { implicit request =>
    withJsonBodyUsing(scriptPublicReads) { script =>
      for {
        _ <- request.identity.hasAdminAccess ?~> Messages("notAllowed")
        _ <- ScriptDAO.insert(script)
        js <- Script.scriptPublicWrites(script)
      } yield {
        Ok(js)
      }
    }
  }

  def get(scriptId: String) = SecuredAction.async { implicit request =>
    for {
      script <- ScriptDAO.findOneById(scriptId) ?~> Messages("script.notFound")
      js <- Script.scriptPublicWrites(script)
    } yield {
      Ok(js)
    }
  }

  def list = SecuredAction.async { implicit request =>
    for {
      scripts <- ScriptDAO.findAll
      js <- Future.traverse(scripts)(s => Script.scriptPublicWrites(s))
    } yield {
      Ok(Json.toJson(js))
    }
  }

  def update(scriptId: String) = SecuredAction.async(parse.json) { implicit request =>
    withJsonBodyUsing(scriptPublicReads) { script =>
      for {
        oldScript <- ScriptDAO.findOneById(scriptId) ?~> Messages("script.notFound")
        _ <- (oldScript._owner == request.identity.id) ?~> Messages("script.notOwner")
        _ <- ScriptDAO.update(oldScript._id, script.copy(_id = oldScript._id))
        js <- Script.scriptPublicWrites(script)
      } yield {
        Ok(js)
      }
    }
  }

  def delete(scriptId: String) = SecuredAction.async { implicit request =>
    for {
      oldScript <- ScriptDAO.findOneById(scriptId) ?~> Messages("script.notFound")
      _ <- (oldScript._owner == request.identity.id) ?~> Messages("script.notOwner")
      _ <- ScriptDAO.removeById(scriptId) ?~> Messages("script.notFound")
      _ <- TaskService.removeScriptFromTasks(scriptId) ?~> Messages("script.taskRemoval.failed")
    } yield {
      Ok
    }
  }
}
