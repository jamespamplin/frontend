package controllers

import com.gu.contentapi.client.model.{Content => ApiContent, Crossword}
import conf.LiveContentApi
import model.Cached
import com.gu.contentapi.client.{model => contentapi}
import common.{Edition, ExecutionContexts}
import crosswords.{CrosswordData, CrosswordPage}
import play.api.mvc.{RequestHeader, Result, Action, Controller}
import services.ContentApiGetters._

import scala.concurrent.Future

class OfflinePage(crossword: CrosswordData, content: contentapi.Content) extends CrosswordPage(crossword, content) {
  override lazy val id: String = "offline-page"

  override lazy val webTitle: String = "Unable to connect to the Internet"
}

object WebAppController extends Controller with ExecutionContexts {

  def serviceWorker() = Action { implicit request =>
    Cached(3600) {
      if (conf.Switches.NotificationsSwitch.isSwitchedOn || conf.Switches.OfflinePageSwitch.isSwitchedOn) {
        Ok(templates.js.serviceWorker())
      } else {
        NotFound
      }
    }
  }

  def manifest() = Action {
    Cached(3600) { Ok(templates.js.webAppManifest()) }
  }

  protected def withCrossword(crosswordType: String, id: Int)(f: (Crossword, ApiContent) => Result)(implicit request: RequestHeader): Future[Result] = {
    LiveContentApi.getResponse(LiveContentApi.item(s"crosswords/series/quick", Edition(request)).showFields("all")).map { response =>
      val maybeCrossword = for {
        content <- response.results.headOption
        crossword <- content.crossword }
        yield f(crossword, content)
      maybeCrossword getOrElse InternalServerError("Crossword response from Content API invalid.")
    } recover { case e =>
      log.error("Content API query returned an error.", e)
      InternalServerError("Content API query returned an error.")
    }
  }

  def offlinePage() = Action.async { implicit request =>
    if (conf.Switches.OfflinePageSwitch.isSwitchedOn) {
      withCrossword("quick", 14127) { (crossword, content) =>
        Cached(60)(Ok(views.html.offlinePage(
          new OfflinePage(CrosswordData.fromCrossword(crossword), content))))
      }
    } else {
      Future(NotFound)
    }
  }
}
