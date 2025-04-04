package org.grails.datastore.gorm.services.implementers

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.grails.datastore.gorm.transform.AstPropertyResolveUtils
import org.grails.datastore.mapping.reflect.AstUtils

import java.beans.Introspector

import static org.codehaus.groovy.ast.tools.GeneralUtils.*

/**
 * Implements property projection by query
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class FindOnePropertyProjectionImplementer extends AbstractProjectionImplementer implements SingleResultProjectionServiceImplementer {


    @Override
    boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        String propertyName = establishPropertyName(methodNode, prefix, domainClass)
        if(propertyName) {
            ClassNode propertyType = AstPropertyResolveUtils.getPropertyType(domainClass, propertyName)
            if(isValidPropertyType(resolveProjectionReturnType(returnType), propertyType)) {
                return true
            }
        }
        return false
    }

    @Override
    protected ClassNode resolveDomainClassFromSignature(ClassNode currentDomainClassNode, MethodNode methodNode) {
        return currentDomainClassNode
    }

    protected ClassNode resolveProjectionReturnType(ClassNode returnType) {
        return returnType
    }


    @Override
    Iterable<String> getHandledPrefixes() {
        return FindOneImplementer.HANDLED_PREFIXES
    }

}
