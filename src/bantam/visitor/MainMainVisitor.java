/*
 * File: MainMainVisitor.java
 * CS461 Project 2.5
 * Author: Phoebe Hughes, Siyuan Li, Joseph Malionek
 * Date: 2/28/17
 */

package bantam.visitor;

import bantam.ast.*;

/**
 * Vistor class for checking a given program has a Main class
 * with a main method and a void return type
 */
public class MainMainVisitor extends Visitor {
    /**
     * Check if root node has a Main class with a main method and a void return type
     * @param root the root node of the program
     * @return a boolean
     */
    public boolean hasMain(ASTNode root){
        return (boolean)root.accept(this);
    }

    /**
     * Check if any nodes in the list has a Main class with a main method
     * @param nodes a list of nodes
     * @return a boolean indicating if there is a Main class and a main method
     */
    private boolean listNodeHasMain(ListNode nodes) {
        for (ASTNode node: nodes){
            boolean hasMain = (boolean)node.accept(this);
            if (hasMain){
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the program node has a Main class and a main method
     * @param node the program node
     * @return a boolean indicating if there is a Main class and a main method
     */
    @Override
    public Object visit(Program node) {
        return node.getClassList().accept(this);
    }

    /**
     * Check if any class nodes has a Main class and a main method
     * @param nodes a list of class nodes
     * @return a boolean indicating if there is a Main class and a main method
     */
    @Override
    public Object visit(ClassList nodes){
        return this.listNodeHasMain(nodes);
    }

    /**
     * Check if a class node has a Main class and a main method
     * @param node the class node
     * @return a boolean indicating if there is a Main class and a main method
     */
    @Override
    public Object visit(Class_ node) {
        boolean nameMain = node.getName().equals("Main");
        if (nameMain){
            return node.getMemberList().accept(this);
        }
        return false;
    }

    /**
     * Check if any member nodes has a Main class and a main method
     * @param nodes a list of memberlist nodes
     * @return a boolean indicating if there is a Main class and a main method
     */
    @Override
    public Object visit(MemberList nodes){
       return this.listNodeHasMain(nodes);
    }

    /**
     * Return false for field nodes to prune the AST
     * @param node the field node
     * @return return false
     */
    @Override
    public Object visit(Field node) {
        return false;
    }

    /**
     * Check if a method node has a Main class and a main method
     * @param node the method node
     * @return a boolean indicating if there is a Main class and a main method
     */
    @Override
    public Object visit(Method node) {
        boolean nameMain = node.getName().equals("main");
        boolean typeVoid = node.getReturnType().toLowerCase().equals("void");
        boolean noParams = node.getFormalList().getSize() == 0;
        if (nameMain && typeVoid && noParams){
            return true;
        }
        return false;
    }
}
