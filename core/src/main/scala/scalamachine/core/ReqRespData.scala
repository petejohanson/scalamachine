package scalamachine.core

import scalamachine.internal.scalaz.LensT._
import scalamachine.internal.scalaz.@>
import HTTPMethods._
import ReqRespData.{HostData,PathData,Metadata}

sealed trait HasHeaders {
  def headers: Headers
}

case class Request(method: HTTPMethod,
                   override val headers: Headers,
                   pathParts: PathParts,
                   queryString: Query,
                   body: HTTPBody) extends HasHeaders {
  def acceptHeader = headers.find(_ == HTTPHeaders.Accept)
}

case class Response(code: ResponseCode,
                    override val headers: Headers,
                    body: HTTPBody) extends HasHeaders {
  def contentType(cType: String) = {
    this.copy(headers = headers ++ Map(HTTPHeaders.ContentTypeHeader -> cType))
  }
  def contentType = headers.find(_._1 == HTTPHeaders.ContentTypeHeader).map(_._2)
}


/**
 * Represents the request being sent by the client as well as the response built by the resource
 *
 * A Note about Headers:
 * framework implementations ensure that only headers that Scalamachine knows
 * about (defined via [[scalamachine.core.HTTPHeaders.createHeader]])
 * are present in header collections. Additionally, header collections do not currenlty support
 * the same header appearing more than once in a request although allowed by the HTTP spec
 * Support for this will be added shortly.
 *
 * Still missing from the original implementation:
 *
 *  - HTTP Version
 *  - Peer (Client) IP
 *  - Cookies
 *  - "App Root" - see webmachine documentation (http://wiki.basho.com/Webmachine-Request.html)
 *
 * @param baseUri The base of the requested URI. Includes the scheme and host without the trailing slash (e.g. http://example.com)
 * @param pathParts List of string tokens, the request URI path split by "/"
 * @param rawPath The entire path part of the URI, including any query string present.
 * @param method The request's [[scalamachine.core.HTTPMethod]]
 * @param statusCode integer response status code
 * @param query parsed query string. duplicate keys will have multiple elements in the values list
 *              otherwise the values list will only have one element
 * @param requestHeaders Request headers
 * @param responseHeaders Response headers
 * @param requestBody The body of the request. See [[scalamachine.core.HTTPBody]] for more
 * @param responseBody The body of the response to be set by this resource. See [[scalamachine.core.HTTPBody]] for more
 * @param doRedirect If true some responses will return 303 instead of 2xx
 *
 *
 *
 */
case class ReqRespData(baseUri: String = "",
                       pathParts: List[String] = Nil,
                       rawPath: String = "",
                       query: Map[String,List[String]] = Map(),
                       hostParts: List[String] = Nil,
                       method: HTTPMethod = GET,
                       statusCode: Int = 200,
                       requestHeaders: Map[HTTPHeader, String] = Map(),
                       responseHeaders: Map[HTTPHeader, String] = Map(),
                       requestBody: HTTPBody = HTTPBody.Empty,
                       responseBody: HTTPBody = HTTPBody.Empty,
                       doRedirect: Boolean = false,
                       private[scalamachine] val pathData: PathData = PathData(),
                       private[scalamachine] val hostData: HostData = HostData(),
                       private[core] val metadata: Metadata = Metadata()) {
  

  private[scalamachine] def setPathData(newPathData: PathData) = copy(pathData = newPathData)
  private[scalamachine] def setHostData(newHostData: HostData) = copy(hostData = newHostData)

  val path = pathParts.mkString("/")
  val host = hostParts.mkString(".")

  val pathTokens = pathData.tokens
  val dispPath = pathData.dispPath
  val pathInfo = pathData.info

  val subdomainTokens = hostData.tokens
  val dispSubdomain = hostData.dispSubdomain
  val hostInfo = hostData.info

  def setStatusCode(code: Int) = copy(statusCode = code)

  def requestHeader(header: HTTPHeader) = requestHeaders.get(header)

  def responseHeader(header: HTTPHeader) = responseHeaders.get(header)

  def setResponseHeader(header: HTTPHeader, value: String) = copy(responseHeaders = responseHeaders + (header -> value))

  def mergeResponseHeaders(newHeaders: Map[HTTPHeader, String]) = copy(responseHeaders = responseHeaders ++ newHeaders)

}

object ReqRespData {
  private[core] val baseUriL: ReqRespData @> String = lensg(d => u => d copy (baseUri = u), _.baseUri)
  private[core] val statusCodeL: ReqRespData @> Int = lensg(d => c => d copy (statusCode = c), _.statusCode)
  private[core] val responseHeadersL: ReqRespData @> Map[HTTPHeader, String] = lensg(d => hdrs => d copy (responseHeaders = hdrs), _.responseHeaders)
  private[core] val requestHeadersL: ReqRespData @> Map[HTTPHeader, String] = lensg(d => hdrs => d copy (requestHeaders = hdrs), _.requestHeaders)
  private[core] val metadataL: ReqRespData @> Metadata = lensg(d => meta => d copy (metadata = meta), _.metadata)
  private[core] val methodL: ReqRespData @> HTTPMethod = lensg(d => m => d copy (method = m), _.method)
  private[core] val respBodyL: ReqRespData @> HTTPBody = lensg(d => b => d copy (responseBody = b), _.responseBody)
  private[core] val pathDataL: ReqRespData @> PathData = lensg(d => pd => d copy (pathData = pd), _.pathData)
  private[core] val pathL: ReqRespData @> String = lensg(d => p => d copy (pathParts = p.split("/").toList), _.path)
  private[core] val dispPathL: ReqRespData @> String = lensg(d => dp => d copy (pathData = d.pathData.copy(tokens = dp.split("/"))), _.dispPath)
  private[core] val doRedirectL: ReqRespData @> Boolean = lensg(d => b => d copy (doRedirect = b), _.doRedirect)

  case class Metadata(contentType: Option[ContentType] = None, chosenCharset: Option[String] = None, chosenEncoding: Option[String] = None)

  object Metadata {
    private[core] val contentTypeL: Metadata @> Option[ContentType] = lensg(m => ct => m copy (contentType = ct), _.contentType)
    private[core] val chosenCharsetL: Metadata @> Option[String] = lensg(m => cc => m copy (chosenCharset = cc), _.chosenCharset)
    private[core] val chosenEncodingL: Metadata @> Option[String] = lensg(m => enc => m copy (chosenEncoding = enc), _.chosenEncoding)
  }

  case class PathData(tokens: Seq[String] = Nil, info: Map[Symbol,String] = Map()) {
    val dispPath = tokens.mkString("/")
  }

  case class HostData(tokens: Seq[String] = Nil, info: Map[Symbol,String] = Map()) {
    val dispSubdomain = tokens.mkString(".")
  }


  /**
   * type class for converting a T into a ReqRespData
   * @tparam T a HasHeaders to be converted into a ReqRespData
   */
  trait AsReqRespData[T <: HasHeaders] {
    def toReqRespData(existing: ReqRespData, t: T): ReqRespData
  }

  /**
   * type class for converting a ReqRespData into a T
   * @tparam T a HasHeaders to be converted into a ReqRespData
   */
  trait FromReqRespData[T <: HasHeaders] {
    def fromReqRespData(existing: ReqRespData): T
  }

  /**
   * a type class for converting HasHeaders <---> ReqRespData
   * @tparam T the HasHeaders to convert to/from
   */
  trait ConvertReqRespData[T <: HasHeaders] extends AsReqRespData[T] with FromReqRespData[T]

  implicit val requestConvertReqRespData = new ConvertReqRespData[Request] {
    override def toReqRespData(existing: ReqRespData, req: Request): ReqRespData = existing.copy(method = req.method,
      requestHeaders = req.headers,
      pathParts = req.pathParts,
      query = req.queryString,
      requestBody = req.body)

    override def fromReqRespData(existing: ReqRespData): Request = Request(existing.method,
      existing.requestHeaders,
      existing.pathParts,
      existing.query,
      existing.requestBody)
  }

  implicit val responseConvertReqRespData = new ConvertReqRespData[Response] {
    override def toReqRespData(existing: ReqRespData, resp: Response): ReqRespData = existing.copy(statusCode = resp.code,
      responseHeaders = resp.headers,
      responseBody = resp.body)

    override def fromReqRespData(existing: ReqRespData): Response = Response(existing.statusCode,
      existing.responseHeaders,
      existing.responseBody)
  }
}

