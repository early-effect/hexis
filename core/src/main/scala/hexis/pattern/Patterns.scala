package hexis.pattern

import hexis.domain.{Answer, Confidence, Probability}

enum Gate[+A]:
  case Act(value: A)
  case Confirm(value: A)
  case Escalate

object Confidence:
  def gate[A](answer: Answer.Choice[A], act: Confidence, confirm: Confidence): Gate[A] =
    val c = answer.confidence.toDouble
    if c >= act.toDouble then Gate.Act(answer.choice)
    else if c >= confirm.toDouble then Gate.Confirm(answer.choice)
    else Gate.Escalate

  def gateScore[A](answer: Answer.Score[A], act: Confidence, confirm: Confidence): Gate[Double] =
    val c = answer.confidence.toDouble
    if c >= act.toDouble then Gate.Act(answer.score)
    else if c >= confirm.toDouble then Gate.Confirm(answer.score)
    else Gate.Escalate
end Confidence

object Noul:
  def band(p: Probability, no: Probability, yes: Probability): Gate[Boolean] =
    if p.toDouble <= no.toDouble then Gate.Act(false)
    else if p.toDouble >= yes.toDouble then Gate.Act(true)
    else Gate.Escalate

object Composite:
  def weighted(parts: (Double, Double)*): Double =
    val (num, den) = parts.foldLeft((0.0, 0.0)) { case ((n, d), (value, weight)) =>
      (n + value * weight, d + weight)
    }
    if den == 0.0 then 0.0 else num / den
