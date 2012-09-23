package scalamachine.core.tests
package dispatch

import org.specs2._
import scalamachine.core.ReqRespData
import scalamachine.core.dispatch.{RoutingTable, Rewrite, Route}
import Route._

class RoutingTableSpecs extends Specification { def is = 
  "Routing Table".title                                                         ^
  """                                                                           
  | The routing table is a collection of rewrite rules and 
  | routes. The routing table is defined for every request for 
  | which it contains a route that is defined for a given request
  """.stripMargin                                                               ^
                                                                                p^
  "Given a request that is defined for a given route, value returned"           ! testHasMatchingRoute ^
  "Given a request that is not defined for any route, value not returned"       ! testNoMatchingRoute ^
  "All matching rewrite rules are applied before routing"                       ! testAppliesRewrites ^
  "Any rewrite rules that don't apply are not applied ever"                     ! testDoesntApplyNotMatchingRewrites ^
                                                                                end

  def testHasMatchingRoute = {
    val route: Route = pathMatching("a") serve null 
    val table = RoutingTable(route)
    table(ReqRespData(pathParts = "a" :: Nil)) must beSome
  }                                               

  def testNoMatchingRoute = {
    val route: Route = pathMatching("b") serve null
    val table = RoutingTable(route)
    table(ReqRespData(pathParts = "a" :: Nil)) must beNone
  }

  def testAppliesRewrites = {
    val match1 = Rewrite(_ => true, d => d.copy(pathParts = "a" :: d.pathParts))
    val match2 = Rewrite(_ => true, d => d.copy(pathParts = "b" :: d.pathParts))                    
    val route: Route = pathStartingWith("b" / "a") serve null
    val table = RoutingTable(match1 :: match2 :: Nil, route)
    table(ReqRespData(pathParts = "c" :: Nil)) must beSome                     
  }
 
  def testDoesntApplyNotMatchingRewrites = {
    val errMatch = Rewrite(_ => false, _ => sys.error("should not be called"))
    val table = RoutingTable(errMatch :: Nil, pathStartingWith("a") serve null)
    table(ReqRespData()) must not(throwA)
  }                                               
}
