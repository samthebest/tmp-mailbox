package tmp_mailbox

import scala.util.Random

case class Email(content: String)
case class UserError(msg: String)
case class Page(emails: List[Email])
case class EmailAddress(prefix: String) {
  val address: String = s"$prefix@ten-min-mailbox.com"
}

case class EmailPrefixList(prefixLength: Int = 10, numPrefixes: Int = 1000000) {
  // TODO Load memorable strings from a static repository and deal with variable length prefixes
  val concatenated: String = Random.alphanumeric.dropWhile(_.isDigit).take(prefixLength * numPrefixes).mkString
  val last: Int = numPrefixes - 1
  val prefixesWithIndex: List[(String, Int)] = concatenated.grouped(prefixLength).zipWithIndex.toList
  val inverseLookup: Map[String, Int] = prefixesWithIndex.toMap.view.mapValues(_ / prefixLength).toMap

  def get(nth: Int): String = concatenated.slice(nth * prefixLength, (nth + 1) * prefixLength)

  def nextN(nth: Int): Int = {
    val next = nth + 1
    if (next > last) 0 else next
  }
}

class Inbox(expiration: Long) {
  private var createdAt: Long = 0
  private var emails: List[Email] = Nil

  def isExpired(now: Long): Boolean = now - createdAt >= expiration

  def reset(time: Long): Unit = this.synchronized {
    createdAt = time
    emails = Nil
  }

  def add(email: Email)(implicit clock: Clock): Either[UserError, Unit] =
    this.synchronized(whenNotExpired(() => emails = email +: emails))

  def getEmails()(implicit clock: Clock): Either[UserError, List[Email]] = {
    val current = emails
    whenNotExpired(() => current)
  }

  def whenNotExpired[T](f: () => T)(implicit clock: Clock): Either[UserError, T] =
    if (isExpired(clock.now())) Left(UserError("Email address has expired")) else Right(f())
}

class TenMinMailbox(emailPrefixList: EmailPrefixList, expiration: Long, pageSize: Int) {
  private val inboxes: Array[Inbox] = Array.fill(emailPrefixList.numPrefixes)(new Inbox(expiration))
  private var inboxOffset: Int = 0

  def createInbox()(implicit clock: Clock): EmailAddress = this.synchronized {
    val now = clock.now()
    val address = EmailAddress(emailPrefixList.get(inboxOffset))
    val inbox = inboxes(inboxOffset)

    require(inbox.isExpired(now), "TPS limit reached, should be impossible. Need a bigger prefix list")

    inboxOffset = emailPrefixList.nextN(inboxOffset)
    inbox.reset(now)

    address
  }

  def storeEmail(address: EmailAddress, email: Email)(implicit clock: Clock): Either[UserError, Unit] =
    getInbox(address).flatMap(_.add(email))

  def getEmails(address: EmailAddress)(implicit clock: Clock): Either[UserError, Iterator[Page]] =
    getInbox(address).flatMap(_.getEmails()).map(_.grouped(pageSize).map(Page))

  def getInbox(address: EmailAddress): Either[UserError, Inbox] =
    emailPrefixList.inverseLookup.get(address.prefix)
      .toRight(UserError(s"Email address not exist: ${address.address}"))
      .map(inboxes(_))
}

trait Clock {
  def now(): Long
}

object DefaultClock extends Clock {
  def now(): Long = System.currentTimeMillis()
}
