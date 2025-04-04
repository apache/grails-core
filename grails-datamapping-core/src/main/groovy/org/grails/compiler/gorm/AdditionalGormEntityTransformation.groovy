package org.grails.compiler.gorm

import groovy.transform.CompilationUnitAware
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation

/**
 * Additional transformations applied to GORM entities
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface AdditionalGormEntityTransformation extends ASTTransformation, CompilationUnitAware {

    /**
     * @return Whether the transformation is available
     */
    boolean isAvailable()

    /**
     * Visit the transform
     *
     * @param classNode The class node
     * @param sourceUnit The source unit
     */
    void visit(ClassNode classNode, SourceUnit sourceUnit)
}