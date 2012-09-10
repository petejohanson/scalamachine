package scalamachine.core
package dispatch

import flow._

trait Dispatch {
  def perform(route: Route, resource: Resource, data: ReqRespData): ReqRespData
}

trait DispatchTable[-A, B, +W[_]] extends Dispatch {

  private var _routes = Vector.empty[Route]

  /**
   * rewrites to apply to a request before it is routed. Override this
   * value to set rewrites for your own dispatch table
   */ 
  val rewrites: Seq[Rewrite] = Nil

  /**
   * Add a route to the dispatch table
   */ 
  def route(route: Route) {
    _routes :+= route
  }

  /**
   * Add routes to a dispatch table
   */ 
  def routes(routes: Route*) {
    _routes ++= Vector(routes:_*)
  }

  def apply(req: A): Option[W[B]] = {
    val data = applyRewrites(toData(req))

    _routes
      .find(_.isDefinedAt(data))
      .map(
        route => {
          val (resource, finalData) = route(data)
          wrap(fromData(perform(route, resource, finalData)))
        }
      )
  }


  // although not used here the route is passed to this function
  // so traits stacked on this one can use the information in the route
  def perform(route: Route, resource: Resource, data: ReqRespData): ReqRespData = {
    flowRunner.run(firstDecision, resource, data)
  }

  // default flow runner
  def flowRunner = new FlowRunner

  def wrap(res: => B): W[B]

  def firstDecision: Decision

  def toData(req: A): ReqRespData

  def fromData(data: ReqRespData): B

  // the HOST (excluding port) split by "."
  protected def host(fullName: String): List[String] = {
    val portStartIdx = fullName indexOf ":"
    val name =
      if (portStartIdx >= 0) fullName dropRight (fullName.length - portStartIdx)
      else fullName

    name.split("\\.").toList
  }

  private def applyRewrites(data: ReqRespData): ReqRespData = 
    rewrites.foldLeft(data) { (data,rewrite) => if (rewrite.predicate(data)) rewrite(data) else data }

}

case class Rewrite(predicate: ReqRespData => Boolean, rewriter: ReqRespData => ReqRespData) {
  def apply(data: ReqRespData): ReqRespData = rewriter(data)
}
