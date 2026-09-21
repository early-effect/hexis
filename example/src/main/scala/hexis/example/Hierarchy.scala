package hexis.example

import hexis.*
import zio.{Chunk, ZIO}

final case class Tree(children: Map[String, Tree]):
  def at(path: Chunk[String]): Tree =
    path.foldLeft(this)((t, label) => t.children(label))
  def isLeaf: Boolean = children.isEmpty

final case class PathCandidate(
    path: Chunk[String],
    logProduct: Double,
    decisions: Int,
):
  def score: Double =
    if decisions == 0 then 1.0 else math.exp(logProduct / decisions)

object Hierarchy:
  val fixture: Tree = Tree(
    Map(
      "drinkware" -> Tree(
        Map(
          "mugs"     -> Tree(Map.empty),
          "tumblers" -> Tree(Map.empty),
        )
      ),
      "sporting" -> Tree(
        Map(
          "bats"  -> Tree(Map.empty),
          "balls" -> Tree(Map.empty),
        )
      ),
    )
  )

  def greedy(root: Tree, state: String, maxDepth: Int = 8): ZIO[SystemOne, JevError, Chunk[String]] =
    def loop(node: Tree, path: Chunk[String], depth: Int): ZIO[SystemOne, JevError, Chunk[String]] =
      if node.isLeaf || depth >= maxDepth then ZIO.succeed(path)
      else
        choose(state, node.children.keys.toSeq).flatMap { probs =>
          val next = probs.maxBy(_._2.toDouble)._1
          loop(node.children(next), path :+ next, depth + 1)
        }
    loop(root, Chunk.empty, 0)

  def beam(
      root: Tree,
      state: String,
      width: Int = 3,
      maxDepth: Int = 8,
  ): ZIO[SystemOne, JevError, Chunk[PathCandidate]] =
    def step(beam: Chunk[PathCandidate], depth: Int): ZIO[SystemOne, JevError, Chunk[PathCandidate]] =
      if depth >= maxDepth then ZIO.succeed(beam)
      else
        val expandable = beam.filter(c => !root.at(c.path).isLeaf)
        val finished   = beam.filter(c => root.at(c.path).isLeaf)
        if expandable.isEmpty then ZIO.succeed(beam)
        else
          ZIO
            .foreachPar(expandable) { cand =>
              val node = root.at(cand.path)
              choose(state, node.children.keys.toSeq).map { probs =>
                Chunk.fromIterable(probs.map { (label, p) =>
                  val decision = if probs.size > 1 then 1 else 0
                  PathCandidate(
                    cand.path :+ label,
                    cand.logProduct + (if decision == 1 then math.log(math.max(p.toDouble, 1e-9)) else 0.0),
                    cand.decisions + decision,
                  )
                })
              }
            }
            .map { expanded =>
              (finished ++ expanded.flatten).sortBy(c => -c.score).take(width)
            }
            .flatMap(step(_, depth + 1))
        end if
    step(Chunk(PathCandidate(Chunk.empty, 0.0, 0)), 0)
  end beam

  private def choose(state: String, labels: Seq[String]): ZIO[SystemOne, JevError, Map[String, Probability]] =
    if labels.size == 1 then ZIO.succeed(Map(labels.head -> Probability.one))
    else
      val q = Choice.dynamic(
        "Which direct child category best matches this document?",
        labels.map(l => l -> Entry.Empty).toMap,
      )
      SystemOne.evaluate(state, q).map(_.probabilities)
end Hierarchy
