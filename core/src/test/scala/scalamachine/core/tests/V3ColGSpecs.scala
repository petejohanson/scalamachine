package scalamachine.core.tests

import org.specs2._
import mock._
import scalamachine.core._
import Resource._
import v3.WebmachineDecisions
import HTTPHeaders._


class V3ColGSpecs extends Specification with Mockito with SpecsHelper with WebmachineDecisions { def is =
  "Webmachine V3 Column G".title                                                    ^
                                                                                    p^
  "G7 - Resource Exists?"                                                           ^
    "Sets the Vary header after conneg"                                             ^
      "vary header contains all values if all 3 headers were used in conneg"        ! testVaryAll ^
      "vary header does not contain accept if list has size 0"                      ! testVaryContentTypesProvided0 ^
      "vary header does not contain accept if list has size 1"                      ! testVaryContentTypesProvided1 ^
      "vary header does not contain accept-charset if short circuited"              ! testVaryCharsetsShortCircuit ^
      "vary header does not contain accept-charset if list has size 0"              ! testVaryCharsetsProvided0 ^
      "vary header does not contain accept-charset if list has size 1"              ! testVaryCharsetsProvided1 ^
      "vary header does not contain accept-encoding if short circuited"             ! testVaryEncodingsShortCircuit ^
      "vary header does not contain accept-encoding if list has size 0"             ! testVaryEncodingsProvided0 ^
      "vary header does not contain accept-encoding if list has size 1"             ! testVaryEncodingsProvided1 ^
      "if resource returns non-empty list, those values are additional"             ! testVaryResourceAdditional ^p^
    "if resource exists, returns decision G8"                                       ! testResourceExistsTrue ^
    "otherwise H7 returned"                                                         ! testResourceExistsFalse ^
                                                                                    p^
  "G8 - If-Match Exists?"                                                           ^
    "if If-Match header exists, G9 is returned"                                     ! testG8IfMatchExists ^
    "otherwise H10 is returned"                                                     ! testG8IfMatchMissing ^
                                                                                    p^
  "G9 - If-Match: *?"                                                               ^
    """if If-Match has value "*", H10 is returned"""                                ! testIfMatchStar ^
    "otherwise G11 is returned"                                                     ! testIfMatchNotStar ^
                                                                                    p^
  "G11 - ETag in If-Match"                                                          ^
    "if ETag for resource is in list of etags in If-Match, H10 is returned"         ! testIfMatchHasEtag ^
    "otherwise a response with code 412 is returned"                                ! testIfMatchMissingEtag ^
                                                                                    end

  def testVaryAll = {
    import Res._
    val ctypes: ContentTypesProvided = List(
      contentTypeProvided(ContentType("text/plain"), defaultResp, result(FixedLengthBody(""))),
      contentTypeProvided(ContentType("application/json"), defaultResp, result(FixedLengthBody("")))
    )

    val charsets: CharsetsProvided = Some(("charset1", identity[Array[Byte]](_)) :: ("charset2", identity[Array[Byte]](_)) :: Nil)
    val encodings: EncodingsProvided = Some(("identity", identity[Array[Byte]](_)) :: ("gzip", identity[Array[Byte]](_)) :: Nil)
    testDecisionResultHasData(
      g7,
      resource => {
        resource.contentTypesProvided(any) returns mkMbAnswer(ctypes)
        resource.charsetsProvided(any) returns ValueRes(charsets)
        resource.encodingsProvided(any) returns ValueRes(encodings)
        resource.variances(any) returns ValueRes(Nil)
        resource.resourceExists(any) returns mkMbAnswer(true)
      }
    ) {
      _.responseHeader(Vary) must beSome.like {
        case vary => vary must contain("Accept") and contain("Accept-Encoding") and contain("Accept-Charset")
      }
    }
  }

  def testVaryContentTypesProvided0 = {
    import Res._
    val ctypes: ContentTypesProvided = Nil
    val charsets: CharsetsProvided = Some(("charset1", identity[Array[Byte]](_)) :: ("charset2", identity[Array[Byte]](_)) :: Nil)
    val encodings: EncodingsProvided = Some(("identity", identity[Array[Byte]](_)) :: ("gzip", identity[Array[Byte]](_)) :: Nil)
    testDecisionResultHasData(
      g7,
      resource => {
        resource.contentTypesProvided(any) returns mkMbAnswer(ctypes)
        resource.charsetsProvided(any) returns ValueRes(charsets)
        resource.encodingsProvided(any) returns ValueRes(encodings)
        resource.variances(any) returns ValueRes(Nil)
        resource.resourceExists(any) returns mkMbAnswer(true)
      }
    ) {
      _.responseHeader(Vary) must beSome.like {
        case vary => vary must not =~("""Accept[^-]""")
      }
    }
  }

  def testVaryContentTypesProvided1 = {
    import Res._
    val ctypes: ContentTypesProvided = List(contentTypeProvided(ContentType("application/json"), defaultResp, result(FixedLengthBody(""))))
    val charsets: CharsetsProvided = Some(("charset1", identity[Array[Byte]](_)) :: ("charset2", identity[Array[Byte]](_)) :: Nil)
    val encodings: EncodingsProvided = Some(("identity", identity[Array[Byte]](_)) :: ("gzip", identity[Array[Byte]](_)) :: Nil)
    testDecisionResultHasData(
      g7,
      resource => {
        resource.contentTypesProvided(any) returns mkMbAnswer(ctypes)
        resource.charsetsProvided(any) returns ValueRes(charsets)
        resource.encodingsProvided(any) returns ValueRes(encodings)
        resource.variances(any) returns ValueRes(Nil)
        resource.resourceExists(any) returns mkMbAnswer(true)
      }
    ) {
      _.responseHeader(Vary) must beSome.like {
        case vary => vary must not =~("""Accept[^-]""")
      }
    }
  }

  def testVaryCharsetsShortCircuit = {
    import Res._
    val ctypes: ContentTypesProvided = List(
      contentTypeProvided(ContentType("text/plain"), defaultResp, result(FixedLengthBody(""))),
      contentTypeProvided(ContentType("application/json"), defaultResp, result(FixedLengthBody("")))
    )

    val charsets: CharsetsProvided = None
    val encodings: EncodingsProvided = Some(("identity", identity[Array[Byte]](_)) :: ("gzip", identity[Array[Byte]](_)) :: Nil)
    testDecisionResultHasData(
      g7,
      resource => {
        resource.contentTypesProvided(any) returns mkMbAnswer(ctypes)
        resource.charsetsProvided(any) returns ValueRes(charsets)
        resource.encodingsProvided(any) returns ValueRes(encodings)
        resource.variances(any) returns ValueRes(Nil)
        resource.resourceExists(any) returns mkMbAnswer(true)
      }
    ) {
      _.responseHeader(Vary) must beSome.like {
        case vary => vary must not contain("Accept-Charset")
      }
    }
  }

  def testVaryCharsetsProvided0 = {
    import Res._
    val ctypes: ContentTypesProvided = List(
      contentTypeProvided(ContentType("text/plain"), defaultResp, result(HTTPBody.Empty)),
      contentTypeProvided(ContentType("application/json"), defaultResp, result(HTTPBody.Empty))
    )

    val charsets: CharsetsProvided = Some(Nil)
    val encodings: EncodingsProvided = Some(("identity", identity[Array[Byte]](_)) :: ("gzip", identity[Array[Byte]](_)) :: Nil)
    testDecisionResultHasData(
      g7,
      resource => {
        resource.contentTypesProvided(any) returns mkMbAnswer(ctypes)
        resource.charsetsProvided(any) returns ValueRes(charsets)
        resource.encodingsProvided(any) returns ValueRes(encodings)
        resource.variances(any) returns ValueRes(Nil)
        resource.resourceExists(any) returns mkMbAnswer(true)
      }
    ) {
      _.responseHeader(Vary) must beSome.like {
        case vary => vary must not contain("Accept-Charset")
      }
    }
  }

  def testVaryCharsetsProvided1 = {
    import Res._
    val ctypes: ContentTypesProvided = List(
      contentTypeProvided(ContentType("text/plain"), defaultResp, result(HTTPBody.Empty)),
      contentTypeProvided(ContentType("application/json"), defaultResp, result(HTTPBody.Empty))
    )

    val charsets: CharsetsProvided = Some(("charset2", identity[Array[Byte]](_)) :: Nil)
    val encodings: EncodingsProvided = Some(("identity", identity[Array[Byte]](_)) :: ("gzip", identity[Array[Byte]](_)) :: Nil)
    testDecisionResultHasData(
      g7,
      resource => {
        resource.contentTypesProvided(any) returns mkMbAnswer(ctypes)
        resource.charsetsProvided(any) returns ValueRes(charsets)
        resource.encodingsProvided(any) returns ValueRes(encodings)
        resource.variances(any) returns ValueRes(Nil)
        resource.resourceExists(any) returns mkMbAnswer(true)
      }
    ) {
      _.responseHeader(Vary) must beSome.like {
        case vary => vary must not contain("Accept-Charset")
      }
    }
  }

  def testVaryEncodingsShortCircuit = {
    import Res._
    val ctypes: ContentTypesProvided = List(
      contentTypeProvided(ContentType("text/plain"), defaultResp, result(HTTPBody.Empty)),
      contentTypeProvided(ContentType("application/json"), defaultResp, result(HTTPBody.Empty))
    )

    val charsets: CharsetsProvided = Some(("charset1", identity[Array[Byte]](_)) :: ("charset2", identity[Array[Byte]](_)) :: Nil)
    val encodings: EncodingsProvided = None
    testDecisionResultHasData(
      g7,
      resource => {
        resource.contentTypesProvided(any) returns mkMbAnswer(ctypes)
        resource.charsetsProvided(any) returns ValueRes(charsets)
        resource.encodingsProvided(any) returns ValueRes(encodings)
        resource.variances(any) returns ValueRes(Nil)
        resource.resourceExists(any) returns mkMbAnswer(true)
      }
    ) {
      _.responseHeader(Vary) must beSome.like {
        case vary => vary must not contain("Accept-Encoding")
      }
    }

  }

  def testVaryEncodingsProvided0 = {
    import Res._
    val ctypes: ContentTypesProvided = List(
      contentTypeProvided(ContentType("text/plain"), defaultResp, result(HTTPBody.Empty)),
      contentTypeProvided(ContentType("application/json"), defaultResp, result(HTTPBody.Empty))
    )

    val charsets: CharsetsProvided = Some(("charset1", identity[Array[Byte]](_)) :: ("charset2", identity[Array[Byte]](_)) :: Nil)
    val encodings: EncodingsProvided = Some(Nil)
    testDecisionResultHasData(
      g7,
      resource => {
        resource.contentTypesProvided(any) returns mkMbAnswer(ctypes)
        resource.charsetsProvided(any) returns ValueRes(charsets)
        resource.encodingsProvided(any) returns ValueRes(encodings)
        resource.variances(any) returns ValueRes(Nil)
        resource.resourceExists(any) returns mkMbAnswer(true)
      }
    ) {
      _.responseHeader(Vary) must beSome.like {
        case vary => vary must not contain("Accept-Encoding")
      }
    }
  }

  def testVaryEncodingsProvided1 = {
    import Res._
    val ctypes: ContentTypesProvided = List(
      contentTypeProvided(ContentType("text/plain"), defaultResp, result(HTTPBody.Empty)),
      contentTypeProvided(ContentType("application/json"), defaultResp, result(HTTPBody.Empty))
    )

    val charsets: CharsetsProvided = Some(("charset1", identity[Array[Byte]](_)) :: ("charset2", identity[Array[Byte]](_)) :: Nil)
    val encodings: EncodingsProvided = Some(("gzip", identity[Array[Byte]](_)) :: Nil)
    testDecisionResultHasData(
      g7,
      resource => {
        resource.contentTypesProvided(any) returns mkMbAnswer(ctypes)
        resource.charsetsProvided(any) returns ValueRes(charsets)
        resource.encodingsProvided(any) returns ValueRes(encodings)
        resource.variances(any) returns ValueRes(Nil)
        resource.resourceExists(any) returns mkMbAnswer(true)
      }
    ) {
      _.responseHeader(Vary) must beSome.like {
        case vary => vary must not contain("Accept-Encoding")
      }
    }
  }

  def testVaryResourceAdditional = {
    import Res._
    val ctypes: ContentTypesProvided = List(
      contentTypeProvided(ContentType("text/plain"), defaultResp, result(HTTPBody.Empty)),
      contentTypeProvided(ContentType("application/json"), defaultResp, result(HTTPBody.Empty))
    )

    val charsets: CharsetsProvided = Some(("charset1", identity[Array[Byte]](_)) :: ("charset2", identity[Array[Byte]](_)) :: Nil)
    val encodings: EncodingsProvided = Some(("identity", identity[Array[Byte]](_)) :: ("gzip", identity[Array[Byte]](_)) :: Nil)
    testDecisionResultHasData(
      g7,
      resource => {
        resource.contentTypesProvided(any) returns mkMbAnswer(ctypes)
        resource.charsetsProvided(any) returns ValueRes(charsets)
        resource.encodingsProvided(any) returns ValueRes(encodings)
        resource.variances(any) returns ValueRes("One" :: "Two" :: Nil)
        resource.resourceExists(any) returns mkMbAnswer(true)
      }
    ) {
      _.responseHeader(Vary) must beSome.like {
        case vary => vary must contain("One") and contain("Two")
      }
    }
  }

  def testResourceExistsTrue = {
    testDecisionReturnsDecision(
      g7,
      g8,
      resource => {
        resource.contentTypesProvided(any) returns mkMbAnswer(Nil)
        resource.charsetsProvided(any) returns ValueRes(None)
        resource.encodingsProvided(any) returns ValueRes(None)
        resource.variances(any) returns ValueRes(Nil)
        resource.resourceExists(any) returns mkMbAnswer(true)
      }
    )
  }

  def testResourceExistsFalse = {
    testDecisionReturnsDecision(
      g7,
      h7,
      resource => {
        resource.contentTypesProvided(any) returns mkMbAnswer(Nil)
        resource.charsetsProvided(any) returns ValueRes(None)
        resource.encodingsProvided(any) returns ValueRes(None)
        resource.variances(any) returns ValueRes(Nil)
        resource.resourceExists(any) returns mkMbAnswer(false)
      }
    )
  }

  def testG8IfMatchExists = {
    testDecisionReturnsDecision(g8,g9, r => {}, data = createData(headers = Map(IfMatch -> "*")))
  }

  def testG8IfMatchMissing = {
    testDecisionReturnsDecision(g8,h10, r => {})
  }

  def testIfMatchStar = {
    testDecisionReturnsDecision(g9,h10, r => {}, data = createData(headers = Map(IfMatch -> "*")))
  }

  def testIfMatchNotStar = {
    testDecisionReturnsDecision(g9,g11, r => {}, data = createData(headers = Map(IfMatch -> "1")))
  }

  def testIfMatchHasEtag = {
    testDecisionReturnsDecision(g11,h10,_.generateEtag(any) answers mkAnswer(Some("1")), data = createData(headers = Map(IfMatch -> "1,2")))
  }

  def testIfMatchMissingEtag = {
    testDecisionReturnsData(g11,_.generateEtag(any) answers mkAnswer(None), data = createData(headers = Map(IfMatch -> "1"))) {
      _.statusCode must beEqualTo(412)
    }
  }

}