package scalamachine.core
package flow

import scalaz.State
import scalaz.Scalaz.get
import scalaz.syntax.monad._
import scalaz.effect.IO

trait Decision {
  def name: String

  def apply(r: Resource): r.ReqRespState[Option[Decision]] = decide(r).run.flatMap {
    case ValueRes(next) => Option(next).point[r.ReqRespState]
    case HaltRes(code,body) => setError(r, code, body)
    case ErrorRes(error) => setError(r, 500, Option(error))
    case EmptyRes => (None: Option[Decision]).point[r.ReqRespState]
  }

  protected def decide(r: Resource): r.Result[Decision]

  private def setError(r: Resource, code: Int, body: Option[HTTPBody]): r.ReqRespState[Option[Decision]] = for {
    _ <- r.setStatusCode(code)
    // TODO: set response body if not already set
//     _ <- (r.dataL >=> ReqRespData.responseBodyL) := 
  } yield (None: Option[Decision])

  override def toString = "Decision(" + name + ")"    
}

object Machine {

  def run(r: Resource, first: Decision, init: ReqRespData): IO[ReqRespData] =
    runDecisions(r, first) eval (init,r.init)

  def runDecisions(r: Resource, decision: Decision): r.ReqRespState[ReqRespData] = 
    decision(r).flatMap {
      case Some(next) => runDecisions(r, next)
      case None => get[(ReqRespData,r.Context)].map(_._1).lift[IO]
    }
}
