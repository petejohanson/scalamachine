package scalamachine.core
package flow

import scalaz.StateT
import scalaz.Scalaz.get
import scalaz.syntax.monad._
import scalaz.effect.IO

trait WebmachineRunner {

  def apply(r: Resource, first: Decision, init: ReqRespData): IO[ReqRespData] = 
    runDecisions(r, first) eval (init,r.init)

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
