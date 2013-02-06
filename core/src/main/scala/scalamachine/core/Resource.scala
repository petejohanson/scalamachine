package scalamachine.core

import java.util.Date
import HTTPMethods._

/**
 * Resources represent HTTP resources in an application's API.
 *
 * Applications should subclass this trait and implement the functions necessary for
 * its resources' logic.
 */
trait Resource {
  import Resource._
  import Res._

  //def init: C

  /**
   * @note default - `true`
   * @return true if the service(s) necessary to service this resource are available, false otherwise
   */
  def serviceAvailable(req: Request): Res[Boolean] = result(true)

  /**
   * @note default - `List(OPTIONS, TRACE, CONNECT, HEAD, GET, POST, PUT, DELETE)`
   * @return list of [[scalamachine.core.HTTPMethod]]s known by this resource
   */
  def knownMethods(req: Request): Res[List[HTTPMethod]] = {
    result(List(OPTIONS, TRACE, CONNECT, HEAD, GET, POST, PUT, DELETE))
  }

  /**
   * @note default - `false`
   * @return true if the request URI is too long, otherwise false
   */
  def uriTooLong(req: Request): Res[Boolean] = result(false)

  /**
   * @note default - `List(GET)`
   * @return list of [[scalamachine.core.HTTPMethod]]s allowed by this resource
   */
  def allowedMethods(req: Request): Res[List[HTTPMethod]] = result(GET :: Nil)

  /**
   * @note default - `false`
   * @return true if the request is malformed, false otherwise
   */
  def isMalformed(req: Request): (MbResponse, Res[Boolean]) = {
    NoResponse -> result(false)
  }

  /**
   * @note default - `AuthSuccess`
   * @return [[scalamachine.core.AuthSuccess]] if request is authorized
   *        [[scalamachine.core.AuthFailure]] otherwise
   */
  def isAuthorized(req: Request): (MbResponse, Res[AuthResult]) = {
    NoResponse -> result(AuthSuccess)
  }

  /**
   * @note default - `false`
   * @return true if request is forbidden, false otherwise
   */
  def isForbidden(req: Request): (MbResponse, Res[Boolean]) = {
    NoResponse -> result(false)
  }

  /**
   * @note default - `true`
   * @return true if `Content-*` headers are valid, false otherwise
   */
  def contentHeadersValid(req: Request): (MbResponse, Res[Boolean]) = {
    NoResponse -> result(true)
  }

  /**
   * @note default - `true`
   * @return true if content type is known, false otherwise
   */
  def isKnownContentType(req: Request): (MbResponse, Res[Boolean]) = {
    NoResponse -> result(true)
  }

  /**
   * @note default - `true`
   * @return true if request body length is valid, false otherwise
   */
  def isValidEntityLength(req: Request): (MbResponse, Res[Boolean]) = {
    NoResponse -> result(true)
  }

  /**
   * @note default - no additional headers
   * @return additional headers to include in the reponse to an `OPTIONS` request
   */
  def options(req: Request): (MbResponse, Res[Headers]) = {
    NoResponse -> result(EmptyHeaders)
  }

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
  def contentTypesProvided(req: Request): (MbResponse, Res[ContentTypesProvided]) = {
    (NoResponse, result((ContentType("text/html") -> defaultResponse) :: Nil))
  }

  /**
   * @note default - `true`
   * @return true if the accepted language is available
   * @todo change to real content negotiation like ruby port
   */
  def isLanguageAvailable(res: Request): Res[Boolean] = {
    result(true)
  }

  /**
   * this functions determines the charset of the response body and influences whether or not the request
   * can be serviced by this resource based on the `Accept-Charset`. If the charset is acceptable,
   * the charsetting function will be run. See information about the return value for more.
   *
   * The charsetting function is of type `Array[Byte] => Array[Byte]`
   *
   * @note default - None
   * @return An optional list of provided charsets. If None, charset short-circuiting is used and no charsetting is performed.
   *         However, if Some containing a list of 2-tuples of charsets and charsetting functions, the chosen charsetting function
   *         will be applied the response body.
   */
  def charsetsProvided(req: Request): Res[CharsetsProvided] = {
    result(None)
  }

  /**
   * @note default - supports the "identity" encoding
   * @return similar to [[scalamachine.core.Resource.charsetsProvided]] except for response encoding
   */
  def encodingsProvided(req: Request): Res[CharsetsProvided] = {
    result(Some(("identity", identity[Array[Byte]](_)) :: Nil))
  }

  /**
   * for most resources that access a datasource this is where that access should most likely be performed and
   * placed into a variable in the resource (or the resource's context -- coming soon)
   * @note default - `true`
   * @return true if the resource exists, false otherwise
   */
  def resourceExists(req: Request): (MbResponse, Res[Boolean]) = {
    NoResponse -> result(true)
  }

  /**
   * @note default - `Nil`
   * @return headers to be included in the `Vary` response header.
   *         `Accept`, `Accept-Encoding`, `Accept-Charset` will always be included and do not need to be in the returned list
   */
  def variances(req: Request): Res[Seq[String]] = {
    result(Nil)
  }

  /**
   * @note default - `None`
   * @return the _etag_, if any, for the resource to be included in the response or to determine if the cached response is fresh
   */
  def generateEtag(req: Request): Res[Option[String]] = {
    result(None)
  }

  /**
   * @note default - `None`
   * @return the last modified date of the resource being requested, if any, included in response or to determine if
   *         cached response is fresh
   */
  def lastModified(req: Request): Res[Option[Date]] = {
    result(None)
  }

  /**
   * @note default - `None`
   * @return None if the resource has not been moved, Some, contain the URI of its new location otherwise
   */
  def movedPermanently(req: Request): Res[Option[String]] = {
    result(None)
  }

  /**
   * @note default - `false`
   * @return true if the resource existed before, although it does not now, false otherwise
   */
  def previouslyExisted(req: Request): Res[Boolean] = {
    result(false)
  }

  /**
   * @note default - `None`
   * @return None if the resource is not moved temporarily, Some containing its temporary URI otherwise
   */
  def movedTemporarily(req: Request): Res[Option[String]] = {
    result(None)
  }

  /**
   * @note default - `false`
   * @return true if this resource allows `POST` requests to be serviced when the resource does not exist,
   *         false otherwise
   */
  def allowMissingPost(req: Request): Res[Boolean] = {
    result(false)
  }

  /**
   * Perform the deletion of the resource during a `DELETE` request
   * @note default - `false`
   * @return true if the deletion succeed (does not necessarily have to complete), false otherwise
   */
  def deleteResource(req: Request): (MbResponse, Res[Boolean]) = {
    NoResponse -> result(false)
  }

  /**
   * @note default - `true`
   * @return true if the deletion is complete at the time this function is called, false otherwise
   */
  def deleteCompleted(req: Request): (MbResponse, Res[Boolean]) = {
    NoResponse -> result(true)
  }

  /**
   * Determines whether or not `createPath` or `processPost` is called during a `POST` request
   * @note default - `false`
   * @return true will cause the resource to follow a path that calls `createPath`, false will
   *         result in the flow calling `processPost`
   */
  def postIsCreate(req: Request): Res[Boolean] = {
    result(false)
  }

  /**
   * Used for `POST` requests that represent generic processing on a resource, instead of creation
   * @note default - `false`
   * @return true of the processing succeeded, false otherwise
   */
  def processPost(req: Request): (Response, Res[Boolean]) = {
    Response(204) -> result(false)
  }

  /**
   * Use for `POST` requests that represent creation of data. Makes handling somewhat similar to
   * a `PUT` request ultimately
   * @note default - `None`
   * @return if this function is called it must return `Some` containing the created path,
   *         returning None is considered an error
   * @todo need to elaborate more on what happens with the returned path and what
   *       exactly the string should be
   */
  def createPath(req: Request): (Response, Res[Option[String]]) = {
    Response(204) -> result(None)
  }

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
  def contentTypesAccepted(req: Request): (Response, Res[ContentTypesAccepted]) = {
    Response(200) -> result(Nil)
  }

  /**
   * @note default - `false`
   * @return true if the `PUT` request cannot be handled due to conflict, false otherwise
   */
  def isConflict(req: Request): (MbResponse, Res[Boolean]) = {
    NoResponse -> result(false)
  }

  /**
   * @note default - `None`
   * @return if `Some` the date will be set as the value of the `Expires` header in the response
   */
  def expires(req: Request): Res[Option[Date]] = result(None)

  def multipleChoices(req: Request): (MbResponse, Res[Boolean]) = {
    NoResponse -> result(false)
  }

  private val defaultResponse: Request => (Response, Res[HTTPBody]) = { req =>
    Response(200) -> result(FixedLengthBody(defaultHtml))
  }

  private val defaultHtml = <html><body>Hello,Scalamachine</body></html>.toString()

}

object Resource {
  private type ContentTypeGeneric[T] = (ContentType, Request => (Response, Res[T]))
  type ContentTypeProvided = ContentTypeGeneric[HTTPBody]
  type ContentTypesProvided = List[ContentTypeProvided]
  type ContentTypeAccepted = ContentTypeGeneric[Boolean]
  type ContentTypesAccepted = List[ContentTypeAccepted]
  type CharsetsProvided = Option[List[(String,Array[Byte] => Array[Byte])]] // None value specifies charset negotiation short-circuiting
  type EncodingsProvided = Option[List[(String,Array[Byte] => Array[Byte])]] // None values specifies encoding negotiation short-circuiting

  type MbResponse = Option[Response]
  val NoResponse = Option.empty[Response]

  private def contentTypeGeneric[T](contentType: ContentType)(fn: Request => (Response, Res[T])): ContentTypeGeneric[T] = {
    contentType -> fn
  }

  private def contentTypeGeneric[T](contentType: ContentType, response: Response, res: Res[T]): ContentTypeGeneric[T] = {
    contentTypeGeneric(contentType) { _ =>
      response -> res
    }
  }

  /**
   * create a single ContentTypeProvided tuple
   * @param contentType the first element of the tuple
   * @param fn the second element of the tuple
   * @return the tuple
   */
  def contentTypeProvided(contentType: ContentType)
                         (fn: Request => (Response, Res[HTTPBody])): ContentTypeProvided = {
    contentTypeGeneric(contentType)(fn)
  }

  /**
   * create a single ContentTypeProvided tuple
   * @param contentType the first element in the tuple
   * @param response the response to return in the second element of the tuple
   * @param httpBodyRes the Res[HTTPBody] to return in the second element of the tuple
   * @return the tuple
   */
  def contentTypeProvided(contentType: ContentType,
                          response: Response,
                          httpBodyRes: Res[HTTPBody]): ContentTypeProvided = {
    contentTypeGeneric(contentType, response, httpBodyRes)
  }

  /**
   * create a ContentTypeAccepted tuple
   * @param contentType the first element in the tuple
   * @param fn the second element in the tuple
   * @return the tuple
   */
  def contentTypeAccepted(contentType: ContentType)
                         (fn: Request => (Response, Res[Boolean])): ContentTypeAccepted = {
    contentTypeGeneric(contentType)(fn)
  }

  /**
   * create a ContentTypeAccepted tuple
   * @param contentType the first element in the tuple
   * @param response the response to return in the second element of the tuple
   * @param booleanRes the Res[Boolean] to return in the second element of the tuple
   * @return the tuple
   */
  def contentTypeAccepted(contentType: ContentType,
                          response: Response,
                          booleanRes: Res[Boolean]): ContentTypeAccepted = {
    contentTypeGeneric(contentType, response, booleanRes)
  }
}
