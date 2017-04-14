package bantam.codegenmips;

import bantam.ast.Field;
import bantam.ast.Method;
import bantam.util.ClassTreeNode;
import bantam.util.Location;
import bantam.visitor.Visitor;

import java.util.Map;

/**
 * Created by joseph on 4/5/17.
 */
public class FieldAdderVisitor extends Visitor {

    private MipsSupport assemblySupport;
    private ClassTreeNode treeNode;
    private int numField;
    private ASTNodeCodeGenVisitor codeGenVisitor;

    public FieldAdderVisitor(MipsSupport assemblySupport, ASTNodeCodeGenVisitor codeGenVisitor){
        this.assemblySupport = assemblySupport;
        this.codeGenVisitor = codeGenVisitor;
    }

    public void initField(ClassTreeNode treeNode){
        this.numField = 0;
        this.treeNode = treeNode;
        this.codeGenVisitor.setTreeNode(treeNode);
        this.treeNode.getASTNode().accept(this);
    }

    public Object visit(Method method){
        return null;
    }

    public Object visit(Field field){
        this.numField++;
        int offset = this.treeNode.getVarSymbolTable().getSize()*4 + 12 - 4*this.numField;
        this.treeNode.getVarSymbolTable().set(field.getName(),
                new Location("$a0", offset), treeNode.getVarSymbolTable().getSize()-1);
        if(field.getInit()!=null){
            this.assemblySupport.genComment("Initializing Field: " + field.getName());
            field.getInit().accept(codeGenVisitor);
            //assume result is in $v0
            this.assemblySupport.genStoreWord("$v0", offset, "$a0");
        }
        return null;
    }
}