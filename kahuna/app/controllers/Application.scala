package controllers

import play.api.mvc.{Action, Controller}
import play.api.Logger
import lib.Config

object Application extends Controller {

  def index(ignored: String) = Action { req =>
    Ok(views.html.main(
      Config.mediaApiUri,
      Config.watUri,
      Config.mixpanelToken,
      Config.sentryDsn))
  }

}
