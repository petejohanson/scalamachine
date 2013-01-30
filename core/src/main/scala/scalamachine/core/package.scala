package scalamachine

/**
 * Created by IntelliJ IDEA.
 *
 * scalamachine.core
 *
 * User: aaron
 * Date: 1/29/13
 * Time: 5:30 PM
 */
package object core {
  type ReqRespDataTup[X] = (ReqRespData, X)
  type Headers = Map[HTTPHeader, String]
  val EmptyHeaders = Map[HTTPHeader, String]()
  type ResponseCode = Int
  type HostParts = List[String]
  type PathParts = List[String]
  type Query = Map[String,List[String]]
  type Halt = Response

  val StandardRESTMethods = HTTPMethods.GET :: HTTPMethods.POST :: HTTPMethods.PUT :: HTTPMethods.DELETE :: Nil
}
