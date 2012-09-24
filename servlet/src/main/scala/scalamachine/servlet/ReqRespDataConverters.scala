package scalamachine.servlet

import java.io.InputStream
import java.net.URI
import javax.servlet.http.HttpServletRequest
import scalamachine.core._
import scala.collection.JavaConverters._

/**
 * @todo there is some code duplication here with the Netty implementation
 */ 
trait ReqRespDataConverters {

  def fromHostData(req: HttpServletRequest): ReqRespData = {
    val uri = new URI(req.getRequestURL.toString)
    ReqRespData(
      method = HTTPMethod.fromString(req.getMethod),
      pathParts = path(uri),
      rawPath = req.getRequestURI,
      hostParts = host(uri.getHost),
      baseUri = "%s://%s".format(req.getScheme, uri.getHost),
      query = queryString(uri),
      requestBody = requestBody(req.getInputStream),
      requestHeaders = requestHeaders(req)
    )
  }

  private def host(fullName: String): List[String] = {
    val portStartIdx = fullName indexOf ":"
    val name =
      if (portStartIdx >= 0) fullName dropRight (fullName.length - portStartIdx)
      else fullName
    
    name.split("\\.").toList
  }

  private def requestHeaders(req: HttpServletRequest): Map[HTTPHeader, String] = {
    val names = req.getHeaderNames.asScala.collect { case s: String => s }
    names.foldLeft(Map[HTTPHeader, String]())((m, n) => {
      val header = Option(n).collect { case HTTPHeader(h) => h }
      header.map(h => m + (h -> req.getHeader(n))).getOrElse(m)
    })
  }

  private def queryString(uri: URI): Map[String, List[String]] = {
    // query string parsing based on Lift's Req.scala:
    // https://github.com/lift/framework/blob/master/web/webkit/src/main/scala/net/liftweb/http/Req.scala
    val params = for {
      nameVal <- Option(uri.getRawQuery).map(_.split("&").toList.map(_.trim).filter(_.length > 0)).getOrElse(List())
      (name, value) <- nameVal.split("=").toList match {
        case Nil => None
        case n :: v :: _ => Some((n, v))
        case n :: _ => Some((n, ""))
      }
    } yield (name, value)

    params.foldLeft(Map[String, List[String]]()) {
      case (map, (name, value)) => map + (name -> (map.getOrElse(name, Nil) ::: List(value)))
    }
  }

  private def requestBody(input: InputStream): Array[Byte] = {
    Iterator.continually(input.read).takeWhile(_ != -1).map(_.toByte).toArray
  }

  private def path(uri: URI): List[String] = {
    val path = uri.getPath
    val parts = path.split("/").toList
    if (path.startsWith("/")) parts.drop(1) else parts
  }

}

