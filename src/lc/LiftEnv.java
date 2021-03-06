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
import java.util.HashMap;
import mil.*;

public class LiftEnv {

  private HashMap<Binding, Lifting> table = new HashMap();

  public void dump() {
    System.out.println("LiftEnv ----------------------------");
    for (Binding b : table.keySet()) {
      System.out.print(b.getId() + " ---> ");
      table.get(b).dump();
      System.out.println();
    }
    System.out.println("End of LiftEnv ---------------------");
  }

  /**
   * Add a lifting that can be used to map occurrences of the variable v to an application of the
   * top-level function top to the specified list of extra variables.
   */
  void addLifting(Binding b, TopLevel topLevel, DefVar[] xvs) {
    table.put(b, new Lifting(topLevel, xvs));
  }

  /** Look for a lifting corresponding to the specified variable v. */
  Lifting findLifting(Binding b) {
    return table.get(b);
  }

  private TopBindings lifted = null;

  public TopBindings getLifted() {
    return lifted;
  }

  /**
   * Add the given list of TopBindings to the list of lifted bindings. This process is implemented
   * using destructive updates, so the input list should not be used again after this call.
   */
  public void addTopBindings(TopBindings tbs) {
    while (tbs != null) {
      TopBindings tmp = tbs;
      tbs = tbs.next;
      tmp.next = lifted;
      lifted = tmp;
    }
  }

  /**
   * Calculate the list of extra variables that are required for an SCC with the given list of free
   * variables.
   */
  DefVars extraVars(DefVars fvs) {
    DefVars xvs = null;
    for (; fvs != null; fvs = fvs.next) {
      Lifting l = table.get(fvs.head);
      xvs = (l == null) ? fvs.head.add(xvs) : l.addLiftedArgs(xvs);
    }
    return xvs;
  }

  void liftBindings(Bindings bindings, DefVar[] xvs) {
    // Step 1: Create a list of top-level bindings and add corresponding entries to the lifting
    // environment
    TopBindings tbs = null;
    for (; bindings != null; bindings = bindings.next) {
      tbs = new TopBindings(bindings.head.liftToTop(xvs, this), tbs);
    }
    // Step 2: Update each of the bodies by adding new parameters and lifting the right hand sides
    for (TopBindings ts = tbs; ts != null; ts = ts.next) {
      ts.head.liftBinding(this); // applying lifting to the right hand side
      ts.head.bindExtras(xvs); // and update the binding with extra parameters
    }
    this.addTopBindings(tbs);
  }
}
