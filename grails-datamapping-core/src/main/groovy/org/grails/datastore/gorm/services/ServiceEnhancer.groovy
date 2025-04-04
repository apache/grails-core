/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.services

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode

/**
 * <p>Complement to the {@link ServiceImplementer} which only runs if another implementer has not already implemented the method.
 * The ServiceEnhancer on other hand will run even if a prior implementer has implemented the method</p>
 *
 * <p>The interface also allows implementation to differentiate between implementation and enhancement operations</p>
 *
 * @author Graeme Rocher
 * @since 6.1
 *
 */
interface ServiceEnhancer extends ServiceImplementer {
    /**
     * Does this service enhancer enhance the method
     *
     * @param domainClass The domain class that this implementor applies to. If not known will be equal to ClassHelper.OBJECT_TYPE
     * @param methodNode The abstract method node
     * @return True if it does
     */
    boolean doesEnhance(ClassNode domainClass, MethodNode methodNode)

    /**
     * Enhances the method
     *
     * @param targetClassNode The target class node
     * @param abstractMethodNode The abstract method node to implement
     * @param newMethodNode The new method node being implemented
     */
    void enhance(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode)
}