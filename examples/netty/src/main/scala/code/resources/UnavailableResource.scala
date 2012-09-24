package code.resources

import scalaz.syntax.monad._
import scalamachine.core.Resource

object UnavailableResource extends Resource {

  type Context = Int

  def init = 0

  override def serviceAvailable: Result[Boolean] = false.point[Result]
}
