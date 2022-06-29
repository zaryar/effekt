package effekt
package typer

import effekt.context.{ Annotations, Context, ContextOps }
import effekt.symbols.*
import effekt.context.assertions.*
import effekt.source.{ Def, MatchPattern, Tree }
import effekt.source.Tree.Visit
import effekt.typer.typeMapToSubstitution

/**
 * Performs additional wellformed-ness checks that are easier to do on already
 * inferred and annotated trees.
 *
 * TODO this does not need to be a Rewrite phase, but can be a visit.
 * TODO implement existential-effect-leaving-the-scope check
 */
case class PostTyperContext(effectsInScope: List[Interface])
object PostTyper extends Phase[Typechecked, Typechecked], Visit[PostTyperContext] {

  val phaseName = "post-typer"

  def run(input: Typechecked)(implicit C: Context) =
    given PostTyperContext = PostTyperContext(Nil)
    query(input.tree)

    if (Context.buffer.hasErrors) {
      None
    } else {
      Some(input)
    }


  override def expr(using Context, PostTyperContext) = {
    /**
     * For handlers we check that the return type does not mention any bound capabilities
     */
    case tree @ source.TryHandle(prog, handlers) => {
      val bound = Context.annotation(Annotations.BoundCapabilities, tree).map(_.capture).toSet
      val tpe = Context.inferredTypeOf(prog)
      val selfRegion = Context.getSelfRegion(tree)

      val free = freeCapture(tpe)
      val escape = free intersect bound
      if (escape.nonEmpty) {
        Context.at(prog) {
          Context.error(s"The return type ${tpe} of the handled statement is not allowed to refer to any of the bound capabilities, but mentions: ${CaptureSet(escape)}")
        }
      }
      if (free contains selfRegion) {
        Context.at(prog) {
          Context.error(s"The return type ${tpe} of the handler body must not mention the self region.")
        }
      }

      // also check prog and handlers
      query(prog)
      handlers foreach query
    }
  }

  override def defn(using Context, PostTyperContext) = {
    /**
     * For functions we check that the self region does not leave as part of the return type.
     */
    case tree @ source.FunDef(id, tps, vps, bps, ret, body) =>
      val selfRegion = Context.getSelfRegion(tree)
      val returnType = Context.inferredTypeOf(body)

      if (freeCapture(returnType) contains selfRegion) {
        Context.at(body) {
          Context.error(s"The return type ${returnType} of the function body must not mention the region of the function itself.")
        }
      }

      query(body)
  }

  override def query(b: source.FunctionArg)(using Context, PostTyperContext): Unit = visit(b) {
    case tree @ source.FunctionArg(tps, vps, bps, body) =>
      val selfRegion = Context.getSelfRegion(tree)
      val returnType = Context.inferredTypeOf(body)

      if (freeCapture(returnType) contains selfRegion) {
        Context.at(body) {
          Context.error(s"The return type ${returnType} of the function body must not mention the region of the function itself.")
        }
      }

      query(body)
  }

  /**
   * This is a quick and dirty implementation of coverage checking. Both performance, and error reporting
   * can be improved a lot.
   *
   * TODO Maybe move exhaustivity check to a separate phase AFTER typer? This way all types are inferred.
   */
  def checkExhaustivity(sc: ValueType, cls: List[MatchPattern])(using Context, PostTyperContext): Unit = ()
  //  {
  //    val catchall = cls.exists { p => p.isInstanceOf[AnyPattern] || p.isInstanceOf[IgnorePattern] }
  //
  //    if (catchall)
  //      return ;
  //
  //    sc match {
  //      case TypeConstructor(t: DataType) =>
  //        t.variants.foreach { variant =>
  //          checkExhaustivity(variant, cls)
  //        }
  //
  //      case TypeConstructor(t: Record) =>
  //        val (related, unrelated) = cls.collect { case p: TagPattern => p }.partitionMap {
  //          case p if p.definition == t => Left(p.patterns)
  //          case p => Right(p)
  //        }
  //
  //        if (related.isEmpty) {
  //          Context.error(s"Non exhaustive pattern matching, missing case for ${sc}")
  //        }
  //
  //        (t.fields.map { f => f.tpe } zip related.transpose) foreach {
  //          case (t, ps) => checkExhaustivity(t, ps)
  //        }
  //      case other =>
  //        ()
  //    }
  //  }


  // Can only compute free capture on concrete sets
  def freeCapture(o: Any): Set[Capture] = o match {
    case t: symbols.Capture   => Set(t)
    case BoxedType(tpe, capt) => freeCapture(tpe) ++ freeCapture(capt)
    case FunctionType(tparams, cparams, vparams, bparams, ret, eff) =>
      // TODO what with capabilities introduced for eff--those are bound in ret?
      freeCapture(vparams) ++ freeCapture(bparams) ++ freeCapture(ret) ++ freeCapture(eff) -- cparams.toSet
    case CaptureSet(cs) => cs
    case x: CaptUnificationVar => sys error s"Cannot compute free variables for unification variable ${x}"
    case x: UnificationVar => sys error s"Cannot compute free variables for unification variable ${x}"
    case _: Symbol | _: String => Set.empty // don't follow symbols
    case t: Iterable[t] =>
      t.foldLeft(Set.empty[Capture]) { case (r, t) => r ++ freeCapture(t) }
    case p: Product =>
      p.productIterator.foldLeft(Set.empty[Capture]) { case (r, t) => r ++ freeCapture(t) }
    case _ =>
      Set.empty
  }

}