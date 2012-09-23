package scalamachine
package core
package v3

import flow._
import scalaz.std.option._
import scalaz.std.string._
import optionSyntax._
import scalaz.syntax.order._
import scalaz.syntax.monad._
import scalaz.OptionT
import OptionT._
import scalaz.Lens._
import scalaz.State
import scalaz.StateT._
import scalaz.Id
import scalaz.effect.IO
import IO._
import Res._
import ResT._
import ReqRespData._
import Metadata._
import Resource._
import java.util.Date
import HTTPHeaders._
import HTTPMethods._

trait WebmachineDecisions {

  /* Service Available? */
  lazy val b13: Decision = new Decision { 
    val name = "v3b13"
    def apply(r: Resource): r.Result[Decision] = 
      r.serviceAvailable.flatMap {
        isAvail => 
          if (isAvail) b12.point[r.Result]
          else r.haltWithCode[Decision](503)
      }
  }

  /* Known Methods */
  lazy val b12: Decision = 
    new Decision {
      val name = "v3b12"
      def apply(r: Resource): r.Result[Decision] = for {
          known <- r.knownMethods
          method <- (r.dataL >=> methodL).lift[IO].liftM[ResT]
          d <- if (known.contains(method)) b11.point[r.Result]
               else r.haltWithCode[Decision](501)
        } yield d 
    }

  /* URI Too Long? */
  lazy val b11: Decision = new Decision {
    val name = "v3b11"
    def apply(r: Resource): r.Result[Decision] = 
      r.uriTooLong.flatMap {
        isTooLong => 
          if (isTooLong) r.haltWithCode[Decision](414)
          else b10.point[r.Result]
      }
    
  }    

  /* Allowed Methods */
  lazy val b10: Decision = new Decision { 
    val name = "v3b10"
    def apply(r: Resource): r.Result[Decision] =
      for {
        allowed <- r.allowedMethods
        method <- (r.dataL >=> methodL).lift[IO].liftM[ResT]
        d <- if (allowed.contains(method)) b9.point[r.Result]
             else setAllowHdr(r, allowed).lift[IO].liftM[ResT] flatMap { _ => r.haltWithCode[Decision](405) }
      } yield d

    private def setAllowHdr(r: Resource, allowed: List[HTTPMethod]) = 
      (r.dataL >=> responseHeadersL) += (Allow -> allowed.map(_.toString).mkString(", "))
  }

  /* Malformed Request? */
  lazy val b9: Decision = new Decision {
    val name = "v3b9"
    def apply(r: Resource): r.Result[Decision] = r.isMalformed.flatMap {
      isMalformed => 
        if (isMalformed) r.haltWithCode[Decision](400)
        else b8.point[r.Result]
    }

  }

  /* Is Authorized? */
  lazy val b8: Decision = new Decision {
    val name = "v3b8"
    def apply(r: Resource): r.Result[Decision] = 
      for {
        authRes <- r.isAuthorized
        d <- authRes.fold(
          success = b7.point[r.Result],
          failure = msg => setWWWAuthHdr(r, msg) flatMap { _ => r.haltWithCode[Decision](401) }
        )
      } yield d
     
    private def setWWWAuthHdr(r: Resource, msg: String): r.Result[Map[HTTPHeader,String]] = 
      ((r.dataL >=> responseHeadersL) += (WWWAuthenticate -> msg)).lift[IO].liftM[ResT]
  }

  /* Is Forbidden? */
  lazy val b7: Decision = new Decision {
    val name = "v3b7"
    def apply(r: Resource): r.Result[Decision] = 
      r.isForbidden.flatMap { 
        isForbidden => 
          if (isForbidden) r.haltWithCode[Decision](403)
          else b6.point[r.Result]
      }
  }

  /* Content-* Headers Are Valid? */
  lazy val b6: Decision = new Decision {
    val name ="v3b6"
    def apply(r: Resource): r.Result[Decision] = 
      r.contentHeadersValid.flatMap { 
        hdrsValid => 
          if (hdrsValid) b5.point[r.Result]
          else r.haltWithCode[Decision](501)
      }
  }

  /* Is Known Content-Type? */
  lazy val b5: Decision = new Decision {
    val name = "v3b5"
    def apply(r: Resource): r.Result[Decision] =
      r.isKnownContentType.flatMap { 
        isKnown =>
          if (isKnown) b4.point[r.Result]
          else r.haltWithCode[Decision](415)
      }
  }

  /* Request Entity Too Large? */
  lazy val b4: Decision = new Decision { 
    val name = "v3b4"
    def apply(r: Resource): r.Result[Decision] =
      r.isValidEntityLength.flatMap {
        isValidLen =>
          if (isValidLen) b3.point[r.Result]
          else r.haltWithCode[Decision](413)
      }
      
  }

  /* OPTIONS? */
  lazy val b3: Decision = new Decision {
    val name = "v3b3"
    def apply(r: Resource): r.Result[Decision] = 
      (r.dataL >=> methodL).lift[IO].liftM[ResT].flatMap {
        case OPTIONS => respondToOptions(r)
        case _ => c3.point[r.Result]
      }

    protected def respondToOptions(r: Resource): r.Result[Decision] = 
      r.options
       .flatMap(hdrs => ((r.dataL >=> responseHeadersL) ++= hdrs.toList).lift[IO].liftM[ResT])
       .flatMap(_ => r.haltWithCode[Decision](200))
  }

  /* Accept Exists? */
  lazy val c3: Decision = new Decision {
    import scalaz.syntax.std.list._

    val name = "v3c3"

    def apply(r: Resource): r.Result[Decision] = {
      def firstOrDefault(provided: r.ContentTypesProvided): ContentType =
        provided.toNel.map(_.head._1) getOrElse defaultContentType

      lazy val resolveContentType: r.Result[Decision] = for {
        provided <- r.contentTypesProvided
        ctype <- firstOrDefault(provided).point[r.Result]
        _ <- ((r.dataL >=> metadataL >=> contentTypeL) := Option(ctype)).lift[IO].liftM[ResT]
      } yield d4
            
      ((r.dataL >=> requestHeadersL) member Accept).lift[IO]
        .liftM[ResT]
        .flatMap(mbAccept => mbAccept >| c4.point[r.Result] | resolveContentType)
    }

  }

  /* Acceptable Media Type Available? */
  lazy val c4: Decision = new Decision {
    val name = "v3c4"

    def apply(r: Resource): r.Result[Decision] = for {
      acceptHeader <- ((r.dataL >=> requestHeadersL) member Accept).map(_ | "*/*").lift[IO].liftM[ResT]
      provided <- r.contentTypesProvided.map(_.unzip._1)
      ctype <- Util.chooseMediaType(provided, acceptHeader).point[r.Result]
      _ <- ((r.dataL >=> metadataL >=> contentTypeL) := ctype).lift[IO].liftM[ResT]
      decision <- ctype >| d4.point[r.Result] | r.haltWithCode[Decision](406)
    } yield decision
  }

  /* Accept-Language Exists? */
  lazy val d4: Decision = new Decision {
    val name = "v3d4"

    def apply(r: Resource): r.Result[Decision] = {
      ((r.dataL >=> requestHeadersL) member AcceptLanguage).lift[IO]
        .liftM[ResT]
        .map(_ >| d5 | e5)
    }
  }

  /* Acceptable Language Available? */
  lazy val d5: Decision = new Decision {
    val name = "v3d5"

    def apply(r: Resource): r.Result[Decision] = r.isLanguageAvailable.flatMap {
      isLangAvail => 
        if (isLangAvail) e5.point[r.Result]
        else r.haltWithCode[Decision](406)
    }
  }

  /* Accept-Charset Exists? */
  lazy val e5: Decision = new Decision {
    val name = "v3e5"

    def apply(r: Resource): r.Result[Decision] = 
      ((r.dataL >=> requestHeadersL) member AcceptCharset).lift[IO]
         .liftM[ResT]
         .flatMap(_ >| e6.point[r.Result] | chooseCharset(r, "*"))
  }

  /* Acceptable Charset Available? */
  lazy val e6: Decision = new Decision {
    val name = "v3e6" 
    def apply(r: Resource): r.Result[Decision] = 
      ((r.dataL >=> requestHeadersL) member AcceptCharset).map(_ | "*")
        .lift[IO]
        .liftM[ResT]
        .flatMap(chooseCharset(r, _))
  }

  /* Accept-Encoding Exists? */
  lazy val f6: Decision = new Decision {
    val name = "v3f6" 
    def apply(r: Resource): r.Result[Decision] = for {
      media <- (r.dataL >=> metadataL >=> contentTypeL).map(_ | defaultContentType).lift[IO].liftM[ResT]
      charset <- (r.dataL >=> metadataL >=> chosenCharsetL).map(_.map(";charset=" + _) | "").lift[IO].liftM[ResT]
      _ <- ((r.dataL >=> responseHeadersL) += ((ContentTypeHeader, media.toHeader + charset))).lift[IO].liftM[ResT]
      mbHeader <- ((r.dataL >=> requestHeadersL) member AcceptEncoding).lift[IO].liftM[ResT]
      decision <- mbHeader >| f7.point[r.Result] | chooseEncoding(r, "identity;q=1.0,*;q=0.5")
    } yield decision
  }

  /* Acceptable Encoding Available? */
  lazy val f7: Decision = new Decision {
    val name = "v3f7"
    def apply(r: Resource): r.Result[Decision] =
      (r.dataL >=> requestHeadersL member AcceptEncoding).map(_ | "identity;q=1.0,*;q=0.5").lift[IO]
        .liftM[ResT]
        .flatMap(chooseEncoding(r, _))      
  }

  /* Resource Exists? */
  lazy val g7: Decision = new Decision {
    val name = "v3g7"
    def apply(r: Resource): r.Result[Decision] = {
      val variances: r.Result[String] = for {
        extra <- r.variances
        ctypes <- r.contentTypesProvided
        charsets <- r.charsetsProvided
        encodings <- r.encodingsProvided
      } yield {
        val defaults = List(
          (ctypes.length, "Accept"),
          (charsets.getOrElse(Nil).length, "Accept-Charset"),
          (encodings.getOrElse(Nil).length, "Accept-Encoding"))
        ((defaults filter { _._1 > 1}).unzip._2 ++ extra).mkString(",") 
      }

      for {
        vary <- variances
        _ <- if (vary.length > 0) ((r.dataL >=> responseHeadersL) += ((Vary, vary))).lift[IO].liftM[ResT]
             else (r.dataL >=> responseHeadersL).lift[IO].liftM[ResT]
        resourceExists <- r.resourceExists
      } yield if (resourceExists) g8 else h7
    }
  }

  /* If-Match Exists? */
  lazy val g8: Decision = new Decision {
    val name = "v3g8" 
    def apply(r: Resource): r.Result[Decision] = 
      headerExists(r, IfMatch, g9, h10)
  }

  /* If-Match: *? */
  lazy val g9: Decision = new Decision {
    val name = "v3g9"
    def apply(r: Resource): r.Result[Decision] = 
      (r.dataL >=> requestHeadersL member IfMatch)
        .map(_.filterNot(_ === "*") >| g11 | h10)
        .lift[IO]
        .liftM[ResT]
  }

  /* Etag in If-Match? */
  lazy val g11: Decision = new Decision {
    val name = "v3g11" 
    def apply(r: Resource): r.Result[Decision] = {
      testEtag(r, IfMatch) flatMap {
        doesMatch => {
          if (doesMatch) h10.point[r.Result]
          else r.haltWithCode[Decision](412)
        }
      }
    }
  }

  /* If-Match Exists? - note: this differs from v3 diagram but follows erlang implementation */
  lazy val h7: Decision = new Decision {
    val name = "v3h7"
    def apply(r: Resource): r.Result[Decision] = 
      headerExists(r, IfMatch, 412, i7)
  }

  /* If-Unmodified-Since Exists? */
  lazy val h10: Decision = new Decision {
    val name = "v3h10"
    def apply(r: Resource): r.Result[Decision] = 
      headerExists(r, IfUnmodifiedSince, h11, i12)
  }

  /* If-Unmodified-Since Valid Date? */
  lazy val h11: Decision = new Decision {
    val name = "v3h11"
    def apply(r: Resource): r.Result[Decision] = 
      validateDate(r, IfUnmodifiedSince, h12, i12)
  }

  /* Last-Modified > If-UnmodifiedSince? */
  lazy val h12: Decision = new Decision {
    val name = "v3h11"
    def apply(r: Resource): r.Result[Decision] = 
      testDate(r, IfUnmodifiedSince, halt(412), result(i12)) { _ > _ }
  }

  /* Moved Permanently? (Apply Put to Different URI?) */
  lazy val i4: Decision = new Decision {
    val name = "v3i4" 
    def apply(r: Resource): r.Result[Decision] =
      moved(r, 301, p3) { r.movedPermanently }
  }
  
  /* PUT? (after finding resource doesn't exist) */
  lazy val i7: Decision = new Decision {
    val name = "v3i7"
    def apply(r: Resource): r.Result[Decision] = (r.dataL >=> methodL).lift[IO].liftM[ResT].map { 
      method => 
        if (method === PUT) i4
        else k7
    }
  }

  /* If-None-Match Exists? */
  lazy val i12: Decision = new Decision {
    val name = "v3i12"
    def apply(r: Resource): r.Result[Decision] = 
      headerExists(r, IfNoneMatch, i13, l13)
  }

  /* If-None-Match: *? */
  lazy val i13: Decision = new Decision {
    val name = "v3i13"
    def apply(r: Resource): r.Result[Decision] = 
      (r.dataL >=> requestHeadersL member IfNoneMatch)
        .map(_.filterNot(_ === "*") >| k13 | j18)
        .lift[IO]
        .liftM[ResT]
  }

  lazy val j18: Decision = new Decision {
    val name = "v3j18" 
    def apply(r: Resource): r.Result[Decision] = (r.dataL >=> methodL).lift[IO].liftM[ResT].flatMap {
      method => 
        if (List(GET,HEAD).contains(method)) r.haltWithCode[Decision](304)
        else r.haltWithCode[Decision](412)
    }
  }

  /* Resource Moved Permanently? (not PUT request) */
  lazy val k5: Decision = new Decision {
    val name = "v3k5"
    def apply(r: Resource): r.Result[Decision] = 
      moved(r, 301,l5) { r.movedPermanently }
  }

  /* Resource Existed Previously ? */
  lazy val k7: Decision = new Decision {
    val name = "v3k7"
    def apply(r: Resource): r.Result[Decision] = r.previouslyExisted map { 
      existed => 
        if (existed) k5
        else l7
    }
  }

  lazy val k13: Decision = new Decision {
    val name = "v3k13"
    def apply(r: Resource): r.Result[Decision] = 
      testEtag(r, IfNoneMatch) map {
        doesMatch => 
          if (doesMatch) j18
          else l13
      }
  }

  /* Moved Temporarily? */
  lazy val l5: Decision = new Decision {
    val name = "v3l5"
    def apply(r: Resource): r.Result[Decision] = 
      moved(r, 307, m5) { r.movedTemporarily }
  }

  /* POST? (after determining resource d.n.e) */
  lazy val l7: Decision = new Decision {
    val name = "v3l7"
    def apply(r: Resource): r.Result[Decision] = (r.dataL >=> methodL).lift[IO].liftM[ResT].flatMap {
      method =>
        if (method === POST) m7.point[r.Result]
        else r.haltWithCode[Decision](404)
    }    

  }

  /* If-Modified-Since Exists? */
  lazy val l13: Decision = new Decision {
    val name = "v3l13"
    def apply(r: Resource): r.Result[Decision] = 
      headerExists(r, IfModifiedSince, l14, m16)
  }

  /* If-Modified-Since Valid Date? */
  lazy val l14: Decision = new Decision {
    val name = "v3l14"
    def apply(r: Resource): r.Result[Decision] = 
      validateDate(r, IfModifiedSince, l15, m16)
  }

  /* If-Modified-Since in Future? */
  lazy val l15: Decision = new Decision {
    val name = "v3l15"
    def apply(r: Resource): r.Result[Decision] = for {
      // since we have already validated the date, in the off chance something gets messed
      // up we handle an invalid date here and proceed accordingly
      headerDate <- (r.dataL >=> requestHeadersL member IfModifiedSince).map(_ | "").lift[IO].liftM[ResT]
      inFuture <- Util.parseDate(headerDate).map(_.getTime > System.currentTimeMillis).getOrElse(true).point[r.Result]
    } yield if (inFuture) m16 else l17
  }

  /* Last Modified > If-Modified-Since */
  lazy val l17: Decision = new Decision {
    val name = "v3l17"
    def apply(r: Resource): r.Result[Decision] = 
      testDate(r, IfModifiedSince, result(m16), halt(304)){ _ > _ }
  }

  /* POST? */
  lazy val m5: Decision = new Decision {
    val name = "v3m5"
    def apply(r: Resource): r.Result[Decision] = (r.dataL >=> methodL).lift[IO].liftM[ResT].flatMap {
      method => 
        if (method === POST) n5.point[r.Result]
        else r.haltWithCode[Decision](410)
    }
  }

  /* Allow Missing Post? */
  lazy val m7: Decision = new Decision {
    val name = "v3m7"
    def apply(r: Resource): r.Result[Decision] = 
      r.allowMissingPost flatMap {
        allowMissing =>
          if (allowMissing) n11.point[r.Result]
          else r.haltWithCode[Decision](404)
      }
  }

  /* DELETE? */
  lazy val m16: Decision = new Decision {
    val name = "v3m16"
    def apply(r: Resource): r.Result[Decision] = (r.dataL >=> methodL).lift[IO].liftM[ResT] map {
      method => if (method === DELETE) m20 else n16
    }
  }

  /* Delete Enacted? */
  lazy val m20: Decision = new Decision {
    val name = "v3m20"
    def apply(r: Resource): r.Result[Decision] = 
      r.deleteResource flatMap {
        ok =>
          if (ok) m20b.point[r.Result]
          else r.haltWithCode[Decision](500)
      }
  }

  /* Delete Enacted? */
  lazy val m20b: Decision = new Decision {
    val name = "v3m20b"
    def apply(r: Resource): r.Result[Decision] = 
      r.deleteCompleted flatMap {
        completed =>
          if (completed) o20.point[r.Result]
          else r.haltWithCode[Decision](202)
      }
  }

  /* Resource allows POST to missing resource */
  lazy val n5: Decision = new Decision {
    val name = "v3n5"
    def apply(r: Resource): r.Result[Decision] = 
      r.allowMissingPost flatMap {
        allowMissing =>
          if (allowMissing) n11.point[r.Result]
          else r.haltWithCode[Decision](410)
      }
  }

  /* Redirect? (also handle POST requests here) */
  lazy val n11: Decision = new Decision {
    val name = "v3n11"
    def apply(r: Resource): r.Result[Decision] = {
      val processPost: r.Result[Unit] = for {
        processedOk <- r.processPost
        _ <- if (processedOk) encodeBodyIfSet(r)
             else resT[r.ReqRespState](error[Decision]("failed to process post").point[r.ReqRespState])
      } yield ()

      val createPath: r.Result[Unit] = for {
        mbCreatePath <- r.createPath
        createPath <- resT[r.ReqRespState](
          mbCreatePath.cata(
            none = error[String]("create path returned none"),
            some = result(_)
          ).point[r.ReqRespState]
        )

        // set dispatch path to new path
        _ <- ((r.dataL >=> dispPathL) := createPath).lift[IO].liftM[ResT]

        // set location header if its not already set
        mbExistingLoc <- (r.dataL >=> responseHeadersL member Location).lift[IO].liftM[ResT]
        baseUri <- (r.dataL >=> baseUriL).lift[IO].liftM[ResT]
        path <- (r.dataL >=> pathL).lift[IO].liftM[ResT]
        _ <- mbExistingLoc
              .map(_ => (r.dataL >=> responseHeadersL member Location).st)
              .getOrElse(((r.dataL >=> responseHeadersL member Location) := Some(List(baseUri,path,createPath).mkString("/"))))
              .lift[IO]
              .liftM[ResT]

        _ <- acceptContent(r)

      } yield ()

      for {
        postIsCreate <- r.postIsCreate
        _ <- if (postIsCreate) createPath else processPost
        doRedirect <- (r.dataL >=> doRedirectL).lift[IO].liftM[ResT]
        mbLoc <- (r.dataL >=> responseHeadersL member Location).lift[IO].liftM[ResT]
        decision <-
          if (doRedirect)
            mbLoc
              .map(_ => r.haltWithCode[Decision](303))
              .getOrElse(resT[r.ReqRespState](error[Decision]("redirect with no location").point[r.ReqRespState]))
          else p11.point[r.Result]
      } yield decision
    }
  }

  /* POST? */
  lazy val n16: Decision = new Decision {
    val name = "v3n16"
    def apply(r: Resource): r.Result[Decision] = 
      (r.dataL >=> methodL).lift[IO].liftM[ResT] map {
        method => if (method === POST) n11 else o16
      }
  }

  /* Is Conflict? (PUT requests are also handled here) */
  lazy val o14: Decision = new Decision {
    val name = "v3o14"
    def apply(r: Resource): r.Result[Decision] = for {
      isConflict <- r.isConflict
      _ <- if (isConflict) r.haltWithCode[Boolean](409)
           else acceptContent(r)
    } yield p11
  }

  /* PUT? */
  lazy val o16: Decision = new Decision {
    val name = "v3016"
    def apply(r: Resource): r.Result[Decision] = 
      (r.dataL >=> methodL).lift[IO].liftM[ResT] map {
        method => if (method === PUT) o14 else o18
      }
  }

  /* Multiple Representations?  also do GET/HEAD body rendering here */
  lazy val o18: Decision = new Decision {
    val name = "v3o18"
    def apply(r: Resource): r.Result[Decision] = {
      def ifGetOrHead[A](isTrue: Boolean, resourceCall: => r.Result[A], default: => A): r.Result[A] =
        if (isTrue) resourceCall
        else default.point[r.Result]

      def setHeader(header: HTTPHeader, value: Option[String]): r.Result[Option[String]] = 
        ((r.dataL >=> responseHeadersL member header) := value).lift[IO].liftM[ResT]

      val setBody: r.Result[Unit] = for {
        // find content providing function given chosen content type and produce body, setting it in the response
        mbChosenCType <- (r.dataL >=> metadataL >=> contentTypeL).lift[IO].liftM[ResT]
        chosenCType <- resT[r.ReqRespState](
          mbChosenCType.cata(
            none = error[ContentType]("internal flow error, missing chosen ctype in o18"), 
            some = result(_)
          ).point[r.ReqRespState]
        )

        mbProvidedF <- r.contentTypesProvided map {
          _.find(_._1 == chosenCType).map(_._2)
        }

        producedBody <- mbProvidedF | HTTPBody.Empty.point[r.Result]
        body <- encodeBody(r, producedBody)
        _ <- ((r.dataL >=> respBodyL) := body).lift[IO].liftM[ResT]
      } yield ()

      for {
        doBody <- (r.dataL >=> methodL).map(m => m === GET || m === HEAD).lift[IO].liftM[ResT]
        // set Etag, last mod, and expires if GET or HEAD and they are provided by resource
        mbEtag <- ifGetOrHead(doBody, r.generateEtag, none[String])
        mbLastMod <- ifGetOrHead(doBody, r.lastModified, none[Date])
        mbExpires <- ifGetOrHead(doBody, r.expires, none[Date])
        _ <- setHeader(ETag, mbEtag)
        _ <- setHeader(LastModified, mbLastMod.map(Util.formatDate(_)))
        _ <- setHeader(Expires, mbExpires.map(Util.formatDate(_)))
        _ <- if (doBody) setBody else ().point[r.Result]

        // determine if response has multiple choices
        mc <- r.multipleChoices
        decision <-
          if (mc) r.haltWithCode[Decision](300) 
          else r.haltWithCode[Decision](200)
      } yield decision // we will never actually get to this yield
    }

  }

  /* Does Response Have Entity? (response body empty? */
  lazy val o20: Decision = new Decision {
    val name = "v3o20"
    def apply(r: Resource): r.Result[Decision] =
      (r.dataL >=> respBodyL).lift[IO].liftM[ResT] flatMap {
        body => 
          if (body.isEmpty) r.haltWithCode[Decision](204)
          else o18.point[r.Result]
      }
  }

  /* Is Conflict? (identical impl to o14) */
  lazy val p3: Decision = new Decision {
    val name = "v3p3"
    def apply(r: Resource): r.Result[Decision] = for {
        isConflict <- r.isConflict
         _ <- if (isConflict) r.haltWithCode[Boolean](409)
              else acceptContent(r)
      } yield p11
  }

  /* New Resource? (basically, is location header set?) */
  lazy val p11: Decision = new Decision {
    val name = "v3p11"
    def apply(r: Resource): r.Result[Decision] = 
      (r.dataL >=> responseHeadersL member Location).lift[IO].liftM[ResT] flatMap {
        mbLoc =>
          if (mbLoc.isDefined) r.haltWithCode[Decision](201)
          else o20.point[r.Result]
      }
  }

  /* Helper Functions */   
  private def chooseCharset(r: Resource, acceptHeader: String): r.Result[Decision] = {

    def doChoose(mbProvided: Resource.CharsetsProvided): Res[Option[String]] =
      mbProvided.map { provided =>
        Util.chooseCharset(provided.unzip._1, acceptHeader)
          .map(c => result(some(c)))
          .getOrElse(halt(406))
      } getOrElse { result(none) }

    
    for {
      mbProvided <- r.charsetsProvided
      mbChosen <- resT[r.ReqRespState](doChoose(mbProvided).point[r.ReqRespState])
      _ <- ((r.dataL >=> metadataL >=> chosenCharsetL) := mbChosen).lift[IO].liftM[ResT]
    } yield f6

  }
  
  private def chooseEncoding(r: Resource, headerValue: String): r.Result[Decision] = {

    def doChoose(mbProvided: Resource.EncodingsProvided): Res[Option[String]] =
      mbProvided.map { provided =>
        Util.chooseEncoding(provided.unzip._1, headerValue: String)
          .map(e => result(some(e)))
          .getOrElse(halt(406))
      } getOrElse result(none)

    for {
      provided <- r.encodingsProvided
      mbChosen <- resT[r.ReqRespState](doChoose(provided).point[r.ReqRespState])
      _ <- ((r.dataL >=> metadataL >=> chosenEncodingL) := mbChosen).lift[IO].liftM[ResT]
      _ <- ((r.dataL >=> responseHeadersL member ContentEncoding) := (mbChosen filterNot { _ === "identity" }))
             .lift[IO]
             .liftM[ResT]
    } yield g7
  }

  private def headerExists(r: Resource, header: HTTPHeader, exists: Decision, dne: Decision): r.Result[Decision] = 
    (r.dataL >=> requestHeadersL member header).map(_ >| exists | dne).lift[IO].liftM[ResT]

  private def headerExists(r: Resource, header: HTTPHeader, existsCode: Int, dne: Decision): r.Result[Decision] = 
    (r.dataL >=> requestHeadersL member header).map(_.isDefined).lift[IO].liftM[ResT].flatMap {
      isDefined => 
        if (isDefined) r.haltWithCode[Decision](existsCode)
        else dne.point[r.Result]
    }

  private def validateDate(r: Resource, 
                           headerName: HTTPHeader,
                           valid: Decision,
                           invalid: Decision): r.Result[Decision] = {
    val validate: State[(ReqRespData,r.Context),Decision] = for {
      // if we have reached here we have verified the header has value already so we default
      // empty string which should never be reached
      iums <- (r.dataL >=> requestHeadersL member headerName).map( _ getOrElse "")
      isValid <- (Util.parseDate(iums) >| true | false).point[({type S[X]=State[(ReqRespData, r.Context),X]})#S]
    } yield if (isValid) valid else invalid
    
    validate.lift[IO].liftM[ResT]
  }

  private def testDate(r: Resource,
                       headerName: HTTPHeader,
                       modified: Res[Decision],
                       notModified: Res[Decision])(test: (Long,Long) => Boolean): r.Result[Decision] = {

    def isModified(mbLastMod: Option[Date], mbHeaderDate: Option[String]) =
      ^(mbLastMod, mbHeaderDate.map(Util.parseDate(_)).getOrElse(none)) {
        (t1,t2) => test(t1.getTime,t2.getTime)
      } getOrElse false

    for {
      mbIums <- (r.dataL >=> requestHeadersL member headerName).lift[IO].liftM[ResT]
      mbLastMod <- r.lastModified      
      decision <- if (isModified(mbLastMod, mbIums)) resT[r.ReqRespState](modified.point[r.ReqRespState])
                  else resT[r.ReqRespState](notModified.point[r.ReqRespState])
    } yield decision

  }
  
  private def moved(r: Resource, movedCode: Int, notMoved: Decision)(resourceCall: => r.Result[Option[String]]): r.Result[Decision] = {
    val setLocation = for {
      loc <- optionT[r.Result](resourceCall)
      _ <- optionT[r.Result] {
        ((r.dataL >=> responseHeadersL) += ((Location, loc))).lift[IO].liftM[ResT].map(Option(_))
      }
    } yield loc
    
    setLocation.run flatMap {
      mbLocation => 
        if (mbLocation.isDefined) r.haltWithCode[Decision](movedCode)
        else notMoved.point[r.Result]     
    }
  }

  private def testEtag(r: Resource, header: HTTPHeader): r.Result[Boolean] = {
    val isMatch: OptionT[r.Result, Boolean] = for {
      etag <- optionT[r.Result](r.generateEtag)
      matches <- optionT[r.Result]((r.dataL >=> requestHeadersL member header).lift[IO].liftM[ResT])
    } yield matches.split(",").map(_.trim).toList.contains(etag)
    
    isMatch getOrElse false
  }

  private def encodeBodyIfSet(r: Resource): r.Result[Unit] = for {
    body <- (r.dataL >=> respBodyL).lift[IO].liftM[ResT]
    newBody <- if (!body.isEmpty) encodeBody(r, body) else body.point[r.Result]
    _ <- ((r.dataL >=> respBodyL) := newBody).lift[IO].liftM[ResT]
  } yield ()

  private def encodeBody(r: Resource, body: HTTPBody): r.Result[HTTPBody] = {
    val id: Array[Byte] => Array[Byte] = identity
    for {
      mbProvidedCh <- r.charsetsProvided
      mbProvidedEnc <- r.encodingsProvided
      mbCharset <- (r.dataL >=> metadataL >=> chosenCharsetL).lift[IO].liftM[ResT]
      mbEncoding <- (r.dataL >=> metadataL >=> chosenEncodingL).lift[IO].liftM[ResT]
      
      charsetter <- ((^(mbProvidedCh,mbCharset) {
        (p,c)  => p.find(_._1 === c)
      }).join.cata(
        none = id,
        some = _._2
      )).point[r.Result]
      encoder <- ((^(mbProvidedEnc,mbEncoding) {
        (p,e) => p.find(_._1 === e)
      }).join.cata(
        none = id,
        some = _._2
      )).point[r.Result]

    } yield body match {
      case FixedLengthBody(bytes) => FixedLengthBody(encoder(charsetter(bytes)))
      case LazyStreamBody(streamer) => LazyStreamBody(streamer.map(_.map {
        case HTTPBody.ByteChunk(bytes) => HTTPBody.ByteChunk(encoder(charsetter(bytes)))
        case otherChunk => otherChunk
      }))
    }
  }

  private def acceptContent(r: Resource): r.Result[Boolean] = {
    val reqCType: OptionT[r.Result, ContentType] = for {
      contentType <- optionT[r.Result]((r.dataL >=> requestHeadersL member ContentTypeHeader).lift[IO].liftM[ResT])
      mediaInfo <- optionT[r.Result](Util.acceptToMediaTypes(contentType).headOption.point[r.Result])
    } yield mediaInfo.mediaRange

    for {
      // get request content type
      contentType <- reqCType getOrElse ContentType("application/octet-stream")

      // lookup content types accepted and find body prod function
      acceptedList <- r.contentTypesAccepted
      mbAcceptableF <- acceptedList.find(_._1 === contentType).map(_._2).point[r.Result]

      // if found, run it, call encodeBodyIfSet if it succeeds, 500 otherwise
      // if not found, return halt 415
      didSucceed <- mbAcceptableF | r.haltWithCode[Boolean](415)
      _ <-
        if (didSucceed) encodeBodyIfSet(r)
        else r.haltWithCode[Unit](500) // TODO: real error message
    } yield didSucceed
  }

  private def defaultContentType: ContentType = ContentType("text/plain")

}
