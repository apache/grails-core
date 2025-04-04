package org.grails.datastore.gorm.services.implementers

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.stmt.Statement
import org.grails.datastore.gorm.GormEntity

/**
 * Interface projection finder
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class FindOneInterfaceProjectionImplementer extends FindOneImplementer implements SingleResultInterfaceProjectionBuilder, SingleResultServiceImplementer<GormEntity> {

    @Override
    protected ClassNode resolveDomainClassFromSignature(ClassNode currentDomainClassNode, MethodNode methodNode) {
        return currentDomainClassNode
    }

    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        return isInterfaceProjection(domainClass, methodNode, returnType)
    }

    @Override
    protected Statement buildReturnStatement(ClassNode targetDomainClass, MethodNode abstractMethodNode, Expression queryMethodCall, Expression args, MethodNode newMethodNode) {
       return buildInterfaceProjection(targetDomainClass, abstractMethodNode, queryMethodCall, args, newMethodNode)
    }
}
