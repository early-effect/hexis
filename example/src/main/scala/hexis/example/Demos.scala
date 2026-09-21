package hexis.example

import hexis.*
import zio.ZIO

object Demos:
  def fanOut(ticket: String): ZIO[SystemOne, JevError, (String, Probability, Double)] =
    val questions = (
      topic = Choice.dynamic(
        "What is this ticket about?",
        Map("billing" -> "money", "technical" -> "bugs", "other" -> "else"),
      ),
      refund = Noul("Does the customer request a refund?"),
      urgency = Score.dynamic("How urgent?", Vector("can wait", "this week", "today")),
    )
    SystemOne.evaluate(ticket, questions).map { a =>
      (a.topic.choice, a.refund.noul, a.urgency.normalized)
    }
  end fanOut

  def composite(ticket: String): ZIO[SystemOne, JevError, Double] =
    val questions = Map(
      "spam"     -> Noul("Looks like spam"),
      "abuse"    -> Noul("Looks abusive"),
      "priority" -> Score.dynamic("Priority", Vector("low", "medium", "high")),
    )
    SystemOne.evaluate(ticket, questions).map { a =>
      val spam  = a.collect { case ("spam", Answer.Noul(p)) => p.toDouble }.headOption.getOrElse(0.0)
      val abuse = a.collect { case ("abuse", Answer.Noul(p)) => p.toDouble }.headOption.getOrElse(0.0)
      val prio  = a.collect { case ("priority", s: Answer.Score[?]) => s.normalized }.headOption.getOrElse(0.0)
      Composite.weighted(spam -> 0.5, abuse -> 0.3, prio -> 0.2)
    }
  end composite

  def route(ticket: String): ZIO[SystemOne, JevError, Gate[String]] =
    val q = Choice.dynamic(
      "Intent",
      Map("balance" -> "check balance", "transfer" -> "send money", "support" -> "help"),
    )
    SystemOne.evaluate(ticket, q).map { c =>
      ConfidenceGate.gate(c, act = Confidence.unsafely(0.9), confirm = Confidence.unsafely(0.5))
    }

  def functionCall(ticket: String): ZIO[SystemOne, JevError, Option[String]] =
    val questions = (
      needsTool = Noul("Does this request need a tool call?"),
      tool = Choice.dynamic(
        "Which function should run?",
        Map(
          "get_balance"   -> "Look up the account balance",
          "create_refund" -> "Issue a refund",
          "open_ticket"   -> "Open a support ticket",
        ),
      ),
    )
    SystemOne.evaluate(ticket, questions).map { a =>
      NoulBand.band(a.needsTool.noul, no = Probability.unsafely(0.3), yes = Probability.unsafely(0.7)) match
        case Gate.Act(true) | Gate.Confirm(true) => Some(a.tool.choice)
        case _                                   => None
    }
  end functionCall
end Demos
