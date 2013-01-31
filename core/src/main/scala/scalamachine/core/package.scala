package scalamachine

import internal.scalaz._
import scalamachine.internal.scalaz._
import Scalaz._

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

  val StandardRESTMethods = HTTPMethods.GET :: HTTPMethods.POST :: HTTPMethods.PUT :: HTTPMethods.DELETE :: Nil

  type Res[+A] = Validation[Halt, A]

  def result[A](a: A): Res[A] = a.success[Halt]
  def halt[A](code: Int): Res[A] = Halt(code).fail[A]
  def halt[A](code: Int, body: HTTPBody): Res[A] = Halt(code, body).fail[A]
  def halt[A](code: Int, err: Throwable): Res[A] = Halt(code, err.getMessage).fail[A]
  def error[A](body: HTTPBody): Res[A] = Halt(500, body).fail[A]
  def error[A](e: Throwable): Res[A] = Halt(500, e.getMessage).fail[A]
  def empty[A]: Res[A] = Halt().fail[A]

  type ResT[M[_], A] = ValidationT[M, Halt, A]

  def resT[M[_]] = new (({type λ[α] = M[Res[α]]})#λ ~> ({type λ[α] = ValidationT[M, Halt, α]})#λ) {
    def apply[A](a: M[Res[A]]) = ValidationT[M, Halt, A](a)
  }

  sealed trait AuthResult {
    def fold[T](success: T, failure: String => T): T = this match {
      case AuthSuccess => success
      case AuthFailure(s) => failure(s)
    }
  }
  case object AuthSuccess extends AuthResult
  case class AuthFailure(headerValue: String) extends AuthResult
}
