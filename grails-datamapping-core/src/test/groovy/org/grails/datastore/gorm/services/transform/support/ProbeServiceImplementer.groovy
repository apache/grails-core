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
import org.codehaus.groovy.ast.stmt.BlockStatement

import org.grails.datastore.gorm.services.ServiceImplementer

import static org.codehaus.groovy.ast.tools.GeneralUtils.constX
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS

/**
 * Test-only {@link ServiceImplementer} registered via {@code META-INF/services} so that
 * {@code ServiceTransformation} loads it through the same {@link ServiceLoader} mechanism used by
 * real GORM implementation modules (see {@code grails-datamapping-rx}). It exists purely to give
 * {@link ProbeServiceImplementerAdapter} something to adapt, exercising the
 * {@code org.grails.datastore.gorm.services.implementers.AdaptedImplementer} handling in
 * {@code ServiceTransformation}.
 * <p>
 * It only ever matches an intentionally obscure method name so it can never interfere with any
 * other {@code @Service} compiled elsewhere in this module's test suite - the {@code ServiceLoader}
 * lookup in {@code ServiceTransformation} is cached for the lifetime of the test JVM, so this
 * implementer becomes part of every subsequent {@code @Service} compilation once loaded.
 *
 * @see ProbeServiceImplementerAdapter
 * @see AdaptedProbeServiceImplementer
 */
@CompileStatic
class ProbeServiceImplementer implements ServiceImplementer {

    static final String TARGET_METHOD_NAME = 'zzzProbeAdapterOnlyMethod'

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        return methodNode.name == TARGET_METHOD_NAME
    }

    @Override
    void implement(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode) {
        BlockStatement body = new BlockStatement()
        body.addStatement(returnS(constX(null)))
        newMethodNode.code = body
    }
}
