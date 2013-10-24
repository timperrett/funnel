package intelmedia.ws.commons.monitoring

import org.scalacheck._
import Prop._
import Arbitrary._
import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.Nondeterminism
import scalaz.stream.{process1, Process}

object MonitronSpec extends Properties("monitron") {

  val B = Buffers

  /*
   * Check that `roundDuration` works as expected for
   * some hardcoded examples.
   */
  property("roundDuration") = secure {
    B.roundDuration(0 minutes, 5 minutes) == (5 minutes) &&
    B.roundDuration(14 seconds, 1 minutes) == (1 minutes) &&
    B.roundDuration(60 seconds, 1 minutes) == (2 minutes) &&
    B.roundDuration(61 seconds, 1 minutes) == (2 minutes) &&
    B.roundDuration(59 seconds, 2 minutes) == (2 minutes) &&
    B.roundDuration(119 seconds, 1 minutes) == (2 minutes) &&
    B.roundDuration(120 seconds, 1 minutes) == (3 minutes) &&
    B.roundDuration(190 milliseconds, 50 milliseconds) == (200 milliseconds)
  }

  /*
   * Check that `counter` properly counts.
   */
  property("counter") = forAll { (xs: List[Int]) =>
    val c = B.counter(0)
    val input: Process[Task,Int] = Process.emitAll(xs)
    val out = input.pipe(c).runLog.run
    out == xs.scanLeft(0)(_ + _)
  }

  /*
   * Check that `resetEvery` properly resets the stream
   * transducer after the elapsed time.
   */
  property("resetEvery") = forAll { (h: Int, t: List[Int]) =>
    val xs = h :: t
    val c = B.resetEvery(5 minutes)(B.counter(0))
    val input: Process[Task,(Int,Duration)] =
      Process.emitAll(xs.map((_, 0 minutes))) ++
      Process.emitAll(xs.map((_, 5 minutes)))
    val out = input.pipe(c).runLog.run
    require(out.length % 2 == 0, "length of output should be even")
    val (now, later) = out.splitAt(out.length / 2)
    (now == later) && (now == xs.scanLeft(0)(_ + _))
  }

  /*
   * Check that all values are eventually received by a
   * buffered signal.
   */
  property("bufferedSignal") = forAll { (xs: List[Int]) =>
    val (snk, s) = Monitoring.bufferedSignal(B.counter(0))
    xs.foreach(x => snk(x, _ => ()))
    val expected = xs.sum
    // this will 'eventually' become true, and loop otherwise
    while (s.continuous.once.runLastOr(0).run != expected) {
      Thread.sleep(10)
    }
    true
  }

  property("distinct") = forAll { (xs: List[Int]) =>
    val input: Process[Task,Int] = Process.emitAll(xs)
    input.pipe(B.distinct).runLog.run.toList == xs.distinct
  }

  /* Check that publishing to a bufferedSignal is 'fast'. */
  property("bufferedSignal-profiling") = secure {
    val N = 100000
    val (snk, s) = Monitoring.bufferedSignal(B.counter(0))
    val t0 = System.nanoTime
    (0 to N).foreach(x => snk(x, _ => ()))
    val expected = (0 to N).sum
    while (s.continuous.once.runLastOr(0).run != expected) {
      Thread.sleep(10)
    }
    val d = Duration.fromNanos(System.nanoTime - t0) / N.toDouble
    // println("Number of microseconds per event: " + d.toMicros)
    // I am seeing around 25 microseconds on avg
    d.toMicros < 500
  }

  property("pub/sub") = forAll(Gen.listOf1(Gen.choose(1,10))) { a =>
    val M = Monitoring.default
    val (k, snk) = M.topic("count")(B.ignoreTime(B.counter(0)))
    val count = M.get(k)
    a.foreach { a => snk(a) }
    val expected = a.sum
    var got = count.continuous.once.map(_.get).runLastOr(0).run
    while (got != expected) {
      got = count.continuous.once.map(_.get).runLastOr(0).run
      Thread.sleep(10)
    }
    true
  }

  property("concurrent-counters-integration-test") = forAll {
    (h: Int, t: List[Int]) =>
      val ab = h :: t
      val (a,b) = ab.splitAt(ab.length / 2)
      import Instruments.default._
      val aN = counter("a")
      val bN = counter("b")
      val abN = counter("ab")
      // println("subscribing to latest")
      val latest = Monitoring.snapshot(Monitoring.default)
      Nondeterminism[Task].both(
        Task { a.foreach { a => aN.incrementBy(a); abN.incrementBy(a) } },
        Task { b.foreach { b => bN.incrementBy(b); abN.incrementBy(b) } }
      ).run
      val m = latest.run
      // println(m)
      true
  }
}

