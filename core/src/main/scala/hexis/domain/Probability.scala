package hexis.domain

opaque type Probability <: Double = Double

object Probability:
  def apply(value: Double): Either[String, Probability] =
    if value >= 0.0 && value <= 1.0 then Right(value)
    else Left(s"Probability must be in [0, 1], got $value")

  def unsafely(value: Double): Probability = value

  def zero: Probability = 0.0
  def one: Probability  = 1.0

opaque type Confidence <: Double = Double

object Confidence:
  def apply(value: Double): Either[String, Confidence] =
    if value >= 0.0 && value <= 1.0 then Right(value)
    else Left(s"Confidence must be in [0, 1], got $value")

  def unsafely(value: Double): Confidence = value

  def zero: Confidence = 0.0
  def one: Confidence  = 1.0
