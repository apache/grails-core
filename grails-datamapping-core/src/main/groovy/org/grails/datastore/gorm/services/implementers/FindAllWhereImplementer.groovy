package org.grails.datastore.gorm.services.implementers

import grails.gorm.services.Where
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.stmt.Statement
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.reflect.AstUtils

import static org.codehaus.groovy.ast.tools.GeneralUtils.castX
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS

/**
 * Implements support for the {@link Where} annotation on {@link grails.gorm.services.Service} instances that return a multiple results
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class FindAllWhereImplementer extends AbstractWhereImplementer implements IterableServiceImplementer<GormEntity> {

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        if( isAnnotated(domainClass, methodNode )) {
            return isCompatibleReturnType(domainClass, methodNode, methodNode.returnType, methodNode.name)
        }
        return false
    }

    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        return AstUtils.isIterableOrArrayOfDomainClasses(returnType)
    }

    @Override
    protected String getQueryMethodToExecute(ClassNode domainClass, MethodNode newMethodNode) {
        return "list"
    }

    @Override
    protected ClassNode resolveDomainClassFromSignature(ClassNode currentDomainClassNode, MethodNode methodNode) {
        ClassNode returnType = methodNode.returnType
        if(returnType.isArray()) {
            return returnType.componentType
        }
        else {
            return returnType.genericsTypes[0].type
        }
    }

    @Override
    protected Statement buildReturnStatement(ClassNode domainClass, MethodNode abstractMethodNode, MethodNode methodNode, Expression queryExpression) {
        return returnS(castX(methodNode.returnType, queryExpression))
    }
}
