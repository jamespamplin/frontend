package model

import com.gu.facia.api.models.FaciaContent
import implicits.FaciaContentImplicits._
import implicits.FaciaContentFrontendHelpers._
import layout.ItemClasses

object FaciaDisplayElement {
  def fromFaciaContentAndCardType(faciaContent: FaciaContent, itemClasses: ItemClasses): Option[FaciaDisplayElement] = {
    faciaContent.mainVideo match {
      case Some(videoElement) if faciaContent.showMainVideo =>
        Some(InlineVideo(
          videoElement,
          faciaContent.webTitle,
          EndSlateComponents.fromFaciaContent(faciaContent).toUriPath,
          InlineImage.fromFaciaContent(faciaContent)
        ))
      case _ if faciaContent.imageSlideshowReplace && itemClasses.canShowSlideshow =>
        InlineSlideshow.fromFaciaContent(faciaContent)
      case _ => InlineImage.fromFaciaContent(faciaContent)
    }
  }
}

sealed trait FaciaDisplayElement

case class InlineVideo(
  videoElement: VideoElement,
  title: String,
  endSlatePath: String,
  fallBack: Option[InlineImage]
) extends FaciaDisplayElement

object InlineImage {
  def fromFaciaContent(faciaContent: FaciaContent): Option[InlineImage] =
    if (!faciaContent.imageHide) {
      faciaContent.trailPicture(5, 3) map { picture =>
        InlineImage(picture)
      }
    } else {
      None
    }
}

case class InlineImage(imageContainer: ImageContainer) extends FaciaDisplayElement

object InlineSlideshow {
  def fromFaciaContent(faciaContent: FaciaContent): Option[InlineSlideshow] =
    for (s <- faciaContent.slideshow) yield InlineSlideshow(s)
}

case class InlineSlideshow(images: Iterable[FaciaImageElement]) extends FaciaDisplayElement
