package pers.fw.tplugin.db;

import pers.fw.tplugin.beans.ArrayMapVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.impl.ParameterListImpl;
import pers.fw.tplugin.util.MethodMatcher;
import pers.fw.tplugin.util.PsiElementUtil;

public class MethodRefVisitor extends PsiRecursiveElementWalkingVisitor {
    private final ArrayMapVisitor visitor;
    private String contextTable;

    public MethodRefVisitor(ArrayMapVisitor visitor, String contextTable) {
        this.visitor = visitor;
        this.contextTable = contextTable;
    }

    private static MethodMatcher.CallToSignature[] alias = new MethodMatcher.CallToSignature[]{
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "alias")};

    private static MethodMatcher.CallToSignature[] join = new MethodMatcher.CallToSignature[]{
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "join")};

    private static MethodMatcher.CallToSignature[] table = new MethodMatcher.CallToSignature[]{
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "table"),
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "name")
    };

    @Override
    public void visitElement(PsiElement element) {
        if (element instanceof MethodReference) {
            PsiElement[] childrens = element.getChildren();
            for (PsiElement paramList : childrens) {
                if (paramList instanceof ParameterListImpl) {
                    if (paramList.getChildren().length > 0) {
                        PsiElement param = paramList.getChildren()[0];
                        if ("alias".equals(((MethodReference) element).getName()) && MethodMatcher.getMatchedSignatureWithDepth(param, alias, 0) != null) {
                            String text = param.getText().replace("'", "").replace("\"", "");
                            this.visitor.visit(text, contextTable);
                        } else if ("join".equals(((MethodReference) element).getName()) && MethodMatcher.getMatchedSignatureWithDepth(param, join, 0) != null) {
                            String text = param.getText().replace("'", "").replace("\"", "");
                            String[] s = text.split(" ");
                            if (s.length == 2) {    //有别名
                                this.visitor.visit(s[1], s[0]);
                            } else if (s.length == 1) { //无别名
                                this.visitor.visit(text, text);
                            }
                        }else if(PsiElementUtil.isFunctionReference(param, "db", 0)
                                ||MethodMatcher.getMatchedSignatureWithDepth(param, table, 0) != null){
                            String text = param.getText().replace("'", "").replace("\"", "");
                            this.visitor.visit(text, text);
                        }
                    }
                } else if (paramList instanceof MethodReference) {  //链式调用方法
                    super.visitElement(element);
                }
            }
        } else {
            super.visitElement(element);
        }
    }
}