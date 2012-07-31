package scalamachine.core

import java.util.Date
import HTTPMethods._


import scalaz.{StateT, LensT}
import StateT._
import scalaz.@>
import scalaz.syntax.monad._
import scalaz.effect.IO

/**
 * Resources represent HTTP resources in an application's API.
 *
 * Applications should subclass this trait and implement the functions necessary for
 * its resources' logic.
 */
trait Resource {
  import Resource._
  import ReqRespData._

  type Context

  type ReqRespState[+A] = StateT[IO,(ReqRespData,Context),A]
  type Result[+A] = ResTransformer[ReqRespState,A]

  type ContentTypesProvided = List[(ContentType,Result[HTTPBody])] 
  type ContentTypesAccepted = List[(ContentType,Result[Boolean])]

  val dataL: (ReqRespData, Context) @> ReqRespData = LensT.firstLens[ReqRespData, Context]
  val contextL: (ReqRespData, Context) @> Context =  LensT.secondLens[ReqRespData, Context]
  def setStatusCode(code: Int): ReqRespState[Int] = ((dataL >=> statusCodeL) := code).lift[IO]

  def haltWithCode[A](code: Int): Result[A] = 
    ResTransformer.resT[ReqRespState](Res.halt[A](code).point[ReqRespState])

  // TODO: take initial data?
  def init: Context

  /**
   * @note default - `true`
   * @return true if the service(s) necessary to service this resource are available, false otherwise
   */
  def serviceAvailable: Result[Boolean] = 
    true.point[Result]  

  /**
   * @note default - `List(OPTIONS, TRACE, CONNECT, HEAD, GET, POST, PUT, DELETE)`
   * @return list of [[scalamachine.core.HTTPMethod]]s known by this resource
   */
  def knownMethods: Result[List[HTTPMethod]] = 
    List[HTTPMethod](OPTIONS, TRACE, CONNECT, HEAD, GET, POST, PUT, DELETE).point[Result]

  /**
   * @note default - `false`
   * @return true if the request URI is too long, otherwise false
   */
  def uriTooLong: Result[Boolean] = 
    false.point[Result] 

  /**
   * @note default - `List(GET)`
   * @return list of [[scalamachine.core.HTTPMethod]]s allowed by this resource
   */
  def allowedMethods: Result[List[HTTPMethod]] = 
    List[HTTPMethod](GET).point[Result]

  /**
   * @note default - `false`
   * @return true if the request is malformed, false otherwise
   */
  def isMalformed: Result[Boolean] = 
    false.point[Result]

  /**
   * @note default - `AuthSuccess`
   * @return [[scalamachine.core.AuthSuccess]] if request is authorized
   *        [[scalamachine.core.AuthFailure]] otherwise
   */
  def isAuthorized: Result[AuthResult] = 
    (AuthSuccess: AuthResult).point[Result]

  /**
   * @note default - `false`
   * @return true if request is forbidden, false otherwise
   */
  def isForbidden: Result[Boolean] = 
    false.point[Result]

  /**
   * @note default - `true`
   * @return true if `Content-*` headers are valid, false otherwise
   */
  def contentHeadersValid: Result[Boolean] =
    true.point[Result]

  /**
   * @note default - `true`
   * @return true if content type is known, false otherwise
   */
  def isKnownContentType: Result[Boolean] = 
    true.point[Result] 

  /**
   * @note default - `true`
   * @return true if request body length is valid, false otherwise
   */
  def isValidEntityLength: Result[Boolean] = 
    true.point[Result]

  /**
   * @note default - no additional headers
   * @return additional headers to include in the reponse to an `OPTIONS` request
   */
  def options: Result[Map[HTTPHeader, String]] = 
    Map[HTTPHeader,String]().point[Result]

  /**
   * This function determines the body of GET/HEAD requests. If your resource responds to them, make sure to implement it
   * because the default most likely will not do. It should return a list of 2-tuples containing the content type and its
   * associated body rendering function. The body rendering function has signature
   * `ReqRespData => (Res[Array[Byte],ReqRespData)`
   *
   * @note default - by default resources provice the `text/html` content type, rendering a simple HTML response
   * @return list of 2-tuples from the [[scalamachine.core.ContentType]] to the rendering function.
   * @see [[scalamachine.core.Resource.ContentTypesProvided]]
   */
  def contentTypesProvided: Result[ContentTypesProvided] = 
    List(ContentType("text/html") -> defaultResponse).point[Result]

  /**
   * @note default - `true`
   * @return true if the accepted language is available
   * @todo change to real content negotiation like ruby port
   */
  def isLanguageAvailable: Result[Boolean] = true.point[Result] 

  /**
   * this functions determines the charset of the response body and influences whether or not the request
   * can be serviced by this resource based on the `Accept-Charset`. If the charset is acceptable,
   * the charsetting function will be run. See information about the return value for more.
   *
   * The charsetting function is of type `Array[Byte] => Array[Byte]`
   *
   * @note default - `None`
   * @return An optional list of provided charsets. If `None``, charset short-circuiting is used and no charsetting is performed.
   *         However, if `Some` containing a list of 2-tuples of charsets and charsetting functions, the chosen charsetting function
   *         will be applied the response body.
   */
  def charsetsProvided: Result[CharsetsProvided] = 
    (None: CharsetsProvided).point[Result]

  /**
   * @note default - supports the "identity" encoding
   * @return similar to [[scalamachine.core.Resource.charsetsProvided]] except for response encoding
   */
  def encodingsProvided: Result[EncodingsProvided] = 
    (Some(List(("identity", identity[Array[Byte]](_)))): EncodingsProvided).point[Result]

  /**
   * for most resources that access a datasource this is where that access should most likely be performed and
   * placed into a variable in the resource (or the resource's context -- coming soon)
   * @note default - `true`
   * @return true if the resource exists, false otherwise
   */
  def resourceExists: Result[Boolean] = 
    true.point[Result]

  /**
   * @note default - `Nil`
   * @return headers to be included in the `Vary` response header.
   *         `Accept`, `Accept-Encoding`, `Accept-Charset` will always be included and do not need to be in the returned list
   */
  def variances: Result[Seq[String]] = 
    (Nil: Seq[String]).point[Result]

  /**
   * @note default - `None`
   * @return the _etag_, if any, for the resource to be included in the response or to determine if the cached response is fresh
   */
  def generateEtag: Result[Option[String]] = 
    (None: Option[String]).point[Result]

  /**
   * @note default - `None`
   * @return the last modified date of the resource being requested, if any, included in response or to determine if
   *         cached response is fresh
   */
  def lastModified: Result[Option[Date]] = 
    (None: Option[Date]).point[Result]

  /**
   * @note default - `None`
   * @return None if the resource has not been moved, Some, contain the URI of its new location otherwise
   */
  def movedPermanently: Result[Option[String]] = 
    (None: Option[String]).point[Result]

  /**
   * @note default - `false`
   * @return true if the resource existed before, although it does not now, false otherwise
   */
  def previouslyExisted: Result[Boolean] = 
    false.point[Result]

  /**
   * @note default - `None`
   * @return None if the resource is not moved temporarily, Some containing its temporary URI otherwise
   */
  def movedTemporarily: Result[Option[String]] = 
    (None: Option[String]).point[Result]

  /**
   * @note default - `false`
   * @return true if this resource allows `POST` requests to be serviced when the resource does not exist,
   *         false otherwise
   */
  def allowMissingPost: Result[Boolean] = 
    false.point[Result]

  /**
   * Perform the deletion of the resource during a `DELETE` request
   * @note default - `false`
   * @return true if the deletion succeed (does not necessarily have to complete), false otherwise
   */
  def deleteResource: Result[Boolean] = 
    false.point[Result]

  /**
   * @note default - `true`
   * @return true if the deletion is complete at the time this function is called, false otherwise
   */
  def deleteCompleted: Result[Boolean] = 
    true.point[Result] 

  /**
   * Determines whether or not `createPath` or `processPost` is called during a `POST` request
   * @note default - `false`
   * @return true will cause the resource to follow a path that calls `createPath`, false will
   *         result in the flow calling `processPost`
   */
  def postIsCreate: Result[Boolean] = 
    false.point[Result]

  /**
   * Used for `POST` requests that represent generic processing on a resource, instead of creation
   * @note default - `false`
   * @return true of the processing succeeded, false otherwise
   */
  def processPost: Result[Boolean] = 
    false.point[Result]

  /**
   * Use for `POST` requests that represent creation of data. Makes handling somewhat similar to
   * a `PUT` request ultimately
   * @note default - `None`
   * @return if this function is called it must return `Some` containing the created path,
   *         returning None is considered an error
   * @todo need to elaborate more on what happens with the returned path and what
   *       exactly the string should be
   */
  def createPath: Result[Option[String]] = 
    (None: Option[String]).point[Result] 

  /**
   * Used during `PUT` requests and `POST` requests if `postIsCreate` returns true. Returns a list
   * of accepted `Content-Type`s and their associated handler functions. The handler functions
   * have the signature `ReqRespData` => `(Res[Boolean],ReqRespData)`. If the handler returns true
   * it is considered successful, otherwise it is considered to have failed and to be an error.
   *
   * If the `Content-Type` of the request is not acceptable a response with code 415 is returned
   *
   * @note default - `Nil`
   * @return a list of 2-tuples containing the accepted content types and their associated handlers
   * @see [[scalamachine.core.Resource.ContentTypesAccepted]]
   */
  def contentTypesAccepted: Result[ContentTypesAccepted] = 
    (Nil: ContentTypesAccepted).point[Result]

  /**
   * @note default - `false`
   * @return true if the `PUT` request cannot be handled due to conflict, false otherwise
   */
  def isConflict: Result[Boolean] = 
    false.point[Result]

  /**
   * @note default - `None`
   * @return if `Some` the date will be set as the value of the `Expires` header in the response
   */
  def expires: Result[Option[Date]] = 
    (None: Option[Date]).point[Result]

  def multipleChoices: Result[Boolean] = 
    false.point[Result]

  private def default[A](value: A): Res[A] = ValueRes(value)

  private val defaultResponse: Result[HTTPBody] = defaultHtml.point[Result]

  private val defaultHtml: HTTPBody = <html><body>Hello,Scalamachine</body></html>.toString

}

object Resource {
  type CharsetsProvided = Option[List[(String,Array[Byte] => Array[Byte])]] // None value specifies charset negotiation short-circuiting
  type EncodingsProvided = Option[List[(String,Array[Byte] => Array[Byte])]] // None values specifies encoding negotiation short-circuiting
}
