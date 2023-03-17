package tmp_mailbox

import org.specs2.mutable.Specification
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

// TODO Put in IT directory or something
object PerformanceTest extends Specification {
  System.err.println("Creating uber prefix list")
  val prefixList = EmailPrefixList()
  System.err.println("Created uber prefix list")
  implicit val clock: DefaultClock.type = DefaultClock

  "TenMinMailbox.createInbox" should {
    val mailbox = new TenMinMailbox(prefixList, 10.minute.toMillis, 10)
    "Handle an extreme burst in 1 second, giving burst TPS of ~1,000,000" in {
      val numFutures = 10
      val numRuns = 1000000 / numFutures - 1

      val count: AtomicInteger = new AtomicInteger()
      var blewUp: Boolean = false
      val start = System.currentTimeMillis()

      def forkFuture(): Future[Unit] =
        Future {
          try {
            (1 to numRuns).foreach(_ => mailbox.createInbox())
          } catch {
            case NonFatal(e) =>
              e.printStackTrace()
              blewUp = true
          } finally {
            count.addAndGet(1)
          }
        }

      (1 to numFutures).foreach(_ => forkFuture())

      while (count.get() < numFutures) java.lang.Thread.sleep(10)

      val end = System.currentTimeMillis()

      System.err.println("(end - start) = " + (end - start))

      (end - start) must beLessThan(1000L)

      blewUp must beFalse
    }
  }
}
