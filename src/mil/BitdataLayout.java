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
import compiler.Position;
import core.*;
import java.math.BigInteger;

/** Represents the type constructor for a specific bitdata layout. */
public class BitdataLayout extends DataName {

  /** The bitdata type for this layout. */
  private BitdataName bn;

  /** The tagbits for this layout. */
  private BigInteger tagbits;

  /** The list of fields within this bitdata value. */
  private BitdataField[] fields;

  /** A description of all valid bit patterns for this layout. */
  private obdd.Pat pat;

  /** Default constructor. */
  public BitdataLayout(
      Position pos,
      String id,
      Kind kind,
      int arity,
      BitdataName bn,
      BigInteger tagbits,
      BitdataField[] fields,
      obdd.Pat pat) {
    super(pos, id, kind, arity);
    this.bn = bn;
    this.tagbits = tagbits;
    this.fields = fields;
    this.pat = pat;

    Type[] stored = new Type[fields.length];
    for (int i = 0; i < fields.length; i++) {
      stored[i] = fields[i].getType();
    }
    // TODO: use a different id?
    cfuns = new Cfun[] {new Cfun(pos, id, this, 0, new AllocType(stored, this.asType()))};
  }

  public BitdataLayout(
      Position pos,
      String id,
      BitdataName bn,
      BigInteger tagbits,
      BitdataField[] fields,
      obdd.Pat pat) {
    this(pos, id, KAtom.STAR, 0, bn, tagbits, fields, pat);
  }

  public int getWidth() {
    return pat.getWidth();
  }

  public Type bitSize() {
    return bn.bitSize();
  }

  public BitdataField[] getFields() {
    return fields;
  }

  public boolean isNullary() {
    return fields.length == 0;
  }

  private obdd.MaskTestPat maskTest;

  public void setMaskTest(obdd.MaskTestPat maskTest) {
    this.maskTest = maskTest;
  }

  /** Return the bit pattern for this object. */
  public obdd.Pat getPat() {
    return pat;
  }

  /** Return the constructor function for this layout. */
  public Cfun getCfun() {
    return cfuns[0];
  }

  public void debugDump() {
    debug.Log.println(
        "Constructor "
            + id
            + ": width "
            + pat.getWidth()
            + " ["
            + Type.numWords(pat.getWidth())
            + " word(s)], tagbits 0x"
            + tagbits.toString(16)
            + " and "
            + fields.length
            + " field(s):");
    for (int i = 0; i < fields.length; i++) {
      debug.Log.print("  " + i + ": ");
      fields[i].debugDump();
    }
  }

  /**
   * Find the Bitdata Layout associated with values of this type, if there is one, or else return
   * null. TODO: perhaps this code should be colocated with bitdataName()?
   */
  public BitdataLayout bitdataLayout() {
    return this;
  }

  DataName canonDataName(TypeSet set) {
    // We do not need to calculate a new version of the type in these cases because we know that
    // none of the
    // Cfun types will change (they are all of the form T.Lab -> T).
    return this;
  }

  /**
   * Return true if this is a newtype constructor (i.e., a single argument constructor function for
   * a nonrecursive type that only has one constructor).
   */
  public boolean isNewtype() { // Don't treat bitdata types as newtypes
    return false;
  }

  Type specializeTycon(MILSpec spec, Type inst) {
    return inst;
  }

  DataName specializeDataName(MILSpec spec, Type inst) {
    // Do not specialize bitdata types
    return this;
  }

  /** Return the representation vector for values of this type. */
  Type[] repCalc() {
    return bn.repCalc();
  }

  Code repTransformAssert(RepTypeSet set, Cfun cf, Atom a, Code c) {
    return c;
  }

  Block maskTestBlock() {
    return maskTestBlock;
  }

  Tail repTransformDataAlloc(RepTypeSet set, Cfun cf, Atom[] args) {
    return new BlockCall(constructorBlock, args);
  }

  Tail repTransformSel(RepTypeSet set, RepEnv env, Cfun cf, int n, Atom a) {
    return fields[n].repTransformSel(set, env, a);
  }

  Code repTransformSel(RepTypeSet set, RepEnv env, Temp[] vs, Cfun cf, int n, Atom a, Code c) {
    return new Bind(vs, repTransformSel(set, env, cf, n, a), c);
  }

  private Block constructorBlock;

  /** Generate code for a constructor for this layout. */
  public void generateConstructor(Cfun cf) {
    int total = getWidth(); // number of bits in output
    int n = Type.numWords(total); // number of words in output
    Temp[][] args = new Temp[fields.length][]; // args for each input
    Temp[] ws = Temp.makeTemps(n);
    Code c = new Done(new Return(Temp.clone(ws)));
    // (Clone ws so that we are free to change its elements without modifying the code in c.)

    // Add code to set each field (assuming that each field is already zero-ed out):
    for (int k = 0; k < fields.length; k++) {
      args[k] = Temp.makeTemps(Type.numWords(fields[k].getWidth()));
      c = fields[k].genUpdateZeroedField(total, ws, args[k], c);
    }

    // Set initial value for tagbits:
    c = initialize(total, ws, tagbits, c);

    // Build a chain of closure definitions to capture constructor arguments:
    Temp[] params = Temp.concat(args);
    constructorBlock = new Block(cf.getPos(), "construct_" + cf, params, c);
  }

  /**
   * Prepend the given code sequence with an initializer that sets the Temps in ws to the given bits
   * value.
   */
  static Code initialize(int total, Temp[] ws, BigInteger bits, Code code) {
    return new Bind(
        ws,
        new Return((total == 1) ? new Atom[] {Atom.asFlag(bits)} : Atom.asWords(bits, ws.length)),
        code);
  }

  static Block generateBitConcat(int u, int v) { // :: Bit u -> Bit v -> Bit (u+v)
    Temp[] as = Temp.makeTemps(Type.numWords(u)); // as :: Bit u
    Temp[] bs = Temp.makeTemps(Type.numWords(v)); // bs :: Bit v
    Temp[] ws = Temp.makeTemps(Type.numWords(u + v));
    return new Block(
        BuiltinPosition.position,
        Temp.append(as, bs),
        initialize(
            u + v,
            ws,
            BigInteger.ZERO,
            genUpdateZeroedBitField(
                0,
                v,
                u + v,
                ws,
                bs,
                genUpdateZeroedBitField(
                    v, u, u + v, ws, as, new Done(new Return(Temp.clone(ws)))))));
  }

  static Code genUpdateZeroedBitField(
      int offset, int width, int total, Temp[] ws, Temp[] as, Code code) {
    return width == 1
        ? BitdataField.genUpdateZeroedFieldBit(offset, width, total, ws, as, code)
        : BitdataField.genUpdateZeroedFieldLo(offset, width, total, ws, as, code);
  }

  private Block maskTestBlock;

  /**
   * Generate the implementation of a mask test predicate for values of this layout using the
   * previously calculated MaskTestPat value. The generated implementation allows the mask and bits
   * to be spread across multiple words. The entry block that is returned has one Word argument for
   * each entry in the mask (and bits) array. The generated code tests one Word at a time using
   * either a suitable call to the appropriate bmaskeq or bmaskneq block, each of which are defined
   * below.
   */
  void generateMaskTest(Cfun cf) {
    int total = getWidth(); // number of bits in output
    BigInteger maskNat = maskTest.getMask();
    BigInteger bitsNat = maskTest.getBits();
    boolean eq = maskTest.getOp();
    if (total == 1) { // special case for single bit types
      Temp[] vs = Temp.makeTemps(1);
      Tail t =
          maskNat.testBit(0) // nonzero mask ==> depends on vs[0]
              ? ((eq != bitsNat.testBit(0)) ? new Return(vs) : Prim.bnot.withArgs(vs[0]))
              : new Return(
                  FlagConst.fromBool(eq == bitsNat.testBit(0))); // zero mask ==> const function
      maskTestBlock = new Block(cf.getPos(), "masktest_" + cf, vs, new Done(t));
    } else {
      int n = Type.numWords(total); // number of words in output
      Atom[] mask = Atom.asWords(maskNat, n);
      Atom[] bits = Atom.asWords(bitsNat, n);
      maskTestBlock = eq ? bfalse : btrue; // base case, if no data to compare

      for (int i = 1; i <= n; i++) {
        Temp[] vs = Temp.makeTemps(i); // i parameters
        Atom[] as = new Atom[] {vs[0], mask[n - i], bits[n - i]};
        Code c;
        if (i == 1) {
          // This branch is used when we are testing the last word of the input, so the final result
          // will be
          // determined exclusively by the result of this comparison.
          c = new Done(new BlockCall(eq ? bmaskneq : bmaskeq, as));
        } else {
          // This branch is used when there are still other words to compare.  Each of these tests
          // uses a call to
          // bmaskeq.  If the result is false, then we can return immediately.  If the result is
          // true, then we will
          // need to consider subsequent words anyway (either to confirm that all bits or equal, or
          // because we are
          // still looking for a place where the input differs from what is required).
          Temp t = new Temp();
          c =
              new Bind(
                  t,
                  new BlockCall(bmaskeq, as),
                  new If(
                      t,
                      new BlockCall(maskTestBlock, Temp.tail(vs)),
                      new BlockCall(eq ? btrue : bfalse, Atom.noAtoms)));
        }
        maskTestBlock = new Block(cf.getPos(), vs, c);
      }
    }
  }

  public static Block btrue = atomBlock("btrue", FlagConst.True);

  public static Block bfalse = atomBlock("bfalse", FlagConst.False);

  public static Block bmaskeq = masktestBlock("bmaskeq", Prim.eq);

  public static Block bmaskneq = masktestBlock("bmaskneq", Prim.neq);

  /**
   * Make a block of the following form that immediately returns the atom a, which could be an
   * IntConst or a Top, but not a Temp (because that would be out of scope). b :: [] >>= [t] b[] =
   * return a
   */
  static Block atomBlock(String name, Atom a) {
    return new Block(BuiltinPosition.position, name, Temp.noTemps, new Done(new Return(a)));
  }

  /**
   * Make a block of the following form for implementing a single word masktest predicate with mask
   * m, target t, and equality/inequality test p: b :: [Word, Word, Word] >>= [Flag] b[v, m, t] = w
   * <- and((v, m)); p((w,t))
   */
  static Block masktestBlock(String name, Prim p) {
    Temp[] params = Temp.makeTemps(3);
    Temp w = new Temp();
    return new Block(
        BuiltinPosition.position,
        name,
        params,
        new Bind(w, Prim.and.withArgs(params[0], params[1]), new Done(p.withArgs(w, params[2]))));
  }

  void calculateBitdataBlocks(Cfun cf) {
    generateConstructor(cf);
    generateMaskTest(cf);
    for (int i = 0; i < fields.length; i++) {
      fields[i].calculateBitdataBlocks(cf, this);
    }
  }

  void display(Cfun cf) {
    MILProgram prog = new MILProgram();
    addToProg(prog);
    try {
      System.out.println("bitdata layout ----------------");
      prog.shake();
      prog.dump();
      System.out.println("Running type checker:");
      prog.typeChecking(new SimpleHandler());
      prog.dump();
      System.out.println("Running optimizer:");
      prog.optimize();
      prog.typeChecking(new SimpleHandler());
      prog.dump();
      debugDump();
    } catch (Exception e) {
      System.out.println("Exception occurred: " + e);
      e.printStackTrace();
    }
    System.out.println("done --------------------------");
  }

  void addToProg(MILProgram prog) {
    prog.addEntry(constructorBlock);
    prog.addEntry(maskTestBlock);
    for (int i = 0; i < fields.length; i++) {
      fields[i].addToProg(prog);
    }
  }

  public static void main(String[] args) {
    if (args.length == 2) {
      try {
        int u = Integer.parseInt(args[0]);
        int v = Integer.parseInt(args[1]);

        MILProgram prog = new MILProgram();
        prog.addEntry(BitdataLayout.generateBitConcat(u, v));

        prog.shake();
        prog.dump();
        System.out.println("Running type checker:");
        prog.typeChecking(new SimpleHandler());
        prog.dump();
        System.out.println("Running optimizer:");
        prog.optimize();
        prog.typeChecking(new SimpleHandler());
        prog.dump();
        System.out.println("done --------------------------");
        return;
      } catch (Exception e) {
        System.out.println("Exception occurred: " + e);
        e.printStackTrace();
      }
    }
    System.out.println("usage: java -cp bin mil.BitdataLayout u v");
  }
}
