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

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCall
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.transform.stc.GroovyTypeCheckingExtensionSupport
import org.codehaus.groovy.transform.stc.StaticTypesMarker
import org.grails.core.artefact.ControllerArtefactHandler

/**
 * A type-checking extension that allows {@code @GrailsCompileStatic} controllers and
 * tag libraries to invoke tag library methods without compile-time errors.
 *
 * <p>Tag calls in controllers and tag libraries are dispatched at runtime through
 * {@code TagLibraryInvoker#methodMissing} and
 * {@code TagLibraryInvoker#propertyMissing}. These hooks are
 * invisible to the static type checker, so this extension marks the affected
 * expressions as dynamic, silencing the false-positive errors while preserving
 * full type checking for all other code in the class.
 *
 * <p>Detection is by name suffix: a class is treated as tag-dispatching when its
 * qualified name ends with {@code "Controller"} or {@code "TagLib"} (both carry the
 * {@code TagLibraryInvoker} trait at runtime). Note that inner classes whose simple
 * name ends with one of those suffixes will also receive this silencing treatment.
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
 * <p><strong>Scope note:</strong> Unresolved variable references in controllers and tag
 * libraries are made dynamic so that namespace dispatcher calls (e.g., {@code g.message(...)})
 * compile. As a consequence, an undeclared identifier used as a dispatch receiver
 * (e.g., a misspelled service name like {@code svcTypo.list()}) will also compile
 * without error. Type-safety for method calls on <em>declared</em> fields and
 * local variables is fully preserved.
 *
 * <p><strong>Composition with other extensions:</strong> because this is a catch-all
 * handler for unresolved calls in controllers and tag libraries, it must run <em>after</em> any other
 * type-checking extension that resolves DSL-style calls (e.g. a criteria extension).
 * When another extension has already resolved a call — flagged on the node via
 * {@code StaticTypesMarker.DYNAMIC_RESOLUTION} — this extension defers and leaves the
 * call unhandled, rather than contributing a second candidate method node (which would
 * make the call ambiguous). For this to work it is registered last in
 * {@code GrailsCompileStatic}.
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
                // Both controllers and tag libraries dispatch tags at runtime through the
                // TagLibraryInvoker trait, so both receive the same dynamic-dispatch silencing.
                // 'TagLib' is matched as a literal because grails-core has no dependency on the
                // taglib module; it mirrors org.grails.core.artefact.gsp.TagLibArtefactHandler.TYPE.
                isTagDispatcher = classNode.name.endsWith(ControllerArtefactHandler.TYPE) ||
                        classNode.name.endsWith('TagLib')
                dynamicNamespaceProperties = [] as Set
            }
        }

        afterVisitClass { ClassNode classNode ->
            scopeExit()
        }

        unresolvedVariable { VariableExpression ve ->
            if (currentScope?.isTagDispatcher && canMakeDynamic()) {
                currentScope.dynamicNamespaceProperties << ve
                return makeDynamic(ve)
            }
            null
        }

        unresolvedProperty { PropertyExpression pe ->
            if (currentScope?.isTagDispatcher && canMakeDynamic() && isThisReceiver(pe)) {
                currentScope.dynamicNamespaceProperties << pe
                return makeDynamic(pe)
            }
            null
        }

        methodNotFound { ClassNode receiver, String name, ArgumentListExpression argList, ClassNode[] argTypes, MethodCall call ->
            if (!currentScope?.isTagDispatcher || !canMakeDynamic()) return null
            if (isAlreadyResolved(call)) return null
            if (isThisReceiver(call)) return makeDynamic(call)
            if (call instanceof MethodCallExpression && call.objectExpression in currentScope.dynamicNamespaceProperties) return makeDynamic(call)
            null
        }

        null
    }

    /**
     * {@code makeDynamic} stores its dynamic-resolution marker on the enclosing method, so it can only be
     * used inside a method body. Tags defined the deprecated way - as closure fields rather than methods -
     * are type-checked as field initializers with no enclosing method; silencing there would throw an NPE.
     * In that case we defer, leaving the call to be reported as a normal type error rather than crashing
     * the compiler. Method-based tags (the supported form) always have an enclosing method.
     */
    private boolean canMakeDynamic() {
        getEnclosingMethod() != null
    }

    private boolean isThisReceiver(Expression expr) {
        if (!(expr instanceof MethodCallExpression || expr instanceof PropertyExpression)) return false
        expr.implicitThis || (expr.objectExpression instanceof VariableExpression && expr.objectExpression.thisExpression)
    }

    private boolean isAlreadyResolved(MethodCall call) {
        call instanceof ASTNode && ((ASTNode) call).getNodeMetaData(StaticTypesMarker.DYNAMIC_RESOLUTION) != null
    }
}
