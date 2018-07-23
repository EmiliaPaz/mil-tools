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
package core;

import compiler.*;
import java.math.BigInteger;
import lc.Env;
import lc.LiftEnv;
import mil.*;

public class StructDefn extends TyconDefn {

  TypeExp sizeExp;

  private StructRegionExp[] regexps;

  /** Default constructor. */
  public StructDefn(Position pos, String id, TypeExp sizeExp, StructRegionExp[] regexps) {
    super(pos, id);
    this.sizeExp = sizeExp;
    this.regexps = regexps;
  }

  private StructName sn;

  /**
   * Return the Tycon associated with this definition, if any. TODO: this method is only used in one
   * place, and it's awkward ... look for opportunities to rewrite
   */
  public Tycon getTycon() {
    return sn;
  }

  public void introduceTycons(Handler handler, TyconEnv env) {
    env.add(sn = new StructName(pos, id, KAtom.AREA));
  }

  /**
   * Determine the list of type definitions (a sublist of defns) that this particular definition
   * depends on.
   */
  public void scopeTycons(Handler handler, CoreDefns defns, TyconEnv env) {
    CoreDefns depends = null;
    if (sizeExp != null) {
      depends = sizeExp.scopeTycons(handler, null, env, defns, depends);
    }
    for (int i = 0; i < regexps.length; i++) {
      depends = regexps[i].scopeTycons(handler, null, env, defns, depends);
    }
    calls(depends);
  }

  public void kindInfer(Handler handler) {
    if (sizeExp != null) {
      sizeExp.checkKind(handler, KAtom.NAT);
    }
    for (int i = 0; i < regexps.length; i++) {
      regexps[i].kindInfer(handler);
    }
  }

  public void fixKinds() {
    sn.fixKinds();
  }

  /** Initialize size information for this definition, if appropriate. */
  void initSizes(Handler handler) {
    try { // TODO: merge this code with the above (only difference is in names sn and bn)
      sn.setByteSize((sizeExp == null) ? new TVar(Tyvar.nat) : sizeExp.toType(null));
    } catch (Failure f) {
      handler.report(f);
    }
  }

  /**
   * Initialize linear equation to calculate size information for this definition, if appropriate.
   */
  public LinearEqns initEqns(Handler handler, LinearEqns eqns) {
    try {
      eqns = new LinearEqns(this.initEqn(sn.byteSize(), sn), eqns);
    } catch (Failure f) {
      handler.report(f);
    }
    return eqns;
  }

  LinearEqn initEqn(Type size, Object hint) throws Failure {
    LinearEqn eqn = new LinearEqn(pos); // Create an empty equation
    eqn.addRhsTerm(size, hint); // Add the size on the right hand side
    for (int i = 0; i < regexps.length; i++) { // Add terms for the other regions
      regexps[i].addTermTo(eqn);
    }
    // ! System.out.println("Equation for " + id + " is " + eqn);
    return eqn;
  }

  void checkSizes() throws Failure {
    // Check that we have computed a valid ByteSize for this structure:
    Type size = sn.byteSize().simplifyNatType(null);
    BigInteger nat = size.getNat();
    if (nat == null) {
      throw new ByteSizeNotDeterminedFailure(pos, sn);
    }
    sn.setByteSize(size); // save simplified size value
    debug.Log.println("ByteSize(" + sn + ") = " + nat);

    // Validate the types and calculate offsets for each region:
    int offset = 0;
    for (int i = 0; i < regexps.length; i++) {
      offset = regexps[i].calcOffset(offset);
    }

    // Calculate the number of fields for this structure:
    int n = 0;
    for (int i = 0; i < regexps.length; i++) {
      n += regexps[i].numFields();
    }

    // Build an array of fields for this structure:
    StructField[] fields = new StructField[n];
    int next = 0;
    for (int i = 0; i < regexps.length; i++) {
      next = regexps[i].collectFields(fields, next);
    }
    sn.setFields(fields);
  }

  /** Calculate types for each of the values that are introduced by this definition. */
  public void calcCfuns(Handler handler) {
    sn.debugDump();
  }

  public void addToMILEnv(Handler handler, MILEnv milenv) {
    // TODO: fill this in!
  }

  public void inScopeOf(Handler handler, MILEnv milenv, Env env) throws Failure {
    for (int i = 0; i < regexps.length; i++) {
      regexps[i].inScopeOf(handler, milenv, env);
    }
  }

  public void inferTypes(Handler handler) throws Failure {
    for (int i = 0; i < regexps.length; i++) {
      regexps[i].inferTypes(handler);
    }
  }

  public void lift(LiftEnv lenv) {
    for (int i = 0; i < regexps.length; i++) {
      regexps[i].lift(lenv);
    }
  }
}