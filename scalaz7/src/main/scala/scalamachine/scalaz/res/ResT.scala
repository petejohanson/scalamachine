package scalamachine.scalaz.res

import scalamachine.core.{Res, ValueRes, HaltRes, ErrorRes, EmptyRes}
import Res._
import scalaz.{MonadTrans, Pointed, Monad, Functor}
import scalaz.syntax.Ops

case class ResT[M[_],A](run: M[Res[A]]) {
  self =>

  def map[B](f: A => B)(implicit F: Functor[M]): ResT[M,B] = {
    ResT(F.map(self.run)((_: Res[A]) map f))
  }

  def flatMap[B](f: A => ResT[M,B])(implicit M: Monad[M]) = {
    ResT(M.bind(self.run) {
      case ValueRes(v) => f(v).run
      case r @ HaltRes(_) => M.point(r: Res[B])
      case r @ ErrorRes(_) => M.point(r: Res[B])
      case r @ EmptyRes => M.point(r: Res[B])
    })
  }

  def filter(p: A => Boolean)(implicit M: Monad[M]) = {
    ResT(M.bind(self.run) { res => M.point(res filter p) })
  }

  def orElse[B >: A](other: ResT[M, B])(implicit M: Monad[M]): ResT[M,B] =
    ResT(
      M.bind(self.run)(_ match {
        case ValueRes(x) => M.point(ValueRes(x))
        case _ => other.run
      })
    )

}

object ResT extends ResTFunctions with ResTInstances with ResTSyntax

trait ResTFunctions {
  import scalaz.~>
  def resT[M[_]] = new (({type λ[α] = M[Res[α]]})#λ ~> ({type λ[α] = ResT[M, α]})#λ) {
    def apply[A](a: M[Res[A]]) = new ResT[M, A](a)
  }
}


trait ResTInstances {
  import scalaz.syntax.pointed._

  implicit def resTInstances[M[_] : Monad] = new Monad[({type R[X]=ResT[M,X]})#R] {
    def point[A](a: => A): ResT[M,A] = ResT[M,A](Pointed[M].point(Pointed[Res].point(a)))
    def bind[A,B](fa: ResT[M,A])(f: A => ResT[M,B]): ResT[M,B] = fa flatMap f
  }

  implicit val ResTMonadTrans = new MonadTrans[ResT] {
    def liftM[G[_], A](ga: G[A])(implicit G: Monad[G]): ResT[G,A] =
      ResT[G,A](G.map(ga)(_.point[Res]))

    implicit def apply[G[_]: Monad]: Monad[({type R[X]=ResT[G,X]})#R] =
      resTInstances[G]
  }
}

trait ResTSyntax {
  implicit def resToOps[A](ra: Res[A]): ResOps[A] = new ResOps[A] {
    val self = ra
  }
}

sealed trait ResOps[A] extends Ops[Res[A]] {
  import ResT.resT
  import scalaz.syntax.pointed._
  def liftT[M[_]](implicit M: Monad[M]): ResT[M, A] = resT[M](self.point[M])
}