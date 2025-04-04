package org.grails.datastore.gorm.services.implementers

import org.codehaus.groovy.ast.MethodNode
import org.grails.datastore.gorm.services.ServiceImplementer

/**
 * An implementer that uses a set of prefixes
 */
interface PrefixedServiceImplementer extends ServiceImplementer {
    /**
     * @return The handled prefixes
     */
    Iterable<String> getHandledPrefixes()

    /**
     * Resolve the prefix used for the given method
     * @param mn The method node
     * @return The prefix used or null if it isn't used
     */
    String resolvePrefix(MethodNode mn)
}