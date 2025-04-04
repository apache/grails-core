package org.grails.datastore.gorm.services.implementers

import grails.gorm.services.Query
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.VariableScope
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.SourceUnit
import org.grails.datastore.gorm.services.ServiceImplementer
import org.grails.datastore.gorm.services.transform.QueryStringTransformer
import org.grails.datastore.mapping.reflect.AstUtils

import java.lang.annotation.Annotation

import static org.codehaus.groovy.ast.tools.GeneralUtils.args
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX

/**
 * Abstract support for String-based queries
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
abstract class AbstractStringQueryImplementer extends AbstractReadOperationImplementer implements AnnotatedServiceImplementer<Query> {
    @Override
    int getOrder() {
        return FindAllByImplementer.POSITION - 100
    }

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        if( isAnnotated(domainClass, methodNode) ) {
            return isCompatibleReturnType(domainClass, methodNode, methodNode.returnType, methodNode.name)
        }
        return false
    }

    @Override
    boolean isAnnotated(ClassNode domainClass, MethodNode methodNode) {
        return AstUtils.findAnnotation(methodNode, getAnnotationType()) != null
    }

    @Override
    void doImplement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
        AnnotationNode annotationNode = AstUtils.findAnnotation(abstractMethodNode, getAnnotationType())
        Expression expr = annotationNode.getMember("value")
        VariableScope scope = newMethodNode.variableScope
        if(expr instanceof GStringExpression) {
            GStringExpression gstring = (GStringExpression)expr
            SourceUnit sourceUnit = abstractMethodNode.declaringClass.module.context
            QueryStringTransformer transformer = createQueryStringTransformer(sourceUnit, scope)
            Expression transformed = transformer.transformQuery(gstring)
            BlockStatement body = (BlockStatement)newMethodNode.code
            Expression argMap = findArgsExpression(newMethodNode)
            if(argMap != null) {
                transformed = args( transformed, argMap )
            }
            body.addStatement(
                buildQueryReturnStatement(domainClassNode, abstractMethodNode, newMethodNode, transformed)
            )
            annotationNode.setMember("value", constX(IMPLEMENTED))
        }
    }

    protected Class<? extends Annotation> getAnnotationType() {
        Query
    }

    /**
     * Creates the query string transformer
     *
     * @param sourceUnit The source unit
     * @param scope the variable scope
     * @return The transformer
     */
    protected QueryStringTransformer createQueryStringTransformer(SourceUnit sourceUnit, VariableScope scope) {
        new QueryStringTransformer(sourceUnit, scope)
    }

    /**
     * Builds the query return statement
     *
     * @param domainClassNode The domain class
     * @param args The arguments
     * @return The statement
     */
    protected abstract Statement buildQueryReturnStatement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, Expression args)

    @Override
    Iterable<String> getHandledPrefixes() {
        return Collections.emptyList()
    }
}
