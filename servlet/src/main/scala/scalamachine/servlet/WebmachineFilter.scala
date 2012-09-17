package scalamachine.servlet

import javax.servlet._
import javax.servlet.http._
import java.io.IOException

class WebmachineFilter extends Filter {

  var dispatchTable: ServletWebmachineV3 = _

  @throws(classOf[ServletException])
  override def init(config: FilterConfig) {
    def clazzName = config.getInitParameter("dispatch-table")
    dispatchTable = Class.forName(clazzName).newInstance.asInstanceOf[ServletWebmachineV3]
  }

  @throws(classOf[IOException])
  @throws(classOf[ServletException])
  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    (request, response) match {
      case (req: HttpServletRequest, resp: HttpServletResponse) => {
        dispatchTable.apply(req).map { data =>
          resp.setStatus(data.statusCode)
          for ((h, v) <- data.responseHeaders) {
            resp.setHeader(h.wireName, v)
          }
          resp.getOutputStream.write(data.responseBody.bytes)
        } getOrElse { chain.doFilter(request, response) }
      }
      case _ => chain.doFilter(request, response)
    }
  }

  override def destroy() { }

}
