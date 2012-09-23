package scalamachine.core.tests
package v3

import org.specs2._
import mock._
import org.mockito.{Matchers => MM}
import scalamachine.core._
import Resource._
import v3.WebmachineDecisions
import HTTPHeaders._
import HTTPMethods._
import ReqRespData.Metadata
import scalaz.syntax.monad._


class V3ColCDEFSpecs extends Specification with Mockito with SpecsHelper with WebmachineDecisions { def is =
  "Webmachine V3 Column C, D, E & F".title                                          ^
                                                                                    p^
  "C3 - Accept Exists?"                                                             ^
    "If the Accept header doesn't exist"                                            ^
      "D4 is returned and 1st type in resources provided list is set in metadata"   ! testMissingAcceptHeader ^
      "If provided list empty, text/plain is set in metadata, D4 still returned"    ! testMissingAcceptEmptyProvidedList ^p^
    "If the Accept header exists decision C4 is returned"                           ! testAcceptHeaderExists ^
                                                                                    p^
  "C4 - Acceptable Media Type Available?"                                           ^
    "if the media type is provided by the resource"                                 ^
      "Decision D4 is returned & the mediatype is set as content type in metadata"  ! testMediaTypeProvided ^p^
    "if the media type is not provided by the resource"                             ^
      "halts with code 406"                                                         ! testMediaTypeNotProvided ^
                                                                                    p^p^
  "D4 - Accept-Language Exists?"                                                    ^
    "if Accept-Language header exists decision D5 is returned"                      ! testHasAcceptLanguage ^
    "otherwise decision E5 is returned"                                             ! testMissingAcceptLanguage ^
                                                                                    p^
  "D5 - Accept-Language Availble?"                                                  ^
    "asks resource if language is available"                                        ^
      "if it is, decision E5 is returned"                                           ! testIsLanguageAvailableTrue ^
      "otherwise, halts with code 406"                                              ! testIsLanguageAvailableFalse ^
                                                                                    p^p^
  "E5 - Accept-Charset Exists?"                                                     ^
    "If the Accept-Charset header exists decision E6 is returned"                   ! testAcceptCharsetExists ^
    "Otherwise"                                                                     ^
      """If "*" charset is acceptable to resource"""                                ^
        "decision F6 is returned"                                                   ! testAcceptMissingStarAcceptable ^
        "first charset provided by resource is set as chosen in metadata"           ! testAcceptMissingStarOkCharsetChosen ^p^
      "If resource specifies charset negotioation short circuting, F6 is returned"  ! testAcceptMissingCharsetNegShortCircuit ^
      "otherwise, halts with code 406"                                              ! testAcceptMissingStarNotAcceptable ^
                                                                                    p^p^
  "E6 - Accept-Charset Available?"                                                  ^
    "If resource specifies charset negotiation short circuting, F6 is returned"     ! testAcceptExistsCharsetNegShortCircuit ^
    "If the charset is provided by the resource, F6 returned, chosen set in meta"   ! testAcceptExistsAcceptableSetInMeta ^
    "If charset is not provided by the resource, halts with code 406 returned"      ! testAcceptExistsNotAcceptable ^
                                                                                    p^
  "F6 - Accept-Encoding Exists?"                                                    ^
    "sets the chosen content type/charset in response content type header"          ^
      """if both are None, "text/plain" is set"""                                   ! testF6MediaAndCharsetNotChosen ^
      "if just the content type is Some, its string value is set"                   ! testF6MediaChosenCharsetNot ^
      """if just the charset is Some, "text/plain; charset=<value>" is set"""       ! testF6CharsetChosenMediaNot ^
      "if both are set the entire string is set"                                    ! testF6MediaAndCharsetChosen ^p^
    "if the accept-encoding header exists, decision F7 is returned"                 ! testAcceptEncodingExists ^
    "if the accept-encoding header is missing"                                      ^
      """if "identity;q=1.0,*;q=0.5" is acceptable"""                               ^
        "chosen is set as the value of Content-Encoding header,in meta, G7 returned"! testAcceptEncodingMissingDefaultAcceptable ^p^
      "otherwise, halts with code 406"                                              ! testAcceptEncodingMissingDefaultNotAcceptable ^
                                                                                    p^p^
  "F7 - Accept Encoding Available?"                                                 ^
    "If resource specifies encoding neg. short circuiting, G7 returned"             ! testAcceptEncodingExistsShortCircuit ^
    "If charset is provided by the resource, G7 returned, chosen set in resp./meta" ! testAcceptEncodingExistsAcceptable ^
    "If charset is not provided, halts wit code 406"                                ! testAcceptEncodingExistsNotAcceptable ^
                                                                                    end

  // TODO: change D5 to do real language negotiation like ruby webmachine implementation

  def testMissingAcceptHeader = {
    val ctypesBase = ContentType("text/html") :: ContentType("text/plain") :: Nil
    val fakeBody: HTTPBody = FixedLengthBody("".getBytes)
    def stub(r: Resource) {
      val ctypes: r.ContentTypesProvided =      
        (ctypesBase, List.fill(2)(fakeBody.point[r.Result])).zipped.toList

      r.contentTypesProvided returns ctypes.point[r.Result]
    }

    testDecisionReturnsDecisionAndData(c3,d4,stub(_)) {
      _.metadata.contentType must beSome.like {
        case ct => ct must beEqualTo(ctypesBase.head)
      }
    }
  }

  def testMissingAcceptEmptyProvidedList = {
    def stub(r: Resource) {
      val ctypes: r.ContentTypesProvided = Nil
      r.contentTypesProvided returns ctypes.point[r.Result]
    }

    testDecisionReturnsDecisionAndData(c3,d4,stub(_)) {
      _.metadata.contentType must beSome.like {
        case ct => ct must beEqualTo(ContentType("text/plain"))
      }
    }
  }

  def testAcceptHeaderExists = {
    testDecisionReturnsDecision(
      c3,
      c4,
      r => r.contentTypesProvided returns (Nil: r.ContentTypesProvided).point[r.Result],
      data = createData(headers = Map(Accept -> "text/html")))
  }

  def testMediaTypeNotProvided = {
    def stub(r: Resource) {
      val ctypes: r.ContentTypesProvided = 
        (ContentType("text/html"), (FixedLengthBody("".getBytes): HTTPBody).point[r.Result]) :: Nil

      r.contentTypesProvided returns ctypes.point[r.Result]
    }

    testDecisionHaltsWithCode(c4, 406, stub(_), data = createData(headers = Map(Accept -> "text/plain")))
  }

  def testMediaTypeProvided = {
    def stub(r: Resource) {
      val ctypes: r.ContentTypesProvided = 
        (ContentType("text/html"), (FixedLengthBody("".getBytes): HTTPBody).point[r.Result]) :: Nil

      r.contentTypesProvided returns ctypes.point[r.Result]
    }

    testDecisionReturnsDecisionAndData(c4,d4,stub(_), data = createData(headers = Map(Accept -> "text/html"))) {
      _.metadata.contentType must beSome.like {
        case ct => ct must beEqualTo(ContentType("text/html"))
      }
    }
  }

  def testMissingAcceptLanguage = {
    testDecisionReturnsDecision(d4,e5,r => {})
  }

  def testHasAcceptLanguage = {
    testDecisionReturnsDecision(d4,d5,r => {},data = createData(headers = Map(AcceptLanguage -> "en/us")))
  }

  def testIsLanguageAvailableFalse = {
    testDecisionHaltsWithCode(d5, 406, r => r.isLanguageAvailable returns false.point[r.Result], data = createData(headers = Map(AcceptLanguage -> "en/us")))
  }

  def testIsLanguageAvailableTrue = {
    testDecisionReturnsDecision(d5,e5, r => r.isLanguageAvailable returns true.point[r.Result], data = createData(headers = Map(AcceptLanguage-> "en/us")))
  }

  def testAcceptCharsetExists = {
    testDecisionReturnsDecision(e5,e6,r => {}, data = createData(headers = Map(AcceptCharset -> "*")))
  }

  def testAcceptMissingStarAcceptable = {
    val provided: CharsetsProvided = Some(("abc", identity[Array[Byte]](_)) :: Nil)
    testDecisionReturnsDecision(e5,f6, r => r.charsetsProvided returns provided.point[r.Result])
  }

  def testAcceptMissingStarOkCharsetChosen = {
    val provided: CharsetsProvided = Some(("abc", identity[Array[Byte]](_)) :: Nil)
    testDecisionReturnsDecisionAndData(e5,f6, r => r.charsetsProvided returns provided.point[r.Result]) {
      _.metadata.chosenCharset must beSome.like { case c => c must beEqualTo("abc") }
    }
  }

  def testAcceptMissingCharsetNegShortCircuit = {
    val provided: CharsetsProvided = None
    testDecisionReturnsDecision(e5,f6,r => r.charsetsProvided returns provided.point[r.Result])
  }

  def testAcceptMissingStarNotAcceptable = {
    val provided: CharsetsProvided = Some(Nil)
    testDecisionHaltsWithCode(e5, 406, r => r.charsetsProvided returns provided.point[r.Result])
  }

  def testAcceptExistsCharsetNegShortCircuit = {
    val provided: CharsetsProvided = None
    testDecisionReturnsDecision(e6, f6, r => r.charsetsProvided returns provided.point[r.Result], data = createData(headers = Map(AcceptCharset -> "ISO-8859-1")))
  }

  def testAcceptExistsAcceptableSetInMeta = {
    val charset = "ISO-8859-1"
    val provided: CharsetsProvided = Some((charset, identity[Array[Byte]](_)) :: Nil)
    testDecisionReturnsDecisionAndData(e6, f6, r => r.charsetsProvided returns provided.point[r.Result], data = createData(headers = Map(AcceptCharset -> charset))) {
      _.metadata.chosenCharset must beSome.which { _ == charset }
    }
  }

  def testAcceptExistsNotAcceptable = {
    val provided: CharsetsProvided = Some(Nil)
    testDecisionHaltsWithCode(e6, 406, r => r.charsetsProvided returns provided.point[r.Result], data = createData(headers = Map(AcceptCharset -> "ISO-8859-1")))
  }

  def testF6MediaAndCharsetNotChosen = {
    val provided: EncodingsProvided = None
    testDecisionResultHasData(f6, r => r.encodingsProvided returns provided.point[r.Result]) {
      _.responseHeader(ContentTypeHeader) must beSome.like {
        case value => value must beEqualTo("text/plain")
      }
    }
  }

  def testF6MediaChosenCharsetNot = {
    val provided: EncodingsProvided = None
    val contentType = ContentType("application/json", Map("a" -> "b", "c" -> "d"))
    testDecisionResultHasData(f6, r => r.encodingsProvided returns provided.point[r.Result], data = createData(metadata = Metadata(contentType = Some(contentType)))) {
      _.responseHeader(ContentTypeHeader) must beSome.like {
        case value => value must beEqualTo(contentType.mediaType + ";a=b,c=d").ignoreSpace.ignoreCase
      }
    }
  }

  def testF6CharsetChosenMediaNot = {
    val provided: EncodingsProvided = None
    val charset = "ISO-8859-1"
    testDecisionResultHasData(f6, r => r.encodingsProvided returns provided.point[r.Result], data = createData(metadata = Metadata(chosenCharset = Some(charset)))) {
      _.responseHeader(ContentTypeHeader) must beSome.like {
        case value => value must beEqualTo("text/plain;charset=" + charset).ignoreSpace.ignoreCase
      }
    }
  }

  def testF6MediaAndCharsetChosen = {
    val provided: EncodingsProvided = None
    val contentType = ContentType("application/json", Map("a" -> "b", "c" -> "d"))
    val charset = "ISO-8859-1"
    testDecisionResultHasData(f6, r => r.encodingsProvided returns provided.point[r.Result], data = createData(metadata = Metadata(contentType = Some(contentType), chosenCharset = Some(charset)))) {
      _.responseHeader(ContentTypeHeader) must beSome.like {
        case value => value must beEqualTo(contentType.mediaType + ";a=b,c=d;charset=" + charset).ignoreSpace.ignoreCase
      }
    }
  }

  def testAcceptEncodingExists = {
    testDecisionReturnsDecision(f6,f7,r => {}, data = createData(headers = Map(AcceptEncoding -> "*")))
  }

  def testAcceptEncodingMissingDefaultAcceptable = {
    val encoding = "some-encoding"
    val provided: EncodingsProvided = Some((encoding, identity[Array[Byte]](_)) :: Nil)
    testDecisionReturnsDecisionAndData(f6,g7, r => r.encodingsProvided returns provided.point[r.Result]) {
      d => (d.responseHeader(ContentEncoding) must beSome.like {
        case enc => enc must beEqualTo(encoding)
      }) and (d.metadata.chosenEncoding must beSome.like {
        case enc => enc must beEqualTo(encoding)
      })
    }
  }

  def testAcceptEncodingMissingDefaultNotAcceptable = {
    val provided: EncodingsProvided = Some(Nil)
    testDecisionHaltsWithCode(f6, 406, r => r.encodingsProvided returns provided.point[r.Result])
  }

  def testAcceptEncodingExistsShortCircuit = {
    val provided: EncodingsProvided = None
    testDecisionReturnsDecision(f7,g7, r => r.encodingsProvided returns provided.point[r.Result], data = createData(headers = Map(AcceptEncoding-> "gzip")))
  }

  def testAcceptEncodingExistsAcceptable = {
    val encoding = "gzip"
    val provided: EncodingsProvided = Some((encoding, identity[Array[Byte]](_)) :: Nil)
    testDecisionReturnsDecisionAndData(f7,g7, r => r.encodingsProvided returns provided.point[r.Result], data = createData(headers = Map(AcceptEncoding -> encoding))) {
      d => (d.responseHeader(ContentEncoding) must beSome.like {
        case enc => enc must beEqualTo(encoding)
      }) and (d.metadata.chosenEncoding must beSome.like {
        case enc => enc must beEqualTo(encoding)
      })
    }
  }

  def testAcceptEncodingExistsNotAcceptable = {
    val provided: EncodingsProvided = Some(Nil)
    def stub(r: Resource) { r.encodingsProvided returns provided.point[r.Result] }
    val data = createData(headers = Map(AcceptEncoding -> "ISO-8859-1"))
    testDecisionHaltsWithCode(f7, 406, stub(_), data = data) and testDecisionReturnsData(f7, r => r.encodingsProvided returns provided.point[r.Result], data = data) {
      d => (d.responseHeader(ContentEncoding) must beNone)
    }
  }

}
