package tmp_mailbox

import org.specs2.mutable.Specification
import scala.concurrent.duration._

object TenMinMailTest extends Specification {
  def at(t: Long): Clock = () => t

  val smallPrefixList = EmailPrefixList(numPrefixes = 3)
  val first :: second :: third :: Nil = smallPrefixList.prefixesWithIndex.map(_._1)
  val pageSize = 3

  "TenMinMailbox.createInbox" should {
    "Cycle through the prefixes" in {
      val mail = new TenMinMail(smallPrefixList, 10.minute.toMillis, pageSize)

      mail.createInbox()(at(10.minute.toMillis)).prefix must_=== first
      mail.createInbox()(at(10.minute.toMillis)).prefix must_=== second
      mail.createInbox()(at(10.minute.toMillis)).prefix must_=== third

      mail.createInbox()(at(20.minute.toMillis)).prefix must_=== first
      mail.createInbox()(at(20.minute.toMillis)).prefix must_=== second
      mail.createInbox()(at(20.minute.toMillis)).prefix must_=== third

      mail.createInbox()(at(30.minute.toMillis)).prefix must_=== first
      mail.createInbox()(at(30.minute.toMillis)).prefix must_=== second
      mail.createInbox()(at(30.minute.toMillis)).prefix must_=== third
    }

    "Throw exception when TPS limit reached (prefix list too small)" in {
      new TenMinMail(smallPrefixList, 10.minute.toMillis, 10)
        .createInbox()(at(9.minute.toMillis)) must throwA[IllegalArgumentException]
    }
  }

  def testInit(): (TenMinMail, EmailAddress) = {
    val mail = new TenMinMail(smallPrefixList, 10.minute.toMillis, pageSize)
    (mail, mail.createInbox()(at(10.minute.toMillis)))
  }

  "TenMinMailbox.storeEmail and getEmails" should {
    "Store emails that we can then get (and pages)" in {
      val (mail, address) = testInit()

      mail.storeEmail(address, Email("a"))(at(0)) must beRight
      mail.storeEmail(address, Email("b"))(at(0)) must beRight
      mail.storeEmail(address, Email("c"))(at(0)) must beRight
      mail.storeEmail(address, Email("d"))(at(0)) must beRight

      mail.getEmails(address)(at(0)).map(_.map(_.emails.map(_.content)).toList) must
        beRight(List(List("d", "c", "b"), List("a")))
    }

    "Trying to store emails in invalid inbox returns UserError" in {
      testInit()._1.storeEmail(EmailAddress("a"), Email("a"))(at(0)) must beLeft(UserError("Not exist: a@mail.com"))
    }

    "Trying to store emails in expired inbox returns UserError" in {
      val (mail, address) = testInit()
      mail.storeEmail(address, Email("a"))(at(20.minute.toMillis)) must beLeft(UserError("Email expired"))
    }

    "Inboxes are reset" in {
      val (mail, address) = testInit()

      mail.storeEmail(address, Email("before reset"))(at(0))

      mail.createInbox()(at(10.minute.toMillis))
      mail.createInbox()(at(10.minute.toMillis))
      mail.createInbox()(at(20.minute.toMillis))

      mail.storeEmail(address, Email("after reset"))(at(0))

      mail.getEmails(address)(at(20.minute.toMillis)).map(_.map(_.emails.map(_.content)).toList) must
        beRight(List(List("after reset")))
    }
  }
}
