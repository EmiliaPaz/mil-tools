/*
    Copyright 2018 Mark P Jones, Portland State University

    This file is part of mil-tools.

    mil-tools is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    mil-tools is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with mil-tools.  If not, see <https://www.gnu.org/licenses/>.
*/
package mil;

import compiler.*;
import compiler.Failure;
import compiler.Position;
import core.*;
import java.io.PrintWriter;

public class PrimCall extends Call {

  private Prim p;

  /** Default constructor. */
  public PrimCall(Prim p) {
    this.p = p;
  }

  /** Test to determine whether a given tail expression has no externally visible side effect. */
  public boolean hasNoEffect() {
    return p.hasNoEffect();
  }

  /** Test to see if two Tail expressions are the same. */
  public boolean sameTail(Tail that) {
    return that.samePrimCall(this);
  }

  boolean samePrimCall(PrimCall that) {
    return this.p == that.p && this.sameArgs(that);
  }

  /** Display a printable representation of this MIL construct on the specified PrintWriter. */
  public void dump(PrintWriter out) {
    dump(out, p.getId(), "((", args, "))");
  }

  /** Construct a new Call value that is based on the receiver, without copying the arguments. */
  Call callDup(Atom[] args) {
    return p.withArgs(args);
  }

  /** Represents a tail expression that halts/terminates the current program. */
  public static final Call halt = Prim.halt.withArgs();

  private BlockType type;

  /** Calculate the list of unbound type variables that are referenced in this MIL code fragment. */
  TVars tvars(TVars tvs) {
    return type.tvars(tvs);
  }

  /** Return the type tuple describing the result that is produced by executing this Tail. */
  public Type resultType() {
    return type.rngType();
  }

  Type inferCallType(Position pos, Type[] inputs) throws Failure {
    type = p.instantiate();
    return type.apply(pos, inputs);
  }

  void invokeCall(MachineBuilder builder, int o) {
    builder.prim(o, p.getIndex());
  }

  /**
   * Determine whether a pair of given Call values are of the same "form", meaning that they are of
   * the same type with the same target (e.g., two block calls to the same block are considered as
   * having the same form, but a block call and a data alloc do not have the same form, and neither
   * do two block calls to distinct blocks. As a special case, two Returns are considered to be of
   * the same form only if they have the same arguments.
   */
  boolean sameCallForm(Call c) {
    return c.samePrimCallForm(this);
  }

  boolean samePrimCallForm(PrimCall that) {
    return that.p == this.p;
  }

  /** Test for code that is guaranteed not to return. */
  boolean doesntReturn() {
    return p.doesntReturn();
  }

  public static final Call loop = Prim.loop.withArgs();

  /**
   * Skip goto blocks in a Tail (for a ClosureDefn or TopLevel). TODO: can this be simplified now
   * that ClosureDefns hold Tails rather than Calls?
   */
  public Tail inlineTail() {
    // !System.out.println("PrimCall was: "); this.dump(); System.out.println();
    Code c = this.rewritePrimCall(null);
    // !System.out.println("Resulting code is:");
    // !if (c==null) {
    // !  System.out.println("  --null--");
    // !} else {
    // !  this.dump();
    // !  System.out.println();
    // !}
    // !
    return (c == null) ? this : c.forceToTail(this);
  }

  /**
   * Test to determine whether a given tail expression may be repeatable (i.e., whether the results
   * of a previous use of the same tail can be reused instead of repeating the tail). TODO: is there
   * a better name for this?
   */
  public boolean isRepeatable() {
    return p.isRepeatable();
  }

  /**
   * Test to determine whether a given tail expression is pure (no externally visible side effects
   * and no dependence on other effects).
   */
  public boolean isPure() {
    return p.isPure();
  }

  Atom isBnot() {
    return p == Prim.bnot ? args[0] : null;
  }

  public Code rewrite(Facts facts) {
    return this.rewritePrimCall(facts);
  }

  Code rewritePrimCall(Facts facts) {

    if (p == Prim.bnot) {
      Atom x = args[0];
      FlagConst a = x.isFlagConst();
      return (a == null) ? bnotVar(x, facts) : Prim.bnot.foldUnary(a.getVal());
    }

    if (p == Prim.not) {
      Atom x = args[0];
      IntConst a = x.isIntConst();
      return (a == null) ? notVar(x, facts) : Prim.not.foldUnary(a.getVal());
    }

    if (p == Prim.neg) {
      Atom x = args[0];
      IntConst a = x.isIntConst();
      return (a == null) ? negVar(x, facts) : Prim.neg.foldUnary(a.getVal());
    }

    if (p == Prim.flagToWord) {
      Atom x = args[0];
      FlagConst a = x.isFlagConst();
      return (a == null) ? flagToWordVar(x, facts) : Prim.flagToWord.foldUnary(a.getVal());
    }

    if (p == Prim.add) {
      Atom x = args[0], y = args[1];
      IntConst a = x.isIntConst(), b = y.isIntConst();
      if (a == null) {
        return (b == null) ? addVarVar(x, y, facts) : addVarConst(x, b.getVal(), facts);
      } else if (b == null) {
        Code nc = addVarConst(y, a.getVal(), facts);
        return (nc != null) ? nc : done(p, y, x);
      } else {
        return Prim.add.foldBinary(a.getVal(), b.getVal());
      }
    }

    if (p == Prim.mul) {
      Atom x = args[0], y = args[1];
      IntConst a = x.isIntConst(), b = y.isIntConst();
      if (a == null) {
        return (b == null) ? mulVarVar(x, y, facts) : mulVarConst(x, b.getVal(), facts);
      } else if (b == null) {
        Code nc = mulVarConst(y, a.getVal(), facts);
        return (nc != null) ? nc : done(p, y, x);
      } else {
        return Prim.mul.foldBinary(a.getVal(), b.getVal());
      }
    }

    if (p == Prim.or) {
      Atom x = args[0], y = args[1];
      IntConst a = x.isIntConst(), b = y.isIntConst();
      if (a == null) {
        return (b == null) ? orVarVar(x, y, facts) : orVarConst(x, b.getVal(), facts);
      } else if (b == null) {
        Code nc = orVarConst(y, a.getVal(), facts);
        return (nc != null) ? nc : done(p, y, x);
      } else {
        return Prim.or.foldBinary(a.getVal(), b.getVal());
      }
    }

    if (p == Prim.and) {
      Atom x = args[0], y = args[1];
      IntConst a = x.isIntConst(), b = y.isIntConst();
      if (a == null) {
        return (b == null) ? andVarVar(x, y, facts) : andVarConst(x, b.getVal(), facts);
      } else if (b == null) {
        Code nc = andVarConst(y, a.getVal(), facts);
        return (nc != null) ? nc : done(p, y, x);
      } else {
        return Prim.and.foldBinary(a.getVal(), b.getVal());
      }
    }

    if (p == Prim.xor) {
      Atom x = args[0], y = args[1];
      IntConst a = x.isIntConst(), b = y.isIntConst();
      if (a == null) {
        return (b == null) ? xorVarVar(x, y, facts) : xorVarConst(x, b.getVal(), facts);
      } else if (b == null) {
        Code nc = xorVarConst(y, a.getVal(), facts);
        return (nc != null) ? nc : done(p, y, x);
      } else {
        return Prim.xor.foldBinary(a.getVal(), b.getVal());
      }
    }

    if (p == Prim.sub) {
      Atom x = args[0];
      Atom y = args[1];
      IntConst a = x.isIntConst();
      IntConst b = y.isIntConst();
      return (a == null)
          ? ((b == null) ? subVarVar(x, y, facts) : subVarConst(x, b.getVal(), facts))
          : ((b == null)
              ? subConstVar(a.getVal(), y, facts)
              : Prim.sub.foldBinary(a.getVal(), b.getVal()));
    }

    if (p == Prim.shl) {
      Atom x = args[0];
      Atom y = args[1];
      IntConst a = x.isIntConst();
      IntConst b = y.isIntConst();
      return (a == null)
          ? ((b == null) ? shlVarVar(x, y, facts) : shlVarConst(x, b.getVal(), facts))
          : ((b == null)
              ? shlConstVar(a.getVal(), y, facts)
              : Prim.shl.foldBinary(a.getVal(), b.getVal()));
    }

    if (p == Prim.lshr) {
      Atom x = args[0];
      Atom y = args[1];
      IntConst a = x.isIntConst();
      IntConst b = y.isIntConst();
      return (a == null)
          ? ((b == null) ? lshrVarVar(x, y, facts) : lshrVarConst(x, b.getVal(), facts))
          : ((b == null)
              ? lshrConstVar(a.getVal(), y, facts)
              : Prim.lshr.foldBinary(a.getVal(), b.getVal()));
    }

    if (p == Prim.ashr) {
      Atom x = args[0];
      Atom y = args[1];
      IntConst a = x.isIntConst();
      IntConst b = y.isIntConst();
      return (a == null)
          ? ((b == null) ? ashrVarVar(x, y, facts) : ashrVarConst(x, b.getVal(), facts))
          : ((b == null)
              ? ashrConstVar(a.getVal(), y, facts)
              : Prim.ashr.foldBinary(a.getVal(), b.getVal()));
    }

    if (p == Prim.eq) {
      Atom x = args[0];
      Atom y = args[1];
      IntConst a = x.isIntConst();
      if (a != null) {
        IntConst b = y.isIntConst();
        if (b != null) {
          return Prim.eq.foldRel(a.getVal(), b.getVal());
        }
      }
      return null;
    }

    if (p == Prim.neq) {
      Atom x = args[0];
      Atom y = args[1];
      IntConst a = x.isIntConst();
      if (a != null) {
        IntConst b = y.isIntConst();
        if (b != null) {
          return Prim.neq.foldRel(a.getVal(), b.getVal());
        }
      }
      return null;
    }

    if (p == Prim.lt) {
      Atom x = args[0];
      Atom y = args[1];
      IntConst a = x.isIntConst();
      if (a != null) {
        IntConst b = y.isIntConst();
        if (b != null) {
          return Prim.lt.foldRel(a.getVal(), b.getVal());
        }
      }
      return null;
    }

    if (p == Prim.gt) {
      Atom x = args[0];
      Atom y = args[1];
      IntConst a = x.isIntConst();
      if (a != null) {
        IntConst b = y.isIntConst();
        if (b != null) {
          return Prim.gt.foldRel(a.getVal(), b.getVal());
        }
      }
      return null;
    }

    if (p == Prim.lte) {
      Atom x = args[0];
      Atom y = args[1];
      IntConst a = x.isIntConst();
      if (a != null) {
        IntConst b = y.isIntConst();
        if (b != null) {
          return Prim.lte.foldRel(a.getVal(), b.getVal());
        }
      }
      return null;
    }

    if (p == Prim.gte) {
      Atom x = args[0];
      Atom y = args[1];
      IntConst a = x.isIntConst();
      if (a != null) {
        IntConst b = y.isIntConst();
        if (b != null) {
          return Prim.gte.foldRel(a.getVal(), b.getVal());
        }
      }
      return null;
    }

    if (p == Prim.load || p == Prim.store) {
      Atom[] nargs = rewriteAddress(args, facts);
      return (args == nargs) ? null : done(p, nargs);
    }

    return null;
  }

  static Code done(Tail t) {
    return new Done(t);
  }

  static Code done(Atom a) {
    return done(new Return(a));
  }

  static Code done(int n) {
    return done(new IntConst(n));
  }

  static Code done(boolean b) {
    return done(new FlagConst(b));
  }

  static Code done(Prim p, Atom[] args) {
    return done(p.withArgs(args));
  }

  static Code done(Prim p, Atom a) {
    return done(p.withArgs(a));
  }

  static Code done(Prim p, Atom a, Atom b) {
    return done(p.withArgs(a, b));
  }

  static Code done(Prim p, Atom a, int n) {
    return done(p.withArgs(a, n));
  }

  static Code done(Prim p, int n, Atom b) {
    return done(p.withArgs(n, b));
  }

  /**
   * Test to see if this tail expression is a call to a specific primitive, returning null in the
   * (most likely) case that it is not.
   */
  Atom[] isPrim(Prim p) {
    return (p == this.p) ? args : null;
  }

  private static Code bnotVar(Atom x, Facts facts) {
    Tail a = x.lookupFact(facts);
    if (a != null) {

      // Eliminate double negation:
      Atom[] ap = a.isPrim(Prim.bnot);
      if (ap != null) {
        MILProgram.report("eliminated double bnot");
        return done(ap[0]); // bnot(bnot(u)) == u
      }

      // Handle negations of relational operators:
      if ((ap = a.isPrim(Prim.eq)) != null) { //  eq --> neq
        MILProgram.report("replaced bnot(eq(x,y)) with neq(x,y)");
        return done(Prim.neq, ap);
      }
      if ((ap = a.isPrim(Prim.neq)) != null) { //  neq --> eq
        MILProgram.report("replaced bnot(neq(x,y)) with eq(x,y)");
        return done(Prim.eq, ap);
      }
      if ((ap = a.isPrim(Prim.lt)) != null) { //  lt --> gte
        MILProgram.report("replaced bnot(lt(x,y)) with gte(x,y)");
        return done(Prim.gte, ap);
      }
      if ((ap = a.isPrim(Prim.lte)) != null) { //  lte --> gt
        MILProgram.report("replaced bnot(lte(x,y)) with gt(x,y)");
        return done(Prim.gt, ap);
      }
      if ((ap = a.isPrim(Prim.gt)) != null) { //  gt --> lte
        MILProgram.report("replaced bnot(gt(x,y)) with lte(x,y)");
        return done(Prim.lte, ap);
      }
      if ((ap = a.isPrim(Prim.gte)) != null) { //  gte --> lt
        MILProgram.report("replaced bnot(gte(x,y)) with lt(x,y)");
        return done(Prim.lt, ap);
      }
    }
    return null;
  }

  private static Code notVar(Atom x, Facts facts) {
    Tail a = x.lookupFact(facts);
    if (a != null) {
      // Eliminate double negation:
      Atom[] ap = a.isPrim(Prim.not);
      if (ap != null) {
        MILProgram.report("eliminated double not");
        return done(ap[0]); // not(not(u)) == u
      }
    }
    return null;
  }

  private static Code negVar(Atom x, Facts facts) {
    Tail a = x.lookupFact(facts);
    if (a != null) {
      Atom[] ap = a.isPrim(Prim.neg);
      if (ap != null) {
        MILProgram.report("rewrite: -(-x) ==> x");
        return done(ap[0]); // neg(neg(u)) == u
      }
      if ((ap = a.isPrim(Prim.sub)) != null) {
        MILProgram.report("rewrite: -(x - y) ==> y - x");
        return done(Prim.sub, ap[1], ap[0]);
      }
      // TODO: -(x * m) ==> x * (-m)   (but careful about large m)
    }
    return null;
  }

  private static Code flagToWordVar(Atom x, Facts facts) {
    return null;
  }

  /** Look for opportunities to simplify an expression using idempotence. */
  private static Code idempotent(Atom x, Atom y) {
    if (x == y) { // simple idempotence
      // TODO: in an expression of the form (x & y), we could further exploit idempotence if x
      // includes an
      // or with y (or vice versa); handling this would require the addition of Prim and a Facts
      // arguments.
      MILProgram.report("rewrite: x ! x ==> x");
      return done(x);
    }
    return null;
  }

  /**
   * Look for opportunities to rewrite an expression involving three operators and two constants as
   * an expression using just two operators and one constant. The specific patterns that we look for
   * are as follows, with p and q representing specific binary operators:
   *
   * <p>p(q(u,c), q(v,d)) == q(p(u,v), p(c,d)) p(q(u,c), v) == q(p(u,v), c) p(u, q(v,d)) == p(p(u,
   * v), c) --- NOTE: no q on rhs
   *
   * <p>These laws hold if p and q are equal and set to a commutative, associative binary operator
   * (add, mul, and, or, xor). But they also hold in at least one other case where p==sub and
   * q==add.
   *
   * <p>The assumptions on entry to this function are that we're trying to optimize an expression of
   * the form p(x, y) having already looked up facts a and b for each of x and y, respectively.
   */
  private static Code redistBin(PrimBinOp p, Prim q, Atom x, Tail a, Atom y, Tail b) {
    Atom[] ap = (a != null) ? a.isPrim(q) : null;
    IntConst c = (ap != null) ? ap[1].isIntConst() : null;

    Atom[] bp = (b != null) ? b.isPrim(q) : null;
    IntConst d = (bp != null) ? bp[1].isIntConst() : null;

    if (c != null) {
      if (d != null) { // (u `q` c) `p` (w `q` d)
        MILProgram.report("rewrite: (u ! c) ! (w ! d) ==> (u ! w) ! (c ! d)");
        return varVarConst(p, ap[0], bp[0], q, p.op(c.getVal(), d.getVal()));
      } else { // (u `q` c) `p` y
        MILProgram.report("rewrite: (u ! c) ! y==> (u ! y) ! c");
        return varVarConst(p, ap[0], y, q, c.getVal());
      }
    } else if (d != null) { // x `p` (w `q` d)
      MILProgram.report("rewrite: x ! (w ! d) ==> (x ! w) ! d");
      return varVarConst(p, x, bp[0], p, d.getVal());
    }
    return null;
  }

  /**
   * Special case of redistBin for associative, commutative operators where we can use the same
   * primitive for both the p and q parameters.
   */
  private static Code commuteRearrange(PrimBinOp p, Atom x, Tail a, Atom y, Tail b) {
    return redistBin(p, p, x, a, y, b);
  }

  /**
   * Create code for (a ! b) ! n where ! is a primitive p; a and b are variables; and n is a known
   * constant.
   */
  private static Code varVarConst(Prim p, Atom a, Atom b, Prim q, int n) {
    Temp v = new Temp();
    return new Bind(v, p.withArgs(a, b), done(q, v, n));
  }

  /**
   * Generate code for a deMorgan's law rewrite. We are trying to rewrite an expression of the form
   * p(x, y) with an associated (possibly null) fact a for x and b for y. If both a and b are of the
   * form inv(_) for some specific "inverting" primitive, inv, then we can rewrite the whole
   * formula, p(inv(u), inv(v)) as inv(q(u,v)) where q is a "dual" for p. There are (at least) three
   * special cases for this rule: if p=and, then q=or, inv=not: ~p | ~q = ~(p & q) if p=or, then
   * q=and, inv=not: ~p & ~q = ~(p | q) if p=add, then q=add, inv=neg: -p + -q = -(p + q) (add is
   * self-dual) if p=sub, then q=sub, inv=neg: -p - -q = -(p - q) (sub is self-dual) Assumes Word as
   * type of operands. (TODO: we only implement the first two rewrites above using the deMorgan
   * function; maybe we should also use deMorgan for the last two?)
   */
  private static Code deMorgan(Prim q, Prim inv, Tail a, Tail b) {
    Atom[] ap;
    Atom[] bp;
    if (a != null && (ap = a.isPrim(inv)) != null && b != null && (bp = b.isPrim(inv)) != null) {
      MILProgram.report("applied a version of deMorgan's law");
      Temp v = new Temp();
      return new Bind(v, q.withArgs(ap[0], bp[0]), done(inv, v));
    }
    return null;
  }

  private static Code addVarVar(Atom x, Atom y, Facts facts) {
    Tail a = x.lookupFact(facts);
    Tail b = y.lookupFact(facts);
    // ! System.out.print("addVarVar: a=");
    // ! if (a==null) { System.out.print("null"); } else { a.dump(); }
    // ! System.out.print(", b=");
    // ! if (b==null) { System.out.print("null"); } else { b.dump(); }
    // ! System.out.println();
    if (a != null || b != null) { // Only look for a rewrite if there are some facts
      Code nc;
      // TODO: commuteRearrange calls redistBin, which checks for combinations that cannot
      // occur here (single constant on one side of operation) ... look for ways to clean up!
      return ((null != (nc = commuteRearrange(Prim.add, x, a, y, b)))
              || (null != (nc = distAdd(x, a, y, b))))
          ? nc
          : null;
    }
    return distAddAnyAny(x, y);
  }

  private static Code addVarConst(Atom x, int m, Facts facts) {
    if (m == 0) { // x + 0 == x
      MILProgram.report("rewrite: x + 0 ==> x");
      return done(x);
    }
    Tail a = x.lookupFact(facts);
    if (a != null) {
      Atom[] ap;
      if ((ap = a.isPrim(Prim.add)) != null) {
        IntConst c = ap[1].isIntConst();
        if (c != null) {
          MILProgram.report("rewrite: (x + n) + m == x + (n + m)");
          return done(Prim.add, ap[0], c.getVal() + m);
        }
      } else if ((ap = a.isPrim(Prim.sub)) != null) {
        IntConst c;
        if ((c = ap[1].isIntConst()) != null) {
          MILProgram.report("rewrite: (x - n) + m == x + (m - n)");
          return done(Prim.add, ap[0], m - c.getVal());
        }
        if ((c = ap[0].isIntConst()) != null) {
          MILProgram.report("rewrite: (n - x) + m == (n + m) - x");
          return done(Prim.add, c.getVal() + m, ap[1]);
        }
      } else if ((ap = a.isPrim(Prim.neg)) != null) {
        MILProgram.report("rewrite: (-x) + m  ==> m - x");
        return done(Prim.sub, m, ap[0]);
      }
    }
    return null;
  }

  private static Code distAdd(Atom x, Tail a, Atom y, Tail b) {
    if (a != null) {
      Atom[] ap;
      if ((ap = a.isPrim(Prim.neg)) != null) {
        return distAddNeg(ap[0], y, b);
      }
      IntConst m;
      if ((ap = a.isPrim(Prim.mul)) != null && (m = ap[1].isIntConst()) != null) {
        return distAddCMul(x, ap[0], m.getVal(), y, b);
      }
    }
    return distAddAny(x, y, b);
  }

  private static Code distSub(Atom x, Tail a, Atom y, Tail b) {
    if (a != null) {
      Atom[] ap;
      if ((ap = a.isPrim(Prim.neg)) != null) {
        return distSubNeg(ap[0], y, b);
      }
      IntConst m;
      if ((ap = a.isPrim(Prim.mul)) != null && (m = ap[1].isIntConst()) != null) {
        return distSubCMul(x, ap[0], m.getVal(), y, b);
      }
    }
    return distSubAny(x, y, b);
  }

  private static Code distAddNeg(Atom u, Atom y, Tail b) {
    if (b != null) {
      Atom[] bp;
      IntConst n;
      if ((bp = b.isPrim(Prim.neg)) != null) {
        return distAddNegNeg(u, bp[0]);
      }
      if ((bp = b.isPrim(Prim.mul)) != null && (n = bp[1].isIntConst()) != null) {
        return distAddNegCMul(u, y, bp[0], n.getVal());
      }
    }
    return distAddNegAny(u, y);
  }

  private static Code distAddAny(Atom x, Atom y, Tail b) {
    if (b != null) {
      Atom[] bp;
      IntConst n;
      if ((bp = b.isPrim(Prim.neg)) != null) {
        return distAddAnyNeg(x, bp[0]);
      }
      if ((bp = b.isPrim(Prim.mul)) != null && (n = bp[1].isIntConst()) != null) {
        return distAddAnyCMul(x, bp[0], n.getVal());
      }
    }
    return distAddAnyAny(x, y);
  }

  private static Code distAddCMul(Atom x, Atom u, int c, Atom y, Tail b) {
    if (b != null) {
      Atom[] bp;
      IntConst n;
      if ((bp = b.isPrim(Prim.neg)) != null) {
        return distAddCMulNeg(x, u, c, bp[0]);
      }
      if ((bp = b.isPrim(Prim.mul)) != null && (n = bp[1].isIntConst()) != null) {
        return distCC(u, Prim.mul, c, Prim.add, bp[0], n.getVal());
      }
    }
    return distAddCMulAny(u, c, y);
  }

  private static Code distSubNeg(Atom u, Atom y, Tail b) {
    if (b != null) {
      Atom[] bp;
      IntConst n;
      if ((bp = b.isPrim(Prim.neg)) != null) {
        return distSubNegNeg(u, bp[0]);
      }
      if ((bp = b.isPrim(Prim.mul)) != null && (n = bp[1].isIntConst()) != null) {
        return distSubNegCMul(u, y, bp[0], n.getVal());
      }
    }
    return distSubNegAny(u, y);
  }

  private static Code distSubAny(Atom x, Atom y, Tail b) {
    if (b != null) {
      Atom[] bp;
      IntConst n;
      if ((bp = b.isPrim(Prim.neg)) != null) {
        return distSubAnyNeg(x, bp[0]);
      }
      if ((bp = b.isPrim(Prim.mul)) != null && (n = bp[1].isIntConst()) != null) {
        return distSubAnyCMul(x, bp[0], n.getVal());
      }
    }
    return distSubAnyAny(x, y);
  }

  private static Code distSubCMul(Atom x, Atom u, int c, Atom y, Tail b) {
    if (b != null) {
      Atom[] bp;
      IntConst n;
      if ((bp = b.isPrim(Prim.neg)) != null) {
        return distSubCMulNeg(x, u, c, bp[0]);
      }
      if ((bp = b.isPrim(Prim.mul)) != null && (n = bp[1].isIntConst()) != null) {
        return distCC(u, Prim.mul, c, Prim.sub, bp[0], n.getVal());
      }
    }
    return distSubCMulAny(u, c, y);
  }

  private static Code distCC(
      Atom u, Prim m, int c, PrimBinOp a, Atom v, int d) { // (u `m` c) `a` (v `m` d) = ...
    if (u == v) {
      MILProgram.report("rewrite: (u `m` c) `a` (u `m` d) ==> u `m` (c `a` d)");
      return done(m, u, a.op(c, d));
    }
    if (c == d) {
      MILProgram.report("rewrite: (u `m` c) `a` (v `m` c) ==> (u `a` v) `m` c");
      Temp t = new Temp();
      return new Bind(t, a.withArgs(u, v), done(m, t, c));
    }
    return null;
  }

  private static Code distRearrange(PrimBinOp p, PrimBinOp q, Atom x, Tail a, Atom y, Tail b) {
    Atom[] ap, bp;
    IntConst c, d;
    if (a != null
        && b != null
        && // check for an expression of the form required by distCC
        (ap = a.isPrim(q)) != null
        && (bp = b.isPrim(q)) != null
        && (c = ap[1].isIntConst()) != null
        && (d = bp[1].isIntConst()) != null) {
      return distCC(ap[0], q, c.getVal(), p, bp[0], d.getVal());
    }
    return null;
  }

  private static Code distAddNegNeg(Atom u, Atom v) {
    MILProgram.report("rewrite: (-u) + (-v) ==> - (u + v)");
    Temp t = new Temp();
    return new Bind(t, Prim.add.withArgs(u, v), done(Prim.neg, t));
  }

  private static Code distSubNegNeg(Atom u, Atom v) {
    MILProgram.report("rewrite: (-u) - (-v) ==> v - u)");
    return done(Prim.sub, v, u);
  }

  private static Code distAddCMulNeg(Atom x, Atom u, int c, Atom v) { // x@(u * c) + (-v) = ...
    if (u == v) {
      MILProgram.report("rewrite: (u * c) + (-u) ==> u * (c - 1)");
      return done(Prim.mul, u, c - 1);
    }
    return distAddAnyNeg(x, v);
  }

  private static Code distAddNegCMul(Atom u, Atom y, Atom v, int d) { // (-u) + y@(v * d) = ...
    if (u == v) {
      MILProgram.report("rewrite: (-u) + (u * d)  ==>  u * (d - 1)");
      return done(Prim.mul, u, d - 1);
    }
    return distAddNegAny(u, y);
  }

  private static Code distSubCMulNeg(Atom x, Atom u, int c, Atom v) { // x@(u * c) - (-v) = ...
    if (u == v) {
      MILProgram.report("rewrite: (u * c) - (-u) ==> u * (c + 1)");
      return done(Prim.mul, u, c + 1);
    }
    return distAddAnyNeg(x, v);
  }

  private static Code distSubNegCMul(Atom u, Atom y, Atom v, int d) { // (-u) - y@(v * d) = ...
    if (u == v) {
      MILProgram.report("rewrite: (-u) - (u * d)  ==>  u * (-(1 + d))");
      return done(Prim.mul, u, -(1 + d));
    }
    return distAddNegAny(u, y);
  }

  private static Code distAddCMulAny(Atom u, int c, Atom y) { // (u * c) + y = ...
    if (u == y) {
      MILProgram.report("rewrite: (u * c) + u ==> u * (c + 1)");
      return done(Prim.mul, u, c + 1);
    }
    return null;
  }

  private static Code distAddAnyCMul(Atom x, Atom v, int d) { // x + (v * d) = ...
    if (x == v) {
      MILProgram.report("rewrite: v + (v * d)  ==>  v * (1 + d)");
      return done(Prim.mul, v, 1 + d);
    }
    return null;
  }

  private static Code distSubCMulAny(Atom u, int c, Atom y) { // (u * c) - y = ...
    if (u == y) {
      MILProgram.report("rewrite: (u * c) - u ==> u * (c - 1)");
      return done(Prim.mul, u, c - 1);
    }
    return null;
  }

  private static Code distSubAnyCMul(Atom x, Atom v, int d) { // x - (v * d) = ...
    if (x == v) {
      MILProgram.report("rewrite: v - (v * d)  ==>  v * (1 - d)");
      return done(Prim.mul, v, 1 - d);
    }
    return null;
  }

  private static Code distAddNegAny(Atom u, Atom y) {
    MILProgram.report("rewrite: (-u) + y ==> y - u");
    return done(Prim.sub, y, u);
  }

  private static Code distAddAnyNeg(Atom x, Atom v) {
    MILProgram.report("rewrite: x + (-v) ==> x - v");
    return done(Prim.sub, x, v);
  }

  private static Code distSubNegAny(Atom u, Atom y) {
    MILProgram.report("rewrite: (-u) - y ==> -(u + y)");
    Temp t = new Temp();
    return new Bind(t, Prim.add.withArgs(u, y), done(Prim.neg, t));
  }

  private static Code distSubAnyNeg(Atom x, Atom v) {
    MILProgram.report("rewrite: x - (-v) ==> x + v");
    return done(Prim.add, x, v);
  }

  private static Code distAddAnyAny(Atom x, Atom y) {
    if (x == y) {
      MILProgram.report("rewrite: x + x ==> x * 2");
      return done(Prim.mul, x, 2);
    }
    return null;
  }

  private static Code distSubAnyAny(Atom x, Atom y) {
    if (x == y) {
      MILProgram.report("rewrite: x - x ==> 0");
      return done(0);
    }
    return null;
  }

  private static Code mulVarVar(Atom x, Atom y, Facts facts) {
    return commuteRearrange(Prim.mul, x, x.lookupFact(facts), y, y.lookupFact(facts));
  }

  private static Code mulVarConst(Atom x, int m, Facts facts) {
    if (m == 0) { // x * 0 == 0
      MILProgram.report("rewrite: x * 0 ==> 0");
      return done(0);
    }
    if (m == 1) { // x * 1 == x
      MILProgram.report("rewrite: x * 1 ==> x");
      return done(x);
    }
    if (m == (-1)) { // x * -1 == neg(x)
      MILProgram.report("rewrite: x * (-1) ==> -x");
      return done(Prim.neg, x);
    }
    if (m > 2 && (m & (m - 1)) == 0) { // x * (1 << n) == x << n
      int n = 0;
      int m0 = m;
      while ((m >>= 1) > 0) {
        n++;
      } // calculate n
      MILProgram.report("rewrite: x * " + m0 + " ==> x << " + n);
      return done(Prim.shl, x, n);
    }
    Tail a = x.lookupFact(facts);
    if (a != null) {
      Atom[] ap;
      if ((ap = a.isPrim(Prim.mul)) != null) {
        IntConst b = ap[1].isIntConst();
        if (b != null) { // (u * c) * m == u * (c * m)
          return done(Prim.mul, ap[0], b.getVal() * m);
        }
      } else if ((ap = a.isPrim(Prim.add)) != null) {
        IntConst b = ap[1].isIntConst();
        if (b != null) { // (u + n) * m == (u * m) + (n * m)
          Temp v = new Temp();
          return new Bind(v, Prim.mul.withArgs(ap[0], m), done(Prim.add, v, b.getVal() * m));
        }
      }
    }
    return null;
  }

  private static Code orVarVar(Atom x, Atom y, Facts facts) {
    Code nc = idempotent(x, y);
    if (nc == null) {
      Tail a = x.lookupFact(facts);
      Tail b = y.lookupFact(facts);
      if ((a != null || b != null)
          && (nc = commuteRearrange(Prim.or, x, a, y, b)) == null
          && (nc = distRearrange(Prim.or, Prim.and, x, a, y, b)) == null) {
        nc = deMorgan(Prim.and, Prim.not, a, b);
      }
    }
    return nc;
  }

  private static Code orVarConst(Atom x, int m, Facts facts) {
    if (m == 0) {
      MILProgram.report("rewrite: x | 0 ==> x");
      return done(x);
    }
    if (m == (~0)) {
      MILProgram.report("rewrite: x | (~0) ==> (~0)");
      return done(~0);
    }
    Tail a = x.lookupFact(facts);
    if (a != null) {
      Atom[] ap;
      if ((ap = a.isPrim(Prim.or)) != null) {
        IntConst c = ap[1].isIntConst();
        if (c != null) {
          MILProgram.report("rewrite: (u | c) | m ==> u | (c | n)");
          return done(Prim.or.withArgs(ap[0], c.getVal() | m));
        }
      } else if ((ap = a.isPrim(Prim.not)) != null) {
        MILProgram.report("rewrite: (~u) | m ==> ~(u & ~m)");
        Temp v = new Temp();
        return new Bind(v, Prim.and.withArgs(ap[0], ~m), done(Prim.not.withArgs(v)));
      } else if ((ap = a.isPrim(Prim.and)) != null) {
        IntConst c = ap[1].isIntConst(); // (_ & c) | m
        if (c != null) {
          Tail b = ap[0].lookupFact(facts); // ((b) & c) | m
          if (b != null) {
            Atom[] bp = b.isPrim(Prim.or); // ((_ | _) & c) | m
            if (bp != null) {
              IntConst d = bp[1].isIntConst(); // ((_ | d) & c) | m
              if (d != null) {
                MILProgram.report("rewrite: ((u | d) & c) | m ==> (u & c) | ((d & c) | m)");
                Temp v = new Temp();
                int n = (d.getVal() & c.getVal()) | m;
                return new Bind(v, Prim.and.withArgs(bp[0], c), done(Prim.or.withArgs(v, n)));
              }
            }
          }
        }
      }
    }
    return null;
  }

  private static Code andVarVar(Atom x, Atom y, Facts facts) {
    Code nc = idempotent(x, y);
    if (nc == null) {
      Tail a = x.lookupFact(facts);
      Tail b = y.lookupFact(facts);
      if ((a != null || b != null)
          && (nc = commuteRearrange(Prim.and, x, a, y, b)) == null
          && (nc = distRearrange(Prim.and, Prim.or, x, a, y, b)) == null) {
        nc = deMorgan(Prim.or, Prim.not, a, b);
      }
    }
    return nc;
  }

  private static Code andVarConst(Atom x, int m, Facts facts) {
    if (m == 0) {
      MILProgram.report("rewrite: x & 0 ==> 0");
      return done(0);
    }
    if (m == (~0)) {
      MILProgram.report("rewrite: x & (~0) ==> x");
      return done(x);
    }
    Tail a = x.lookupFact(facts); // (a) & m
    if (a != null) {
      Atom[] ap;
      if ((ap = a.isPrim(Prim.and)) != null) {
        IntConst c = ap[1].isIntConst();
        if (c != null) {
          MILProgram.report("rewrite: (u & c) & m ==> u & (c & n)");
          return done(Prim.and.withArgs(ap[0], c.getVal() & m));
        }
      } else if ((ap = a.isPrim(Prim.not)) != null) {
        MILProgram.report("rewrite: (~u) & m ==> ~(u | ~m)");
        Temp v = new Temp();
        return new Bind(v, Prim.or.withArgs(ap[0], ~m), done(Prim.not.withArgs(v)));
      } else if ((ap = a.isPrim(Prim.or)) != null) {
        IntConst c = ap[1].isIntConst(); // (_ | c) & m
        if (c != null) {
          MILProgram.report("rewrite: (a | c) & m ==> (a & m) | (c & m)");
          Temp v = new Temp();
          return new Bind(
              v, Prim.and.withArgs(ap[0], m), done(Prim.or.withArgs(v, c.getVal() & m)));
        }
      } else if ((ap = a.isPrim(Prim.shl)) != null) {
        // TODO: would it be better to rewrite (x << c) & m ==> (x & (m>>c)) << c?
        // (observation: rewriting would avoid repeated triggering the logic here)
        // Q1: is this valid (intuition: (x<<c)&m = (x<<c)&((m>>c)<<c) = (x&(m>>c))<<c)
        // Q2: does this interfere with rewrites for (x & m) << c?  May need to remove those ...
        IntConst c = ap[1].isIntConst(); // (_ << c) & m
        if (c != null) {
          int w = c.getVal();
          if (w > 0 && w < Type.WORDSIZE) {
            // left shifting by w bits performs an effective mask by em on the result:
            int em = ~((1 << w) - 1);
            if ((m & em) == em) { // if specified mask doesn't do more than effective mask ...
              MILProgram.report(
                  "rewrite: (x << "
                      + w
                      + ") & 0x"
                      + Integer.toHexString(m)
                      + " ==> (x << "
                      + w
                      + ")");
              return done(x);
            }
          }
        }
      } else if ((ap = a.isPrim(Prim.lshr)) != null) {
        IntConst c = ap[1].isIntConst(); // (_ >> c) & m
        if (c != null) {
          int w = c.getVal();
          if (w > 0 && w < Type.WORDSIZE) {
            // right shifting by w bits performs an effective mask by em on the result:
            int em = (1 << (Type.WORDSIZE - w)) - 1;
            if ((m & em) == em) { // if specified mask doesn't do more than effective mask ...
              MILProgram.report(
                  "rewrite: (x >> "
                      + w
                      + ") & 0x"
                      + Integer.toHexString(m)
                      + " ==> (x >> "
                      + w
                      + ")");
              return done(x);
            }
          }
        }
      } else if ((ap = a.isPrim(Prim.add)) != null) {
        // TODO: generalize this to work with other primitives (e.g., sub, mul)
        // and to eliminate masks on either left or right hand sides
        Tail b = ap[0].lookupFact(facts); // ((b) + y) & m
        if (b != null) {
          Atom[] bp = b.isPrim(Prim.and);
          if (bp != null) { // ((u & v) + y) & m
            IntConst c = bp[1].isIntConst();
            if (c != null && modarith(c.getVal(), m)) { // ((u & m) + y) & m
              MILProgram.report(
                  "rewrite: ((x & 0x"
                      + Integer.toHexString(c.getVal())
                      + ") + y) & 0x"
                      + Integer.toHexString(m)
                      + " ==> (x + y) & 0x"
                      + Integer.toHexString(m));
              Temp v = new Temp();
              return new Bind(v, Prim.add.withArgs(bp[0], ap[1]), done(Prim.and.withArgs(v, m)));
            }
          }
        }
      }
    }
    return null;
  }

  /** Return true if ((x & m1) + y) & m2 == (x + y) & m2. */
  private static boolean modarith(int m1, int m2) {
    // if m is a run of bits, then  m | ~(m-1)  has  the same run of bits
    // with all more significant bits set to 1
    return bitrun(m1) && bitrun(m2) && ((m1 & (m2 | ~(m2 - 1))) == m1);
  }

  /** Return true if value m is a single run of 1 bits (no zero bits between 1s). */
  private static boolean bitrun(int m) {
    // If m is a run of bits, then m | (m-1) will be a run of bits with the
    // same most significant bit and all lower bits set to 1.
    int v = (m | (m - 1));
    // In which case, that value plus one will be a power of two:
    return (v & (v + 1)) == 0;
  }

  private static Code xorVarVar(Atom x, Atom y, Facts facts) {
    if (x == y) { // simple annihilator
      // TODO: in an expression of the form (x ^ y), we could further
      // exploit annihilation if x includes an or with y (or vice versa).
      MILProgram.report("rewrite: x ^ x ==> 0");
      return done(0);
    }
    return commuteRearrange(Prim.xor, x, x.lookupFact(facts), y, y.lookupFact(facts));
  }

  private static Code xorVarConst(Atom x, int m, Facts facts) {
    if (m == 0) { // x ^ 0 == x
      MILProgram.report("rewrite: x ^ 0 ==> x");
      return done(x);
    }
    if (m == (~0)) { // x ^ (~0) == not(x)
      MILProgram.report("rewrite: x ^ (~0) ==> not(x)");
      return done(Prim.not.withArgs(x));
    }
    return null;
  }

  private static Code subVarVar(Atom x, Atom y, Facts facts) {
    if (x == y) { // x - x == 0
      MILProgram.report("rewrite: x - x ==> 0");
      return done(0);
    }
    Tail a = x.lookupFact(facts);
    Tail b = y.lookupFact(facts);
    if (a != null || b != null) { // Only look for a rewrite if there are some facts
      Code nc;
      return ((null != (nc = redistBin(Prim.sub, Prim.add, x, a, y, b)))
              || (null != (nc = distSub(x, a, y, b))))
          ? nc
          : null;
    }
    return distSubAnyAny(x, y);
  }

  private static Code subVarConst(Atom x, int m, Facts facts) {
    if (m == 0) { // x - 0 == x
      MILProgram.report("rewrite: x - 0 ==> x");
      return done(x);
    }
    Tail a = x.lookupFact(facts);
    if (a != null) {
      Atom[] ap;
      if ((ap = a.isPrim(Prim.add)) != null) {
        IntConst b = ap[1].isIntConst();
        if (b != null) {
          MILProgram.report("rewrite: (x + n) - m == x + (n - m)");
          return done(Prim.add, ap[0], b.getVal() - m);
        }
      } else if ((ap = a.isPrim(Prim.sub)) != null) {
        IntConst c;
        if ((c = ap[1].isIntConst()) != null) {
          MILProgram.report("rewrite: (x - n) - m == x - (n + m)");
          return done(Prim.sub, ap[0], c.getVal() + m);
        }
        if ((c = ap[0].isIntConst()) != null) {
          MILProgram.report("rewrite: (n - x) - m == (n - m) - x");
          return done(Prim.sub, c.getVal() - m, ap[1]);
        }
      } else if ((ap = a.isPrim(Prim.neg)) != null) {
        MILProgram.report("rewrite: (-x) - m  == -(x + m)");
        Temp v = new Temp();
        return new Bind(v, Prim.add.withArgs(ap[0], m), done(Prim.neg, v));
      }
    }

    // TODO: not sure about this one; it turns simple decrements like sub(x,1) into
    // adds like add(x, -1); I guess this could be addressed by a code generator that
    // doesn't just naively turn every add(x,n) into an add instruction ...
    //
    // If n==0,  then add(x,n) shouldn't occur ...
    //    n==1,  then add(x,n) becomes an increment instruction
    //    n>1,   then add(x,n) becomes an add with immediate argument
    //    n==-1, then add(x,n) becomes a decrement instruction
    //    n< -1, then add(x,n) becomes a subtract with immediate argument
    //
    return done(Prim.add, x, (-m)); // x - n == x + (-n)
  }

  private static Code subConstVar(int n, Atom y, Facts facts) {
    if (n == 0) { // 0 - y == -y
      MILProgram.report("rewrite: 0 - y ==> -y");
      return done(Prim.neg, y);
    }
    Tail b = y.lookupFact(facts);
    if (b != null) {
      Atom[] bp;
      if ((bp = b.isPrim(Prim.add)) != null) {
        IntConst c = bp[1].isIntConst();
        if (c != null) {
          MILProgram.report("rewrite: n - (x + m) == (n - m) - x");
          return done(Prim.sub, n - c.getVal(), bp[0]);
        }
      } else if ((bp = b.isPrim(Prim.sub)) != null) {
        IntConst c;
        if ((c = bp[1].isIntConst()) != null) {
          MILProgram.report("rewrite: n - (x - m) == (n + m) - x");
          return done(Prim.sub, n + c.getVal(), bp[0]);
        }
        if ((c = bp[0].isIntConst()) != null) {
          MILProgram.report("rewrite: n - (m - x) == (n - m) + x");
          return done(Prim.add, n - c.getVal(), bp[0]);
        }
      } else if ((bp = b.isPrim(Prim.neg)) != null) {
        MILProgram.report("rewrite: n - (-x) == x + n");
        return done(Prim.add, bp[0], n);
      }
    }
    return null;
  }

  private static Code shlVarVar(Atom x, Atom y, Facts facts) {
    return null;
  }

  private static Code shlVarConst(Atom x, int m, Facts facts) {
    if (m == 0) { // x << 0 == x
      MILProgram.report("rewrite: x << 0 ==> x");
      return done(x);
    } else if (m < 0 || m >= Type.WORDSIZE) { // x << m == x << (m % WORDSIZE)
      int n = m % Type.WORDSIZE;
      // TODO: Is this architecture dependent?
      MILProgram.report("rewrite: x << " + m + " ==> x << " + n);
      return done(Prim.shl, x, n);
    }
    Tail a = x.lookupFact(facts);
    if (a != null) {
      Atom[] ap;
      if ((ap = a.isPrim(Prim.shl)) != null) {
        IntConst b = ap[1].isIntConst();
        if (b != null) {
          int n = b.getVal();
          if (n >= 0 && n < Type.WORDSIZE && m >= 0 && m < Type.WORDSIZE) {
            if (n + m >= Type.WORDSIZE) {
              MILProgram.report("rewrite: (x << " + n + ") << " + m + " ==> 0");
              return done(0);
            } else {
              MILProgram.report("rewrite: (x << " + n + ") << " + m + " ==> x << " + (n + m));
              return done(Prim.shl, ap[0], n + m);
            }
          }
        }
      } else if ((ap = a.isPrim(Prim.lshr)) != null) {
        IntConst b = ap[1].isIntConst();
        if (b != null) {
          int n = b.getVal();
          if (n == m && n > 0 && n < Type.WORDSIZE) {
            int mask = (-1) << m;
            MILProgram.report(
                "rewrite: (x >>> " + m + ") << " + m + " ==>  x & 0x" + Integer.toHexString(mask));
            return done(Prim.and, ap[0], mask);
          }
        }
      } else if ((ap = a.isPrim(Prim.and)) != null) {
        IntConst b = ap[1].isIntConst();
        if (b != null) {
          // TODO: is this a good idea?  Unless n << m == 0, this makes the mask bigger ...
          MILProgram.report("rewrite: (x & n) << m  ==  (x<<m) & (n<<m)");
          int n = b.getVal();
          Temp v = new Temp();
          return new Bind(v, Prim.shl.withArgs(ap[0], m), done(Prim.and, v, n << m));
        }
      } else if ((ap = a.isPrim(Prim.or)) != null) {
        IntConst b = ap[1].isIntConst();
        if (b != null) {
          // TODO: is this a good idea?  Unless n << m == 0, this makes the constant bigger ...
          // (But it might reduce the need for a shift if the shift on x can be combined with
          // another shift (i.e., if x = (y << p), say) ... which can happen in practice ...
          MILProgram.report("rewrite: (x | n) << m  ==  (x<<m) | (n<<m)");
          int n = b.getVal();
          Temp v = new Temp();
          return new Bind(v, Prim.shl.withArgs(ap[0], m), done(Prim.or, v, n << m));
        }
      } else if ((ap = a.isPrim(Prim.xor)) != null) {
        IntConst b = ap[1].isIntConst();
        if (b != null) {
          MILProgram.report("rewrite: (x ^ n) << m  ==  (x<<m) ^ (n<<m)");
          int n = b.getVal();
          Temp v = new Temp();
          return new Bind(v, Prim.shl.withArgs(ap[0], m), done(Prim.xor, v, n << m));
        }
      } else if ((ap = a.isPrim(Prim.add)) != null) {
        // TODO: we are using the same basic pattern here for &, |, ^, and +
        // ... can we generalize and perhaps include other operators too?
        IntConst b = ap[1].isIntConst();
        if (b != null) {
          MILProgram.report("rewrite: (x + n) << m  ==  (x<<m) + (n<<m)");
          int n = b.getVal();
          Temp v = new Temp();
          return new Bind(v, Prim.shl.withArgs(ap[0], m), done(Prim.add, v, n << m));
        }
      }
    }
    return null;
  }

  private static Code shlConstVar(int n, Atom y, Facts facts) {
    if (n == 0) { // 0 << y == 0
      MILProgram.report("rewrite: 0 << y ==> 0");
      return done(0);
    }
    return null;
  }

  private static Code lshrVarVar(Atom x, Atom y, Facts facts) {
    return null;
  }

  private static Code lshrVarConst(Atom x, int m, Facts facts) {
    if (m == 0) { // x >>> 0 == x
      MILProgram.report("rewrite: lshr((x, 0)) ==> x");
      return done(x);
    } else if (m < 0 || m >= Type.WORDSIZE) { // x >>> m == x >>> (m % WORDSIZE)
      int n = m % Type.WORDSIZE;
      // TODO: Is this architecture dependent?
      MILProgram.report("rewrite: x >>> " + m + " ==> x >>> " + n);
      return done(Prim.lshr, x, n);
    }
    Tail a = x.lookupFact(facts);
    if (a != null) {
      Atom[] ap;
      if ((ap = a.isPrim(Prim.lshr)) != null) {
        IntConst b = ap[1].isIntConst();
        if (b != null) {
          int n = b.getVal();
          if (n >= 0 && n < Type.WORDSIZE && m >= 0 && m < Type.WORDSIZE) {
            if (n + m >= Type.WORDSIZE) {
              MILProgram.report("rewrite: (x >>> " + n + ") >>> " + m + " ==> 0");
              return done(0);
            } else {
              MILProgram.report("rewrite: (x >>> " + n + ") >>> " + m + " ==> x >>> " + (n + m));
              return done(Prim.lshr, ap[0], n + m);
            }
          }
        }
      } else if ((ap = a.isPrim(Prim.shl)) != null) {
        IntConst b = ap[1].isIntConst();
        if (b != null) {
          int n = b.getVal();
          if (n == m && n > 0 && n < Type.WORDSIZE) {
            int mask = (-1) >>> m;
            MILProgram.report(
                "rewrite: (x << " + m + ") >>> " + m + " ==>  x & 0x" + Integer.toHexString(mask));
            return done(Prim.and, ap[0], mask);
          }
        }
      } else if ((ap = a.isPrim(Prim.and)) != null) {
        IntConst b = ap[1].isIntConst();
        if (b != null) {
          MILProgram.report("rewrite: (x & n) >>> m  ==  (x>>>m) & (n>>>m)");
          int n = b.getVal();
          Temp v = new Temp();
          return new Bind(v, Prim.lshr.withArgs(ap[0], m), done(Prim.and, v, n >>> m));
        }
      } else if ((ap = a.isPrim(Prim.or)) != null) {
        IntConst b = ap[1].isIntConst();
        if (b != null) {
          MILProgram.report("rewrite: (x | n) >>> m  ==  (x>>>m) | (n>>>m)");
          int n = b.getVal();
          Temp v = new Temp();
          return new Bind(v, Prim.lshr.withArgs(ap[0], m), done(Prim.or, v, n >>> m));
        }
      } else if ((ap = a.isPrim(Prim.xor)) != null) {
        IntConst b = ap[1].isIntConst();
        if (b != null) {
          MILProgram.report("rewrite: (x ^ n) >>> m  ==  (x>>>m) ^ (n>>>m)");
          int n = b.getVal();
          Temp v = new Temp();
          return new Bind(v, Prim.lshr.withArgs(ap[0], m), done(Prim.xor, v, n >>> m));
        }
      }
    }
    return null;
  }

  private static Code lshrConstVar(int n, Atom y, Facts facts) {
    if (n == 0) { // 0 >>> y == 0
      MILProgram.report("rewrite: lshr((0, y)) ==> 0");
      return done(0);
    }
    return null;
  }

  private static Code ashrVarVar(Atom x, Atom y, Facts facts) {
    return null;
  }

  private static Code ashrVarConst(Atom x, int m, Facts facts) {
    if (m == 0) { // x >> 0 == x
      MILProgram.report("rewrite: ashr((x, 0)) ==> x");
      return done(x);
    } else if (m < 0 || m >= Type.WORDSIZE) { // x >>> m == x >>> (m % WORDSIZE)
      int n = m % Type.WORDSIZE;
      // TODO: Is this architecture dependent?
      MILProgram.report("rewrite: x >> " + m + " ==> x >> " + n);
      return done(Prim.ashr, x, n);
    }
    Tail a = x.lookupFact(facts);
    if (a != null) {
      Atom[] ap;
      if ((ap = a.isPrim(Prim.ashr)) != null) {
        IntConst b = ap[1].isIntConst();
        if (b != null) {
          int n = b.getVal();
          if (n >= 0 && n < Type.WORDSIZE && m >= 0 && m < Type.WORDSIZE) {
            if (n + m >= Type.WORDSIZE) {
              MILProgram.report(
                  "rewrite: (x >> " + n + ") >> " + m + " ==> x >> " + (Type.WORDSIZE - 1));
              return done(Prim.ashr, ap[0], Type.WORDSIZE - 1);
            } else {
              MILProgram.report("rewrite: (x >> " + n + ") >> " + m + " ==> x >> " + (n + m));
              return done(Prim.ashr, ap[0], n + m);
            }
          }
        }
      } else if ((ap = a.isPrim(Prim.and)) != null) {
        // It seems unlikely that arithmetic shifts will be used with bitwise operators,
        // but it shouldn't do any harm to include these optimization cases ...
        IntConst b = ap[1].isIntConst();
        if (b != null) {
          MILProgram.report("rewrite: (x & n) >> m  ==  (x>>m) & (n>>m)");
          int n = b.getVal();
          Temp v = new Temp();
          return new Bind(v, Prim.ashr.withArgs(ap[0], m), done(Prim.and, v, n >> m));
        }
      } else if ((ap = a.isPrim(Prim.or)) != null) {
        IntConst b = ap[1].isIntConst();
        if (b != null) {
          MILProgram.report("rewrite: (x | n) >> m  ==  (x>>m) | (n>>m)");
          int n = b.getVal();
          Temp v = new Temp();
          return new Bind(v, Prim.ashr.withArgs(ap[0], m), done(Prim.or, v, n >> m));
        }
      } else if ((ap = a.isPrim(Prim.xor)) != null) {
        IntConst b = ap[1].isIntConst();
        if (b != null) {
          MILProgram.report("rewrite: (x ^ n) >> m  ==  (x>>m) ^ (n>>m)");
          int n = b.getVal();
          Temp v = new Temp();
          return new Bind(v, Prim.ashr.withArgs(ap[0], m), done(Prim.xor, v, n >> m));
        }
      }
    }
    return null;
  }

  private static Code ashrConstVar(int n, Atom y, Facts facts) {
    if (n == 0) { // 0 >> y == 0
      MILProgram.report("rewrite: ashr((0, y)) ==> 0");
      return done(0);
    } else if (~n == 0) { // ~0 >> y = ~0
      MILProgram.report("rewrite: ashr((~0, y)) ==> ~0");
      return done(n);
    }
    return null;
  }

  /**
   * Rewrite the components of an address, specified as the argument to a load or store operation
   * for example, to introduce elements of more complex addressing modes (such as the use of
   * offsets, index values, and offsets).
   */
  private static Atom[] rewriteAddress(Atom[] orig, Facts facts) {
    Atom[] args = orig;

    // We will assume, without attempting to validate it, that the args array has (at least five)
    // components: S=args[0], b=args[1], o=args[2], i=args[3], and m=args[4].  Additional arguments
    // may
    // be provided (for example, to specify the value for use in a store operation) but they will be
    // ignored here.

    // Facts about the values of the o and i parameters (in other words, Tail values capturing the
    // form
    // of values in o and i, if known), are stored in fo and fi.  The values in fo and fi must be
    // "refreshed" by further calls to lookupFact() if the values of o or i are changed by a
    // rewrite.
    Tail fo = args[2].lookupFact(facts);
    Tail fi = args[3].isZero() ? null : args[3].lookupFact(facts);
    Atom[] ps;

    // 0)  ((S, _, B, i, m)) ---> ((S, B, _, i, m))
    if (args[1].isZero() && args[2].isBase()) {
      args = Atom.ensureFreshArgs(args);
      args[1] = args[2];
      args[2] = IntConst.Zero; // TODO: swap args 1 and 2?
      MILProgram.report("rewrite: use offset as base address");
    }

    // 1)  ((S, _, B+o, i, m)) ---> ((S, B, o, i, m))
    if (args[1].isZero() // no base set
        && fo != null
        && (ps = fo.isPrim(Prim.add)) != null) { // offset is a sum
      if (ps[0].isBase()) { // left argument is a base
        MILProgram.report("rewrite: base addressing using " + ps[0]);
        args = Atom.ensureFreshArgs(args, orig);
        args[1] = ps[0];
        args[2] = ps[1];
        fo = args[2].lookupFact(facts);
      } else if (ps[1].isBase()) { // right argument is a base
        MILProgram.report("rewrite: base addressing using " + ps[1]);
        args = Atom.ensureFreshArgs(args, orig);
        args[1] = ps[1];
        args[2] = ps[0];
        fo = args[2].lookupFact(facts);
      }
    }

    // 2)  ((S, _, o, B+i, _)) ---> ((S, B, o, i, _))
    if (args[1].isZero() // no base set
        && args[4].isZero() // unit/no multiplier
        && fi != null
        && (ps = fi.isPrim(Prim.add)) != null) { // index is a sum
      if (ps[0].isBase()) { // left argument is a base
        MILProgram.report("rewrite: base addressing using " + ps[0]);
        args = Atom.ensureFreshArgs(args, orig);
        args[1] = ps[0];
        args[3] = ps[1];
        fi = args[3].lookupFact(facts);
      } else if (ps[1].isBase()) { // right argument is a base
        MILProgram.report("rewrite: base addressing using " + ps[1]);
        args = Atom.ensureFreshArgs(args, orig);
        args[1] = ps[1];
        args[3] = ps[0];
        fi = args[3].lookupFact(facts);
      }
    }

    if (args[4].isZero()) { // no multiplier set
      // Try to split the offset:
      if (args[3].isZero() // no index set
          && fo != null
          && (ps = fo.isPrim(Prim.add)) != null) { // offset is a sum
        // 3)  ((S, b, o+i, _, _)) ---> ((S, b, o, i, _))
        MILProgram.report("rewrite: address is sum " + ps[0] + "+" + ps[1]);
        args = Atom.ensureFreshArgs(args, orig);
        args[2] = ps[0];
        fo = args[2].lookupFact(facts);
        args[3] = ps[1];
        fi = args[3].lookupFact(facts);
      }

      // Look for opportunities for scaling:
      if (fo != null
          && (ps = fo.isPrim(Prim.mul)) != null // offset is a multiply
          && ps[1].isMultiplier()) { // by a valid multiplier
        // 4)  ((S, b, i*M, o, _)) ---> ((S, b, o, i, M))
        MILProgram.report("rewrite: scaled address multiplier " + ps[1]);
        args = Atom.ensureFreshArgs(args, orig);
        args[2] = args[3]; // move original index into offset position
        args[3] = ps[0]; // set new index
        args[4] = ps[1]; // set multiplier
      } else if (fi != null
          && (ps = fi.isPrim(Prim.mul)) != null // index is a multiply
          && ps[1].isMultiplier()) { // by a valid multiplier
        // 5)  ((S, b, o, i*M, _)) ---> ((S, b, o, i, M))
        MILProgram.report("rewrite: scaled address multiplier " + ps[1]);
        args = Atom.ensureFreshArgs(args, orig);
        args[3] = ps[0]; // set the index
        args[4] = ps[1]; // set the multiplier
      }
    }

    return args;
  }

  /**
   * Compute an integer summary for a fragment of MIL code with the key property that alpha
   * equivalent program fragments have the same summary value.
   */
  int summary() {
    return summary(p.summary()) * 33 + 1;
  }

  /** Test to see if two Tail expressions are alpha equivalent. */
  boolean alphaTail(Temps thisvars, Tail that, Temps thatvars) {
    return that.alphaPrimCall(thatvars, this, thisvars);
  }

  /** Test two items for alpha equivalence. */
  boolean alphaPrimCall(Temps thisvars, PrimCall that, Temps thatvars) {
    return this.p == that.p && this.alphaArgs(thisvars, that, thatvars);
  }

  void collect(TypeSet set) {
    type = type.canonBlockType(set);
    p = p.canonPrim(set);
    Atom.collect(args, set);
  }

  /** Generate a specialized version of this Call. */
  Call specializeCall(MILSpec spec, TVarSubst s, SpecEnv env) {
    return new PrimCall(p.specializePrim(spec, type, s));
  }

  Tail repTransform(RepTypeSet set, RepEnv env) {
    return p.repTransformPrim(set, Atom.repArgs(set, env, args));
  }

  /** Generate LLVM code to execute this Tail with NO result from the right hand side of a Bind. */
  llvm.Code toLLVM(TypeMap tm, VarMap vm, TempSubst s, llvm.Code c) {
    return p.toLLVM(tm, vm, s, args, c);
  }

  /**
   * Generate LLVM code to execute this Tail and return a result from the right hand side of a Bind.
   */
  llvm.Code toLLVM(TypeMap tm, VarMap vm, TempSubst s, llvm.Local lhs, llvm.Code c) {
    return p.toLLVM(tm, vm, s, args, lhs, c);
  }
}