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
import core.*;

class CallLabel extends Label {

  private Block b;

  /** Default constructor. */
  CallLabel(Block b) {
    this.b = b;
  }

  /** Return a string label that can be used to identify this node. */
  String label() {
    return "c" + num;
  }

  /** Return a string with the options (e.g., fillcolor) for displaying this CFG node. */
  String dotAttrs() {
    return "style=filled, fillcolor=palegreen";
  }

  /** Find the CFG successors for this Label. */
  void findSuccs(CFG cfg) {
    succs = Label.noLabels;
  }

  TempSubst toLLVM(TypeMap tm, VarMap vm, TempSubst s) {
    if (preds == null || preds.next != null) {
      debug.Internal.error("CallLabel should have a unique predecessor");
    }
    llvm.Value[] vals = Atom.toLLVM(tm, vm, s, preds.args);
    llvm.Type rt = b.retType(tm);
    if (rt == llvm.Type.vd) { // use CallVoid if block does not produce a value
      code = new llvm.CallVoid(tm.globalFor(b), vals, new llvm.RetVoid());
    } else { // otherwise use Call
      llvm.Local v = vm.reg(rt); // and allocate a register to hold the result
      code = new llvm.Op(v, new llvm.Call(v.getType(), tm.globalFor(b), vals), new llvm.Ret(v));
    }
    return s;
  }
}