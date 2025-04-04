package org.grails.datastore.gorm.services.implementers

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.stmt.Statement

/**
 * Created by graemerocher on 05/04/2017.
 */
@CompileStatic
class FindAllByInterfaceProjectionImplementer extends FindAllByImplementer implements IterableProjectionServiceImplementer, IterableInterfaceProjectionBuilder {

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        return super.doesImplement(domainClass, methodNode)
    }

    @Override
    protected ClassNode resolveDomainClassFromSignature(ClassNode currentDomainClassNode, MethodNode methodNode) {
        return currentDomainClassNode
    }

    @Override
    protected ClassNode resolveDomainClassForReturnType(ClassNode currentDomainClass, boolean isArray, ClassNode returnType) {
        return currentDomainClass
    }

    @Override
    boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        isInterfaceProjection(domainClass, methodNode, returnType)
    }

    @Override
    protected Statement buildReturnStatement(ClassNode domainClass, MethodNode abstractMethodNode, MethodNode newMethodNode, Expression queryExpression) {
        return buildInterfaceProjection(domainClass, abstractMethodNode, queryExpression, queryExpression, newMethodNode)
    }
}

