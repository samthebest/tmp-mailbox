package tmp_mailbox

import org.specs2.mutable.Specification

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

// TODO Put in IT directory or something
object PerformanceTest extends Specification {
  "TenMinMailbox.createInbox" should {
    "Handle an extreme burst in 1 second, giving burst TPS of ~1,000,000" in {
      val mail = new TenMinMail(EmailPrefixList(), 10.minute.toMillis, 10)
      val numFutures = 10
      val count: AtomicInteger = new AtomicInteger()
      val blewUp: AtomicBoolean = new AtomicBoolean()
      val start = System.currentTimeMillis()

      def createMany(): Unit =
        try (1 until 1000000 / numFutures).foreach(_ => mail.createInbox()(() => System.currentTimeMillis()))
        catch {
          case NonFatal(e) =>
            e.printStackTrace()
            blewUp.set(true)
        } finally count.addAndGet(1)

      (1 to numFutures).foreach(_ => Future(createMany()))

      while (count.get() < numFutures) java.lang.Thread.sleep(10)

      (System.currentTimeMillis() - start) must beLessThan(1000L)
      blewUp.get() must beFalse
    }
  }
}
