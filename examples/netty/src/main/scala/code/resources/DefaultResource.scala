package code.resources

import scalamachine.core._

object DefaultResource extends Resource {
  // we don't use the context so we just put something fake in here
  type Context = Option[Nothing]
  def init = None
}
