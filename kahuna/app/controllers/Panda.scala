package controllers

import lib.Config
import play.api.mvc.{Action, Controller}
import com.gu.mediaservice.lib.auth.{ArgoErrorResponses, PanDomainAuthActions}
import com.gu.mediaservice.lib.argo.ArgoHelpers
import com.gu.pandomainauth.model.AuthenticatedUser
import com.gu.pandomainauth.service.GoogleAuthException

import scala.concurrent.Future
import scala.util.{Success, Try, Failure}

object Panda extends Controller
  with PanDomainAuthActions
  with ArgoHelpers
  with ArgoErrorResponses {

  override lazy val authCallbackBaseUri = Config.rootUri
  override lazy val loginUriTemplate    = Config.loginUriTemplate


  // FIXME: how to use usual Authenticated action helper for this? separate Controller?
  def session = Action { implicit request =>
    readAuthenticatedUser(request).map { case AuthenticatedUser(user, _, _, _, _) =>
      val userData = Map("user" -> (
        Map(
          "name"      -> s"${user.firstName} ${user.lastName}",
          "firstName" -> user.firstName,
          "lastName"  -> user.lastName,
          "email"     -> user.email
        ) ++ user.avatarUrl.map("avatarUrl" -> _)
      ))
      respond(userData)
    }.getOrElse(notAuthenticatedResult)
  }

  // Trigger the auth cycle
  // If a redirectUri is provided, redirect the browser there once auth'd,
  // else return a dummy page (e.g. for automatically re-auth'ing in the background)
  def doLogin(redirectUri: Option[String] = None) = AuthAction { req =>
    redirectUri map (Redirect(_)) getOrElse Ok("logged in")
  }

  def oauthCallback = Action.async { implicit request =>
    // We use the `Try` here as the `GoogleAuthException` are thrown before we
    // get to the asynchronicity of the `Future` it returns.
    Try(processGoogleCallback) match {
      case Success(result) => result
      case Failure(error) => Future.successful(error match {
        // This is when session session args are missing
        case e: GoogleAuthException =>
          respondError(BadRequest, "missing-session-parameters", e.getMessage, loginLinks)
      })
    }
  }

  def logout = Action { implicit request =>
    processLogout
  }

}
