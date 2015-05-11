package com.gu.mediaservice.lib.auth

import com.amazonaws.auth.BasicAWSCredentials
import com.gu.pandomainauth.action.AuthActions
import com.gu.pandomainauth.model.AuthenticatedUser

import com.gu.mediaservice.lib.config.Properties
import org.joda.time.DateTime

trait PanDomainAuthActions extends AuthActions {

  lazy val properties = Properties.fromPath("/etc/gu/panda.properties")

  override def validateUser(authedUser: AuthenticatedUser): Boolean = {
    (authedUser.user.emailDomain == "***REMOVED***") && authedUser.multiFactor
  }

//  def now = new DateTime()
//  def inFuture(duration: Duration) = now.plus(duration)
  def devExpiryOverride = Some(new DateTime().plusSeconds(10).getMillis)
  override def generateCookie(authedUser: AuthenticatedUser) = {
    val updatedUser = devExpiryOverride.fold(authedUser)(expiryOverride => authedUser.copy(expires = expiryOverride))
    super.generateCookie(updatedUser)
  }

  val authCallbackBaseUri: String

  override def authCallbackUrl: String = s"$authCallbackBaseUri/oauthCallback"

  override lazy val domain: String = properties("panda.domain")
  lazy val awsKeyId                = properties.get("panda.aws.key")
  lazy val awsSecretAccessKey      = properties.get("panda.aws.secret")

  override lazy val awsCredentials = for (key <- awsKeyId; sec <- awsSecretAccessKey) yield {new BasicAWSCredentials(key, sec)}

  override val system: String = "media-service"
}
