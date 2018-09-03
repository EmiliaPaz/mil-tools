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
import compiler.BuiltinPosition;
import compiler.Failure;
import compiler.Handler;
import compiler.Position;
import core.*;

public abstract class Top extends Atom {

  protected Type type;

  /** Default constructor. */
  public Top(Type type) {
    this.type = type;
  }

  public abstract Top clone();

  /** Generate a printable description of this atom. */
  public String toString() {
    return getId();
  }

  public abstract String getId();

  public abstract Defn getDefn();

  boolean isStatic() {
    return true;
  }

  /**
   * Update the information that we have recorded about a given formal parameter to reflect the use
   * of this actual parameter. The input, orig, will be one of: - null, indicating that no previous
   * information has been found - a specific Const or Top, indicating that this single value was
   * used in all previous calls - a special value, top, indicating that multiple distinct values
   * have been encountered in previous calls.
   */
  Atom update(Atom orig) {
    return (orig == null || orig == this) ? this : Atom.top;
  }

  Atom isKnown() {
    return this;
  }

  void collect(TypeSet set) {
    if (type != null) {
      type = type.canonType(set);
    }
  }

  /** Return the representation vector for this Atom. */
  Type[] repCalc() {
    return type.repCalc();
  }

  public static final Top Unit =
      new TopDef(
          new TopLevel(BuiltinPosition.pos, new TopLhs(), new DataAlloc(Cfun.Unit).withArgs()), 0);

  public void setDeclared(Handler handler, Position pos, Scheme scheme) {
    handler.report(
        new Failure(
            pos, "Cannot use type signature; \"" + getId() + "\" is not a top level variable"));
  }

  /**
   * Determine whether this item is for a non-Unit, corresponding to a value that requires a
   * run-time representation in the generated LLVM.
   */
  boolean nonUnit() {
    return type.nonUnit();
  }

  public llvm.Value staticValue() {
    return null;
  }

  Type getType() {
    return type;
  }

  /** Calculate an LLVM Value corresponding to a given MIL argument. */
  llvm.Value toLLVMAtom(LLVMMap lm, VarMap vm) {
    return vm.lookupGlobal(lm, this);
  }
}
