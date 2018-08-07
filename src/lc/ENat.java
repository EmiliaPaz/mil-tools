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
package lc;

import compiler.*;
import core.*;
import debug.Screen;
import java.math.BigInteger;
import mil.*;

class ENat extends ELit {

  private BigInteger nat;

  /** Default constructor. */
  ENat(Position pos, BigInteger nat) {
    super(pos);
    this.nat = nat;
  }

  void display(Screen s) {
    s.print(nat.toString());
  }

  /**
   * Print an indented description of this abstract syntax node, including a name for the node
   * itself at the specified level of indentation, plus more deeply indented descriptions of any
   * child nodes.
   */
  void indent(IndentOutput out, int n) {
    indent(out, n, "ENat: " + nat);
  }

  /**
   * Infer a type for this expression, using information in the tis parameter to track the set of
   * type variables that appear in an assumption.
   */
  Type inferType(TVarsInScope tis) throws Failure { // nat
    // TODO: check that numeric value will fit in a word ...
    return type = DataName.word.asType();
  }

  /** Compile an expression into an Atom. */
  Code compAtom(final CGEnv env, final AtomCont ka) {
    return ka.with(new IntConst(nat.intValue()));
  }

  /** Compile an expression into a Tail. */
  Code compTail(final CGEnv env, final Block abort, final TailCont kt) { //  integer literal
    return kt.with(new Return(new IntConst(nat.intValue())));
  }
}
