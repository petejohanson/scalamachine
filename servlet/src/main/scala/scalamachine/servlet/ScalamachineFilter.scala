package scalamachine.servlet

import javax.servlet._
import javax.servlet.http._
import java.io.IOException
import scalamachine.core.flow.WebmachineRunner
import scalamachine.core.dispatch.RoutingTable

class ScalamachineFilter extends Filter with ReqRespDataConverters {

  private var routes: RoutingTable = _
  private var runner: WebmachineRunner = _

  @throws(classOf[ServletException])
  override def init(config: FilterConfig) {
    val runnerClazzName = config.getInitParameter("runner")
    val routesClazzName = config.getInitParameter("routes")
    if (runnerClazzName.endsWith("$")) { // hackity hack for singleton object like WebmachineV3Runner
      val ctor = Class.forName(runnerClazzName).getDeclaredConstructors()(0)
      ctor.setAccessible(true)
      runner = ctor.newInstance().asInstanceOf[WebmachineRunner]
    } else runner = Class.forName(runnerClazzName).newInstance().asInstanceOf[WebmachineRunner]

    routes = Class.forName(routesClazzName).newInstance().asInstanceOf[ServletRoutingTable].routes
  }

  @throws(classOf[IOException])
  @throws(classOf[ServletException])
  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    (request, response) match {
      case (req: HttpServletRequest, resp: HttpServletResponse) => {
        runner.runUnsafe(req, routes, fromHostData(_: HttpServletRequest), identity) map { data => 
          resp.setStatus(data.statusCode)
          for ((h, v) <- data.responseHeaders) {
            resp.setHeader(h.wireName, v)
          }
          // TODO: this doesn't properly handle streaming bodies                                                                                          
          resp.getOutputStream.write(data.responseBody.bytes)
        } getOrElse { chain.doFilter(request, response) }
      }
      case _ => chain.doFilter(request, response)
    }
  }

  override def destroy() { }

}

