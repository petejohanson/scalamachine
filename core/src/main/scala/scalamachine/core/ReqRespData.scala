package scalamachine.core

import scalaz.LensT._
import scalaz.@>
import HTTPMethods._
import ReqRespData.{HostData,PathData,Metadata}

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
  private[core] val metadataL: ReqRespData @> Metadata = lensg(d => meta => d copy (metadata = meta), _.metadata)
  private[core] val pathDataL: ReqRespData @> PathData = lensg(d => pd => d copy (pathData = pd), _.pathData)
  private val hostDataL: ReqRespData @> HostData = lensg(d => hd => d copy (hostData = hd), _.hostData)

  val baseUriL: ReqRespData @> String = lensg(d => u => d copy (baseUri = u), _.baseUri)
  val statusCodeL: ReqRespData @> Int = lensg(d => c => d copy (statusCode = c), _.statusCode)
  val responseHeadersL: ReqRespData @> Map[HTTPHeader, String] = lensg(d => hdrs => d copy (responseHeaders = hdrs), _.responseHeaders)
  val requestHeadersL: ReqRespData @> Map[HTTPHeader, String] = lensg(d => hdrs => d copy (requestHeaders = hdrs), _.requestHeaders)
  val methodL: ReqRespData @> HTTPMethod = lensg(d => m => d copy (method = m), _.method)
  val reqBodyL: ReqRespData @> HTTPBody = lensg(d => b => d copy (requestBody = b), _.requestBody)
  val respBodyL: ReqRespData @> HTTPBody = lensg(d => b => d copy (responseBody = b), _.responseBody)
  val pathL: ReqRespData @> String = lensg(d => p => d copy (pathParts = p.split("/").toList), _.path)
  val pathInfoL: ReqRespData @> Map[Symbol,String] = pathDataL >=> lensg(d => i => d copy (info = i), _.info)
  val pathTokensL: ReqRespData @> Seq[String] = pathDataL >=> lensg(d => ts => d copy (tokens = ts), _.tokens)
  val pathPartsL: ReqRespData @> List[String] = lensg(d => p => d copy (pathParts = p), _.pathParts)
  val rawPathL: ReqRespData @> String = lensg(d => p => d copy (rawPath = p), _.rawPath)
  val dispPathL: ReqRespData @> String = lensg(d => dp => d copy (pathData = d.pathData.copy(tokens = dp.split("/"))), _.dispPath)
  val doRedirectL: ReqRespData @> Boolean = lensg(d => b => d copy (doRedirect = b), _.doRedirect)
  val queryL: ReqRespData @> Map[String,List[String]] = lensg(d => q => d copy (query = q), _.query)
  val hostInfoL: ReqRespData @> Map[Symbol,String] = hostDataL >=> lensg(d => i => d copy (info = i), _.info)
  val hostTokensL: ReqRespData @> Seq[String] = hostDataL >=> lensg(d => ts => d copy (tokens = ts), _.tokens)
  val hostPartsL: ReqRespData @> List[String] = lensg(d => ps => d copy (hostParts = ps), _.hostParts)
  val hostL: ReqRespData @> String = lensg(d => ps => d copy (hostParts = ps.split(".").toList), _.host)
  val dispSubdomainL: ReqRespData @> String = lensg(d => ds => d copy (hostData = d.hostData.copy(tokens = ds.split(".").toList)), _.dispSubdomain)

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
}

