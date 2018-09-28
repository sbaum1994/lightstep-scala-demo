import com.lightstep.tracer.shared.Options
import com.lightstep.tracer.shared.Options.OptionsBuilder
import com.lightstep.tracer.jre.JRETracer
import com.lightstep.tracer.shared.Span
import java.util
import java.io.FileReader
import java.net.URLEncoder
import scopt.OptionParser
import cats._
import cats.data._
import cats.implicits._
//import io.opentracing.Span
import io.circe.yaml.{parser => yamlParser}
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

case class Config(
                 accessToken: String = "",
                 host: String = "collector-grpc.lightstep.com",
                 port: Int = -1,
                 secure: Boolean = true,
                 service: String = "test-service",
                 operation: String = "test-operation",
                 verbosity: Int = 4,
                 traceDef: String = "",
                 repeat: Int = 1
                 )

case class SpanOps(tags: Map[String, String], parallelChildren: Boolean, delay: Int, responseDelay: Int, logs: Array[String], children: Array[SpanDef])
case class SpanDef(operation: String, options: SpanOps)
case class ParsedSpanOps(tags: Option[Map[String, String]], parallelChildren: Option[Boolean], delay: Option[Int], responseDelay: Option[Int], logs: Option[Array[String]], children: Option[Array[ParsedSpanDef]])
case class ParsedSpanDef(operation: String, options: Option[ParsedSpanOps])
case class CreateSpanFailure(err: Option[String])
case class CreateSpanSuccess(parent: Span)

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

  def genDuration() : Int = {
    50 + new scala.util.Random().nextInt(100)
  }

  def genChildren(arr: Array[ParsedSpanDef]): Array[SpanDef] = {
    arr.map(genSpanDef)
  }
  def genLogs() : Array[String] = {
    Array[String]()
  }

  def genSpanOps() : SpanOps = {
    SpanOps(tags = Map[String,String](), parallelChildren = false, delay = genDuration(), responseDelay = genDuration(), logs = genLogs(), children = Array[SpanDef]())
  }

  def genSpanOps(maybeOps: Option[ParsedSpanOps]) : SpanOps = {
    maybeOps match {
      case Some(ops) => {
        SpanOps(
          tags = ops.tags.getOrElse(Map[String, String]()),
          parallelChildren = ops.parallelChildren.getOrElse(false),
          delay = ops.delay.getOrElse(genDuration()),
          responseDelay = ops.responseDelay.getOrElse(genDuration()),
          logs = ops.logs.getOrElse(genLogs()),
          children = ops.children.fold(Array[SpanDef]())(genChildren))
      }
      case None => {
        genSpanOps()
      }
    }
  }

  def genSpanDef(parsedSpanDef: ParsedSpanDef) : SpanDef = {
    SpanDef(operation = parsedSpanDef.operation, options = genSpanOps(parsedSpanDef.options))
  }

  def genSpans(spanDef: SpanDef, tracer: JRETracer, parentSpan: Option[Span]): Either[CreateSpanFailure, CreateSpanSuccess] = {
    try {
      val span: Span = parentSpan.fold({
        tracer
          .buildSpan(spanDef.operation)
          .startManual
          .asInstanceOf[Span]
      })({ p =>
        tracer
          .buildSpan(spanDef.operation)
          .asChildOf(p)
          .startManual
          .asInstanceOf[Span]
      })
      span.log("Received.")

      spanDef.options.tags
        .foreach{ case (k,v) => span.setTag(k, v)}

      spanDef.options.logs.foreach(span.log) // probably should distribute within chunks of duration
      Thread.sleep(spanDef.options.delay)

      if (spanDef.options.parallelChildren) {
        spanDef.options.children.par.foreach(genSpans(_, tracer, Some(span)))
      } else {
        spanDef.options.children.foreach(genSpans(_, tracer, Some(span)))
      }

      Thread.sleep(spanDef.options.responseDelay)
      span.log("Returning.")

      span.finish()
      Right(CreateSpanSuccess(span))
    } catch {
      case e: Exception => {
        val errStr = s"An error occurred $e"
        Left(CreateSpanFailure(Some(errStr)))
      }
    }
  }

  def parseTraceDef(filePath: String) : ParsedSpanDef = {
    yamlParser.parse(new FileReader(filePath))
        .leftMap(err => err: Error)
        .flatMap(_.as[ParsedSpanDef])
        .valueOr(throw _)
  }

  def encodeURIComponent(l: Long) : String = {
    URLEncoder.encode(l.toString, "base")
      .replaceAll("\\+", "%20")
      .replaceAll("\\%21", "!")
      .replaceAll("\\%27", "'")
      .replaceAll("\\%28", "(")
      .replaceAll("\\%29", ")")
      .replaceAll("\\%7E", "~")
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

    opt[String]('t', "trace-def")
      .action( (x, c) => c.copy(traceDef = x) ).text("trace definition file (for defining child spans etc.)")

    opt[String]('r', "service")
      .action( (x, c) => c.copy(service = x) ).text("service to make this trace under")

    opt[String]('o', "operation")
      .action( (x, c) => c.copy(operation = x) ).text("operation name to use for the test span, if you specify a trace def file, that will override this setting")

    opt[Int]('v', "verbosity")
        .action( (x, c) => c.copy(verbosity = x) ).text("tracer logging verbosity")

    opt[Int]('n', "repeat")
      .action( (x, c) => c.copy(repeat = x) ).text("number of times to repeat")

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
        .withComponentName(config.service)
        .withVerbosity(config.verbosity)
        .build

      val tracer: JRETracer = new JRETracer(options)
      if (config.traceDef != "") {
        try {
          val definition = genSpanDef(parseTraceDef(config.traceDef))
          val range = Vector.range(0, config.repeat)
          range.par.foreach({ i => {
            genSpans(definition, tracer, None)
              .leftMap(failure => {
                println(failure)
                sys.exit(1)
              })
              .flatMap({ s: CreateSpanSuccess =>
                if (i == config.repeat-1) {
                  val prefix = "https://app.lightstep.com"
                  val spanId : Long = s.parent.context().getSpanId()
                  val spanStr = java.lang.Long.toHexString(spanId)
                  val url = s"$prefix/${config.accessToken}/trace?span_guid=$spanStr"
                  println(s"Span Url: $url")
                }
                Right()
              })
            tracer.flush(100)
          }})
        } catch {
          case err: Error => {
            println(s"Error parsing trace definition. ${err.toString}")
            sys.exit(1)
          }
          case _: Throwable => {
            println(s"Error parsing trace definition.")
            sys.exit(1)
          }
        }
      } else {
        val span = tracer.buildSpan(config.operation).startManual
        span.setTag("Lorem", "Ipsum")
        span.log("Lorem Ipsum Dolor")
        span.finish()
      }
      tracer.flush(100)
    }

    case None =>
    // arguments are bad, error message will have been displayed
  }

  sys.exit(0)
}