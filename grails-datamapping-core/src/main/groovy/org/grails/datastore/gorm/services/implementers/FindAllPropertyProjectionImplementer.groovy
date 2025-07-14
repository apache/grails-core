/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

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
        if (propertyName == null) return false

        ClassNode propertyType = AstPropertyResolveUtils.getPropertyType(domainClass, propertyName)
        if (propertyType == null) return false

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
