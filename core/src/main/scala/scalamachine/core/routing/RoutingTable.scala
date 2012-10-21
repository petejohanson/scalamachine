package scalamachine.core
package routing

/**
 * A collection of [[scalamachine.core.dispatch.Rewrite]] and [[scalamachine.core.dispatch.Route]]
 * rules that defines which [[scalamachine.core.Resource]] will handle a given request. If a
 * [[scalamachine.core.Route]] is defined for a given request then the
 * [[scalamachine.core.Resource]] and updated [[scalamachine.core.ReqRespData]] with the proper
 * path and host info will be returned. Otherwise nothing is returned.
 *
 * Rewrite rules are applied before attempting to find which route matches a given request.
 * All rules that are defined for the given request are applied in the order they are given
 * when creating the RoutingTable.
 *
 * All routes are searched in the order they are given when creating the RoutingTable
 *
 * @see scalamachine.core.dispatch.Route
 * @see scalamachine.core.dispatch.Rewrite
 */ 
sealed trait RoutingTable {
  
  /**
   * The collection of [[scalamachine.core.dispatch.Route]] rules this RoutingTable is
   * defined for
   */ 
  def routes: Seq[Route]

  /**
   * The collection of rewrite rules for this RoutingTable
   */
  val rewrites: Seq[Rewrite] = Nil

  /**
   * Returns a value if the given data is defined for a [[scalamachine.core.dispatch.Route]]
   * in this RoutingTable, otherwise no value is returned. The [[scalamachine.core.dispatch.Rewrite]]
   * rules this RoutingTable contains that are defined for the given data will be applied
   * before searching for a defined [[scalamachine.core.dispatch.Route]]
   */
  def apply(req: ReqRespData): Option[(Resource, ReqRespData)] = {
    val rewritten = applyRewrites(req)
    routes.find(_.isDefinedAt(rewritten)).map(_(rewritten))
  }

  private def applyRewrites(data: ReqRespData): ReqRespData = 
    rewrites.foldLeft(data) { (data,rewrite) => 
      if (rewrite.predicate(data)) rewrite(data) else data 
    }
}

object RoutingTable {
  /**
   * Creates a routing table from the given [[scalamachine.core.dispatch.Route]] rules. The returned
   * table will have no rewrite rules. 
   *
   * @param rs the routes this table is defined for. Routes will be searched in the order given
   * @see scalamachine.core.dispatch.Route
   */  
  def apply(rs: Route*): RoutingTable = new RoutingTable {
    val routes = rs
  }

  /**
   * Creates a routing table from the given [[scalamachine.core.dispatch.Rewrite]] and
   * [[scalamachine.core.dispatch.Route]] rules.
   *
   * @param rws the rewrite rules for this routing table. Defined rules will be applied
   *            in the order given
   * @param rts the routes this table is defined for. Routes will be searched in the order given
   *
   * @see scalamachine.core.dispatch.Route
   * @see scalamachine.core.dispatch.Rewrite
   */
  def apply(rws: Seq[Rewrite], rts: Route*): RoutingTable = new RoutingTable {
    val routes = rts
    override val rewrites = rws
  }
}

case class Rewrite(predicate: ReqRespData => Boolean, rewriter: ReqRespData => ReqRespData) {
  def apply(data: ReqRespData): ReqRespData = rewriter(data)
}
