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

package grails.compiler.ast

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.SourceUnit

/**
 * Helper super class to ease the creation of {@link AllArtefactClassInjector} implementations
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
abstract class GlobalClassInjectorAdapter implements GlobalClassInjector {

    Set<String> processesClassNames = []

    @Override
    void performInjection(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        performInjectionOnAnnotatedClass(source, classNode)
    }

    @Override
    void performInjection(SourceUnit source, ClassNode classNode) {
        performInjectionOnAnnotatedClass(source, classNode)
    }

    @Override
    void performInjectionOnAnnotatedClass(SourceUnit source, ClassNode classNode) {
        def className = classNode.name
        if(!processesClassNames.contains(className)) {
            performInjectionInternal source, classNode
            processesClassNames.add className
        }
    }

    abstract void performInjectionInternal(SourceUnit source, ClassNode classNode)

    @Override
    boolean shouldInject(URL url) {
        return true
    }
}
