package scalamachine
package netty

import scalaz.effect.IO
import scalaz.iteratee.EnumeratorT
import core.HTTPBody
import org.jboss.netty.handler.codec.http.HttpResponse

sealed trait WrappedHttpResponse {
  def response: HttpResponse
}
case class FixedLengthResponse(response: HttpResponse) extends WrappedHttpResponse
case class ChunkedResponse(response: HttpResponse, chunks: IO[EnumeratorT[HTTPBody.Chunk,IO]]) extends WrappedHttpResponse

