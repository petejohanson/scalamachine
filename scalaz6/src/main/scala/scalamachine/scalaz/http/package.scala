package scalamachine.scalaz

import scalamachine.core.HTTPMethod

/**
 * Created by IntelliJ IDEA.
 *
 * scalamachine.scalaz.http
 *
 * User: aaron
 * Date: 8/8/12
 * Time: 2:28 PM
 */

package object http {
  import scalaz._
  import Scalaz._
  implicit val httpMethodEqual: Equal[HTTPMethod] = equalA[HTTPMethod]
}
