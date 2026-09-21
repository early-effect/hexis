package hexis.domain

enum Answer[A]:
  case Noul(noul: Probability)
  case Choice(choice: A, probabilities: Map[A, Probability], confidence: Confidence)
  case Score(
      score: Double,
      probabilities: Map[A, Probability],
      legend: Map[A, Entry],
      confidence: Confidence,
      levels: Seq[A],
  )
end Answer

object Answer:
  extension [A](s: Answer.Score[A])
    def nearest: A =
      val idx = s.score.round.toInt.max(0).min(s.levels.length - 1)
      s.levels(idx)

    def normalized: Double =
      val top = (s.levels.length - 1).max(1)
      s.score / top.toDouble
end Answer
