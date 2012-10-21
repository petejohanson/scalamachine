package scalamachine.netty

import scalaz.effect.IO
import scalaz.syntax.monad._
import scalaz.iteratee.{EnumeratorT, IterateeT}
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.buffer.ChannelBuffers
import org.slf4j.LoggerFactory
import scalamachine.core.routing.RoutingTable
import scalamachine.core.flow.WebmachineRunner
import scalamachine.core.{HTTPBody, LazyStreamBody}

class ScalamachineRequestHandler(routes: RoutingTable, runner: WebmachineRunner) extends SimpleChannelUpstreamHandler with ReqRespDataConverters {

  private val logger = LoggerFactory.getLogger(classOf[ScalamachineRequestHandler])

  override def messageReceived(ctx: ChannelHandlerContext, evt: MessageEvent) {
    val request = evt.getMessage.asInstanceOf[HttpRequest]
   
    if (HttpHeaders.is100ContinueExpected(request)) send100Continue(evt)
    else {
      val keepAlive = HttpHeaders.isKeepAlive(request)
      val doWrite = runner.runIO(request, routes, fromHostData(_: HttpRequest), toHostData(_)) getOrElse {
        FixedLengthResponse(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND))
      } flatMap {
        case FixedLengthResponse(r) => writeFixedLengthResponse(evt, r, keepAlive)
        case ChunkedResponse(r, cs) => writeChunkedResponse(evt, r, cs, keepAlive)
      }

      doWrite.unsafePerformIO
    }
  }

  // TODO: real exception handling
  override def exceptionCaught(ctx: ChannelHandlerContext, evt: ExceptionEvent) {
    logger.error("Exception in ScalamachineRequestHandler", evt.getCause)
  }

  private def writeFixedLengthResponse(evt: MessageEvent, response: HttpResponse, keepAlive: Boolean): IO[Unit] = IO {
    val prepared = prepareResponse(response, keepAlive, isChunked = false)
    val mbWrite = writeToChannel(evt, prepared)

    if (!keepAlive) mbWrite.foreach(_.addListener(ChannelFutureListener.CLOSE))
  }

  private def writeChunkedResponse(evt: MessageEvent, 
                                   response: HttpResponse, 
                                   chunks: IO[EnumeratorT[HTTPBody.Chunk,IO]], 
                                   keepAlive: Boolean): IO[Unit] = IO {
    val prepared = prepareResponse(response, keepAlive, isChunked = true)    
    writeToChannel(evt, prepared)    
  } >> chunks flatMap { enumerator => (writeChunks(evt, keepAlive) &= enumerator).run }

  private def writeChunks(evt: MessageEvent, keepAlive: Boolean): IterateeT[HTTPBody.Chunk, IO, Unit] = 
    LazyStreamBody.forEachChunkUntilFalse {
      case HTTPBody.ByteChunk(bytes) => {
        val chunk = new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(bytes))
        writeToChannel(evt, chunk).isDefined
      }
      case HTTPBody.ErrorChunk(e) => {
        logger.error("Error Producing Chunk", e)
        writeFinalChunk(evt, keepAlive)
      }
      case HTTPBody.EOFChunk => writeFinalChunk(evt, keepAlive)
    }

  private def writeToChannel(e: MessageEvent, msg: HttpResponse): Option[ChannelFuture] = {
    if (e.getChannel.isConnected) {
      Some(e.getChannel.write(msg))
    } else None
  }
  
  private def writeToChannel(e: MessageEvent, msg: HttpChunk): Option[ChannelFuture] = {
    if (e.getChannel.isConnected) {
      Some(e.getChannel.write(msg))
    } else None
  }
  
  private def writeFinalChunk(e: MessageEvent, keepAlive: Boolean): Boolean = {
    writeToChannel(e, HttpChunk.LAST_CHUNK) map { future =>
      if (!keepAlive) {
        future.addListener(ChannelFutureListener.CLOSE)
        true
      }
      else true
    } getOrElse false
  }

  private def prepareResponse(response: HttpResponse, isKeepAlive: Boolean, isChunked: Boolean): HttpResponse = {

    if (isKeepAlive) {
      response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE)
    }

    response.setChunked(isChunked)
    if (isChunked) {
      response.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED)
    } else {
      response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, response.getContent.readableBytes())
    }

    response
  }

  private def send100Continue(e: MessageEvent) {
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE)
    writeToChannel(e, response)
  }

}
