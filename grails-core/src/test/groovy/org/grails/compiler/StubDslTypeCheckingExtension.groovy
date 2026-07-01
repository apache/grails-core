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
package org.grails.compiler

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.MethodCall
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.transform.stc.GroovyTypeCheckingExtensionSupport.TypeCheckingDSL

/**
 * Test fixture that mimics a DSL-handling type-checking extension (such as a criteria
 * extension): it resolves unrecognised implicit-{@code this} method calls by making
 * them dynamic. Used to verify that {@link TagLibraryInvokerTypeCheckingExtension}
 * composes with other extensions that also resolve unrecognised calls, rather than
 * both contributing a candidate method node and producing an "ambiguous method" error.
 */
class StubDslTypeCheckingExtension extends TypeCheckingDSL {

    @Override
    Object run() {
        methodNotFound { ClassNode receiver, String name, ArgumentListExpression argList, ClassNode[] argTypes, MethodCall call ->
            if (call instanceof MethodCallExpression && call.implicitThis) {
                return makeDynamic(call)
            }
            null
        }
        null
    }
}
