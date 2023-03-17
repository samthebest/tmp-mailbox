package tmp_mailbox

import org.specs2.mutable.Specification

import scala.concurrent.duration._

object TenMinMailboxTest extends Specification {
  def fixedClock(at: Long): Clock = () => at

  val smallPrefixList = EmailPrefixList(10, 3)
  val first :: second :: third :: Nil = smallPrefixList.prefixesWithIndex.map(_._1)
  val pageSize = 3

  "TenMinMailbox.createInbox" should {
    "Cycle through the prefixes" in {
      val mailbox = new TenMinMailbox(smallPrefixList, 10.minute.toMillis, pageSize)

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

  def testInit(): (TenMinMailbox, EmailAddress) = {
    val mailbox = new TenMinMailbox(smallPrefixList, 10.minute.toMillis, pageSize)
    (mailbox, mailbox.createInbox()(fixedClock(10.minute.toMillis)))
  }

  "TenMinMailbox.storeEmail and getEmails" should {
    "Store emails that we can then get (and pages)" in {
      val (mailbox, address) = testInit()

      mailbox.storeEmail(address, Email("ben"))(fixedClock(0)) must beRight
      mailbox.storeEmail(address, Email("bob"))(fixedClock(0)) must beRight
      mailbox.storeEmail(address, Email("sam"))(fixedClock(0)) must beRight
      mailbox.storeEmail(address, Email("bill"))(fixedClock(0)) must beRight

      mailbox.getEmails(address)(fixedClock(0)).map(_.map(_.emails.map(_.content)).toList) must
        beRight(List(List("bill", "sam", "bob"), List("ben")))
    }

    "Trying to store emails in invalid inbox returns UserError" in {
      val (mailbox, _) = testInit()
      mailbox.storeEmail(EmailAddress("nonsense"), Email("ben"))(fixedClock(0)) must
        beLeft(UserError("Email address not exist: nonsense@ten-min-mailbox.com"))
    }

    "Trying to store emails in expired inbox returns UserError" in {
      val (mailbox, address) = testInit()
      mailbox.storeEmail(address, Email("ben"))(fixedClock(20.minute.toMillis)) must
        beLeft(UserError("Email address has expired"))
    }

    "Inboxes are reset" in {
      val (mailbox, address) = testInit()

      mailbox.storeEmail(address, Email("before reset"))(fixedClock(0))

      mailbox.createInbox()(fixedClock(10.minute.toMillis))
      mailbox.createInbox()(fixedClock(10.minute.toMillis))
      mailbox.createInbox()(fixedClock(20.minute.toMillis))

      mailbox.storeEmail(address, Email("after reset"))(fixedClock(0))

      mailbox.getEmails(address)(fixedClock(20.minute.toMillis)).map(_.map(_.emails.map(_.content)).toList) must
        beRight(List(List("after reset")))
    }
  }
}
