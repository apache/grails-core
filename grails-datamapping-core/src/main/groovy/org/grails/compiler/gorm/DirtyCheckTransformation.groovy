package org.grails.compiler.gorm

import grails.gorm.dirty.checking.DirtyCheck
import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

/**
 * Applies the DirtyCheck transformation
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class DirtyCheckTransformation implements ASTTransformation, CompilationUnitAware {

    private static final ClassNode MY_TYPE = new ClassNode(DirtyCheck.class);
    private static final String MY_TYPE_NAME = "@" + MY_TYPE.getNameWithoutPackage();

    CompilationUnit compilationUnit

    @Override
    @CompileStatic
    void visit(ASTNode[] astNodes, SourceUnit source) {

        AnnotatedNode parent = (AnnotatedNode) astNodes[1];
        AnnotationNode node = (AnnotationNode) astNodes[0];

        if (!(astNodes[0] instanceof AnnotationNode) || !(astNodes[1] instanceof AnnotatedNode)) {
            throw new RuntimeException("Internal error: wrong types: ${node.getClass()} / ${parent.getClass()}");
        }

        if (!MY_TYPE.equals(node.getClassNode()) || !(parent instanceof ClassNode)) {
            return;
        }

        ClassNode cNode = (ClassNode) parent;


        def dirtyCheckingTransformer = new DirtyCheckingTransformer()
        dirtyCheckingTransformer.compilationUnit = compilationUnit
        dirtyCheckingTransformer.performInjection(source, cNode)
    }
}
