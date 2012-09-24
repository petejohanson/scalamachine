package scalamachine
package netty

import scala.collection.JavaConverters._
import java.net.URI
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http._
import core._

trait ReqRespDataConverters {
  
  def fromHostData(req: HttpRequest): ReqRespData = 
    ReqRespData(
      method = HTTPMethod.fromString(req.getMethod.getName),
      pathParts = path(req),
      rawPath = req.getUri,
      hostParts = host(
        Option(HttpHeaders.getHost(req))
          .filterNot(_ == "")
          .getOrElse(java.net.InetAddress.getLocalHost.getHostName)
      ),
      baseUri = "http://" + HttpHeaders.getHost(req),
      query = new QueryStringDecoder(req.getUri).getParameters.asScala.mapValues(_.asScala.toList).toMap,
      requestBody = reqBody(req),
      requestHeaders = for {
        (k,v) <- req.getHeaders.asScala.map(entry => (entry.getKey, entry.getValue)).toMap
        hdr <- HTTPHeader.fromString(k)
      }  yield (hdr, v)
    )


  def toHostData(data: ReqRespData): WrappedHttpResponse = {
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(data.statusCode))
    for {
      (h,v) <- data.responseHeaders
    } yield response.setHeader(h.wireName,v)

    data.responseBody match {
      case FixedLengthBody(bytes) => {
        response.setChunked(false)
        response.setContent(ChannelBuffers.copiedBuffer(data.responseBody.bytes))
        FixedLengthResponse(response)
      }
      case LazyStreamBody(streamer) => {
        ChunkedResponse(response, streamer)
      }
    }
  }

  private def path(req: HttpRequest): List[String] = {
    val path = new URI(req.getUri).getPath
    val parts = path.split("/").toList
    if (path.startsWith("/")) parts drop 1 else parts
  }

  private def host(fullName: String): List[String] = {
    val portStartIdx = fullName indexOf ":"
    val name =
      if (portStartIdx >= 0) fullName dropRight (fullName.length - portStartIdx)
      else fullName
    
    name.split("\\.").toList
  }

  private def reqBody(req: HttpRequest): HTTPBody = {
    val contentLength = HttpHeaders.getContentLength(req)
    val array: Array[Byte] = new Array(contentLength.toInt)
    req.getContent.getBytes(0, array)

    array
  }


}
