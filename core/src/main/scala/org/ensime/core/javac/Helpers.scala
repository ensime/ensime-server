// Copyright: 2010 - 2016 https://github.com/ensime/ensime-server/graphs
// License: http://www.gnu.org/licenses/gpl-3.0.en.html
package org.ensime.core.javac

import akka.event.slf4j.SLF4JLogging
import com.sun.source.tree._
import com.sun.source.util.TreePath
import javax.lang.model.`type`._
import javax.lang.model.element._
import javax.lang.model.element.ElementKind._
import scala.collection.JavaConversions._

import org.ensime.core.{ DocFqn, DocSig }
import org.ensime.indexer._

trait Helpers extends UnsafeHelpers with SLF4JLogging {

  private implicit class EnhachedElement(e: Element) {
    def isOf(kinds: ElementKind*): Boolean = kinds.exists(_ == e.getKind)
  }

  def typeMirror(c: Compilation, t: Tree): Option[TypeMirror] = {
    Option(c.trees.getTypeMirror(c.trees.getPath(c.compilationUnit, t)))
  }

  def typeElement(c: Compilation, t: Tree): Option[Element] = {
    typeMirror(c, t).map(c.types.asElement)
  }

  def element(c: Compilation, path: TreePath): Option[Element] = {
    Option(c.trees.getElement(path))
      .orElse(unsafeGetElement(path.getLeaf))
      .orElse(Option(c.trees.getTypeMirror(path))
        .flatMap(t => Option(c.types.asElement(t))))
  }

  def toSymbolName(fqn: FullyQualifiedName): String = fqn match {
    case m: MethodName =>

      val owner = m.owner.fqnString
      val name = m.name

      s"$owner.$name"

    case x => x.fqnString
  }

  def fqn(c: Compilation, el: Element): Option[FullyQualifiedName] = el match {
    case e: ExecutableElement =>

      descriptor(c, e).map { descriptor =>

        val name = e.getSimpleName.toString
        val params = descriptor.params
          .map(_.fqnString).mkString(",")

        MethodName(
          ClassName.fromFqn(e.getEnclosingElement.toString),
          s"$name($params)", descriptor
        )
      }

    case e: VariableElement if e.isOf(PARAMETER, LOCAL_VARIABLE) =>

      Some(ClassName(PackageName(Nil), e.toString))

    case e: VariableElement if e.isOf(FIELD) =>

      Some(FieldName(
        ClassName.fromFqn(
          e.getEnclosingElement.toString
        ),
        e.getSimpleName.toString
      ))

    case e: VariableElement if e.isOf(ENUM_CONSTANT) =>

      fqn(c, e.asType()).map {
        FieldName(_, e.getSimpleName.toString)
      }

    case e => fqn(c, e.asType())
  }

  private def descriptor(c: Compilation, e: ExecutableElement): Option[Descriptor] = {
    import scala.collection.breakOut
    fqn(c, e.getReturnType).map { returnType =>
      val params: List[DescriptorType] = e.getParameters.flatMap(p => fqn(c, p.asType()))(breakOut)
      Descriptor(params, returnType)
    }
  }

  def path(c: Compilation, t: Tree): Option[TreePath] = {
    Option(c.trees.getPath(c.compilationUnit, t))
  }

  def fqn(c: Compilation, t: Tree): Option[FullyQualifiedName] = {
    path(c, t).flatMap(fqn(c, _))
  }

  def fqn(c: Compilation, p: TreePath): Option[FullyQualifiedName] = {
    element(c, p).flatMap(fqn(c, _))
  }

  def fqn(c: Compilation, tm: TypeMirror): Option[ClassName] = {
    // "Using instanceof is not necessarily a reliable idiom for
    // determining the effective class of an object in this modeling
    // hierarchy since an implementation may choose to have a single
    // object implement multiple TypeMirror subinterfaces." --
    // TypeMirror docs
    // tm match {
    //   case tm: DeclaredType if tm.getKind == TypeKind.DECLARED => {
    //     tm.asElement match {
    //       case te: TypeElement => Some(ClassName.fromFqn(te.getQualifiedName.toString))
    //       case _ => {
    //         None
    //       }
    //     }
    //   }
    //   case tm: PrimitiveType if tm.getKind.isPrimitive => Some(ClassName(PackageName(Nil), tm.toString))
    //   case _ => None
    // }
    Some(ClassName.fromFqn(tm.toString))
  }

  def toDocSign(fqn: FullyQualifiedName): DocSig = fqn match {
    case p: PackageName => DocSig(DocFqn(p.parent.fqnString, p.path.last), None)
    case c: ClassName => DocSig(DocFqn(c.pack.fqnString, c.name), None)
    case m: MethodName => DocSig(DocFqn(m.owner.pack.fqnString, m.owner.name), Some(m.name))
    case f: FieldName => DocSig(DocFqn(f.owner.fqnString, f.name), Some(f.name))
  }
}
