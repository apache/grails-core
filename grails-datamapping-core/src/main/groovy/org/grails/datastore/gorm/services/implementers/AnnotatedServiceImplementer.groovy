package org.grails.datastore.gorm.services.implementers

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode

/**
 * An annotated implementer
 *
 * @param <A> The annotation type
 */
interface AnnotatedServiceImplementer<A> extends PrefixedServiceImplementer {
    /**
     * Is the method annotated
     *
     * @param domainClass The domain class node
     * @param methodNode The method node
     * @return Whether it is annotated
     */
    boolean isAnnotated(ClassNode domainClass, MethodNode methodNode)
}