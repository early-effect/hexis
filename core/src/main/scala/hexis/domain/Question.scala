package hexis.domain

import scala.deriving.Mirror
import scala.compiletime.{erasedValue, summonInline}

final case class NoulCriteria(yes: Entry, no: Entry)

final case class Criteria[A](pairs: IArray[(A, Entry)]):
  def size: Int            = pairs.length
  def keys: Seq[A]         = pairs.iterator.map(_._1).toSeq
  def toMap: Map[A, Entry] = pairs.iterator.toMap
  def labels: Seq[String]  = pairs.iterator.map((a, _) => Criteria.label(a)).toSeq

object Criteria:
  def label[A](a: A): String =
    a match
      case p: Product =>
        val simple = p.productPrefix
        if p.productArity == 0 then simple
        else s"$simple(${(0 until p.productArity).map(p.productElement).mkString(",")})"
      case other => other.toString

final case class Levels[A](pairs: IArray[(A, Entry)]):
  def size: Int                  = pairs.length
  def keys: Seq[A]               = pairs.iterator.map(_._1).toSeq
  def toMap: Map[A, Entry]       = pairs.iterator.toMap
  def indexOf(a: A): Option[Int] =
    val i = pairs.indexWhere(_._1 == a)
    if i < 0 then None else Some(i)
  def at(index: Int): Option[A] =
    if index >= 0 && index < pairs.length then Some(pairs(index)._1) else None

enum Question[A]:
  case Noul(instructions: Entry, criteria: Option[NoulCriteria])
  case Choice(instructions: Entry, criteria: Criteria[A])
  case Score(instructions: Entry, levels: Levels[A])

object Question:
  def noul(instructions: Entry | String, criteria: Option[NoulCriteria] = None): Question.Noul[Boolean] =
    Question.Noul(asEntry(instructions), criteria)

  def asEntry(value: Entry | String): Entry =
    value match
      case e: Entry  => e
      case s: String => Entry.Text(s)

trait ChoiceDomain[A]:
  def values: List[A]

object ChoiceDomain:
  inline def derived[A](using m: Mirror.SumOf[A]): ChoiceDomain[A] =
    new ChoiceDomain[A]:
      def values: List[A] = summonCases[m.MirroredElemTypes, A]

trait ScoreDomain[A]:
  def values: List[A]

object ScoreDomain:
  inline def derived[A](using m: Mirror.SumOf[A]): ScoreDomain[A] =
    new ScoreDomain[A]:
      def values: List[A] = summonCases[m.MirroredElemTypes, A]

private inline def summonCases[T <: Tuple, A]: List[A] =
  inline erasedValue[T] match
    case _: EmptyTuple => Nil
    case _: (h *: t)   =>
      summonInline[ValueOf[h]].value.asInstanceOf[A] :: summonCases[t, A]

object Noul:
  def apply(instructions: Entry | String, criteria: Option[NoulCriteria] = None): Question.Noul[Boolean] =
    Question.noul(instructions, criteria)

  def apply(instructions: Entry | String, yes: Entry | String, no: Entry | String): Question.Noul[Boolean] =
    Question.Noul(
      Question.asEntry(instructions),
      Some(NoulCriteria(Question.asEntry(yes), Question.asEntry(no))),
    )

object Choice:
  def apply[A](instructions: Entry | String, pairs: (A, Entry | String)*)(using
      domain: ChoiceDomain[A]
  ): Question.Choice[A] =
    val mapped = IArray.from(pairs.map((a, e) => a -> Question.asEntry(e)))
    requireComplete(domain.values, mapped.iterator.map(_._1).toSeq, "Choice")
    requireRange(mapped.length, 1, 255, "Choice")
    Question.Choice(Question.asEntry(instructions), Criteria(mapped))

  def dynamic(instructions: Entry | String, options: Map[String, Entry | String]): Question.Choice[String] =
    requireRange(options.size, 1, 255, "Choice")
    val mapped = IArray.from(options.iterator.map((k, e) => k -> Question.asEntry(e)).toSeq)
    Question.Choice(Question.asEntry(instructions), Criteria(mapped))
end Choice

object Score:
  def apply[A](instructions: Entry | String, pairs: (A, Entry | String)*)(using
      domain: ScoreDomain[A]
  ): Question.Score[A] =
    val mapped = IArray.from(pairs.map((a, e) => a -> Question.asEntry(e)))
    requireComplete(domain.values, mapped.iterator.map(_._1).toSeq, "Score")
    requireOrder(domain.values, mapped.iterator.map(_._1).toSeq, "Score")
    requireRange(mapped.length, 2, 10, "Score")
    Question.Score(Question.asEntry(instructions), Levels(mapped))

  def dynamic(instructions: Entry | String, levels: Seq[Entry | String]): Question.Score[Int] =
    requireRange(levels.size, 2, 10, "Score")
    val mapped = IArray.from(levels.zipWithIndex.map((e, i) => i -> Question.asEntry(e)))
    Question.Score(Question.asEntry(instructions), Levels(mapped))
end Score

private def requireRange(n: Int, min: Int, max: Int, kind: String): Unit =
  if n < min || n > max then throw IllegalArgumentException(s"$kind requires $min..$max entries, got $n")

private def requireComplete[A](expected: Seq[A], got: Seq[A], kind: String): Unit =
  val missing = expected.toSet -- got.toSet
  val extra   = got.toSet -- expected.toSet
  if missing.nonEmpty || extra.nonEmpty then
    throw IllegalArgumentException(
      s"$kind criteria must cover the domain exactly. missing=$missing extra=$extra"
    )

private def requireOrder[A](expected: Seq[A], got: Seq[A], kind: String): Unit =
  if expected != got then
    throw IllegalArgumentException(s"$kind levels must follow declaration order: ${expected.mkString(", ")}")
