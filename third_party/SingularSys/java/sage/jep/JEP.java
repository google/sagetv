/*****************************************************************************

Copyright 2015 The SageTV Authors. All Rights Reserved.

 JEP - Java Math Expression Parser 2.24
      December 30 2002
      (c) Copyright 2002, Nathan Funk
      See LICENSE.txt for license information.

 *****************************************************************************/

package sage.jep;

import java.io.*;
import java.util.*;
import sage.jep.function.*;

/**
 * The JEP class is the main interface with which the user should
 * interact. It contains all neccessary methods to parse and evaluate
 * expressions.
 * <p>
 * The most important methods are parseExpression(String), for parsing the
 * mathematical expression, and getValue() for obtaining the value of the
 * expression.
 * <p>
 * Visit <a href="http://www.singularsys.com/jep">http://www.singularsys.com/jep</a>
 * for the newest version of JEP, and complete documentation.
 *
 * @author Nathan Funk
 */
public final class JEP {

  /** Function Table */
  protected FunctionTable funTab;

  /** Error List */
  protected Vector errorList;

  /** The parser object */
  //private Parser parser;

  /** Node at the top of the parse tree */
  private CommandElement[] fastNodes;

  // If we're simply a constant, then store that so we can return it quickly
  private boolean fastResultSet;
  private Object fastResult;
  private String fastVarLookup;

  private boolean specialVarMatch;
  private boolean volatileVarMatch;
  private boolean volatileVarMatch2;

  private String assignmentVar;

  private String expression;

  public void setAssignmentVar(String x) { assignmentVar = x; }
  public String getAssignmentVar() { return assignmentVar; }

  public boolean isVolatileVarMatch() { return volatileVarMatch; }
  public void checkForVolatileVar(String expr, String var)
  {
    if (expr.indexOf(var) != -1)
      volatileVarMatch = true;
  }

  public boolean isVolatileVarMatch2() { return volatileVarMatch2; }
  public void checkForVolatileVar2(String expr, String var)
  {
    if (expr.indexOf(var) != -1)
      volatileVarMatch2 = true;
  }

  public boolean isSpecialVarMatch() { return specialVarMatch; }
  public void checkForSpecialVars(String expr, String[] varList)
  {
    for (int i = 0; i < varList.length; i++)
    {
      if (expr.indexOf(varList[i]) != -1)
      {
        specialVarMatch = true;
        return;
      }
    }
  }

  /**
   * Creates a new JEP instance with the default settings.
   * <p>
   * Traverse = false<br>
   * Allow undeclared variables = false<br>
   * Implicit multiplication = false<br>
   * Number Factory = DoubleNumberFactory
   */
  public JEP() {
    //		initFunTab();
    //		errorList = new Vector();
    //parser = new Parser((java.io.Reader)null);
  }

  /**
   * Creates a new FunctionTable object as funTab.
   */
  public void initFunTab() {
    //Init FunctionTable
    funTab = new FunctionTable();
  }

  public boolean isConstant()
  {
    return fastResultSet;
  }

  public Boolean getConstantBoolResult()
  {
    // If it triggers a focus listener then don't say its constant
    if (expression.indexOf("\"Focused\"") != -1 || expression.indexOf("\"FocusedChild\"") != -1)
      return null;
    // Returns null if this isn't going to be a fixed boolean evaluation; otherwise it returns a Boolean object that represents the result
    // The result can only be a fixed boolean if the last function is a logical operation or not
    if (fastNodes[fastNodes.length - 1].type == CommandElement.FUNC && (fastNodes[fastNodes.length - 1].pfmc instanceof sage.jep.function.Logical ||
        fastNodes[fastNodes.length - 1].pfmc instanceof sage.jep.function.Not))
    {
      // Run the execution for real; but replace all unknown values with null and known ones with their bool; if we end up with a bool at the end
      // then the result is fixed
      java.util.Stack stack = new java.util.Stack();
      for (int i = 0; i < fastNodes.length; i++)
      {
        if (fastNodes[i].type == CommandElement.CONST)
        {
          if (fastNodes[i].value instanceof Boolean)
            stack.push(fastNodes[i].value);
          else
            stack.push(null);
        }
        else if (fastNodes[i].type == CommandElement.VAR)
          stack.push(null);
        else // FUNC
        {
          if (fastNodes[i].pfmc instanceof sage.jep.function.Not)
          {
            Object last = stack.pop();
            if (last instanceof Boolean)
            {
              Boolean lb = (Boolean) last;
              stack.push(lb.booleanValue() ? Boolean.FALSE : Boolean.TRUE);
            }
            else
              stack.push(null);
          }
          else if (fastNodes[i].pfmc instanceof sage.jep.function.Logical)
          {
            Object v1 = stack.pop();
            Object v2 = stack.pop();
            if (((sage.jep.function.Logical) fastNodes[i].pfmc).isAnd())
            {
              if (((v1 instanceof Boolean) && !((Boolean) v1).booleanValue()) ||
                  ((v2 instanceof Boolean) && !((Boolean) v2).booleanValue()))
              {
                stack.push(Boolean.FALSE);
              }
              else
                stack.push(null);
            }
            else
            {
              if (((v1 instanceof Boolean) && ((Boolean) v1).booleanValue()) ||
                  ((v2 instanceof Boolean) && ((Boolean) v2).booleanValue()))
              {
                stack.push(Boolean.TRUE);
              }
              else
                stack.push(null);
            }
          }
          else
          {
            for (int j = 0; j < fastNodes[i].nParam; j++)
              stack.pop();
            stack.push(null);
          }
        }
      }
      return (Boolean)stack.pop();
    }
    return null;
  }

  public boolean referencesThisVar()
  {
    for (int i = 0; i < fastNodes.length; i++)
    {
      if (fastNodes[i].type == CommandElement.VAR && fastNodes[i].varName == null)
        return true;
    }
    return false;
  }

  /**
   * Parses the expression. If there are errors in the expression,
   * they are added to the <code>errorList</code> member.
   *
   * @param expression_in The input expression string
   */
  public void parseExpression(String expression_in) {
    Reader reader = new StringReader(expression = expression_in);
    ExpressionCompiler compiler = null;
    Parser parser = getParserFromPool();
    try {
      // try parsing
      errorList = sage.Pooler.getPooledVector();
      Node topNode = parser.parseStream(reader, this);

      // Compile it further
      compiler = getECFromPool();
      fastNodes = compiler.compile(topNode);
      if (fastNodes != null && fastNodes.length == 1)
      {
        if (fastNodes[0].type == CommandElement.CONST)
        {
          fastResultSet = true;
          fastResult = fastNodes[0].value;
        }
        else if (fastNodes[0].type == CommandElement.VAR)
        {
          fastVarLookup = fastNodes[0].varName;
        }
      }
    } catch (Throwable e) {
      // an exception was thrown, so there is no parse tree

      // check the type of error
      if (e instanceof ParseException) {
        // the ParseException object contains additional error
        // information
        errorList.addElement(((ParseException)e).getErrorInfo());
      } else {
        // if the exception was not a ParseException, it was most
        // likely a syntax error
        errorList.addElement("Syntax error");
      }
    }

    // FREE the parser!!!!
    returnParserToPool(parser);
    if (compiler != null)
      returnECToPool(compiler);
  }

  private static java.util.Vector pooledEVs = new java.util.Vector();
  private static int numEVsCreated = 0;
  private static EvaluatorVisitor getEVFromPool()
  {
    synchronized (pooledEVs)
    {
      if (!pooledEVs.isEmpty())
        return (EvaluatorVisitor) pooledEVs.remove(pooledEVs.size() - 1);
      else
      {
        if (sage.Sage.DBG) System.out.println("EVPoolSize=" + (++numEVsCreated));
        return new EvaluatorVisitor();
      }
    }
  }

  private static void returnEVToPool(EvaluatorVisitor ev)
  {
    pooledEVs.add(ev);
  }

  private static java.util.Vector pooledCEs = new java.util.Vector();
  private static int numCEsCreated = 0;
  private static CommandEvaluator fastCE = new CommandEvaluator();
  private static CommandEvaluator getCEFromPool()
  {
    synchronized (pooledCEs)
    {
      if (fastCE != null)
      {
        CommandEvaluator rv = fastCE;
        fastCE = null;
        return rv;
      }
      if (!pooledCEs.isEmpty())
        return (CommandEvaluator) pooledCEs.remove(pooledCEs.size() - 1);
      else
      {
        if (sage.Sage.DBG) System.out.println("CEPoolSize=" + (++numCEsCreated));
        return new CommandEvaluator();
      }
    }
  }

  private static void returnCEToPool(CommandEvaluator ev)
  {
    synchronized (pooledCEs)
    {
      if (fastCE == null)
      {
        fastCE = ev;
        return;
      }
      pooledCEs.add(ev);
    }
  }

  private static java.util.Vector pooledECs = new java.util.Vector();
  private static int numECsCreated = 0;
  private static ExpressionCompiler fastEC = new ExpressionCompiler();
  private static ExpressionCompiler getECFromPool()
  {
    synchronized (pooledECs)
    {
      if (fastEC != null)
      {
        ExpressionCompiler rv = fastEC;
        fastEC = null;
        return rv;
      }
      if (!pooledECs.isEmpty())
        return (ExpressionCompiler) pooledECs.remove(pooledECs.size() - 1);
      else
      {
        if (sage.Sage.DBG) System.out.println("ECPoolSize=" + (++numECsCreated));
        return new ExpressionCompiler();
      }
    }
  }

  private static void returnECToPool(ExpressionCompiler ev)
  {
    synchronized (pooledECs)
    {
      if (fastEC == null)
      {
        fastEC = ev;
        return;
      }
      pooledECs.add(ev);
    }
  }

  private static java.util.Vector pooledParsers = new java.util.Vector();
  private static int numParsersCreated = 0;
  private static Parser fastParser = new Parser((java.io.Reader)null);
  private static Parser getParserFromPool()
  {
    synchronized (pooledParsers)
    {
      if (fastParser != null)
      {
        Parser rv = fastParser;
        fastParser = null;
        return rv;
      }
      if (!pooledParsers.isEmpty())
        return (Parser) pooledParsers.remove(pooledParsers.size() - 1);
      else
      {
        if (sage.Sage.DBG) System.out.println("ParserPoolSize=" + (++numParsersCreated));
        return new Parser((java.io.Reader)null);
      }
    }
  }

  private static void returnParserToPool(Parser ev)
  {
    synchronized (pooledParsers)
    {
      if (fastParser == null)
      {
        fastParser = ev;
        return;
      }
      pooledParsers.add(ev);
    }
  }

  /**
   * Evaluates and returns the value of the expression as an object.
   * The EvaluatorVisitor member ev is used to do the evaluation procedure.
   * This method is useful when the type of the value is unknown, or
   * not important.
   * @return The calculated value of the expression if no errors occur.
   * Returns null otherwise.
   */
  public Object getValueAsObject(sage.Catbert.Context inSymTab, sage.ZPseudoComp inUIComp)
  {
    Object result;
    if (fastResultSet)
    {
      // These are always constants so we don't need to worry about it matching a special/volatile variable; even on the assignment
      if (assignmentVar != null)
      {
        inSymTab.set(assignmentVar, fastResult);
      }
      return fastResult;
    }
    if (inUIComp != null)
    {
      if (specialVarMatch)
        inUIComp.setPagingListenState(true);
      if (volatileVarMatch)
        inUIComp.setFocusListenState(sage.ZPseudoComp.PARENT_FOCUS_CHANGES);
      if (volatileVarMatch2)
        inUIComp.setFocusListenState(sage.ZPseudoComp.ALL_FOCUS_CHANGES);
    }
    if (fastVarLookup != null)
    {
      // The fastVar may be a special/volatile so we need to do the check above before we do this optimization
      result = inSymTab.get(fastVarLookup);
      if (assignmentVar != null)
      {
        inSymTab.set(assignmentVar, result);
      }
      return result;
    }

    if (fastNodes != null && !hasError())
    {
      CommandEvaluator ce = getCEFromPool();
      try
      {
        ce.stack.setUIMgr(inSymTab.getUIMgr());
        ce.stack.setUIComponent(inUIComp);
        result = ce.evaluate(fastNodes, inSymTab);

      } catch (Exception e) {
        if (sage.Sage.DBG) System.out.println("EXCEPTION in getValueAsObject:" + e + " for:" + expression);
        if (sage.Sage.DBG) e.printStackTrace();
        Throwable realT = e;
        if (realT instanceof ParseException)
        {
          realT = ((ParseException) e).getCause();
          if (realT == null)
            realT = e;
        }
        if (realT instanceof java.lang.reflect.InvocationTargetException)
        {
          realT = ((java.lang.reflect.InvocationTargetException) realT).getCause();
        }
        inSymTab.setLocal(sage.Catbert.EXCEPTION_VAR, realT);
        return null;
      }
      finally
      {
        // If we don't reset it then extra junk left on the stack from before can corrupt
        // further operations AND leave around references which prevent GC from occuring!
        ce.stack.clear();
        returnCEToPool(ce);
      }
      if (assignmentVar != null)
        inSymTab.set(assignmentVar, result);
      return result;
    } else {
      return null;
    }
  }

  /**
   * Returns true if an error occured during the most recent
   * action (parsing or evaluation).
   * @return Returns <code>true</code> if an error occured during the most
   * recent action (parsing or evaluation).
   */
  public boolean hasError() {
    return errorList != null && !errorList.isEmpty();
  }
  public void clearError() { if (errorList != null) {
    sage.Pooler.returnPooledVector(errorList); errorList = null;  }
  }

  /**
   * Reports information on the errors that occured during the most recent
   * action.
   * @return A string containing information on the errors, each separated
   * by a newline character; null if no error has occured
   */
  public String getErrorInfo() {
    if (hasError()) {
      String str = "";

      // iterate through all errors and add them to the return string
      for (int i=0; i<errorList.size(); i++) {
        str += errorList.elementAt(i) + "\n";
      }

      return str;
    } else {
      return null;
    }
  }

  /**
   * Returns the top node of the expression tree. Because all nodes are
   * pointed to either directly or indirectly, the entire expression tree
   * can be accessed through this node. It may be used to manipulate the
   * expression, and subsequently evaluate it manually.
   * @return The top node of the expression tree
   */
  public Node getTopNode() {
    try
    {
      return new Parser(new StringReader("")).parseStream(new StringReader(expression), this);
    }
    catch (Exception e)
    {
      return null;
    }
  }

  public void setFunctionTable(FunctionTable newTab) { funTab = newTab; }


  public static class CommandElement {
    public final static int VAR   = 0;
    public final static int CONST = 1;
    public final static int FUNC  = 2;
    public int                 type;
    public String              varName;
    public PostfixMathCommandI pfmc;
    public int                 nParam;
    public Object              value;
  }

  public static class CommandEvaluator {
    public sage.Catbert.FastStack stack;

    public CommandEvaluator() {
      stack = new sage.Catbert.FastStack();
    }

    public Object evaluate(CommandElement[] commands, SymbolTable symTab) throws Exception {

      int nCommands = commands.length - 1;
      CommandElement command;
      PostfixMathCommandI pfmc;

      //		stack.removeAllElements();

      // for each command
      int i = 0;
      while (i<nCommands) {
        command = commands[i];

        int type = command.type;
        if (type == CommandElement.FUNC)
        {
          // Function
          pfmc = command.pfmc;

          // set the number of current parameters
          // (it is no faster to first check getNumberOfParameters()==-1)
          pfmc.setCurNumberOfParameters(command.nParam);

          pfmc.run(stack);
        }
        else if (type == CommandElement.VAR)
        {
          stack.push(symTab.get(command.varName));
        }
        else
        {
          // Constant
          stack.push(command.value);
        }

        i++;
      }
      // This avoids an extra push/pop call if this is only a variable or constant expression
      {
        command = commands[i];

        int type = command.type;
        if (type == CommandElement.FUNC)
        {
          // Function
          pfmc = command.pfmc;

          // set the number of current parameters
          // (it is no faster to first check getNumberOfParameters()==-1)
          pfmc.setCurNumberOfParameters(command.nParam);

          pfmc.run(stack);
          return stack.pop();
        }
        else if (type == CommandElement.VAR)
        {
          return symTab.get(command.varName);
        }
        else
        {
          // Constant
          return command.value;
        }

      }
      //		if (stack.size() != 1) {
      //			throw new Exception("CommandEvaluator.evaluate(): Stack size is not 1");
      //		}
      //		return stack.pop();
    }
  }

  public static class ExpressionCompiler implements ParserVisitor {
    /** Commands */
    private ArrayList commands;

    public ExpressionCompiler() {
      commands = new ArrayList();
    }

    public CommandElement[] compile(Node node) throws ParseException{
      commands.clear();
      node.jjtAccept(this, null);
      CommandElement[] temp = new CommandElement[commands.size()];
      Iterator en = commands.listIterator();
      int i = 0;
      while (en.hasNext()) {
        temp[i++] = (CommandElement)en.next();
      }
      return temp;
    }

    public Object visit(ASTFunNode node, Object data){
      node.childrenAccept(this,data);

      CommandElement c = new CommandElement();
      c.type = CommandElement.FUNC;
      c.pfmc = node.getPFMC();
      c.nParam = node.jjtGetNumChildren();
      commands.add(c);

      return data;
    }

    public Object visit(ASTVarNode node, Object data) {
      CommandElement c = new CommandElement();
      // Check for constants here to make it faster
      String name = node.getName();
      if ("true".equals(name))
      {
        c.type = CommandElement.CONST;
        c.value = Boolean.TRUE;
      }
      else if ("false".equals(name))
      {
        c.type = CommandElement.CONST;
        c.value = Boolean.FALSE;
      }
      else if ("null".equals(name))
      {
        c.type = CommandElement.CONST;
        c.value = null;
      }
      else if ("this".equals(name))
      {
        c.type = CommandElement.VAR;
        c.varName = null; // null looks up faster than "this"
      }
      else
      {
        c.type = CommandElement.VAR;
        c.varName = node.getName().intern();
      }
      commands.add(c);

      return data;
    }

    public Object visit(ASTConstant node, Object data) {
      CommandElement c = new CommandElement();
      c.type = CommandElement.CONST;
      c.value = node.getValue();
      commands.add(c);

      return data;
    }

    public Object visit(SimpleNode node, Object data) {
      return data;
    }

    public Object visit(ASTStart node, Object data) {
      return data;
    }
  }
}

