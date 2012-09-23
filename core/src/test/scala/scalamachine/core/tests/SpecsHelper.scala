package scalamachine.core.tests

import org.specs2._
import matcher.MatchResult
import mock._
import scalamachine.core._
import flow.Decision
import HTTPMethods._
import ReqRespData.Metadata

// this is needed to make mockito happy about the Context type
class MockableResource extends Resource {
  type Context = Int
  def init = 0
}

trait SpecsHelper {
  this: Specification with Mockito =>

  def createResource = spy(new MockableResource) // this is necessary to make mockito happy about the Context type
  def createData(method: HTTPMethod = GET,
                 pathParts: List[String] = Nil,
                 headers: Map[HTTPHeader,String] = Map(),
                 metadata: Metadata = Metadata(),
                 baseUri: String = "",
                 respHdrs: Map[HTTPHeader,String] = Map(),
                 respBody: HTTPBody = HTTPBody.Empty,
                 doRedirect: Boolean = false) =
    ReqRespData(
      baseUri = baseUri,
      pathParts = pathParts,
      method = method,
      requestHeaders = headers,
      responseHeaders = respHdrs,
      responseBody = respBody,
      metadata = metadata,
      doRedirect = doRedirect)

  def testDecision(decision: Decision,
                   stubF: MockableResource => Unit,
                   r: MockableResource = createResource,
                   data: ReqRespData = createData())(f: (ReqRespData, Option[Decision]) => MatchResult[Any]): MatchResult[Any] = {
    stubF(r) // make call to stub/mock
    val res: r.ReqRespState[Option[Decision]] = decision(r).run.map(_.toOption)
    val ((newData,_),mbNext) = res.apply((data, r.init)).unsafePerformIO 
    f(newData, mbNext)
  }

  def testDecisionHaltsWithCode(decision: Decision,
                                code: Int,
                                stubF: MockableResource => Unit,
                                r: MockableResource = createResource,
                                data: ReqRespData = createData()) = {
    stubF(r)
    val haltCode = decision(r).run.eval((data, r.init)).unsafePerformIO match {      
      case HaltRes(code, _) => code
      case ErrorRes(_) => 500
      case _ => -1
    }
 
    haltCode must beEqualTo(code)                                  
  }

  def testDecisionReturnsDecision(toTest: Decision,
                                  expectedDecision: Decision,
                                  stubF: MockableResource => Unit,
                                  resource: MockableResource = createResource,
                                  data: ReqRespData = createData()): MatchResult[Any] = {
    testDecision(toTest, stubF, resource, data) {
      (_: ReqRespData, mbNextDecision: Option[Decision]) => mbNextDecision must beSome.like { case d => d must_== expectedDecision }
    }
  }

  def testDecisionReturnsDecisionAndData(toTest: Decision,
                                         expectedDecision: Decision,
                                         stubF: MockableResource => Unit,
                                         resource: MockableResource = createResource,
                                         data: ReqRespData = createData())(f: ReqRespData => MatchResult[Any]): MatchResult[Any] = {
    testDecision(toTest, stubF, resource, data) {
      (data: ReqRespData, mbNextDecision: Option[Decision]) => mbNextDecision must beSome.which { _ == expectedDecision } and f(data)
    }
  }

  // test ReqRespData given no decision was returned
  def testDecisionReturnsData(toTest: Decision,
                              stubF: MockableResource => Unit,
                              resource: MockableResource = createResource,
                              data: ReqRespData = createData())(f: ReqRespData => MatchResult[Any]): MatchResult[Any] = {
    testDecision(toTest, stubF, resource, data) {
      (retData: ReqRespData, mbNextDecision: Option[Decision]) => (mbNextDecision must beNone) and f(retData)
    }
  }

  // test ReqRespData regardless of whether a decision was returned
  def testDecisionResultHasData(toTest: Decision,
                                stubF: MockableResource => Unit,
                                resource: MockableResource = createResource,
                                data: ReqRespData = createData())(f: ReqRespData => MatchResult[Any]): MatchResult[Any] = {
    testDecision(toTest, stubF, resource, data) {
      (retData: ReqRespData, _: Option[Decision]) => f(retData)
    }
  }

  def mkAnswer[T](value: T): Any => (ReqRespData,Res[T]) = d => (d.asInstanceOf[ReqRespData],ValueRes(value))
  def mkResAnswer[T](value: Res[T]): Any => (ReqRespData,Res[T]) = d => (d.asInstanceOf[ReqRespData],value)


}
