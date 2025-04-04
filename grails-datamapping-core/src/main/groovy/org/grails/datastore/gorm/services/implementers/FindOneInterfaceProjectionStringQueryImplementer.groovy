package org.grails.datastore.gorm.services.implementers

import grails.gorm.services.Query
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement

/**
 * Interface projections for string-based queries
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class FindOneInterfaceProjectionStringQueryImplementer extends FindOneStringQueryImplementer implements SingleResultInterfaceProjectionBuilder, AnnotatedServiceImplementer<Query> {

    @Override
    protected ClassNode resolveDomainClassFromSignature(ClassNode currentDomainClassNode, MethodNode methodNode) {
        return currentDomainClassNode
    }

    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        return isInterfaceProjection(domainClass, methodNode, returnType)
    }

    @Override
    protected Statement buildQueryReturnStatement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, Expression queryArg) {
        ReturnStatement rs = (ReturnStatement)super.buildQueryReturnStatement(domainClassNode, abstractMethodNode, newMethodNode, queryArg)
        return buildInterfaceProjection(domainClassNode, abstractMethodNode, rs.expression, queryArg, newMethodNode)
    }
}
