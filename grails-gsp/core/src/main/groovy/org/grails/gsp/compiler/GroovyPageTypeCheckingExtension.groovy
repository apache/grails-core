/*
 * Copyright 2016 the original author or authors.
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

package org.grails.gsp.compiler

import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.transform.stc.GroovyTypeCheckingExtensionSupport

/**
 * CompileStatic type checking extension for GSPs
 *
 * This makes all unresolved property, variable and method calls dynamic
 *
 */
class GroovyPageTypeCheckingExtension extends GroovyTypeCheckingExtensionSupport.TypeCheckingDSL {
    @Override
    Object run() {
        ClassNode configAnnotationClassNode = ClassHelper.make(GroovyPageTypeCheckingConfig)

        beforeVisitClass { ClassNode classNode ->
            newScope {
                allowedTagLibs = [] as Set
                dynamicProperties = [] as Set
            }
            AnnotationNode configAnnotation = classNode.getAnnotations(configAnnotationClassNode)?.find{it}
            if (configAnnotation) {
                Expression taglibsExpression = configAnnotation.getMember('taglibs')
                if (taglibsExpression instanceof ListExpression) {
                    currentScope.allowedTagLibs = ListExpression.cast(taglibsExpression).expressions.collect([] as Set) { it.text.trim() }
                }
            }
        }

        unresolvedProperty { PropertyExpression pe ->
            if (isThisTheReceiver(pe) && currentScope.allowedTagLibs.contains(pe.propertyAsString)) {
                currentScope.dynamicProperties << pe
                return makeDynamic(pe)
            }
        }

        unresolvedVariable { VariableExpression ve ->
            if (currentScope.allowedTagLibs.contains(ve.name)) {
                currentScope.dynamicProperties << ve
                return makeDynamic(ve)
            }
        }

        methodNotFound { receiver, name, argList, argTypes, call ->
            if (isThisTheReceiver(call) || (call.objectExpression != null && currentScope.dynamicProperties.contains(call.objectExpression))) {
                return makeDynamic(call)
            }
        }
    }

    def isThisTheReceiver(expr) {
        expr.implicitThis || (expr.objectExpression instanceof VariableExpression && expr.objectExpression.thisExpression)
    }
}
