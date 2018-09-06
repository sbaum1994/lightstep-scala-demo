// Important the OpenTracing interfaces
import io.opentracing.Span
import io.opentracing.Tracer
import com.lightstep.tracer.shared.Options
import com.lightstep.tracer.shared.Options.OptionsBuilder
import com.lightstep.tracer.jre.JRETracer
import java.util

object Main extends App {

  def createMessagePayloaMap(message: String, payload: AnyRef) = {
    val m = new util.HashMap[String, AnyRef]
    m.put("message", message)
    m.put("payload", payload)
    m
  }

  def splitPayload(payload: String) = {
    val m = new util.HashMap[String, String]
    val parts = payload.grouped(500).toList
    parts.zipWithIndex.foreach{case (part, i) =>
      m.put("part" + i, part)
    }
    m
  }

// Initialize the OpenTracing Tracer with LightStep's implementation
  val options: Options = new OptionsBuilder()
    .withAccessToken("425c9b9734e6cd039b41689aa83937cd")
    .withDisableReportingLoop(true)
    .withVerbosity(4)
    .build

  val tracer: JRETracer = new JRETracer(options)

  // Start and finish a Span
  val span = tracer.buildSpan("chunked-logs").startManual
  val truncatedMessage = splitPayload("c" * 9000)
  span.log(truncatedMessage);
  span.finish
  tracer.flush(4000)
}