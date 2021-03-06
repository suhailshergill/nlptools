package edu.washington.cs.knowitall
package tool
package parse

import java.io.File

import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import scala.collection.JavaConversions.asScalaSet

import org.maltparser.MaltParserService

import edu.washington.cs.knowitall.collection.immutable.Interval
import edu.washington.cs.knowitall.tool.parse.graph.Dependency
import graph.DependencyGraph
import graph.DependencyNode
import postag.OpenNlpPostagger
import stem.MorphaStemmer

/** MaltParser is much faster than the StanfordParser but has a lower F-score.
  * It includes wrapper code so that it can still use the Stanford postprocessing.
  */
object MaltParser extends DependencyParserMain {
  var model = new File("engmalt.linear-1.7")

  override def init(args: Array[String]) {
    val index = args.indexOf("-m")
    if (index >= 0) {
      model = new File(args(index + 1))
    }
  }

  lazy val parser = new MaltParser(model, None);
}

class MaltParser(modelFile: File = new File("engmalt.linear-1.7"), logFile: Option[File] = None) extends DependencyParser {
  val parser = initializeMaltParserService()
  val tagger = new OpenNlpPostagger
  val stemmer = MorphaStemmer.instance
  
  private def initializeMaltParserService() = {
    val dir = Option(modelFile.getParentFile()) map (_.getAbsolutePath)
    val file = modelFile.getName
    val command = 
      "-c " + file + 
      (dir match {
        case Some(dir) => " -w " + dir
        case None => ""
      }) +
      " -m parse" + 
      // turn logging off if no log file is specified
      (logFile match {
        case Some(file) => " -lfi " + file.getPath
        case None => " -cl off"
      })
      
    System.err.println("Initializing malt: " + command);
    val service = new MaltParserService()
    service.initializeParserModel(command);
    
    service
  }

  override def dependencies(sentence: String): Iterable[Dependency] = {
    val tokens = tagger.postag(sentence).iterator.zipWithIndex.map { case (t, i) =>
      new DependencyNode(t, Interval.singleton(i))
    }.toIndexedSeq

    val lemmatized = tokens.map(stemmer.stemToken)
    
    val maltTokens: Array[String] = lemmatized.zipWithIndex.map { case (ltok, i) =>
      Iterable(i+1,
          ltok.token.string,
          ltok.lemma,
          ltok.token.postag,
          ltok.token.postag,
          "-").mkString("\t")
    }(scala.collection.breakOut)
    val structure = parser.parse(maltTokens)
    
    val tables = structure.getSymbolTables

    structure.getEdges.flatMap { edge =>
      if (edge.getSource.getIndex == 0 || edge.getTarget.getIndex == 0) {
        // skip the root
        None
      }
      else {
        val source = tokens(edge.getSource.getIndex - 1)
        val dest = tokens(edge.getTarget.getIndex - 1)

        val types = edge.getLabelTypes
        val labels = types.map(edge.getLabelSymbol)
        val label = labels.head

        Some(new Dependency(source, dest, label))
      }
    }
  }

  override def dependencyGraph(sentence: String): DependencyGraph = {
    val deps = dependencies(sentence)
    val nodes: Set[DependencyNode] = deps.flatMap(dep => Set(dep.source, dep.dest))(scala.collection.breakOut)
    new DependencyGraph(sentence, nodes, deps)
  }
}
