import com.lightstep.tracer.shared.Options
import com.lightstep.tracer.shared.Options.OptionsBuilder
import com.lightstep.tracer.jre.JRETracer
import java.util
import scopt.OptionParser

case class Config(
                 accessToken: String = "", // access token to use when reporting spans
                 host: String = "collector-grpc.lightstep.com", // hostname of the collector to which reports should be sent
                 port: Int = -1, // port of the collector to which reports should be sent
                 secure: Boolean = true, // whether or not to use TLS
                 operation: String = "test-operation" // operation name to use for the test span
                 )

object Main extends App {

  /* this is an example of logging a large stack trace/long log that would normally get cut off in LightStep's UI

  val span = tracer.buildSpan("chunked-logs").startManual
  val truncatedMessage = splitPayload("c" * 9000)
  span.log(truncatedMessage); */

  /* utils */
  def createMessagePayloadMap(message: String, payload: AnyRef) = {
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

  val parser = new OptionParser[Config]("lightstep-scala-span-generator") {
    opt[String]('a', "access-token")
      .action( (x, c) => c.copy(accessToken = x) ).text("access token to use when reporting spans")

    opt[String]('h', "collector-host")
      .action( (x, c) => c.copy(host = x) ).text("hostname of the collector to which reports should be sent")

    opt[Int]('p', "collector-port")
      .action( (x, c) => c.copy(port = x) ).text("port of the collector to which reports should be sent")

    opt[Boolean]('s', "secure")
        .action( (x, c) => c.copy(secure = x) ).text("whether or not to use TLS")

    opt[String]('o', "operation")
      .action( (x, c) => c.copy(operation = x) ).text("operation name to use for the test span")

    help("help").text("prints usage text")
  }

  parser.parse(args, Config()) match {
    case Some(config) => {
      val protocol = if (config.secure) "https" else "http"
      val port = if (config.secure && config.port == -1) 443 else if (config.port == -1) 80 else config.port

      val options: Options = new OptionsBuilder()
        .withAccessToken(config.accessToken)
        .withCollectorHost(config.host)
        .withCollectorPort(port)
        .withCollectorProtocol(protocol)
        .withDisableReportingLoop(true)
        .withVerbosity(4)
        .build

      val tracer: JRETracer = new JRETracer(options)
      val span = tracer.buildSpan(config.operation).startManual
      span.setTag("Lorem", "Ipsum")
      span.log("Lorem Ipsum Dolor")
      span.finish
      tracer.flush(4000)
    }

    case None =>
    // arguments are bad, error message will have been displayed
  }

  sys.exit(0)
}