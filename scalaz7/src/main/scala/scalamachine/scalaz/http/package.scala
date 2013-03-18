package scalamachine.scalaz

import scalamachine.core.HTTPMethod
import scalaz.Equal

package object http {
  implicit val httpMethodEqual: Equal[HTTPMethod] = Equal.equalA[HTTPMethod]
}
