package scalamachine.core.tests

import org.specs2._
import mock._
import org.mockito.{Matchers => MM}
import scalamachine.core._
import Resource._
import v3.WebmachineDecisions
import HTTPHeaders._
import HTTPMethods._
import scalaz.syntax.monad._


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
    def stub(r: Resource) {    
      val ctypes: r.ContentTypesProvided =
        (ContentType("text/plain"), (FixedLengthBody(""): HTTPBody).point[r.Result]) ::
         (ContentType("application/json"), (FixedLengthBody(""): HTTPBody).point[r.Result]) :: Nil
      val charsets: CharsetsProvided = Some(("charset1", identity[Array[Byte]](_)) :: ("charset2", identity[Array[Byte]](_)) :: Nil)
      val encodings: EncodingsProvided = Some(("identity", identity[Array[Byte]](_)) :: ("gzip", identity[Array[Byte]](_)) :: Nil)

      r.contentTypesProvided returns ctypes.point[r.Result]
      r.charsetsProvided returns charsets.point[r.Result]
      r.encodingsProvided returns encodings.point[r.Result]
      r.variances returns (Nil: Seq[String]).point[r.Result]
      r.resourceExists returns true.point[r.Result]
      
    }

    testDecisionResultHasData(
      g7,
      stub(_)) {
        _.responseHeader(Vary) must beSome.like {
          case vary => vary must contain("Accept") and contain("Accept-Encoding") and contain("Accept-Charset")
        }
      }
  }

  def testVaryContentTypesProvided0 = {
    import Res._
    def stub(r: Resource) {
      val ctypes: r.ContentTypesProvided = Nil
      val charsets: CharsetsProvided = Some(("charset1", identity[Array[Byte]](_)) :: ("charset2", identity[Array[Byte]](_)) :: Nil)
      val encodings: EncodingsProvided = Some(("identity", identity[Array[Byte]](_)) :: ("gzip", identity[Array[Byte]](_)) :: Nil)

      r.contentTypesProvided returns ctypes.point[r.Result]
      r.charsetsProvided returns charsets.point[r.Result]
      r.encodingsProvided returns encodings.point[r.Result]
      r.variances returns (Nil: Seq[String]).point[r.Result]
      r.resourceExists returns true.point[r.Result]

    }
    testDecisionResultHasData(
      g7,
      stub(_)) {
        _.responseHeader(Vary) must beSome.like {
          case vary => vary must not =~("""Accept[^-]""")
        }
      }
  }

  def testVaryContentTypesProvided1 = {
    import Res._
    def stub(r: Resource) {
      val ctypes: r.ContentTypesProvided = 
        (ContentType("application/json"), (FixedLengthBody(""): HTTPBody).point[r.Result]) :: Nil
      val charsets: CharsetsProvided = Some(("charset1", identity[Array[Byte]](_)) :: ("charset2", identity[Array[Byte]](_)) :: Nil)
      val encodings: EncodingsProvided = Some(("identity", identity[Array[Byte]](_)) :: ("gzip", identity[Array[Byte]](_)) :: Nil)

      r.contentTypesProvided returns ctypes.point[r.Result]
      r.charsetsProvided returns charsets.point[r.Result]
      r.encodingsProvided returns encodings.point[r.Result]
      r.variances returns (Nil: Seq[String]).point[r.Result]
      r.resourceExists returns true.point[r.Result]
    }

    testDecisionResultHasData(
      g7,
      stub(_)) {
        _.responseHeader(Vary) must beSome.like {
          case vary => vary must not =~("""Accept[^-]""")
        }
      }
  }

  def testVaryCharsetsShortCircuit = {
    import Res._
    def stub(r: Resource) {
      val ctypes: r.ContentTypesProvided =
        (ContentType("text/plain"), (FixedLengthBody(""): HTTPBody).point[r.Result]) ::
          (ContentType("application/json"), (FixedLengthBody(""): HTTPBody).point[r.Result]) :: Nil
      
      val charsets: CharsetsProvided = None
      val encodings: EncodingsProvided = Some(("identity", identity[Array[Byte]](_)) :: ("gzip", identity[Array[Byte]](_)) :: Nil)

      r.contentTypesProvided returns ctypes.point[r.Result]
      r.charsetsProvided returns charsets.point[r.Result]
      r.encodingsProvided returns encodings.point[r.Result]
      r.variances returns (Nil: Seq[String]).point[r.Result]
      r.resourceExists returns true.point[r.Result]

    }
    testDecisionResultHasData(
      g7,
      stub(_)) {
      _.responseHeader(Vary) must beSome.like {
        case vary => vary must not contain("Accept-Charset")
      }
    }
  }

  def testVaryCharsetsProvided0 = {
    import Res._
    def stub(r: Resource) {
      val ctypes: r.ContentTypesProvided =
        (ContentType("text/plain"), (HTTPBody.Empty: HTTPBody).point[r.Result]) ::
          (ContentType("application/json"), (HTTPBody.Empty: HTTPBody).point[r.Result]) :: Nil
    

      val charsets: CharsetsProvided = Some(Nil)
      val encodings: EncodingsProvided = Some(("identity", identity[Array[Byte]](_)) :: ("gzip", identity[Array[Byte]](_)) :: Nil)

      r.contentTypesProvided returns ctypes.point[r.Result]
      r.charsetsProvided returns charsets.point[r.Result]
      r.encodingsProvided returns encodings.point[r.Result]
      r.variances returns (Nil: Seq[String]).point[r.Result]
      r.resourceExists returns true.point[r.Result]
    }

    testDecisionResultHasData(
      g7,
      stub(_)) {
        _.responseHeader(Vary) must beSome.like {
          case vary => vary must not contain("Accept-Charset")
        }
      }
  }

  def testVaryCharsetsProvided1 = {
    import Res._
    def stub(r: Resource) {
      val ctypes: r.ContentTypesProvided =
        (ContentType("text/plain"), (HTTPBody.Empty: HTTPBody).point[r.Result]) ::
         (ContentType("application/json"), (HTTPBody.Empty: HTTPBody).point[r.Result]) :: Nil
    

      val charsets: CharsetsProvided = Some(("charset2", identity[Array[Byte]](_)) :: Nil)
      val encodings: EncodingsProvided = Some(("identity", identity[Array[Byte]](_)) :: ("gzip", identity[Array[Byte]](_)) :: Nil)

      r.contentTypesProvided returns ctypes.point[r.Result]
      r.charsetsProvided returns charsets.point[r.Result]
      r.encodingsProvided returns encodings.point[r.Result]
      r.variances returns (Nil: Seq[String]).point[r.Result]
      r.resourceExists returns true.point[r.Result]
    }
    testDecisionResultHasData(
      g7,
      stub(_)) {
        _.responseHeader(Vary) must beSome.like {
          case vary => vary must not contain("Accept-Charset")
        }
      }
  }

  def testVaryEncodingsShortCircuit = {
    import Res._
    def stub(r: Resource) {
      val ctypes: r.ContentTypesProvided =
        (ContentType("text/plain"), (HTTPBody.Empty: HTTPBody).point[r.Result]) ::
          (ContentType("application/json"), (HTTPBody.Empty: HTTPBody).point[r.Result]) :: Nil

      val charsets: CharsetsProvided = Some(("charset1", identity[Array[Byte]](_)) :: ("charset2", identity[Array[Byte]](_)) :: Nil)
      val encodings: EncodingsProvided = None

      r.contentTypesProvided returns ctypes.point[r.Result]
      r.charsetsProvided returns charsets.point[r.Result]
      r.encodingsProvided returns encodings.point[r.Result]
      r.variances returns (Nil: Seq[String]).point[r.Result]
      r.resourceExists returns true.point[r.Result]
    }
    testDecisionResultHasData(
      g7,
      stub(_)) {
        _.responseHeader(Vary) must beSome.like {
          case vary => vary must not contain("Accept-Encoding")
        }
      }

  }

  def testVaryEncodingsProvided0 = {
    import Res._
    def stub(r: Resource) {
      val ctypes: r.ContentTypesProvided =
        (ContentType("text/plain"), (HTTPBody.Empty: HTTPBody).point[r.Result]) ::
          (ContentType("application/json"), (HTTPBody.Empty: HTTPBody).point[r.Result]) :: Nil

      val charsets: CharsetsProvided = Some(("charset1", identity[Array[Byte]](_)) :: ("charset2", identity[Array[Byte]](_)) :: Nil)
      val encodings: EncodingsProvided = Some(Nil)

      r.contentTypesProvided returns ctypes.point[r.Result]
      r.charsetsProvided returns charsets.point[r.Result]
      r.encodingsProvided returns encodings.point[r.Result]
      r.variances returns (Nil: Seq[String]).point[r.Result]
      r.resourceExists returns true.point[r.Result]
    }
    testDecisionResultHasData(
      g7,
      stub(_)) {
        _.responseHeader(Vary) must beSome.like {
          case vary => vary must not contain("Accept-Encoding")
        }
      }
  }

  def testVaryEncodingsProvided1 = {
    import Res._
    def stub(r: Resource) {
      val ctypes: r.ContentTypesProvided =
        (ContentType("text/plain"), (HTTPBody.Empty: HTTPBody).point[r.Result]) ::
          (ContentType("application/json"), (HTTPBody.Empty: HTTPBody).point[r.Result]) :: Nil


      val charsets: CharsetsProvided = Some(("charset1", identity[Array[Byte]](_)) :: ("charset2", identity[Array[Byte]](_)) :: Nil)
      val encodings: EncodingsProvided = Some(("gzip", identity[Array[Byte]](_)) :: Nil)

      r.contentTypesProvided returns ctypes.point[r.Result]
      r.charsetsProvided returns charsets.point[r.Result]
      r.encodingsProvided returns encodings.point[r.Result]
      r.variances returns (Nil: Seq[String]).point[r.Result]
      r.resourceExists returns true.point[r.Result]
    }
    testDecisionResultHasData(
      g7,
      stub(_)) {
        _.responseHeader(Vary) must beSome.like {
          case vary => vary must not contain("Accept-Encoding")
        }
      }
  }

  def testVaryResourceAdditional = {
    import Res._
    def stub(r: Resource) {
      val ctypes: r.ContentTypesProvided =
        (ContentType("text/plain"), (HTTPBody.Empty: HTTPBody).point[r.Result]) ::
          (ContentType("application/json"), (HTTPBody.Empty: HTTPBody).point[r.Result]) :: Nil
      

      val charsets: CharsetsProvided = Some(("charset1", identity[Array[Byte]](_)) :: ("charset2", identity[Array[Byte]](_)) :: Nil)
      val encodings: EncodingsProvided = Some(("identity", identity[Array[Byte]](_)) :: ("gzip", identity[Array[Byte]](_)) :: Nil)

      r.contentTypesProvided returns ctypes.point[r.Result]
      r.charsetsProvided returns charsets.point[r.Result]
      r.encodingsProvided returns encodings.point[r.Result]
      r.variances returns Seq("One", "Two").point[r.Result]
      r.resourceExists returns true.point[r.Result]
    }
    testDecisionResultHasData(
      g7,
      stub(_)) {
        _.responseHeader(Vary) must beSome.like {
          case vary => vary must contain("One") and contain("Two")
        }
      }
  }

  def testResourceExistsTrue = {
    testDecisionReturnsDecision(
      g7,
      g8,
      r => {
        r.contentTypesProvided returns (Nil: r.ContentTypesProvided).point[r.Result]
        r.charsetsProvided returns (None: CharsetsProvided).point[r.Result]
        r.encodingsProvided returns (None: EncodingsProvided).point[r.Result]
        r.variances returns (Nil: Seq[String]).point[r.Result]
        r.resourceExists returns true.point[r.Result]
      }
    )
  }

  def testResourceExistsFalse = {
    testDecisionReturnsDecision(
      g7,
      h7,
      r => {
        r.contentTypesProvided returns (Nil: r.ContentTypesProvided).point[r.Result]
        r.charsetsProvided returns (None: CharsetsProvided).point[r.Result]
        r.encodingsProvided returns (None: EncodingsProvided).point[r.Result]
        r.variances returns (Nil: Seq[String]).point[r.Result]
        r.resourceExists returns false.point[r.Result]
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
    testDecisionReturnsDecision(g11,h10,r => r.generateEtag returns Option("1").point[r.Result], data = createData(headers = Map(IfMatch -> "1,2")))
  }

  def testIfMatchMissingEtag = {
    testDecisionHaltsWithCode(g11, 412, r => r.generateEtag returns (None: Option[String]).point[r.Result], data = createData(headers = Map(IfMatch -> "1")))
  }

}
