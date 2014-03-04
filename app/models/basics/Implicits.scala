package models.basics

import oxalis.security.UserAwareRequest
import models.user.User
import braingames.reactivemongo.{AuthorizedAccessContext, DBAccessContext}

/**
 * Company: scalableminds
 * User: tmbo
 * Date: 10.06.13
 * Time: 01:11
 */
object Implicits extends Implicits

trait Implicits {
  implicit def authedRequestToDBAccess(implicit request: UserAwareRequest[_]): DBAccessContext = {
    DBAccessContext(request.userOpt)
  }

  implicit def userToDBAccess(user: User): DBAccessContext = {
    AuthorizedAccessContext(user)
  }
}
