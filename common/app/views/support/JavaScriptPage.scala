package views.support

import common.Maps.RichMap
import common.{Edition, InternationalEdition}
import conf.Configuration
import conf.Configuration.environment
import conf.Switches.IdentitySocialOAuthSwitch
import model.{Content, MetaData}
import org.joda.time.DateTime
import play.api.Play
import play.api.Play.current
import play.api.libs.json.{JsBoolean, JsString, Json}
import play.api.mvc.RequestHeader

case class JavaScriptPage(metaData: MetaData)(implicit request: RequestHeader) {

  def get = {
    val edition = Edition(request)
    val internationalEdition = InternationalEdition(request) map { edition =>
      ("internationalEdition", JsString(edition.variant))
    }

    Json.toJson(metaData.metaData ++ internationalEdition ++ Map(
      ("edition", JsString(edition.id))
    ))
  }

}
