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
import compiler.Handler;
import core.*;
import java.math.BigInteger;

class BitConstExp extends AtomExp {

  private BigInteger n;

  private int w;

  /** Default constructor. */
  BitConstExp(BigInteger n, int w) {
    this.n = n;
    this.w = w;
  }

  /**
   * Perform scope analysis on this AtomExp, checking that any referenced identifier is in scope,
   * and returning a corresponding MIL Atom.
   */
  Atom inScopeOf(Handler handler, MILEnv milenv, TempEnv tenv) {
    return new BitConst(n, w);
  }
}