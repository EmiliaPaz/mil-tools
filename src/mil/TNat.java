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
import java.math.BigInteger;

public class TNat extends TLit {

  private BigInteger num;

  /** Default constructor. */
  public TNat(BigInteger num) {
    this.num = num;
  }

  public TNat(long w) {
    this(BigInteger.valueOf(w));
  }

  /**
   * Test to determine whether this type is alpha equivalent to another type, by checking to see if
   * the two type skeletons are equal. (Assumes that TGen generics have been allocated in the same
   * order in both inputs.)
   */
  boolean alphaType(Type that) {
    return that.alphaTNat(this);
  }

  /** Test to determine whether this type is equal to a given TNat. */
  boolean alphaTNat(TNat that) {
    return this.num.equals(that.num);
  }

  /**
   * Write this type to the specified writer, in a context with the specified precedence and number
   * of arguments.
   */
  void write(TypeWriter tw, int prec, int args) {
    tw.write(num.toString()); // TODO: output using bigToString/suffixes?
  }

  /** Test to determine whether this type is equal to a specified type literal. */
  boolean sameTLit(Type[] thisenv, TLit t) {
    return t.alphaTNat(this);
  }

  /**
   * Return the kind of this type. We assume here that the type is already known to be kind correct,
   * so the intent here is just to return the kind of the type as quickly as possible (i.e., with
   * minimal traversal of the type data structure), and not to (re)check that the type is kind
   * correct.
   */
  Kind calcKind(Type[] thisenv) {
    return KAtom.NAT;
  }

  void unifyTLit(Type[] thisenv, TLit t) throws UnifyException {
    if (!t.alphaTNat(this)) {
      throw new TypeMismatchException(t, null, this, thisenv);
    }
  }

  /**
   * Simplify this natural number type, using the specified type environment if needed, returning
   * either an unbound TVar, or else a TNat literal. TODO: This could be used more generally as a
   * way to eliminate all TGen, TInd, bound TVar, or Synonym nodes at the root of any type, not just
   * natural number types ... Suggest rewriting description and renaming method to reflect that ...
   * (and testing too ...)
   */
  public Type simplifyNatType(Type[] tenv) {
    return this;
  }

  /**
   * Return the number associated with this type if it is a natural number type, or else return
   * null.
   */
  public BigInteger getNat() {
    return num;
  }

  /**
   * Find a canonical version of this type in the given set, using the specified environment to
   * interpret TGens, and assuming that we have already pushed a certain number of args for this
   * type on the stack.
   */
  Type canonType(Type[] env, TypeSet set, int args) {
    return set.canonLit(num, this, args);
  }

  /**
   * Return the representation for a value of type Bit n, assuming that this object is the TNat for
   * n.
   */
  Type[] bitvectorRep() {
    return Type.repBits(num.intValue());
  }
}