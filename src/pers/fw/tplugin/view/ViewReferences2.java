package pers.fw.tplugin.view;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.fw.tplugin.inter.GotoCompletionContributor;
import pers.fw.tplugin.inter.GotoCompletionLanguageRegistrar;
import pers.fw.tplugin.inter.GotoCompletionProvider;
import pers.fw.tplugin.inter.GotoCompletionRegistrarParameter;
import pers.fw.tplugin.util.MethodMatcher;
import pers.fw.tplugin.util.PsiElementUtil;
import pers.fw.tplugin.util.PsiElementUtils;
import pers.fw.tplugin.util.Util;
import pers.fw.tplugin.view.dict.TemplatePath;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

// view 处理类
public class ViewReferences2 implements GotoCompletionLanguageRegistrar {
    private static MethodMatcher.CallToSignature[] VIEWS = new MethodMatcher.CallToSignature[]{
            new MethodMatcher.CallToSignature("\\think\\View", "fetch"),
            new MethodMatcher.CallToSignature("\\think\\Controller", "fetch"),
            new MethodMatcher.CallToSignature("\\think\\Controller", "display"),
    };

    @Override
    public void register(GotoCompletionRegistrarParameter registrar) {
        registrar.register(PlatformPatterns.psiElement(), new GotoCompletionContributor() {
            @Nullable
            @Override
            public GotoCompletionProvider getProvider(@Nullable PsiElement psiElement) {
                if (psiElement == null) {// || !LaravelProjectComponent.isEnabled(psiElement)) {
                    return null;
                }

                PsiElement parent = psiElement.getParent();
                if (parent != null
                        && (PsiElementUtil.isFunctionReference(parent, "view", 0) || MethodMatcher.getMatchedSignatureWithDepth(parent, VIEWS) != null)
                        && handlePath(psiElement)) {
                    return new ViewDirectiveCompletionProvider(parent);
                }
                return null;
            }
        });
    }


    @Override
    public boolean support(@NotNull Language language) {
        return PhpLanguage.INSTANCE == language;
    }

    public boolean handlePath(PsiElement psiElement) {
        // todo ensure base dir
//        String application = "application";
//        String projectPath = psiElement.getProject().getBasePath();//"D:\\project2\\test";
//        String currentFilePath = psiElement.getContainingFile().getVirtualFile().getPath(); //"D:\\project2\\test\\application\\index\\controller\\Index.php";
//        String[] arr = currentFilePath.replace(projectPath, "").split("/");
//        if (arr.length < 4 || !arr[1].equals(application)) {
//                return false;
//        }
        String application = Util.getApplicationDir(psiElement);
//        String moduleName = arr[2];
        String moduleName = Util.getCurTpModuleName(psiElement);
        ViewCollector.DEFAULT_TEMPLATE_PATH = new TemplatePath[]{new TemplatePath(application + "/" + moduleName + "/view", false)};
        ViewCollector.curModule = moduleName;
        return true;
    }


    private static class ViewDirectiveCompletionProvider extends GotoCompletionProvider {
        private ViewDirectiveCompletionProvider(PsiElement element) {
            super(element);
        }

        @NotNull
        @Override
        public Collection<LookupElement> getLookupElements() {

            final Collection<LookupElement> lookupElements = new ArrayList<>();

            ViewCollector.visitFile(getProject(), new ViewCollector.ViewVisitor() {
                        @Override
                        public void visit(@NotNull VirtualFile virtualFile, @NotNull String name) {
                            lookupElements.add(LookupElementBuilder.create(name).withIcon(virtualFile.getFileType().getIcon()));
                        }
                    }
            );

            // @TODO: no filesystem access in test; fake item
            if (ApplicationManager.getApplication().isUnitTestMode()) {
                lookupElements.add(LookupElementBuilder.create("test_view"));
            }

            return lookupElements;
        }

        @NotNull
        @Override
        public Collection<? extends PsiElement> getPsiTargets(@NotNull PsiElement psiElement, int offset, @NotNull Editor editor) {
            PsiElement stringLiteral = psiElement.getParent();
            if (!(stringLiteral instanceof StringLiteralExpression)) {
                return Collections.emptyList();
            }

            String contents = ((StringLiteralExpression) stringLiteral).getContents();

            String viewDir = getProject().getBaseDir() + Util.getApplicationDir(psiElement) + "/" + Util.getCurTpModuleName(psiElement) + "/" + "view" + "/";
            String className = "";
            String methodName = "";

            if (contents.contains("/")) {  // index/test
                String[] split = contents.split("/");
                className = split[0];
                methodName = contents.substring(className.length() + 1);
            } else if ("".equals(contents)) {   //
                className = Util.getPhpClass(psiElement).getName().toLowerCase();
                methodName = Util.getMethod(psiElement).getName();
            } else {                            // test
                className = Util.getPhpClass(psiElement).getName().toLowerCase();
                methodName = contents;
            }

            String dir = viewDir + className;//+ "/" + methodName;
            String filePath = viewDir + className + "/" + methodName;
            dir = dir.replace("file://", "");
            filePath = filePath.replace("file://", "");
            File file = new File(dir);
            File targetFile = TemplateUtil.recursionMatch(file, filePath);
            Collection<VirtualFile> virFiles = new ArrayList<>();
            if (targetFile != null) {
                VirtualFile fileByIoFile = VfsUtil.findFileByIoFile(targetFile, false);
                virFiles.add(fileByIoFile);
            }
            Collection<PsiElement> targets = new ArrayList<>(PsiElementUtils.convertVirtualFilesToPsiFiles(getProject(), virFiles));

            return targets;
        }
    }
}
