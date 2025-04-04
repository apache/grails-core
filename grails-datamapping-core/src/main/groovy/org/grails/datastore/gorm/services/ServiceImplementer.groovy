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
 * An interface for classes that provide implementations for service methods
 *
 * @author Graeme Rocher
 * @since 6.1
 */
interface ServiceImplementer {

    /**
     * Marker that should be stored on the method node once a method has been implemented
     */
    String IMPLEMENTED = "IMPLEMENTED"

    /**
     * Used to store the resolved return type when it has been resolved from generics within the ast metadata
     */
    String RETURN_TYPE = "RETURN_TYPE"
    /**
     * Does this service implement the method
     *
     * @param domainClass The domain class that this implementor applies to. If not known will be equal to ClassHelper.OBJECT_TYPE
     * @param methodNode The abstract method node
     * @return True if it does
     */
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode)

    /**
     * Implement the method
     *
     * @param targetClassNode The target class node
     * @param abstractMethodNode The abstract method node to implement
     * @param newMethodNode The new method node being implemented
     */
    void implement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode)
}