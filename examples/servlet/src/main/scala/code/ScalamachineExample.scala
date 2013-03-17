package code

import scalamachine.servlet.ServletWebmachineV3
import scalamachine.core.dispatch.Route._
import scalamachine.core.{Request, ValueRes, ReqRespData, Resource}
import scalamachine.core.dispatch.DispatchLogging

class EmptyResource extends Resource

class UnavailableResource extends Resource {
  override def serviceAvailable(req: Request) = ValueRes(false)
}

class ScalamachineExample extends ServletWebmachineV3 with DispatchLogging {
  routes(
    pathMatching {
      "unavailable"
    } serve new UnavailableResource,
    pathMatching {
      "empty"
    } serve new EmptyResource
  )
}
