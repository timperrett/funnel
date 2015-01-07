package oncue.svc.funnel
package agent

import argonaut._, Argonaut._
// import oncue.svc.funnel.instruments.Counter

case class Request(
  cluster: String,
  metrics: List[ArbitraryMetric]
)
case class ArbitraryMetric(
  name: String,
  kind: InstrumentKind,
  value: Option[String]
)

case class InstrumentRequest(
  cluster: String,
  counters: List[ArbitraryMetric] = Nil,
  timers: List[ArbitraryMetric] = Nil,
  stringGauges: List[ArbitraryMetric] = Nil,
  doubleGauges: List[ArbitraryMetric] = Nil
)

trait InstrumentKind
object InstrumentKinds {
  case object Counter extends InstrumentKind
  case object Timer extends InstrumentKind
  case object GaugeString extends InstrumentKind
  case object GaugeDouble extends InstrumentKind
}

object JSON {
  import InstrumentKinds._
  import concurrent.duration._

  // {
  //   "cluster": "foo-whatever",
  //   "metrics": [
  //     {
  //       "name": "ntp/whatever",
  //       "kind": "gauge-double",
  //       "value": "0.1234"
  //     }
  //   ]
  // }

  implicit def JsonToInstrumentRequest: DecodeJson[InstrumentRequest] =
    DecodeJson(c => for {
      k <- (c --\ "cluster").as[String]
      m <- (c --\ "metrics").as[List[ArbitraryMetric]]
    } yield InstrumentRequest(
      cluster      = k,
      counters     = m.filter(_.kind == Counter),
      timers       = m.filter(_.kind == Timer),
      stringGauges = m.filter(_.kind == GaugeString),
      doubleGauges = m.filter(_.kind == GaugeDouble)
    ))

  implicit val JsonToArbitraryMetric: DecodeJson[ArbitraryMetric] =
    DecodeJson(c => for {
      n <- (c --\ "name").as[String]
      k <- (c --\ "kind").as[InstrumentKind]
      v <- (c --\ "value").as[String].option
    } yield ArbitraryMetric(n, k, v))

  implicit val JsonToInstrumentKind: DecodeJson[InstrumentKind] = DecodeJson { c =>
    c.as[String].map(_.toLowerCase).flatMap {
      case "counter"      => DecodeResult.ok { Counter }
      case "timer"        => DecodeResult.ok { Timer }
      case "gauge-string" => DecodeResult.ok { GaugeString }
      case "gauge-double" => DecodeResult.ok { GaugeDouble }
    }
  }
}

