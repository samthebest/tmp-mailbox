package tmp_mailbox

import scala.util.Random

case class Email(content: String)
case class UserError(msg: String)
case class Page(emails: List[Email])
case class EmailAddress(prefix: String) {
  val address: String = s"$prefix@mail.com"
}

trait Clock {
  def now(): Long
}

case class EmailPrefixList(prefixLength: Int = 10, numPrefixes: Int = 1000000) {
  // TODO Load memorable strings from a pre-gen static repository and deal with variable length prefixes
  val concatenated: String = Random.alphanumeric.take(prefixLength * numPrefixes).mkString
  val prefixesWithIndex: List[(String, Int)] = concatenated.grouped(prefixLength).zipWithIndex.toList
  val inverseLookup: Map[String, Int] = prefixesWithIndex.toMap.view.mapValues(_ / prefixLength).toMap
}

class Inbox(expiration: Long) {
  private var createdAt: Long = 0
  private var emails: List[Email] = Nil

  def isExpired(now: Long): Boolean = now - createdAt >= expiration

  def reset(time: Long)(implicit clock: Clock): Unit = this.synchronized {
    require(isExpired(clock.now()), "Trying to reset a live inbox. Maybe need a bigger prefix list")
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
    if (isExpired(clock.now())) Left(UserError("Email expired")) else Right(f())
}

class TenMinMail(emailPrefixList: EmailPrefixList, expiration: Long, pageSize: Int) {
  import emailPrefixList._
  private val inboxes: Array[Inbox] = Array.fill(numPrefixes)(new Inbox(expiration))
  private var offset: Int = 0

  def createInbox()(implicit clock: Clock): EmailAddress = this.synchronized {
    val next = offset + 1
    inboxes(offset).reset(clock.now())
    offset = if (next > numPrefixes - 1) 0 else next
    EmailAddress(concatenated.substring((next - 1) * prefixLength, next * prefixLength))
  }

  def storeEmail(address: EmailAddress, email: Email)(implicit clock: Clock): Either[UserError, Unit] =
    getInbox(address).flatMap(_.add(email))

  def getEmails(address: EmailAddress)(implicit clock: Clock): Either[UserError, Iterator[Page]] =
    getInbox(address).flatMap(_.getEmails()).map(_.grouped(pageSize).map(Page))

  def getInbox(address: EmailAddress): Either[UserError, Inbox] =
    inverseLookup.get(address.prefix).toRight(UserError(s"Not exist: ${address.address}")).map(inboxes(_))
}
