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
import org.codehaus.groovy.ast.MethodNode
import org.grails.datastore.gorm.transform.AstPropertyResolveUtils

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
