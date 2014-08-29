package oncue.svc.funnel.chemist

import org.scalatest.{FlatSpec,Matchers}
import scalaz.{\/,\/-,-\/,==>>}
import scalaz.stream.{Process,Sink}
import scalaz.concurrent.Task
import Sharding.{Distribution,Target}

class LifecycleSpec extends FlatSpec with Matchers with ChemistSpec {
  val sqs = new TestAmazonSQS
  val ec2 = TestAmazonEC2(Fixtures.instances:_*)

  val r = new StatefulRepository(ec2)
  val k1 = "i-dx947af7"
  val k2 = "i-15807647"

  private def effect(a: Action, s: Sink[Task, Action]): Unit = {
    val stream: Process[Task, Action] = Process.emit(a)
    stream.to(s).runLast.run
  }

  it should "Lifecycle.stream should process the ASG event JSON into the right algebra" in {
    Lifecycle.stream("doesntexist")(sqs)
      .until(Process.emit(false)).runLast.run should equal (
        Some(\/-(Redistribute("i-dd947af7"))))
  }

  ///// as we're testing effects in sinks, keep these in this order (urgh!) //////

  it should "1. Lifecycle.toSink should compute and update state given 'AddCapacity' command" in {
    println {
      effect(AddCapacity(k1), Lifecycle.sink(r))
      r.assignedTargets(k1).run should equal (Set.empty[Target])
    }


    // r1.get should equal ( ==>>(k1 -> Set.empty) )

    // effect(AddCapacity(k2), Lifecycle.sink.noop)
    // r1.get should equal ( ==>>(k1 -> Set.empty, k2 -> Set.empty) )
  }

  // it should "2. Lifecycle.toSink should compute and update state given 'Redistribute' command" in {
  //   val target = Target("foo", SafeURL("http://bar.internal"))
  //   r1.update(_.adjust(k1, _ ++ Set(target)))

  //   effect(Redistribute(k1), Lifecycle.sink.noop)
  //   r1.get should equal ( ==>>(k2 -> Set(target)) )
  // }

}
