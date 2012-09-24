package code

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.handler.execution.{OrderedMemoryAwareThreadPoolExecutor, ExecutionHandler}
import org.slf4j.LoggerFactory
import scalamachine.netty.ScalamachineV3ChannelPipelineFactory
import scalamachine.core.dispatch.{RoutingTable, Route}
import Route._
import scalamachine.core.v3.WebmachineV3Runner
import resources.{DefaultResource, UnavailableResource, LocalFileResource}

object ExampleServer extends App {

  private val logger = LoggerFactory.getLogger(this.getClass)

  val defaultRoute = pathMatching {
    "default"
  } serve DefaultResource

  val unavailableRoute = pathMatching {
    "unavailable"
  } serve UnavailableResource

  val localFileRoute = pathMatching {
    "localfile"
  } serve LocalFileResource

  val bootstrap = new ServerBootstrap(
    new NioServerSocketChannelFactory(
      Executors.newCachedThreadPool(),
      Executors.newCachedThreadPool()
    )
  )

  val execHandler = new ExecutionHandler(new OrderedMemoryAwareThreadPoolExecutor(16, 1048576, 1048576))
  bootstrap.setPipelineFactory(
    new ScalamachineV3ChannelPipelineFactory(
      execHandler, 
      RoutingTable(
        defaultRoute,
        unavailableRoute,
        localFileRoute
      )
    )
  )

  logger.info("starting Scalamachine Netty Example Server on port 8080")
  bootstrap.bind(new InetSocketAddress(8080))

}
