package edu.washington.cs.knowitall
package tool
package parse
package graph

import scala.io.Source
import scopt.OptionParser
import tool.stem.IdentityStemmer

/** An executable that applies a pattern to a sentence. */
object ApplyPattern {
  def main(args: Array[String]) {
    val parser = new OptionParser("applypat") {
      var patternFilePath: String = null
      var sentenceFilePath: String = null
      opt("p", "patterns", "<file>", "pattern file", { v: String => patternFilePath = v })
      opt("s", "sentences", "<file>", "sentence file", { v: String => sentenceFilePath = v })
    }

    if (parser.parse(args)) {
      val patternSource = Source.fromFile(parser.patternFilePath)
      val patterns = patternSource.getLines.map(DependencyPattern.deserialize(_)(IdentityStemmer.instance)).toList
      patternSource.close

      val sentenceSource = Source.fromFile(parser.sentenceFilePath)
      try {
        for (p <- patterns) {
          println("pattern: " + p)
          for (line <- sentenceSource.getLines) {
            val Array(text, deps) = line.split("\t")
            val graph = DependencyGraph.deserialize(deps)
            for (m <- p(graph.graph)) {
              println(m)
            }
          }

          println()
          println()
        }
      } finally {
        sentenceSource.close
      }
    }
  }
}
