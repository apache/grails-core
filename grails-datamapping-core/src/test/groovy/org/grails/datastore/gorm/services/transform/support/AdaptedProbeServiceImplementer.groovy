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
package org.grails.datastore.gorm.services.transform.support

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode

import org.grails.datastore.gorm.services.ServiceImplementer
import org.grails.datastore.gorm.services.implementers.AdaptedImplementer
import org.grails.datastore.mapping.core.Ordered

/**
 * The adapted form of {@link ProbeServiceImplementer} produced by {@link ProbeServiceImplementerAdapter}.
 * Declares an order lower than the default (un-ordered) precedence of {@link ProbeServiceImplementer}
 * so that {@code ServiceTransformation} always tries this adapted implementer first, guaranteeing its
 * {@code AdaptedImplementer} branch is exercised deterministically rather than depending on collection
 * ordering.
 */
@CompileStatic
class AdaptedProbeServiceImplementer implements ServiceImplementer, AdaptedImplementer, Ordered {

    private final ServiceImplementer adapted

    AdaptedProbeServiceImplementer(ServiceImplementer adapted) {
        this.adapted = adapted
    }

    @Override
    ServiceImplementer getAdapted() {
        return adapted
    }

    @Override
    int getOrder() {
        return 0
    }

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        return adapted.doesImplement(domainClass, methodNode)
    }

    @Override
    void implement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
        adapted.implement(domainClassNode, abstractMethodNode, newMethodNode, targetClassNode)
    }
}
