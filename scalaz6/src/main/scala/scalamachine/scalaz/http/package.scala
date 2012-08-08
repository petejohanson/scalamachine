package scalamachine.scalaz

import _root_.scalaz._
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

package object http extends Equals {
  implicit val httpMethodEqual: Equal[HTTPMethod] = equalA[HTTPMethod]
}
