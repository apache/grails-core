package org.grails.datastore.gorm.services.implementers

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.MethodNode
import org.grails.datastore.gorm.transform.AstPropertyResolveUtils

import static org.grails.datastore.mapping.reflect.AstUtils.implementsInterface

/**
 * Support for projections that return multiple results
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class FindAllPropertyProjectionImplementer extends AbstractProjectionImplementer implements IterableProjectionServiceImplementer {
    @Override
    boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        boolean isCompatibleReturnType = false
        String propertyName = establishPropertyName(methodNode, prefix, domainClass)
        if(propertyName == null) return false

        ClassNode propertyType = AstPropertyResolveUtils.getPropertyType(domainClass, propertyName)
        if(propertyType == null) return false

        if (returnType.name == Iterable.name || implementsInterface(returnType, Iterable.name)) {
            GenericsType[] genericsTypes = returnType.genericsTypes
            if (genericsTypes.length > 0) {
                ClassNode concreteType = genericsTypes[0].type
                if (concreteType != null && isValidPropertyType(concreteType, propertyType)) {
                    isCompatibleReturnType = true
                }
            }
        } else if (returnType.isArray()) {

            ClassNode componentType = returnType.componentType
            if (componentType != null && isValidPropertyType(componentType, propertyType)) {
                isCompatibleReturnType = true
            }
        }
        return isCompatibleReturnType
    }

    @Override
    Iterable<String> getHandledPrefixes() {
        return FindAllImplementer.HANDLED_PREFIXES
    }

    @Override
    protected String getQueryMethodToInvoke(ClassNode domainClassNode, MethodNode newMethodNode) {
        return "list"
    }
}
