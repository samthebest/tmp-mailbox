package tmp_mailbox

import org.specs2.mutable.Specification

import scala.concurrent.duration._

object TenMinMailboxTest extends Specification {
  def fixedClock(at: Long): Clock = () => at

  "TenMinMailbox.createInbox" should {
    val smallPrefixList = EmailPrefixList(10, 3)

    System.err.println("smallPrefixList.prefixesWithIndex = " + smallPrefixList.prefixesWithIndex)

    val first :: second :: third :: Nil = smallPrefixList.prefixesWithIndex.map(_._1)
    "Cycle through the prefixes" in {
      val mailbox = new TenMinMailbox(smallPrefixList, 10.minute.toMillis, 10)

      mailbox.createInbox()(fixedClock(10.minute.toMillis)).prefix must_=== first
      mailbox.createInbox()(fixedClock(10.minute.toMillis)).prefix must_=== second
      mailbox.createInbox()(fixedClock(10.minute.toMillis)).prefix must_=== third

      mailbox.createInbox()(fixedClock(20.minute.toMillis)).prefix must_=== first
      mailbox.createInbox()(fixedClock(20.minute.toMillis)).prefix must_=== second
      mailbox.createInbox()(fixedClock(20.minute.toMillis)).prefix must_=== third

      mailbox.createInbox()(fixedClock(30.minute.toMillis)).prefix must_=== first
      mailbox.createInbox()(fixedClock(30.minute.toMillis)).prefix must_=== second
      mailbox.createInbox()(fixedClock(30.minute.toMillis)).prefix must_=== third
    }

    "Throw exception when TPS limit reached (prefix list too small)" in {
      new TenMinMailbox(smallPrefixList, 10.minute.toMillis, 10)
        .createInbox()(fixedClock(9.minute.toMillis)) must throwA[IllegalArgumentException]
    }
  }
}
