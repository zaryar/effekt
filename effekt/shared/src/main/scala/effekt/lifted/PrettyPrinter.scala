package effekt
package lifted

import kiama.output.ParenPrettyPrinter

import scala.language.implicitConversions
import effekt.symbols.{ Name, Wildcard, builtins }

object PrettyPrinter extends ParenPrettyPrinter {

  import kiama.output.PrettyPrinterTypes.Document

  override val defaultIndent = 2

  def format(t: ModuleDecl): Document =
    pretty(toDoc(t), 4)

  def format(s: Stmt): String =
    pretty(toDoc(s), 60).layout

  def format(defs: List[Definition]): String =
    pretty(toDoc(defs), 60).layout

  val emptyline: Doc = line <> line

  def toDoc(m: ModuleDecl): Doc = {
    "module" <+> m.path <> emptyline <> vsep(m.imports.map { im => "import" <+> im }, line) <>
      emptyline <> vcat(m.decls.map(toDoc)) <>
      emptyline <> toDoc(m.definitions)
  }

  def signatures(m: ModuleDecl): Doc = {
    val defs = m.definitions.map {
      case Definition.Def(name, block) => "def" <+> toDoc(name) <> ":" <+> toDoc(block.tpe)
      case Definition.Let(name, expr) => "let" <+> toDoc(name) <> ":" <+> toDoc(expr.tpe)
    }
    "// Signatures" <@> vcat(defs)
  }

  def toDoc(p: Param): Doc = p match {
    case Param.ValueParam(id, tpe) => id.name.toString <> ":" <+> toDoc(tpe)
    case Param.BlockParam(id, tpe) => id.name.toString <> ":" <+> toDoc(tpe)
    case Param.EvidenceParam(id) => id.name.toString <> ": EV"
  }

  def toDoc(e: Extern): Doc = e match {
    case Extern.Def(id, tparams, params, ret, body) =>
      "extern def" <+> toDoc(id.name) <+> "=" <+> parens(hsep(params.map(toDoc), comma)) <+> "=" <+> "\"" <> body <> "\""
    case Extern.Include(contents) => emptyDoc // right now, do not print includes.
  }

  def toDoc(b: Block): Doc = b match {
    case BlockVar(v, _) => v.name.toString
    case BlockLit(tps, ps, body) =>
      braces { space <> parens(hsep(ps.map(toDoc), comma)) <+> "=>" <+> nest(line <> toDoc(body)) <> line }
    case Member(b, id, tpe) =>
      toDoc(b) <> "." <> id.name.toString
    case Unbox(e)         => parens("unbox" <+> toDoc(e))
    case New(handler)     => "new" <+> toDoc(handler)
  }

  def toDoc(n: Name): Doc = n.toString

  def toDoc(s: symbols.Symbol): Doc = toDoc(s.name)

  def toDoc(e: Expr): Doc = e match {
    case Literal((), _)          => "()"
    case Literal(s: String, _)   => "\"" + s + "\""
    case l: Literal              => l.value.toString
    case ValueVar(id, _)         => id.name.toString

    case PureApp(b, targs, args) => toDoc(b) <> parens(hsep(args map argToDoc, comma))

    case Select(b, field, tpe) =>
      toDoc(b) <> "." <> toDoc(field.name)

    case Box(b) => parens("box" <+> toDoc(b))
    case Run(s)  => "run" <+> block(toDoc(s))
  }

  def argToDoc(e: Argument): Doc = e match {
    case e: Expr     => toDoc(e)
    case b: Block    => toDoc(b)
    case e: Evidence => toDoc(e)
  }

  def toDoc(e: Evidence): Doc = e match {
    case Evidence(Nil)  => "<>"
    case Evidence(list) => angles(hsep(list.map { ev => toDoc(ev.name) }, ","))
  }

  def toDoc(impl: Implementation): Doc = {
    val handlerName = toDoc(impl.interface.name.name)
    val clauses = impl.operations.map {
      case Operation(id, BlockLit(tparams, params, body)) =>
        "def" <+> toDoc(id.name) <> parens(params.map(toDoc)) <+> "=" <+> nested(toDoc(body))
    }
    handlerName <+> block(vsep(clauses))
  }

  def toDoc(d: Declaration): Doc = d match {
    case Declaration.Data(did, tparams, ctors) =>
      val tps = if tparams.isEmpty then emptyDoc else brackets(tparams.map(toDoc))
      "type" <+> toDoc(did.name) <> tps <+> braces(ctors.map(toDoc))

    case Declaration.Interface(id, tparams, operations) =>
      val tps = if tparams.isEmpty then emptyDoc else brackets(tparams.map(toDoc))
      "interface" <+> toDoc(id.name) <> tps <+> braces(operations.map(toDoc))
  }

  def toDoc(c: Constructor): Doc = c match {
    case Constructor(id, fields) => toDoc(id) <> parens(fields.map(toDoc))
  }
  def toDoc(f: Field): Doc = f match {
    case Field(name, tpe) => toDoc(name) <> ":" <+> toDoc(tpe)
  }
  def toDoc(f: Property): Doc = f match {
    case Property(name, tpe) => toDoc(name) <> ":" <+> toDoc(tpe)
  }

  def toDoc(d: Definition): Doc = d match {
    case Definition.Def(id, BlockLit(tparams, params, body)) =>
      "def" <+> toDoc(id.name) <> parens(params.map(toDoc)) <> ":" <+> toDoc(body.tpe) <+> "=" <> nested(toDoc(body))
    case Definition.Def(id, block) =>
      "def" <+> toDoc(id.name) <+> "=" <+> toDoc(block)
    case Definition.Let(id, binding) =>
      "let" <+> toDoc(id.name) <+> "=" <+> toDoc(binding)
  }

  def toDoc(definitions: List[Definition]): Doc =
    vsep(definitions map toDoc, semi)

  def toDoc(s: Stmt): Doc = s match {
    case Scope(definitions, rest) =>
      toDoc(definitions) <> emptyline <> toDoc(rest)

    case Val(Wildcard(), binding, body) =>
      toDoc(binding) <> ";" <> line <>
        toDoc(body)

    case Val(id, binding, body) =>
      "val" <+> toDoc(id.name) <+> "=" <+> toDoc(binding) <> ";" <> line <>
        toDoc(body)

    case App(b, targs, args) =>
      toDoc(b) <> parens(hsep(args map argToDoc, comma))

    case If(cond, thn, els) =>
      "if" <+> parens(toDoc(cond)) <+> block(toDoc(thn)) <+> "else" <+> block(toDoc(els))

    case Return(e) =>
      toDoc(e)

    case Try(body, hs) =>
      "try" <+> toDoc(body) <+> "with" <+> hsep(hs.map(toDoc), " with")

    case State(id, init, region, body) =>
      "var" <+> toDoc(id.name) <+> "in" <+> toDoc(region.name) <+> "=" <+> toDoc(init) <+> ";" <> line <> toDoc(body)

    case Region(body) =>
      "region" <+> toDoc(body)

    case Match(sc, clauses, default) =>
      val cs = braces(nest(line <> vsep(clauses map { case (p, b) => "case" <+> toDoc(p.name) <+> toDoc(b) })) <> line)
      val d = default.map { body => space <> "else" <+> braces(nest(line <> toDoc(body))) }.getOrElse { emptyDoc }
      toDoc(sc) <+> "match" <+> cs <> d

    case Hole() =>
      "<>"
  }


  def toDoc(tpe: lifted.BlockType): Doc = tpe match {
    case lifted.BlockType.Function(tparams, eparams, vparams, bparams, result) =>
      val tps = if tparams.isEmpty then emptyDoc else brackets(tparams.map(toDoc))
      val eps = eparams.map { _ => string("EV") }
      val vps = vparams.map(toDoc)
      val bps = bparams.map(toDoc)
      val res = toDoc(result)
      tps <> parens(eps ++ vps ++ bps) <+> "=>" <+> res
    case lifted.BlockType.Interface(symbol, Nil) => toDoc(symbol)
    case lifted.BlockType.Interface(symbol, targs) => toDoc(symbol) <> brackets(targs.map(toDoc))
  }

  def toDoc(tpe: lifted.ValueType): Doc = tpe match {
    case lifted.ValueType.Var(name) => toDoc(name)
    case lifted.ValueType.Data(symbol, targs) => toDoc(symbol, targs)
    case lifted.ValueType.Boxed(tpe) => "box" <+> toDoc(tpe)
  }

  def toDoc(tpeConstructor: symbols.Symbol, targs: List[lifted.ValueType]): Doc =
    if (targs.isEmpty) then toDoc(tpeConstructor)
    else toDoc(tpeConstructor) <> brackets(targs.map(toDoc))

  def nested(content: Doc): Doc = group(nest(line <> content))

  def parens(docs: List[Doc]): Doc = parens(hsep(docs, comma))

  def braces(docs: List[Doc]): Doc = braces(hsep(docs, semi))

  def brackets(docs: List[Doc]): Doc = brackets(hsep(docs, comma))

  def block(content: Doc): Doc = braces(nest(line <> content) <> line)

  def block(docs: List[Doc]): Doc = block(vsep(docs, line))

}
