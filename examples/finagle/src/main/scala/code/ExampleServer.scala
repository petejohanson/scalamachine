package code

import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.http.Http
import java.net.InetSocketAddress
import scalamachine.core.HTTPHeaders
import scalamachine.core.dispatch.Rewrite
import scalamachine.core.dispatch.Route._
import scalamachine.core.dispatch.StringPart
import scalamachine.finagle.{FinagleWebmachineV3, FinagleWebmachineService}
import scalamachine.core.flow.{FlowLogging, FlowRunner}
import resources.{EchoPostBodyResource, EmptyResource, UnavailableResource}

object ScalamachineExample extends FinagleWebmachineV3 {

  override val rewrites = 
    Rewrite(_.rawPath.contains("old_empty"), _.copy(rawPath = "empty", pathParts = "empty" :: Nil)) ::
     Rewrite(_.pathParts.headOption.map(_ == "empty").getOrElse(false), d => d.copy(responseHeaders = d.responseHeaders + (HTTPHeaders.Server -> "some server"))) :: 
     Rewrite(_ => false, _ => throw new Exception("I should never be reached")) :: Nil

  route {
    hostMatching {
      "jordan" dot "localhost"
    } andPathMatching {
      "unavailable" / 'id / "a"
    } serve {
      new UnavailableResource
    }
  }

  route {
    pathMatching {
      "unavailable"
    } serve new UnavailableResource
  }

  route {
    pathMatching {
      "empty"
    } serve  new EmptyResource
  }

  route {
    pathMatching {
      "echo"
    } serve new EchoPostBodyResource
  }

/*  route {
    hostEndingWith {
      "localhost"
    } serve new EmptyResource
  }*/


  override val flowRunner = new FlowRunner with FlowLogging
}

object ExampleServer extends App {

  val server =
    ServerBuilder()
      .codec(Http())
      .bindTo(new InetSocketAddress(8080))
      .name("FinagleWebmachine")
      .build(new FinagleWebmachineService(ScalamachineExample))

}
