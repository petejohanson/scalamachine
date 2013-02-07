package scalamachine.servlet

import java.io.InputStream
import java.net.URI
import javax.servlet.http.HttpServletRequest
import scalamachine.core._
import scalamachine.internal.scalaz.Id._
import dispatch.DispatchTable
import v3.V3DispatchTable
import scala.collection.JavaConverters._

trait ServletWebmachine[M[_]] {
  this: DispatchTable[HttpServletRequest, ReqRespData, M] =>

  override def toData(req: HttpServletRequest): ReqRespData = {
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

  override def fromData(data: ReqRespData): ReqRespData = data

  private def requestHeaders(req: HttpServletRequest): Map[HTTPHeader, String] = {
    val names = req.getHeaderNames.asScala.collect { case s: String => s }
    names.flatMap { (headerName) => Option(req.getHeader(headerName)).map((HTTPHeader(headerName), _)) }.toMap
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

trait ServletWebmachineV3 extends V3DispatchTable[HttpServletRequest, ReqRespData, Id] with ServletWebmachine[Id] {
  override def wrap(resp: => ReqRespData): Id[ReqRespData] = resp
}
