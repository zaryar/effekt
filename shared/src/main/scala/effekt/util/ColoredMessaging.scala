package effekt
package util

import org.bitbucket.inkytonik.kiama.util.{ Message, Messaging, Positions, Severities }

class ColoredMessaging(positions: Positions) extends Messaging(positions) {

  import Severities._

  def severityToWord(severity: Severity): String =
    severity match {
      case Error       => s"${Console.RED}error${Console.RESET}"
      case Warning     => s"${Console.YELLOW}warning${Console.RESET}"
      case Information => s"${Console.WHITE}info${Console.RESET}"
      case Hint        => "hint"
    }

  override def formatMessage(message: Message): String =
    start(message) match {
      case Some(pos) =>
        val severity = severityToWord(message.severity)
        val context = pos.optContext.getOrElse("")
        s"[$severity] ${pos.format} ${message.label}\n$context\n"
      case None =>
        s"${message.label}\n"
    }

}
