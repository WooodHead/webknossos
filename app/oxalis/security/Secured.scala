package oxalis.security

import models.user.User
import play.api.mvc._
import play.api.mvc.BodyParsers
import play.api.mvc.Results._
import play.api.i18n.Messages
import play.api.mvc.Request
import play.api.Play
import play.api.mvc.WebSocket
import play.api.mvc.WebSocket._
import play.api.Play.current
import play.api.libs.iteratee.Input
import controllers.routes
import play.api.libs.iteratee.Done
import models.security.Role
import models.security.Permission
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.libs.iteratee.Concurrent
import play.api.libs.concurrent.Akka
import akka.actor.Props
import oxalis.user.ActivityMonitor
import oxalis.user.UserActivity
import oxalis.view.AuthedSessionData
import scala.concurrent.Future


class AuthenticatedRequest[A](
                               val user: User, override val request: Request[A]) extends UserAwareRequest(Some(user), request)

class UserAwareRequest[A](
                           val userOpt: Option[User], val request: Request[A]) extends WrappedRequest(request)


object Secured {
  /**
   * Key used to store authentication information in the client cookie
   */
  val SessionInformationKey = "userId"

  val ActivityMonitor = Akka.system.actorOf(Props[ActivityMonitor], name = "activityMonitor")

  /**
   * Creates a map which can be added to a cookie to set a session
   */
  def createSession(user: User): Tuple2[String, String] =
    (SessionInformationKey -> user.id)
}

/**
 * Provide security features
 */
trait Secured {
  /**
   * Defines the access role which is used if no role is passed to an
   * authenticated action
   */
  val defaultUserName = "scmboy@scalableminds.com"
  lazy val defaultUser = User.findLocalByEmail(defaultUserName)

  def DefaultAccessRole: Option[Role]

  val userService = oxalis.user.UserService

  /**
   * Defines the default permission used for authenticated actions if not
   * specified otherwise
   */
  def DefaultAccessPermission: Option[Permission] = None

  /**
   * Tries to extract the user from a request
   */
  def maybeUser(implicit request: RequestHeader): Option[User] = {
    for {
      userId <- userId(request)
      user <- userService.findOneById(userId)
    } yield user
  }

  /**
   * Retrieve the connected users email address.
   */
  private def userId(request: RequestHeader) = {
    request.session.get(Secured.SessionInformationKey) match {
      case Some(id) =>
        Some(id)
      case _ if Play.configuration.getBoolean("application.enableAutoLogin").get =>
        // development setting: if the above key is set, one gets logged in 
        // automatically
        defaultUser.map(_.id)
      case _ =>
        None
    }
  }

  /**
   * Awesome construct to create an authenticated action. It uses the helper
   * function defined below this one to ensure that a user is logged in. If
   * a user fails this check he is redirected to the result of 'onUnauthorized'
   *
   * Example usage:
   * def initialize = Authenticated( role=Admin ) { user =>
   * implicit request =>
   * Ok("User is logged in!")
   * }
   *
   */

  def Authenticated(role: Option[Role] = DefaultAccessRole, permission: Option[Permission] = DefaultAccessPermission) =
    new ActionBuilder[AuthenticatedRequest] {
      def invokeBlock[A](request: Request[A], block: (AuthenticatedRequest[A]) => Future[SimpleResult]) = {
        maybeUser(request).map {
          user =>
            Secured.ActivityMonitor ! UserActivity(user, System.currentTimeMillis)
            if (user.verified) {
              if (hasAccess(user, role, permission))
                block(new AuthenticatedRequest(user, request))
              else
                Future.successful(Forbidden(views.html.error.defaultError(Messages("user.noPermission"), true)(AuthedSessionData(user, request.flash))))
            } else {
              Future.successful(Forbidden(views.html.error.defaultError(Messages("user.notVerified"), false)(AuthedSessionData(user, request.flash))))
            }
        }.getOrElse(Future.successful(onUnauthorized(request)))
      }
    }

  object UserAwareAction extends ActionBuilder[UserAwareRequest] {
    def invokeBlock[A](request: Request[A], block: (UserAwareRequest[A]) => Future[SimpleResult]) = {
      maybeUser(request).filter(_.verified).map {
        user =>
          Secured.ActivityMonitor ! UserActivity(user, System.currentTimeMillis)
          block(new AuthenticatedRequest(user, request))
      }.getOrElse {
        block(new UserAwareRequest(None, request))
      }
    }
  }

  def AuthenticatedWebSocket(role: Option[Role] = DefaultAccessRole, permission: Option[Permission] = DefaultAccessPermission) =
    new ActionBuilder[AuthenticatedRequest] {
      def invokeBlock[A](request: Request[A], block: (AuthenticatedRequest[A]) => Future[SimpleResult]) = {
        maybeUser(request).map {
          user =>
            Secured.ActivityMonitor ! UserActivity(user, System.currentTimeMillis)
            if (user.verified) {
              if (hasAccess(user, role, permission))
                block(new AuthenticatedRequest(user, request))
              else
                Future.successful(Forbidden(views.html.error.defaultError(Messages("user.noPermission"), true)(AuthedSessionData(user, request.flash))))
            } else {
              Future.successful(Forbidden(views.html.error.defaultError(Messages("user.notVerified"), false)(AuthedSessionData(user, request.flash))))
            }
        }.getOrElse(Future.successful(onUnauthorized(request)))
      }
    }


//  def AuthenticatedWebSocket[A](
//                                 role: Option[Role] = DefaultAccessRole,
//                                 permission: Option[Permission] = DefaultAccessPermission)(f: => User => RequestHeader => Future[(Iteratee[A, _], Enumerator[A])])(implicit formatter: FrameFormatter[A]) =
//    WebSocket.async[A] {
//      request =>
//        (for {
//          user <- maybeUser(request)
//          if (hasAccess(user, role, permission))
//        } yield {
//          f(user)(request)
//        }).getOrElse {
//          val iteratee = Done[A, Unit]((), Input.EOF)
//          // Send an error and close the socket
//          val (enumerator, channel) = Concurrent.broadcast[A]
//          channel.eofAndEnd
//
//          Future.successful((iteratee -> enumerator))
//        }
//    }

  def hasAccess(user: User, role: Option[Role], permission: Option[Permission]) =
    (role.isEmpty || user.hasRole(role.get)) &&
      (permission.isEmpty || user.hasPermission(permission.get))

  /**
   * Redirect to login if the user in not authorized.
   */
  private def onUnauthorized(request: RequestHeader) =
    Results.Redirect(routes.Authentication.login)

  // --

  /**
   * Action for authenticated users.
   */
  def IsAuthenticated(f: => String => Request[AnyContent] => Result) =
    Security.Authenticated(userId, onUnauthorized) {
      user =>
        Action(request => f(user)(request))
    }

}
