package org.grails.datastore.gorm.services.implementers

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.grails.datastore.mapping.reflect.AstUtils

/**
 * Implements countBy* methods on {@link grails.gorm.services.Service} instances
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class CountByImplementer extends FindAllByImplementer implements SingleResultServiceImplementer<Number> {
    static final List<String> HANDLED_PREFIXES = ['countBy']


    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        return AstUtils.isNumberType(returnType)
    }

    @Override
    Iterable<String> getHandledPrefixes() {
        return HANDLED_PREFIXES
    }

    @Override
    protected String getDynamicFinderPrefix() {
        return "countBy"
    }
}
