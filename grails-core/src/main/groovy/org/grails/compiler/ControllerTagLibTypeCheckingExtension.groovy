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
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCall
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.transform.stc.GroovyTypeCheckingExtensionSupport
import org.grails.core.artefact.ControllerArtefactHandler

/**
 * A type-checking extension that allows {@code @GrailsCompileStatic} controllers
 * to invoke tag library methods without compile-time errors.
 *
 * <p>Tag calls in controllers are dispatched at runtime through
 * {@code TagLibraryInvoker#methodMissing} and
 * {@code TagLibraryInvoker#propertyMissing}. These hooks are
 * invisible to the static type checker, so this extension marks the affected
 * expressions as dynamic, silencing the false-positive errors while preserving
 * full type checking for all other code in the controller.
 *
 * <p>Controller detection mirrors {@code ControllerActionTransformer}: a class is
 * treated as a controller when its qualified name ends with {@code "Controller"}.
 * Note that inner classes whose simple name ends with {@code "Controller"} (e.g.,
 * a {@code FooController} defined inside a service) will also receive this
 * silencing treatment.
 *
 * <p>Two calling patterns are supported:
 * <ul>
 *   <li>Direct calls on {@code this}: {@code link(controller: 'home')},
 *       {@code message(code: 'key')}</li>
 *   <li>Namespaced calls via a namespace dispatcher property:
 *       {@code g.message(code: 'key')}, {@code my.customTag(attr: 'val')}</li>
 * </ul>
 *
 * <p><strong>Limitation — tag-as-property:</strong> Accessing a tag as a property
 * (e.g., {@code def t = link}) compiles without error because the unresolved
 * property is silenced, but will throw {@code MissingPropertyException} at runtime.
 * {@code TagLibraryInvoker#propertyMissing} only returns a
 * {@code NamespacedTagDispatcher} for namespace names, not for individual tag names.
 * Use the method-call form ({@code link(...)}) instead.
 *
 * <p><strong>Scope note:</strong> Unresolved variable references in controllers are
 * made dynamic so that namespace dispatcher calls (e.g., {@code g.message(...)})
 * compile. As a consequence, an undeclared identifier used as a dispatch receiver
 * (e.g., a misspelled service name like {@code svcTypo.list()}) will also compile
 * without error. Type-safety for method calls on <em>declared</em> fields and
 * local variables is fully preserved.
 *
 * @since 8.0
 */
class ControllerTagLibTypeCheckingExtension extends GroovyTypeCheckingExtensionSupport.TypeCheckingDSL {

    @Override
    Object run() {
        setup { newScope() }

        finish { scopeExit() }

        beforeVisitClass { ClassNode classNode ->
            newScope {
                isController = classNode.name.endsWith(ControllerArtefactHandler.TYPE)
                dynamicNamespaceProperties = [] as Set
            }
        }

        afterVisitClass { ClassNode classNode ->
            scopeExit()
        }

        unresolvedVariable { VariableExpression ve ->
            if (currentScope?.isController) {
                currentScope.dynamicNamespaceProperties << ve
                return makeDynamic(ve)
            }
            null
        }

        unresolvedProperty { PropertyExpression pe ->
            if (currentScope?.isController && isThisReceiver(pe)) {
                currentScope.dynamicNamespaceProperties << pe
                return makeDynamic(pe)
            }
            null
        }

        methodNotFound { ClassNode receiver, String name, ArgumentListExpression argList, ClassNode[] argTypes, MethodCall call ->
            if (!currentScope?.isController) return null
            if (isThisReceiver(call)) return makeDynamic(call)
            if (call instanceof MethodCallExpression && call.objectExpression in currentScope.dynamicNamespaceProperties) return makeDynamic(call)
            null
        }

        null
    }

    private boolean isThisReceiver(Expression expr) {
        if (!(expr instanceof MethodCallExpression || expr instanceof PropertyExpression)) return false
        expr.implicitThis || (expr.objectExpression instanceof VariableExpression && expr.objectExpression.thisExpression)
    }
}
