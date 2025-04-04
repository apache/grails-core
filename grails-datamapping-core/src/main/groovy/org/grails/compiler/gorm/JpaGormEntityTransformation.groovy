package org.grails.compiler.gorm

import grails.gorm.annotation.Entity
import grails.gorm.annotation.JpaEntity
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.grails.datastore.mapping.reflect.AstUtils

/**
 * Enhanced GORM entity annotated with JPA annotations
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class JpaGormEntityTransformation extends GormEntityTransformation {
    private static final ClassNode MY_TYPE = new ClassNode(JpaEntity.class);

    @Override
    void visit(ASTNode[] astNodes, SourceUnit sourceUnit) {
        AnnotatedNode parent = (AnnotatedNode) astNodes[1];
        AnnotationNode node = (AnnotationNode) astNodes[0];

        if (!(astNodes[0] instanceof AnnotationNode) || !(astNodes[1] instanceof AnnotatedNode)) {
            throw new RuntimeException("Internal error: wrong types: ${node.getClass()} / ${parent.getClass()}");
        }

        if (!MY_TYPE.equals(node.getClassNode()) || !(parent instanceof ClassNode)) {
            return;
        }

        ClassNode cNode = (ClassNode) parent;

        visit(cNode, sourceUnit)
    }

    @Override
    void visit(ClassNode classNode, SourceUnit sourceUnit) {
        if(!hasAnnotation(classNode, JPA_ENTITY_CLASS_NODE)) {
            classNode.addAnnotation(JPA_ENTITY_ANNOTATION_NODE)
        }
        super.visit(classNode, sourceUnit)
    }
}
