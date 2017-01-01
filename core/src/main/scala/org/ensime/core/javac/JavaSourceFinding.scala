// Copyright: 2010 - 2017 https://github.com/ensime/ensime-server/graphs
// License: http://www.gnu.org/licenses/gpl-3.0.en.html
package org.ensime.core.javac

import akka.event.slf4j.SLF4JLogging
import com.sun.source.util.TreePath
import javax.lang.model.element.Element

import org.ensime.api._
import org.ensime.util.ensimefile._
import org.ensime.model.LineSourcePositionHelper
import org.ensime.vfs._
import org.ensime.indexer.SearchService
import org.ensime.indexer.FullyQualifiedName

trait JavaSourceFinding extends Helpers with SLF4JLogging {

  def askLinkPos(fqn: FullyQualifiedName, file: SourceFileInfo): Option[SourcePosition]
  def search: SearchService
  def vfs: EnsimeVFS
  def config: EnsimeConfig

  protected def findInCompiledUnit(c: Compilation, fqn: FullyQualifiedName): Option[SourcePosition] = {
    Option(c.elements.getTypeElement(fqn.fqnString)).flatMap(elementPosition(c, _))
  }

  private def elementPosition(c: Compilation, el: Element): Option[SourcePosition] = {
    // if we can get a tree for the element, determining start position
    // is easy
    Option(c.trees.getPath(el)).map { path =>
      OffsetSourcePosition(
        EnsimeFile(path.getCompilationUnit.getSourceFile.getName),
        c.trees.getSourcePositions
          .getStartPosition(path.getCompilationUnit, path.getLeaf).toInt
      )
    }
  }

  protected def findDeclPos(c: Compilation, path: TreePath): Option[SourcePosition] = {
    element(c, path).flatMap(elementPosition(c, _)).orElse(findInIndexer(c, path))
  }

  private def findInIndexer(c: Compilation, path: TreePath): Option[SourcePosition] = {
    val javaFqn = fqn(c, path)
    val query = javaFqn.map(_.fqnString).getOrElse("")
    val hit = search.findUnique(query)
    if (log.isTraceEnabled())
      log.trace(s"search: '$query' = $hit")
    hit.flatMap(LineSourcePositionHelper.fromFqnSymbol(_)(config, vfs)).flatMap { sourcePos =>
      if (sourcePos.file.isJava && sourcePos.file.exists())
        javaFqn.flatMap(askLinkPos(_, SourceFileInfo(sourcePos.file, None, None))).orElse(Some(sourcePos))
      else
        Some(sourcePos)
    }
  }
}
