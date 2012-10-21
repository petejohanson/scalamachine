package scalamachine.servlet

import scalamachine.core.routing.RoutingTable

/**
 * Wraps the [[scalamachine.core.RoutingTable]] for a [[scalamachine.servlet.ScalamachineFilter]].
 * A concrete implementation of this trait can be used to pass the routes for the filter
 * using web.xml init-params. 
 */
trait ServletRoutingTable {
  def routes: RoutingTable
}
