/*
 * File: SemanticAnalyzer.java
 * CS461 Project 6
 * Edited by: Phoebe Hughes, Siyuan Li, Joseph Malionek,
 * Date: 5/10/17
 */

/* Bantam Java Compiler and Language Toolset.

   Copyright (C) 2009 by Marc Corliss (corliss@hws.edu) and 
                         David Furcy (furcyd@uwosh.edu) and
                         E Christopher Lewis (lewis@vmware.com).
   ALL RIGHTS RESERVED.

   The Bantam Java toolset is distributed under the following 
   conditions:

     You may make copies of the toolset for your own use and 
     modify those copies.

     All copies of the toolset must retain the author names and 
     copyright notice.

     You may not sell the toolset or distribute it in 
     conjunction with a commerical product or service without 
     the expressed written consent of the authors.

   THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS 
   OR IMPLIED WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE 
   IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A 
   PARTICULAR PURPOSE. 
*/

package bantam.semant;

import bantam.ast.*;
import bantam.util.*;
import bantam.visitor.MainMainVisitor;
import bantam.visitor.MemberAdderVisitor;
import bantam.visitor.TypeCheckVisitor;

import java.util.*;

/** The <tt>SemanticAnalyzer</tt> class performs semantic analysis.
 * In particular this class is able to perform (via the <tt>analyze()</tt>
 * method) the following tests and analyses: (1) legal inheritence
 * hierarchy (all classes have existing parent, no cycles), (2)
 * legal class member declaration, (3) there is a correct bantam.Main class
 * and main() method, and (4) each class member is correctly typed.
 *
 * This class is incomplete and will need to be implemented by the student.
 * */
public class SemanticAnalyzer {
	/** Root of the AST */
	private Program program;

	/** Root of the class hierarchy tree */
	private ClassTreeNode root;

	/** Maps class names to ClassTreeNode objects describing the class */
	private Hashtable<String,ClassTreeNode> classMap = new Hashtable<String,ClassTreeNode>();

	/**
	 * Set of classes that are not in a loop
	 */
	private HashSet<ClassTreeNode> nonLoopClasses = new HashSet<>();

	/** Object for error handling */
	private ErrorHandler errorHandler = new ErrorHandler();

	/** Boolean indicating whether debugging is enabled */
	private boolean debug = false;

	/** Maximum number of inherited and non-inherited fields
	 * that can be defined for any one class */
	private final int MAX_NUM_FIELDS = 1500;


	/**
	 * A set of names that cannot be variable, field, class, ect. names
	 */
	private Set<String> disallowedNames;

	/** SemanticAnalyzer constructor
	 * @param program root of the AST
	 * @param debug boolean indicating whether debugging is enabled
	 * */
	public SemanticAnalyzer(Program program, boolean debug) {
		this.program = program;
		this.debug = debug;
		this.disallowedNames = new HashSet<>(Arrays.asList(
						new String[]{"null", "void", "super", "this", "boolean", "int"}));
	}

	/** Analyze the AST checking for semantic errors and annotating the tree
	 * Also builds an auxiliary class hierarchy tree
	 * @return root of the class hierarchy tree (needed for code generation)
	 *
	 * Must add code to do the following:
	 *   1 - build built-in class nodes in class hierarchy tree (already done)
	 *   2 - build and check the class hierarchy tree
	 *   3 - build the environment for each class (adding class members only) and check
	 *       that members are declared properly
	 *   4 - check that the bantam.Main class and main method are declared properly
	 *   5 - type check each class member
	 * See the lab manual for more details on each of these steps.
	 * */
	public ClassTreeNode analyze() {
		// 1 - add built in classes to class tree
		this.updateBuiltins();

		// 2 - build and check the class hierarchy tree
		this.buildTree();
		this.checkHierarchy();

		// 3 - build the environment for each class (adding class members only) and check
		// that members are declared properly
		this.buildEnvironment();

		// 4 - check that the bantam.Main class and main method are declared properly
		this.checkMainMain();

		// 5 - type check each class member
		this.checkTypes();

		this.errorHandler.checkErrors();
		return root;
	}

	/**
	 * Builds the tree of class tree nodes
	 */
	private void buildTree() {
		//puts each classes into hashmap
		for (ASTNode node: this.program.getClassList()){
			Class_ classNode = (Class_)node;
			String className = classNode.getName();
			ClassTreeNode classTreeNode = new ClassTreeNode(classNode,
					false, true, this.classMap);

			//registers error if invalid class name
            if (this.disallowedNames.contains(className)){
                this.errorHandler.register(2, classNode.getFilename(),
                        classNode.getLineNum(), "Reserved word, " + className
                                + ", cannot be used as a class name.");
            }
            //registers error if class already defined
            else if (this.classMap.containsKey(className)){
                this.errorHandler.register(2, classNode.getFilename(),
                        classNode.getLineNum(),
                        "Class " + className + " already declared.");
            }
            this.classMap.put(className, classTreeNode);
		}

		//update parent nodes/descendants
		for (ClassTreeNode node: this.classMap.values()){
			if (!node.getName().equals("Object")) {
				Class_ astNode = node.getASTNode();
				String parent = astNode.getParent();
				if (parent != null) {
					ClassTreeNode parentTreeNode = this.classMap.get(parent);

					//registers error if trying to extend unextendable class
					if (parentTreeNode == null || !parentTreeNode.isExtendable()){
                        this.errorHandler.register(2, astNode.getFilename(),
                                astNode.getLineNum(), "Cannot extend " + parent);
                    }
					else {
                        node.setParent(parentTreeNode);
					}
				} else {
					node.setParent(this.root);
				}
			}
		}
	}

	/**
	 * Check to make sure tree has a valid hiearchy
	 */
	private void checkHierarchy(){
		Stack<ClassTreeNode> unchecked = new Stack<>();
		Set<ClassTreeNode> checked = new HashSet<>();

		unchecked.add(this.root);

		while(!unchecked.empty()){
			ClassTreeNode popped = unchecked.pop();
			Class_ astNode = popped.getASTNode();

			//checks if there is a loop of classes
			if (checked.contains(popped)){
				this.errorHandler.register(2, astNode.getFilename(),
						astNode.getLineNum(),
						"Illegal Tree Structure!");
				return;
			}
			else{
				this.nonLoopClasses.add(popped);
				checked.add(popped);
			}

			Iterator childrenIterator = popped.getChildrenList();
			while (childrenIterator.hasNext()){
				ClassTreeNode child = (ClassTreeNode)childrenIterator.next();
				unchecked.push(child);
			}
		}
		if (checked.size() != this.classMap.size()){
            this.errorHandler.register(2, "Illegal Tree Structure.");
        }
	}

	/**
	 * Builds the symbol table.
	 */
	private void buildEnvironment(){
		MemberAdderVisitor visitor = new MemberAdderVisitor(this.errorHandler,
				this.disallowedNames);

		this.buildSymbolTables(this.root, visitor);
	}

	/**
	 * Adds the methods and fields to the symbol tables of a class tree node
	 * @param classTreeNode the class tree node
	 * @param memberAdderVisitor the visitor that adds these
	 */
	private void buildSymbolTables(ClassTreeNode classTreeNode,
								   MemberAdderVisitor memberAdderVisitor) {
		Iterator<ClassTreeNode> iterator = classTreeNode.getChildrenList();
		memberAdderVisitor.getSymbolTables(classTreeNode);
		while(iterator.hasNext()) {
			ClassTreeNode child = iterator.next();
			child.getVarSymbolTable().setParent(child.getParent().getVarSymbolTable());
			child.getMethodSymbolTable().setParent(child.getParent()
										.getMethodSymbolTable());
			this.buildSymbolTables(child, memberAdderVisitor);
		}

	}

	/**
	 * Ensures that the program has a Main class and main method.
	 */
	private void checkMainMain(){
		MainMainVisitor mainMainVisitor = new MainMainVisitor(this.classMap);
		boolean hasMain = mainMainVisitor.hasMain(this.program);
		if (!hasMain){
			this.errorHandler.register(2, "Valid programs must have a "
					+ "'Main' class with a 'main' method.");
		}

	}

	/**
	 * Checks to make sure that the types are compatible
	 */
	private void checkTypes(){
		TypeCheckVisitor typeCheckVisitor = new TypeCheckVisitor(
				this.errorHandler, this.disallowedNames);
		for(ClassTreeNode treeNode : this.nonLoopClasses){
			typeCheckVisitor.checkTypes(treeNode);
		}
	}

	/**
	 * @return the ErrorHandler for this Parser
	 */
	public ErrorHandler getErrorHandler() { return errorHandler; }

	/**
	 * Add built in classes to the class tree
	 */
	private void updateBuiltins() {
		// create AST node for object
		Class_ astNode =
				new Class_(-1, "<built-in class>", "Object", null,
						(MemberList) (new MemberList(-1)).addElement(
								new Method(-1, "Object", "clone", new FormalList(-1),
										(StmtList) (new StmtList(-1)).addElement(
												new ReturnStmt(-1,
														new VarExpr(-1,
																null, "null")))))
								.addElement(new Method(-1, "boolean", "equals",
								(FormalList) (new FormalList(-1))
										.addElement(new Formal(-1, "Object", "o")),
								(StmtList) (new StmtList(-1))
										.addElement(new ReturnStmt(-1,
												new ConstBooleanExpr(-1, "false")))))
								.addElement(new Method(-1, "String", "toString",
										new FormalList(-1),
										(StmtList) (new StmtList(-1))
												.addElement(new ReturnStmt(-1,
														new VarExpr(-1, null,"null"))))));
		// create a class tree node for object, save in variable root
		root = new ClassTreeNode(astNode, /*built-in?*/true,
				/*extendable?*/true, classMap);
		// add object class tree node to the mapping
		classMap.put("Object", root);

		// note: String, TextIO, and Sys all have fields that are not shown below. Because
		// these classes cannot be extended and fields are protected,
		// they cannot be accessed by
		// other classes, so they do not have to be included in the AST.

		// create AST node for String
		astNode =
				new Class_(-1, "<built-in class>",
						"String", "Object",
						(MemberList) (new MemberList(-1))
								.addElement(new Field(-1, "int", "length",
										/*0 by default*/null))
			   /* note: str is the character sequence -- no applicable type for a
			  character sequence so it is just made an int.  it's OK to
			  do this since this field is only accessed (directly) within
			  the runtime system */
								.addElement(new Method(-1, "int", "length",
										new FormalList(-1),
										(StmtList) (new StmtList(-1))
												.addElement(new ReturnStmt(-1,
														new ConstIntExpr(-1, "0")))))
								.addElement(new Method(-1, "boolean", "equals",
										(FormalList) (new FormalList(-1))
												.addElement(new Formal(-1, "Object",
														"str")),
										(StmtList) (new StmtList(-1))
												.addElement(new ReturnStmt(-1,
														new ConstBooleanExpr(-1,
																"false")))))
								.addElement(new Method(-1, "String", "toString",
										new FormalList(-1),
										(StmtList) (new StmtList(-1))
												.addElement(new ReturnStmt(-1,
														new VarExpr(-1, null, "null")))))
								.addElement(new Method(-1, "String", "substring",
										(FormalList) (new FormalList(-1))
												.addElement(new Formal(-1, "int",
														"beginIndex"))
												.addElement(new Formal(-1, "int",
														"endIndex")),
										(StmtList) (new StmtList(-1))
												.addElement(new ReturnStmt(-1,
														new VarExpr(-1, null, "null")))))
								.addElement(new Method(-1, "String", "concat",
										(FormalList) (new FormalList(-1))
												.addElement(new Formal(-1, "String",
														"str")),
										(StmtList) (new StmtList(-1))
												.addElement(new ReturnStmt(-1,
														new VarExpr(-1, null,"null"))))));
		// create class tree node for String, add it to the mapping
		classMap.put("String", new ClassTreeNode(astNode, /*built-in?*/true,
				/*extendable?*/false, classMap));

		// create AST node for TextIO
		astNode =
				new Class_(-1, "<built-in class>",
						"TextIO", "Object",
						(MemberList) (new MemberList(-1))
								.addElement(new Field(-1, "int", "readFD",
										/*0 by default*/null))
								.addElement(new Field(-1, "int", "writeFD",
										new ConstIntExpr(-1, "1")))
								.addElement(new Method(-1, "void", "readStdin",
										new FormalList(-1),
										(StmtList) (new StmtList(-1))
												.addElement(new ReturnStmt(-1, null))))
								.addElement(new Method(-1, "void", "readFile",
										(FormalList) (new FormalList(-1))
												.addElement(new Formal(-1, "String",
														"readFile")),
										(StmtList) (new StmtList(-1))
												.addElement(new ReturnStmt(-1, null))))
								.addElement(new Method(-1, "void", "writeStdout",
										new FormalList(-1),
										(StmtList) (new StmtList(-1))
												.addElement(new ReturnStmt(-1, null))))
								.addElement(new Method(-1, "void", "writeStderr",
										new FormalList(-1),
										(StmtList) (new StmtList(-1))
												.addElement(new ReturnStmt(-1, null))))
								.addElement(new Method(-1, "void", "writeFile",
										(FormalList) (new FormalList(-1))
												.addElement(new Formal(-1, "String",
														"writeFile")),
										(StmtList) (new StmtList(-1))
												.addElement(new ReturnStmt(-1, null))))
								.addElement(new Method(-1, "String", "getString",
										new FormalList(-1),
										(StmtList) (new StmtList(-1))
												.addElement(new ReturnStmt(-1,
														new VarExpr(-1, null, "null")))))
								.addElement(new Method(-1, "int", "getInt",
										new FormalList(-1),
										(StmtList) (new StmtList(-1))
												.addElement(new ReturnStmt(-1,
														new ConstIntExpr(-1, "0")))))
								.addElement(new Method(-1, "TextIO", "putString",
										(FormalList) (new FormalList(-1))
												.addElement(new Formal(-1, "String",
														"str")),
										(StmtList) (new StmtList(-1))
												.addElement(new ReturnStmt(-1,
														new VarExpr(-1, null, "null")))))
								.addElement(new Method(-1, "TextIO", "putInt",
										(FormalList) (new FormalList(-1))
												.addElement(new Formal(-1, "int",
														"n")),
										(StmtList) (new StmtList(-1))
												.addElement(new ReturnStmt(-1,
														new VarExpr(-1, null, "null"))))));
		// create class tree node for TextIO, add it to the mapping
		classMap.put("TextIO", new ClassTreeNode(astNode, /*built-in?*/true,
				/*extendable?*/false, classMap));

		// create AST node for Sys
		astNode =
				new Class_(-1, "<built-in class>",
						"Sys", "Object",
						(MemberList) (new MemberList(-1))
								.addElement(new Method(-1, "void", "exit",
										(FormalList) (new FormalList(-1))
												.addElement(new Formal(-1, "int",
														"status")),
										(StmtList) (new StmtList(-1))
												.addElement(new ReturnStmt(-1, null))))
			   /* MC: time() and random() requires modifying SPIM to add a time system
			   call
			  (note: random() does not need its own system call although it uses the time
			  system call).  We have a version of SPIM with this system call available,
			  otherwise, just comment out. (For x86 and jvm there are no issues.) */
								.addElement(new Method(-1, "int", "time",
										new FormalList(-1),
										(StmtList) (new StmtList(-1))
												.addElement(new ReturnStmt(-1,
														new ConstIntExpr(-1, "0")))))
								.addElement(new Method(-1, "int", "random",
										new FormalList(-1),
										(StmtList) (new StmtList(-1))
												.addElement(new ReturnStmt(-1,
														new ConstIntExpr(-1, "0")))))
				);
		// create class tree node for Sys, add it to the mapping
		classMap.put("Sys", new ClassTreeNode(astNode, /*built-in?*/true,
				/*extendable?*/false, classMap));

		// create AST node for Exception
		astNode =
				new Class_(-1, "<built-in class>", "Exception", "Object",
						(MemberList) (new MemberList(-1))
								.addElement(new Field(-1, "String", "message", null))
								.addElement(new Method(-1, "String", "getMessage",
										new FormalList(-1),
										(StmtList) (new StmtList(-1)).addElement(
												new ReturnStmt(-1, new VarExpr(-1,
														new VarExpr(-1, null, "this"),
														"message")))))
								.addElement(new Method(-1, "void", "setMessage",
										(FormalList) (new FormalList(-1))
												.addElement(new Formal(-1, "String","m")),
										(StmtList) (new StmtList(-1))
												.addElement(new ExprStmt(-1,
														new AssignExpr(-1, "this",
																"message",
																new VarExpr(-1,
																		null, "m")))))));

		//create class tree node for Exception, add it to the mapping
		classMap.put("Exception", new ClassTreeNode(astNode, /*built-in?*/true,
				/*extendable?*/true, classMap));

		//create AST node & class tree node for NullPointerException. then add to the mapping
		astNode = new Class_(-1, "<built-in class>", "NullPointerException", "Exception",
				(MemberList) (new MemberList(-1)));
		classMap.put("NullPointerException",
                new ClassTreeNode(astNode, /*built-in?*/true, false, classMap));

		//create AST node & class tree node for DivideByZeroException. then add to the mapping
		astNode = new Class_(-1, "<built-in class>", "DivideByZeroException", "Exception",
				(MemberList) (new MemberList(-1)));
		classMap.put("DivideByZeroException",
                new ClassTreeNode(astNode, /*built-in?*/true, false, classMap));

		//create AST node & class tree node for ClassCastException. then add to the mapping
		astNode = new Class_(-1, "<built-in class>", "ClassCastException", "Exception",
				(MemberList) (new MemberList(-1)));
		classMap.put("ClassCastException",
				new ClassTreeNode(astNode, true, false, classMap));

		//create AST node & class tree node for ArrayIndexOutOfBoundsException. then add to the mapping
		astNode = new Class_(-1, "<built-in class>", "ArrayIndexOutOfBoundsException",
				"Exception", (MemberList) (new MemberList(-1)));
		classMap.put("ArrayIndexOutOfBoundsException",
                new ClassTreeNode(astNode, true, false, classMap));

		//create AST node & class tree node for ArraySizeException. then add to the mapping
		astNode = new Class_(-1, "<built-in class>", "ArraySizeException", "Exception",
				(MemberList) (new MemberList(-1)));
		classMap.put("ArraySizeException",
				new ClassTreeNode(astNode, /*built-in?*/true, false, classMap));

		//create AST node & class tree node for ArrayStoreException. then add to the mapping
		astNode = new Class_(-1, "<built-in class>", "ArrayStoreException", "Exception",
				(MemberList) (new MemberList(-1)));
		classMap.put("ArrayStoreException",
				new ClassTreeNode(astNode, /*built-in?*/true, false, classMap));

	}
}
