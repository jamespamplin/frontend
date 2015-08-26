package common

import java.io.{File, FileInputStream}

import com.amazonaws.AmazonClientException
import com.amazonaws.auth._
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.gu.conf.ConfigurationFactory
import conf.Configuration
import org.apache.commons.io.IOUtils
import play.api.{Configuration => PlayConfiguration, Play}
import play.api.Play.current

import scala.util.Try

class BadConfigurationException(msg: String) extends RuntimeException(msg)

class GuardianConfiguration(val application: String, val webappConfDirectory: String = "env") extends Logging {

  case class OAuthCredentials(oauthClientId: String, oauthSecret: String, oauthCallback: String)

  protected val configuration = ConfigurationFactory.getConfiguration(application, webappConfDirectory)

  private implicit class OptionalString2MandatoryString(conf: com.gu.conf.Configuration) {
    def getMandatoryStringProperty(property: String) = configuration.getStringProperty(property)
      .getOrElse(throw new BadConfigurationException(s"$property not configured"))
  }

  object environment {
    private val installVars = new File("/etc/gu/install_vars") match {
      case f if f.exists => IOUtils.toString(new FileInputStream(f))
      case _ => ""
    }

    private val properties = Properties(installVars)

    def apply(key: String, default: String) = properties.getOrElse(key, default).toLowerCase

    val stage = apply("STAGE", "unknown")

    lazy val projectName = Play.application.configuration.getString("guardian.projectName").getOrElse("frontend")
    lazy val secure = Play.application.configuration.getBoolean("guardian.secure").getOrElse(false)

    lazy val isProd = stage == "prod"
    lazy val isNonProd = List("dev", "code", "gudev").contains(stage.toLowerCase)

    lazy val isPreview = projectName == "preview"
  }

  override def toString = configuration.toString

  case class Auth(user: String, password: String)

  object contentApi {
    val contentApiLiveHost: String = configuration.getMandatoryStringProperty("content.api.host")

    def contentApiDraftHost: Option[String] = configuration.getStringProperty("content.api.draft.host")

    lazy val key: Option[String] = configuration.getStringProperty("content.api.key")
    lazy val timeout: Int = configuration.getIntegerProperty("content.api.timeout.millis").getOrElse(2000)

    lazy val previewAuth: Option[Auth] = for {
      user <- configuration.getStringProperty("content.api.preview.user")
      password <- configuration.getStringProperty("content.api.preview.password")
    } yield Auth(user, password)
  }

  object ophanApi {
    lazy val key = configuration.getStringProperty("ophan.api.key")
    lazy val host = configuration.getStringProperty("ophan.api.host")
  }

  object frontend {
    lazy val store = configuration.getMandatoryStringProperty("frontend.store")
  }

  // object site {
  //   lazy val host = configuration.getStringProperty("guardian.page.host").getOrElse("")
  // }

  object facia {
    lazy val stage = configuration.getStringProperty("facia.stage").getOrElse(Configuration.environment.stage)
    lazy val collectionCap: Int = 35
  }

  object faciatool {
    lazy val contentApiPostEndpoint = configuration.getStringProperty("contentapi.post.endpoint")
    lazy val frontPressCronQueue = configuration.getStringProperty("frontpress.sqs.cron_queue_url")
    lazy val frontPressToolQueue = configuration.getStringProperty("frontpress.sqs.tool_queue_url")
    /** When retrieving items from Content API, maximum number of requests to make concurrently */
    lazy val frontPressItemBatchSize = configuration.getIntegerProperty("frontpress.item_batch_size", 30)
    /** When retrieving items from Content API, maximum number of items to request per concurrent request */
    lazy val frontPressItemSearchBatchSize = {
      val size = configuration.getIntegerProperty("frontpress.item_search_batch_size", 20)
      assert(size <= 100, "Best to keep this less then 50 because of pageSize on search queries")
      size
    }

    lazy val pandomainHost = configuration.getStringProperty("faciatool.pandomain.host")
    lazy val pandomainDomain = configuration.getStringProperty("faciatool.pandomain.domain")
    lazy val pandomainSecret = configuration.getStringProperty("pandomain.aws.secret")
    lazy val pandomainKey = configuration.getStringProperty("pandomain.aws.key")

    lazy val configBeforePressTimeout: Int = 1000

    val showTestContainers =
      configuration.getStringProperty("faciatool.show_test_containers").contains("true")

    lazy val adminPressJobStandardPushRateInMinutes: Int =
      Try(configuration.getStringProperty("admin.pressjob.standard.push.rate.inminutes").get.toInt)
        .getOrElse(5)

    lazy val adminPressJobHighPushRateInMinutes: Int =
      Try(configuration.getStringProperty("admin.pressjob.high.push.rate.inminutes").get.toInt)
        .getOrElse(1)

    lazy val adminPressJobLowPushRateInMinutes: Int =
      Try(configuration.getStringProperty("admin.pressjob.low.push.rate.inminutes").get.toInt)
        .getOrElse(60)

    lazy val faciaToolUpdatesStream: Option[String] = configuration.getStringProperty("faciatool.updates.stream")

    lazy val sentryPublicDSN = configuration.getStringProperty("faciatool.sentryPublicDSN")
  }

  object aws {

    lazy val region = configuration.getMandatoryStringProperty("aws.region")
    lazy val bucket = configuration.getMandatoryStringProperty("aws.bucket")

    def mandatoryCredentials: AWSCredentialsProvider = credentials.getOrElse(throw new BadConfigurationException("AWS credentials are not configured"))
    val credentials: Option[AWSCredentialsProvider] = {
      val provider = new AWSCredentialsProviderChain(
        new EnvironmentVariableCredentialsProvider(),
        new SystemPropertiesCredentialsProvider(),
        new ProfileCredentialsProvider("nextgen"),
        new InstanceProfileCredentialsProvider
      )

      // this is a bit of a convoluted way to check whether we actually have credentials.
      // I guess in an ideal world there would be some sort of isConfigued() method...
      try {
        provider.getCredentials
        Some(provider)
      } catch {
        case ex: AmazonClientException =>
          log.error(ex.getMessage, ex)

          // We really, really want to ensure that PROD is configured before saying a box is OK
          if (Play.isProd) throw ex
          // this means that on dev machines you only need to configure keys if you are actually going to use them
          None
      }
    }
  }
}
