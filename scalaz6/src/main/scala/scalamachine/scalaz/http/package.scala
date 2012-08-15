package scalamachine.scalaz

import scalamachine.core.HTTPMethod

package object http {
  import scalaz._
  import Scalaz._
  implicit val httpMethodEqual: Equal[HTTPMethod] = equalA[HTTPMethod]
}
