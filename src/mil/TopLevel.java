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
import compiler.Handler;
import compiler.Position;
import core.*;
import java.io.PrintWriter;

public class TopLevel extends TopDefn {

  private TopLhs[] lhs;

  protected Tail tail;

  /** Default constructor. */
  public TopLevel(Position pos, TopLhs[] lhs, Tail tail) {
    super(pos);
    this.lhs = lhs;
    this.tail = tail;
  }

  public TopLevel(Position pos, TopLhs l, Tail tail) {
    this(pos, new TopLhs[] {l}, tail);
  }

  public TopLevel(Position pos, String id, Tail tail) {
    this(pos, new TopLhs(id), tail);
  }

  /** Set the value associated with a top-level definition. */
  public void setTail(Tail tail) {
    if (this.tail != null) {
      debug.Internal.error("TopLevel tail has already been set");
    }
    this.tail = tail;
  }

  private Type defining;

  private Scheme declared;

  /**
   * Return references to all components of this top level definition in an array of
   * atoms/arguments.
   */
  Atom[] tops() {
    Atom[] as = new Atom[lhs.length];
    for (int i = 0; i < lhs.length; i++) {
      as[i] = new TopDef(this, i);
    }
    return as;
  }

  /** Get the declared type, or null if no type has been set. */
  public Scheme getDeclared() {
    return declared;
  }

  /** Set the declared type. */
  public void setDeclared(Scheme declared) {
    this.declared = declared;
  }

  public Scheme getDeclared(int i) {
    return lhs[i].getDeclared();
  }

  public void setDeclared(int i, Scheme scheme) {
    lhs[i].setDeclared(scheme);
  }

  public String getId(int i) {
    return lhs[i].getId();
  }

  public String toString() {
    if (lhs.length == 1) {
      return lhs[0].getId();
    } else {
      StringBuilder buf = new StringBuilder("[");
      if (lhs.length > 0) {
        buf.append(lhs[0].getId());
        for (int i = 1; i < lhs.length; i++) {
          buf.append(",");
          buf.append(lhs[i].getId());
        }
      }
      buf.append("]");
      return buf.toString();
    }
  }

  /** Find the list of Defns that this Defn depends on. */
  public Defns dependencies() {
    return tail.dependencies(null);
  }

  void displayDefn(PrintWriter out) {
    for (int i = 0; i < lhs.length; i++) {
      lhs[i].displayDefn(out);
    }
    out.print(toString());
    out.print(" <-");
    out.println();
    Code.indent(out);
    tail.displayln(out);
  }

  Type instantiate(int i) {
    return lhs[i].instantiate();
  }

  /**
   * Set the initial type for this definition by instantiating the declared type, if present, or
   * using type variables to create a suitable skeleton. Also sets the types of bound variables.
   */
  void setInitialType() throws Failure {
    Type[] types = new Type[lhs.length];
    for (int i = 0; i < lhs.length; i++) {
      types[i] = lhs[i].setInitialType();
    }
    defining = Type.tuple(types);
  }

  /** Type check the body of this definition. */
  void checkBody(Handler handler) throws Failure {
    try {
      checkBody(pos);
    } catch (Failure f) {
      // We can recover from a type error in this definition (at least for long enough to type
      // check other definitions) if the types are all declared (and there is a handler).
      if (allTypesDeclared() && handler != null) {
        handler.report(f); // Of course, we still need to report the failure
        defining = null; // Mark this definition as having failed to check
      } else {
        throw f;
      }
    }
  }

  void checkBody(Position pos) throws Failure {
    tail.inferType(pos).unify(pos, defining);
  }

  boolean allTypesDeclared() {
    // Check that there are declared types for all of the items defined here:
    for (int i = 0; i < lhs.length; i++) {
      if (!lhs[i].allTypesDeclared()) {
        return false;
      }
    }
    return true;
  }

  /** Lists the generic type variables for this definition. */
  protected TVar[] generics = TVar.noTVars;

  /** Produce a printable description of the generic variables for this definition. */
  public String showGenerics() {
    return TVar.show(generics);
  }

  /**
   * Calculate a generalized type for this binding, adding universal quantifiers for any unbound
   * type variable in the inferred type. (There are no "fixed" type variables here because all mil
   * definitions are at the top level.)
   */
  void generalizeType(Handler handler) throws Failure {
    if (defining != null) {
      for (int i = 0; i < lhs.length; i++) {
        lhs[i].generalizeType(handler);
      }
      TVars gens = defining.tvars();
      generics = TVar.generics(gens, null);
      // !   debug.Log.println("generics: " + showGenerics());
      declared = defining.generalize(generics);
      // !   debug.Log.println("TopLevel group inferred " + toString() + " :: " + declared);
      findAmbigTVars(handler, gens); // search for ambiguous type variables ...
    }
  }

  void findAmbigTVars(Handler handler, TVars gens) {}

  void extendAddrMap(HashAddrMap addrMap, int addr) {
    for (int i = 0; i < lhs.length; i++) {
      addrMap.addGlobalLabel(addr + i, lhs[i].getId());
    }
  }

  /** First pass code generation: produce code for top-level definitions. */
  void generateMain(Handler handler, MachineBuilder builder) {
    tail.generateCallCode(builder, 0); // Code to evaluate tail, starting frame at address 0
    int n = lhs.length;
    if (n > 0) {
      builder.setAddr(this, builder.saveGlobal(0));
      for (int i = 1; i < n; i++) {
        builder.saveGlobal(i);
      }
    }
  }

  /** Apply inlining. */
  public void inlining() {
    // !  System.out.println("==================================");
    // !  System.out.println("Going to try inlining on:");
    // !  displayDefn();
    // !  System.out.println();
    tail = tail.inlineTail();
    // !  System.out.println("And the result is:");
    // !  displayDefn();
    // !  System.out.println();
  }

  /**
   * Count the number of unused arguments for this definition using the current unusedArgs
   * information for any other items that it references.
   */
  int countUnusedArgs() {
    return 0;
  }

  /** Rewrite this program to remove unused arguments in block calls. */
  void removeUnusedArgs() {
    tail = tail.removeUnusedArgs();
  }

  public Tail lookupFact(TopLevel tl) {
    return tail.lookupFact(tl);
  }

  public void flow() {
    // TODO: Do something more here ... ?
    // The main purpose of this code is to run liveness analysis on the tail expression, which will
    // have the effect of shorting out top level atom references where possible.
    tail = tail.rewriteTail(null /* facts */);
    if (tail.liveness(null) != null) {
      debug.Internal.error("Tail expression in TopLevel has live variables");
    }
  }

  Atom shortTopLevel(Top d, int i) {
    return tail.shortTopLevel(d, i);
  }

  /**
   * Test to determine whether this Code/Tail value corresponds to a closure allocator, returning
   * either a ClosAlloc value, or else a null result.
   */
  ClosAlloc lookForClosAlloc() {
    return tail.lookForClosAlloc();
  }

  public Tail entersTopLevel(Atom[] iargs) {
    MILProgram.report("replacing " + toString() + " @ ... with block call");
    return this.toBlockCall().deriveWithEnter(iargs);
  }

  /**
   * Return a BlockCall for a TopLevel value, possibly introducing a new (zero argument) block to
   * hold the original code for the TopLevel value.
   */
  public BlockCall toBlockCall() {
    BlockCall bc = tail.isBlockCall();
    if (bc == null) {
      Block b = new Block(pos, Temp.noTemps, new Done(tail));
      bc = new BlockCall(b, Atom.noAtoms);
      tail = bc;
    }
    return bc;
  }

  /**
   * Test to determine whether this Tail value corresponds to a data allocator, returning either a
   * DataAlloc value, or else a null result.
   */
  DataAlloc lookForDataAlloc() {
    return tail.lookForDataAlloc();
  }

  /**
   * Determine whether this src argument is a value base (i.e., a numeric or global/primitive
   * constant) that is suitable for use in complex addressing modes.
   */
  boolean isBase() {
    return false;
  }

  /** Holds the most recently computed summary value for this item. */
  private int summary;

  void findIn(TopLevels[] topLevels) {
    summary = tail.summary();
    int idx = this.summary % topLevels.length;
    if (idx < 0) idx += topLevels.length;
    for (TopLevels ts = topLevels[idx]; ts != null; ts = ts.next) {
      if (ts.head.summary == this.summary
          && this.tail.alphaTail(null, ts.head.tail, null)
          && this.lhs.length == ts.head.lhs.length) {
        MILProgram.report("Identifying topdefn " + toString() + " with " + ts.head.toString());
        this.tail = new Return(ts.head.tops());
      }
    }
    topLevels[idx] = new TopLevels(this, topLevels[idx]);
  }

  /**
   * Compute a summary for this definition (if it is a block or top-level) and then look for a
   * previously encountered item with the same code in the given table. Return true if a duplicate
   * was found.
   */
  boolean summarizeDefns(Blocks[] blocks, TopLevels[] topLevels) {
    findIn(topLevels);
    return false;
  }

  void eliminateDuplicates() {
    tail.eliminateDuplicates();
  }

  void collect() {
    tail.collect();
  }

  void collect(TypeSet set) {
    for (int i = 0; i < lhs.length; i++) {
      lhs[i].collect(set);
    }
    tail.collect(set);
  }

  /** Apply constructor function simplifications to this program. */
  void cfunSimplify() {
    tail = tail.removeNewtypeCfun();
  }

  void printlnSig(PrintWriter out) {
    for (int i = 0; i < lhs.length; i++) {
      lhs[i].printlnSig(out);
    }
  }

  /** Test to determine if this is an appropriate definition to match the given type. */
  TopLevel isTopLevelOfType(Scheme inst) {
    return declared.alphaEquiv(inst) ? this : null;
  }

  TopLevel(TopLevel t) {
    this(t.pos, t.makeLhs(t.lhs.length), null);
  }

  private TopLhs[] makeLhs(int n) {
    TopLhs[] lhs = new TopLhs[n];
    for (int i = 0; i < n; i++) {
      lhs[i] = new TopLhs();
    }
    return lhs;
  }

  /** Fill in the body of this TopLevel as a specialized version of the given TopLevel. */
  void specialize(MILSpec spec, TopLevel torig) {
    // TODO: eliminate ugly cast in the following line
    TVarSubst s = torig.declared.specializingSubst(torig.generics, (Type) this.declared);
    for (int i = 0; i < lhs.length; i++) {
      this.lhs[i].specialize(torig.lhs[i], s);
    }
    debug.Log.println(
        "TopLevel specialize: "
            + torig
            + " :: "
            + torig.declared
            + "  ~~>  "
            + this
            + " :: "
            + this.declared
            + ", generics="
            + torig.showGenerics()
            + ", substitution="
            + s);
    this.tail = torig.tail.specializeTail(spec, s, null);
  }

  /**
   * Return a specialized version of this TopLevel in which the ith component of the lhs has the
   * given type.
   */
  TopLevel specializedTopLevel(MILSpec spec, Type inst, int i) {
    return spec.specializedTopLevel(this, defining.apply(lhs[i].specializingSubst(inst)));
  }

  /**
   * Generate a specialized version of an entry point. This requires a monomorphic definition (to
   * ensure that the required specialization is uniquely determined, and to allow the specialized
   * version to share the same name as the original.
   */
  Defn specializeEntry(MILSpec spec) throws Failure {
    if (declared.isQuantified()) {
      throw new PolymorphicEntrypointFailure("top-level", this);
    }
    TopLevel tl = spec.specializedTopLevel(this, declared);
    TopLhs.copyIds(tl.lhs, this.lhs); // use the same names as in the original program
    return tl;
  }

  /** Update all declared types with canonical versions. */
  void canonDeclared(MILSpec spec) {
    for (int i = 0; i < lhs.length; i++) {
      lhs[i].canonDeclared(spec);
    }
  }

  void topLevelrepTransform(RepTypeSet set) {
    // Is a change of representation required?
    Type[][] reps = TopLhs.reps(lhs);
    if (reps != null) {
      // Add an entry to the hash table:
      set.putTopLevelReps(this, reps);

      // Figure out how long the new lhs should be:
      int len = 0;
      for (int i = 0; i < reps.length; i++) {
        len += (reps[i] == null ? 1 : reps[i].length);
      }

      // Make the new lhs:
      TopLhs[] lhs = new TopLhs[len];
      int j = 0;
      for (int i = 0; i < reps.length; i++) {
        if (reps[i] == null) {
          lhs[j++] = this.lhs[i];
        } else {
          if (reps[i].length > 0) {
            Type[] ts = reps[i];
            for (int k = 0; k < ts.length; k++) {
              lhs[j] = new TopLhs();
              lhs[j++].setDeclared(ts[k]);
            }
          }
        }
      }
      this.lhs = lhs;
    }
  }

  void repTransform(Handler handler, RepTypeSet set) {
    tail = tail.repTransform(set, null);
    for (int i = 0; i < lhs.length; i++) {
      lhs[i].repTransform(handler, set);
    }
  }

  void setDeclared(Handler handler, Position pos, int i, Scheme scheme) {
    lhs[i].setDeclared(handler, pos, scheme);
  }

  /** Perform scope analysis on the definition for this top level value. */
  public void inScopeOf(Handler handler, MILEnv milenv, String[] args, CodeExp cexp)
      throws Failure {
    this.tail = cexp.toTail(handler, milenv, args);
  }

  void addExport(MILEnv exports) {
    for (int i = 0; i < lhs.length; i++) {
      lhs[i].addExport(exports, new TopDef(this, i));
    }
  }

  /**
   * Find the arguments that are needed to enter this definition. The arguments for each block or
   * closure are computed the first time that we visit the definition, but are returned directly for
   * each subsequent call.
   */
  Temp[] addArgs() throws Failure {
    // !     System.out.println("In TopLevel for " + toString());
    // Note: these definitions will be visited only once from the top-level
    // pass through the list of definitions in the program.
    Temps ts = tail.addArgs(null);
    if (ts != null) {
      debug.Internal.error(
          "Top-level definition of " + toString() + " has free arguments " + Temps.toString(ts));
    }
    return null;
  }

  void countCalls() {
    tail.countCalls();
  }

  /**
   * An array of llvm.Value objects (one for each left hand side) that stores the compile-time
   * values for this TopLevel. A null entry indicates that the values cannot be determined at
   * compile-time and must instead be computed and stored in variables when the program begins
   * execution.
   */
  private llvm.Value[] staticValue;

  /**
   * Return the static value for the ith component of this toplevel, or null if there is no known
   * static value.
   */
  public llvm.Value staticValue(int i) {
    return (staticValue != null && staticValue[i] != null) ? staticValue[i] : null;
  }

  /** Calculate a staticValue (which could be null) for each top level definition. */
  void calcStaticValues(TypeMap tm, llvm.Program prog) {
    staticValue = tail.staticValueCalc(tm, prog);
    // Add global variable definitions for any lhs components without a static value:
    for (int i = 0; i < lhs.length; i++) {
      if (staticValue == null || staticValue[i] == null) {
        prog.add(lhs[i].globalVarDefn(tm));
      }
    }
  }

  /**
   * Reset the static value field and return true if this is a toplevel definition, or return false
   * for any other form of definition.
   */
  boolean resetStaticValues() {
    staticValue = null;
    return true;
  }

  /**
   * Generate code (in reverse) to initialize each TopLevel (unless all of the components are
   * statically known). TODO: what if a TopLevel has an empty array of Lhs?
   */
  llvm.Code addRevInitCode(TypeMap tm, InitVarMap ivm, llvm.Code code) {
    if (staticValue == null || staticValue.length == 0) {
      return this.revInitCode(tm, ivm, code); // no static values
    } else {
      for (int i = 0; i < staticValue.length; i++) {
        if (staticValue[i] == null) {
          return this.revInitCode(tm, ivm, code); // some static values
        }
      }
      return code; // all components have static values, no new code required
    }
  }

  /**
   * Worker function for generateRevInitCode, called when we have established that the tail
   * expression for this TopLevel should be executed during program initialization.
   */
  private llvm.Code revInitCode(TypeMap tm, InitVarMap ivm, llvm.Code code) {
    Temp[] vs = new Temp[lhs.length];
    for (int i = 0; i < lhs.length; i++) {
      vs[i] = lhs[i].makeTemp();
    }
    code = llvm.Code.reverseOnto(tail.toLLVM(tm, ivm, null, vs, null), code);
    for (int i = 0; i < lhs.length; i++) {
      if (staticValue == null || staticValue[i] == null) {
        llvm.Local var = ivm.lookup(tm, vs[i]);
        ivm.mapGlobal(this, i, var);
        code = new llvm.Store(var, new llvm.Global(var.getType().ptr(), lhs[i].getId()), code);
      }
    }
    return code;
  }
}