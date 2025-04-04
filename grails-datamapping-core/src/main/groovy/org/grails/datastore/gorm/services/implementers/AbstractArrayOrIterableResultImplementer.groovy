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
package org.grails.datastore.gorm.services.implementers

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.MethodNode
import org.grails.datastore.mapping.reflect.AstUtils

/**
 * Abstract implementation of a finder that handles Array and Iterables of domain classes
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
abstract class AbstractArrayOrIterableResultImplementer extends AbstractReadOperationImplementer {

    /**
     * Return true if the provided return type is compatible with this implementor. By default returns true of Iterable and Arrays of domain classes
     *
     * @param returnType The return type
     * @return True if it is a compatible return type
     */
    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        AstUtils.isIterableOrArrayOfDomainClasses(returnType)
    }

    @Override
    final void doImplement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
        ClassNode returnType = (ClassNode)newMethodNode.getNodeMetaData(RETURN_TYPE) ?: abstractMethodNode.returnType
        boolean isArray = returnType.isArray()
        ClassNode domainClassForReturnType = resolveDomainClassForReturnType(domainClassNode, isArray, returnType)
        if(AstUtils.isDomainClass(domainClassForReturnType)) {
            domainClassNode = domainClassForReturnType
        }
        doImplement(domainClassNode, targetClassNode, abstractMethodNode, newMethodNode, isArray)
    }

    /**
     *
     * Resolves the domain class type for the return type
     *
     * @param returnType The return type
     * @param isArray Whether the return type is an array
     * @return The domain class type
     */
    protected ClassNode resolveDomainClassForReturnType(ClassNode currentDomainClass, boolean isArray, ClassNode returnType) {
        if(returnType.isArray()) {
            return returnType.componentType
        }
        else {
            GenericsType[] genericTypes = returnType.genericsTypes
            if(genericTypes) {
                return genericTypes[0].type
            }
        }
        return currentDomainClass
    }

    /**
     * Implement the method for the given arguments
     *
     * @param domainClassNode The domain class being queried
     * @param targetClassNode The target class node being transformed
     * @param abstractMethodNode The abstract method
     * @param newMethodNode The new method being added
     * @param isArray Whether the return type is an array
     */
    abstract void doImplement(ClassNode domainClassNode,
                              ClassNode targetClassNode,
                              MethodNode abstractMethodNode,
                              MethodNode newMethodNode,
                              boolean isArray)
}
