package scalamachine.core.tests

import org.specs2._
import mock._
import scalaz.{StateT, State}
import scalaz.effect.IO
import State._
import scalamachine.core._
import flow._
import scalamachine.core.dispatch.RoutingTable

class WebmachineRunnerSpecs 
  extends Specification 
  with Mockito
  with SpecsHelper { def is = 
  "Webmachine Runner".title                                                    ^   
  """                                                                             
  Description                                                                     
  """                                                                          ^   
                                                                               p^   
  "Given a Decision that does not result in a next decision"                   ^   
    "if the result is an EmptyRes"                                             ^  
      "the final state returned by the decision is the result"                 ! testEmptyRes ^p^   
    "if the result is a HaltRes"                                               ^   
      "the response code of the returned data is equal to halt code"           ! testHaltResSetsCode ^     
      "if the HaltRes contains a response body"                                ^   
        "if the body wasn't previously set the given body is set"              ! testHaltResHasBodyNoBodySet ^     
        "if the body was previously set it is left in tact"                    ! testHaltResHasBodyAndBodySet ^p^p^     
    "if the result is an ErrorRes"                                             ^   
      "the response code of the returned data is equal to 500"                 ! testErrorRes500 ^     
      "if the response already contained a body the error body is not set"     ! testErrorResBodyNotSet ^     
      "if the response does not already contain a body the error body is set"  ! testErrorResBodySet ^
                                                                               end

  object TestMachine extends WebmachineRunner                
                
  def testEmptyRes = {
    val r: Resource = createResource
    val endData = mock[ReqRespData]
    val startData = mock[ReqRespData]
    val decision = mock[Decision]
    decision.apply(r) returns ResT.resT[r.ReqRespState](State((s: (ReqRespData,r.Context)) => {
      ((endData, s._2), Res.empty[Decision])
    }).lift[IO])
     
    TestMachine(r, decision, startData).unsafePerformIO must beEqualTo(endData)
  }                

  def testHaltResSetsCode = {
    val r: Resource = createResource
    val decision = mock[Decision]
    val code = 540
    decision.apply(r) returns ResT.haltT[r.ReqRespState, Decision](code)
    TestMachine(r, decision, ReqRespData(statusCode = 200)).map(_.statusCode).unsafePerformIO must beEqualTo(code)
  }

  def testHaltResHasBodyNoBodySet = {
    val r: Resource = createResource
    val decision = mock[Decision]
    val expectedBody: HTTPBody = "body"
    decision.apply(r) returns ResT.haltT[r.ReqRespState, Decision](500, expectedBody)
    TestMachine(r, decision, ReqRespData()).map(_.responseBody.stringValue).unsafePerformIO must beEqualTo(expectedBody.stringValue)
  }    

  def testHaltResHasBodyAndBodySet = {
    val r: Resource = createResource
    val decision = mock[Decision]
    val expectedBody: HTTPBody = "body"
    decision.apply(r) returns ResT.haltT[r.ReqRespState, Decision](500, "other")
    TestMachine(r, decision, ReqRespData(responseBody = expectedBody)).map(_.responseBody.stringValue).unsafePerformIO must beEqualTo(expectedBody.stringValue)    
  }   

  def testErrorRes500 = {
    val r: Resource = createResource
    val decision = mock[Decision]
    decision.apply(r) returns ResT.errorT[r.ReqRespState, Decision]("")
    TestMachine(r, decision, ReqRespData()).map(_.statusCode).unsafePerformIO must beEqualTo(500)
  }                    

  def testErrorResBodyNotSet = {
    val r: Resource = createResource
    val decision = mock[Decision]
    val expectedBody: HTTPBody = "body"
    decision.apply(r) returns ResT.errorT[r.ReqRespState, Decision](expectedBody)
    TestMachine(r, decision, ReqRespData()).map(_.responseBody.stringValue).unsafePerformIO must beEqualTo(expectedBody.stringValue)
  }

  def testErrorResBodySet = {
    val r: Resource = createResource
    val decision = mock[Decision]
    val expectedBody: HTTPBody = "body"
    decision.apply(r) returns ResT.errorT[r.ReqRespState, Decision]("newbody")
    TestMachine(r, decision, ReqRespData(responseBody = expectedBody)).map(_.responseBody.stringValue).unsafePerformIO must beEqualTo(expectedBody.stringValue)
  }
}
