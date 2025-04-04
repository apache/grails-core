package org.grails.datastore.gorm.services.implementers

import grails.gorm.services.Where
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement

/**
 * Interface projection implementer for {@link grails.gorm.services.Where} queries
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class FindOneInterfaceProjectionWhereImplementer extends FindOneWhereImplementer implements SingleResultInterfaceProjectionBuilder, AnnotatedServiceImplementer<Where> {

    @Override
    protected ClassNode resolveDomainClassFromSignature(ClassNode currentDomainClassNode, MethodNode methodNode) {
        return currentDomainClassNode
    }

    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode newMethodNode, ClassNode returnType, String prefix) {
        return isInterfaceProjection(domainClass, newMethodNode, returnType)
    }

    @Override
    protected Statement buildReturnStatement(ClassNode domainClass, MethodNode abstractMethodNode, MethodNode newMethodNode, Expression queryExpression) {
        ReturnStatement rs = (ReturnStatement)super.buildReturnStatement(domainClass, abstractMethodNode, newMethodNode, queryExpression)
        return buildInterfaceProjection(domainClass, abstractMethodNode, rs.expression, queryExpression, newMethodNode)
    }
}
