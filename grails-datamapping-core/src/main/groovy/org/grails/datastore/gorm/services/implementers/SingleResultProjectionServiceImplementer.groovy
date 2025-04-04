package org.grails.datastore.gorm.services.implementers

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.grails.datastore.gorm.GormEntity

/**
 * Represents a projection implementer
 *
 * @author Graeme Rocher
 * @since 6.1.1
 */
interface SingleResultProjectionServiceImplementer extends SingleResultServiceImplementer<GormEntity> {

    /**
     * Is the return type compatible with the projection query
     *
     * @param domainClass The domain class
     * @param methodNode the method noe
     * @param prefix The resolved prefix
     * @return True if it is
     */
    boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix)
}

