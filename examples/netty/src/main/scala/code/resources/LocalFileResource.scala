package code.resources

import scalaz.syntax.monad._
import scalaz.effect.IO
import scalamachine.core._
import ReqRespData.queryL
import java.io.{FileReader, File}

object LocalFileResource extends Resource {
  
  // TODO: actually use the context to store the path
  type Context = String
  def init = defaultPath
  
  import Res._

  private val chunkSize = 1024
  private val defaultPath = "examples/netty/src/main/scala/code/resources/LocalFileResource.scala"

  override def contentTypesProvided: Result[ContentTypesProvided] = {
    List((ContentType("text/plain"), renderFile)).point[Result]
  }

  private def renderFile: Result[HTTPBody] = {
    (dataL >=> queryL member "path").map(_.flatMap(_.headOption) getOrElse defaultPath).lift[IO].liftM[ResT] map { 
      path => 
        LazyStreamBody(
          initialize = new FileReader(new File(path)),
          produce = (reader: FileReader) => {
            val characters = new Array[Char](chunkSize)
            try {
              val numRead = reader.read(characters)
              println("READ BYTES: %d" format numRead)
              if (numRead < 0) HTTPBody.EOFChunk
              else HTTPBody.ByteChunk(characters.slice(0, numRead).map(_.toByte))
            } catch {
              case e => HTTPBody.ErrorChunk(e)
            }
          },
          ensuring = (r: FileReader) => { println("CLOSING FILE"); r.close() }
        )
    }    
  }

}
