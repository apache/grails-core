package org.grails.compiler.gorm

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.AbstractASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import jakarta.persistence.Entity

/**
 * Makes all entities annotated with @Entity JPA into GORM entities
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class GlobalJpaEntityTransform extends AbstractASTTransformation implements ASTTransformation, CompilationUnitAware {

    CompilationUnit compilationUnit

    @Override
    void visit(ASTNode[] astNodes, SourceUnit source) {
        ModuleNode ast = source.getAST();
        List<ClassNode> classes = ast.getClasses();
        for (ClassNode aClass : classes) {
            visitClass(aClass, source)
        }
    }

    void visitClass(ClassNode classNode, SourceUnit source) {
        if(hasAnnotation(classNode, ClassHelper.make(Entity))) {
            def jpaEntityTransformation = new JpaGormEntityTransformation()
            jpaEntityTransformation.compilationUnit = compilationUnit
            jpaEntityTransformation.visit(classNode, source)
        }
    }
}
