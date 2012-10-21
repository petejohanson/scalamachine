package scalamachine.core
package flow

import scalaz.{StateT, OptionT, ~>}
import scalaz.Scalaz.get
import scalaz.syntax.monad._
import scalaz.effect.IO
import IO._
import OptionT._
import routing.RoutingTable

trait WebmachineRunner {

  /**
   * Run the Webmachine for a given request from some source famework, a routing table,
   * a function to transform the host framework request type (I) to a [[scalamachine.core.ReqRespData]]
   * and a function to transform a [[scalamachine.core.ReqRespData]] to the host framework's
   * response type (O).
   *
   * @param init the request data to run the webmachine against assuming there is a defined route
   * @param routes the routing table to search for an applicable resource for the given request
   * @param in a function transforming the input request type to a [[scalamachine.core.ReqRespData]]
   * @param out a functiont ransforming the output data to an output response type
   * @tparam I the type of the input request
   * @tparam O the type of the output response
   */
  def runIO[I,O](init: I, routes: RoutingTable, in: I => ReqRespData, out: ReqRespData => O): OptionT[IO,O] = 
    runFlowIO(in(init),routes).map(out(_))

  /**
   * Similar to `runIO` except if an implicit natural transformation ([[scalaz.~>]])
   * between [[scalaz.IO]] and some other Monad, M, is in scope or provided explicity this function
   * can be applied and will in turn apply the natural transformation to the result of runIO
   */
  def runM[M[+_], I, O](init: I, routes: RoutingTable, in: I => ReqRespData, out: ReqRespData => O)
                       (implicit T: IO ~> M): OptionT[M,O] = 
    optionT[M](T.apply(runIO(init, routes,in,out).run))

  /**
   * Immediately calls `unsafePerformIO` on the result of `runIO`
   */ 
  def runUnsafe[I,O](init: I, routes: RoutingTable, in: I => ReqRespData, out: ReqRespData => O): Option[O] = 
    runIO(init, routes, in, out).run.unsafePerformIO

  /**
   * to create a concrete WebmachineRuner this method should be implemented
   * by fixiing `runMachineWithRoutesIO` to a given first decision
   */
  protected def runFlowIO(init: ReqRespData, routes: RoutingTable): OptionT[IO,ReqRespData]

  protected def runMachineWithRoutesIO(routes: RoutingTable, first: Decision, init: ReqRespData): OptionT[IO,ReqRespData] =
    optionT[IO](routes(init).point[IO]) flatMap { callInfo =>
      runMachineIO(callInfo._1, first, callInfo._2).liftM[OptionT]
    }                                           
    
  protected def runMachineIO(r: Resource, first: Decision, init: ReqRespData): IO[ReqRespData] = {
    val initContext = r.init

    val handleThrown: Throwable => IO[ReqRespData] = 
      e => setError(r, 500, Option(e.getMessage)) >> getData(r) eval (init, initContext)

    IO(runDecisions(r, first) eval (init,initContext)).join.except(handleThrown)
  }

  private def runDecisions(r: Resource, decision: Decision): r.ReqRespState[ReqRespData] = 
    decision(r).run flatMap {
      case ValueRes(next) => runDecisions(r, next)
      case HaltRes(code, body) => setError(r, code, body) >> getData(r)
      case ErrorRes(error) => setError(r, 500, Option(error)) >> getData(r)
      case EmptyRes => getData(r)
    }

  private def setError(r: Resource, code: Int, body: Option[HTTPBody]): r.ReqRespState[HTTPBody] = 
    r.setStatusCode(code) >> setBodyIfNoneExists(r, body)


  private def setBodyIfNoneExists(r: Resource, body: Option[HTTPBody]): r.ReqRespState[HTTPBody] = 
    (r.dataL >=> ReqRespData.respBodyL).mods((b: HTTPBody) => {
      body map { newBody => if (b.isEmpty) newBody else b } getOrElse b
    }).lift[IO]
    
  private def getData(r: Resource): StateT[IO, (ReqRespData,r.Context), ReqRespData] = 
    get[(ReqRespData, r.Context)].map(_._1).lift[IO]


}
