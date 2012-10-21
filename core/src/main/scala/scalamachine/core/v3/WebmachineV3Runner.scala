package scalamachine.core
package v3

import scalaz.OptionT
import scalaz.effect.IO
import flow.WebmachineRunner
import routing.RoutingTable

object WebmachineV3Runner extends WebmachineRunner with WebmachineDecisions {
  protected def runFlowIO(init: ReqRespData, routes: RoutingTable): OptionT[IO,ReqRespData] = 
    runMachineWithRoutesIO(routes, b13, init)
}

