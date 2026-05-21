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
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.MethodCall
import org.codehaus.groovy.ast.stmt.EmptyStatement
import org.codehaus.groovy.transform.stc.GroovyTypeCheckingExtensionSupport.TypeCheckingDSL

/**
 *
 * @since 3.3
 */
class NamedQueryTypeCheckingExtension extends TypeCheckingDSL {

    @Override
    Object run() {
        setup { newScope() }

        finish { scopeExit() }

        beforeVisitClass { ClassNode classNode ->
            def namedQueryProperty = classNode.getField('namedQueries')

            if (namedQueryProperty && namedQueryProperty.isStatic() && namedQueryProperty.initialExpression instanceof ClosureExpression) {
                newScope {
                    namedQueryClosureCode = namedQueryProperty.initialExpression.code
                }
                namedQueryProperty.initialExpression.code = new EmptyStatement()
            } else {
                newScope()
            }
        }

        afterVisitClass { ClassNode classNode ->
            if (currentScope.namedQueryClosureCode) {
                def namedQueryProperty = classNode.getField('namedQueries')
                namedQueryProperty.initialExpression.code = currentScope.namedQueryClosureCode
                currentScope.checkingNamedQueryClosure = true
            }
            scopeExit()
        }

        methodNotFound { ClassNode receiver, String name, ArgumentListExpression argList, ClassNode[] argTypes, MethodCall call ->
            def dynamicCall
            if (currentScope.namedQueryClosureCode && currentScope.checkingNamedQueryClosure) {
                dynamicCall = makeDynamic(call)
            }
            dynamicCall
        }

        null
    }
}
