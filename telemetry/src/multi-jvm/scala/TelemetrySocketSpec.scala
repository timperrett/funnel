package funnel
package telemetry

import scalaz.concurrent._
import scalaz.syntax.traverse._
import scalaz.std.vector._
import scalaz.stream.async._
import scalaz.stream.{Process,Channel,io, Sink, wye}
import scalaz.std.anyVal._
import java.net.URI
import scalaz.{-\/,\/,\/-,Either3}
import Telemetry._
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}

trait TelemetryMultiTest {
  val S  = signalOf[Boolean](true)(Strategy.Executor(Monitoring.serverPool))
  val U1 = new URI("ipc:///tmp/u1.socket")

  val dummyActor: Actor[Any] = Actor[Any]{ _ => () }(Strategy.Executor(Monitoring.serverPool))

  val testKeys = List(
    Key("key1", Reportable.B, Units.Count, "desc", Map("ka1" -> "va1")),
    Key("key2", Reportable.D, Units.Ratio, "desc", Map("kb1" -> "vb1")),
    Key("key3", Reportable.S, Units.TrafficLight, "desc", Map("kc1" -> "vc1")),
    Key("key4", Reportable.B, Units.Load, "desc", Map("kd1" -> "vd1")),
    Key("key5", Reportable.D, Units.Count, "desc", Map("ke1" -> "ve1")),
    Key("key6", Reportable.S, Units.TrafficLight, "desc", Map("kf1" -> "vf1"))
  )

  val errors = List(
    Error(Names("kind1", "mine", new URI("http://theirs"))),
    Error(Names("kind2", "mine", new URI("http://theirs"))),
    Error(Names("kind3", "mine", new URI("http://theirs"))),
    Error(Names("kind4", "mine", new URI("http://theirs")))
  )
}


class SpecMultiJvmPub extends FlatSpec with Matchers with TelemetryMultiTest {
  import scala.concurrent.duration.DurationInt

  "publish socket" should "publish" in {
    S.set(true).run

    val keysIn = signalOf(Set.empty[Key[Any]])(Strategy.Executor(Monitoring.serverPool))
    val keysInD = keysIn.discrete

    val sets: Vector[Set[Key[Any]]] = testKeys.tails.toVector.reverse.map(_.toSet).filterNot(_.isEmpty)

    sets.traverse_{ s => keysIn.set(s) }.run

    val errorsS = Process.emitAll(errors)

    val ST = Strategy.Executor(Monitoring.serverPool)

    val events = errorsS.wye(keysInD pipe keyChanges)(wye.merge)(ST)
    val pub: Task[Unit] = telemetryPublishSocket(U1, S, events)
    pub.runAsync {
      case -\/(e) => e.printStackTrace
      case \/-(_) =>
    }

    Thread.sleep(5000)
    keysIn.close.run
  }
}

class SpecMultiJvmSub extends FlatSpec with Matchers with TelemetryMultiTest {
  "sub socket" should "sub" in {

    var keysOut: Map[URI, Set[Key[Any]]] = Map.empty
    val keyActor: Actor[(URI, Set[Key[Any]])] = Actor[(URI,Set[Key[Any]])] {
      case (uri,keys) =>
        keysOut = keysOut + (uri -> keys)
    }(Strategy.Executor(Monitoring.serverPool))

    var errorsOut: List[Error] = List.empty
    val errorsActor: Actor[Error] = Actor[Error] { e =>
      errorsOut = e :: errorsOut
    }(Strategy.Executor(Monitoring.serverPool))

    Thread.sleep(100)
    val sub = telemetrySubscribeSocket(U1, S,
                                       keyActor,
                                       errorsActor,
                                       dummyActor.asInstanceOf[Actor[Either3[URI,URI,(URI,String)]]])

    sub.runAsync(x => println("RESULT OF RUNNING TELEMETRY: " + x))

    Thread.sleep(5000)

    errorsOut.reverse should be (errors)

    keysOut.size should be (1)
    keysOut(U1) should be (testKeys.toSet)

  }

}

