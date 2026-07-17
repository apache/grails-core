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

/*
 * Groovy compiler configuration script applied to every GroovyCompile task
 * (see org.apache.grails.buildsrc.CompilePlugin) to keep builds reproducible.
 *
 * When Groovy copies an annotation from a precompiled class (e.g. @DelegatesTo
 * on trait methods woven into controllers and GORM entities), it populates the
 * AnnotationNode member map from Class.getDeclaredMethods(), whose order is
 * unspecified and varies between JVM runs. The members are then written to the
 * class file in that map order, producing byte-level differences between
 * otherwise identical builds. Annotation member order carries no semantic
 * meaning, so sort the members alphabetically before bytecode generation.
 */

import org.codehaus.groovy.ast.expr.AnnotationConstantExpression
import org.codehaus.groovy.ast.expr.ListExpression

withConfig(configuration) {
    inline(phase: 'INSTRUCTION_SELECTION') { source, context, classNode ->
        def sortAnnotationMembers
        sortAnnotationMembers = { annotations ->
            annotations?.each { annotation ->
                def members = annotation.members
                if (members.size() > 1) {
                    def sorted = new TreeMap<>(members)
                    members.clear()
                    members.putAll(sorted)
                }
                members.values().each { value ->
                    if (value instanceof AnnotationConstantExpression) {
                        sortAnnotationMembers([value.value])
                    } else if (value instanceof ListExpression) {
                        sortAnnotationMembers(
                                value.expressions
                                        .findAll { it instanceof AnnotationConstantExpression }
                                        .collect { it.value }
                        )
                    }
                }
            }
        }
        def visitClass
        visitClass = { cn ->
            sortAnnotationMembers(cn.annotations)
            cn.fields.each { sortAnnotationMembers(it.annotations) }
            cn.properties.each { sortAnnotationMembers(it.annotations) }
            (cn.methods + cn.declaredConstructors).each { method ->
                sortAnnotationMembers(method.annotations)
                method.parameters.each { sortAnnotationMembers(it.annotations) }
            }
            cn.innerClasses.each { visitClass(it) }
        }
        visitClass(classNode)
    }
}
