package scalamachine.netty

import org.jboss.netty.channel.{Channels, ChannelPipeline, ChannelPipelineFactory}
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.execution.ExecutionHandler
import scalamachine.core.routing.RoutingTable
import scalamachine.core.flow.WebmachineRunner
import scalamachine.core.v3.WebmachineV3Runner

class ScalamachineV3ChannelPipelineFactory(execHandler: ExecutionHandler, routes: RoutingTable) 
  extends ScalamachineChannelPipelineFactory(execHandler, routes, WebmachineV3Runner)

class ScalamachineChannelPipelineFactory(private val execHandler: ExecutionHandler, routes: RoutingTable, runner: WebmachineRunner)
  extends ChannelPipelineFactory {

  def getPipeline: ChannelPipeline = {
    val pipeline = Channels.pipeline()

    pipeline.addLast("request-decoder", new HttpRequestDecoder)
    pipeline.addLast("chunk-aggregator", new HttpChunkAggregator(1048576)) // not handling streaming requests yet
    pipeline.addLast("response-encoder", new HttpResponseEncoder)
    pipeline.addLast("execution-handler", execHandler)
    pipeline.addLast("request-handler", new ScalamachineRequestHandler(routes, runner))


    pipeline
  }

}
