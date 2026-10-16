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
package org.grails.datastore.gorm.query.transform

import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import spock.lang.Specification

/**
 * {@link GlobalDetachedCriteriaASTTransformation} is a global transformation: it runs inside
 * whichever Groovy compiles a source unit that has this module on its classpath, which is not
 * always the Groovy this module was compiled with (Gradle compiles build scripts with its own,
 * older Groovy). Its first action is to instantiate {@link DetachedCriteriaTransformer}, so that
 * class' static initialisation must not depend on Groovy runtime helpers that differ between
 * Groovy versions, such as the {@code NumberMath} method the static compiler emits for {@code %}
 * ({@code mod} up to Groovy 4, {@code remainder} from Groovy 5). The array accessors of
 * {@code BytecodeInterface8} have been the same since Groovy 2 and are the only runtime calls allowed.
 */
class DetachedCriteriaTransformerClassInitSpec extends Specification {

    private static final String GROOVY_RUNTIME_PACKAGE = 'org/codehaus/groovy/runtime/'

    private static final String ARRAY_ACCESS_HELPER = 'org/codehaus/groovy/runtime/BytecodeInterface8.'

    void "the static initialisation of the transformer makes no Groovy runtime calls"() {
        given:
        String internalName = DetachedCriteriaTransformer.name.replace('.', '/')
        Map<String, List<String>> callsByMethod = [:]
        ClassReader reader = new ClassReader(DetachedCriteriaTransformer.getResourceAsStream("/${internalName}.class").bytes)
        reader.accept(new ClassVisitor(Opcodes.ASM9) {

            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                List<String> calls = []
                callsByMethod[name + descriptor] = calls
                return new MethodVisitor(Opcodes.ASM9) {

                    @Override
                    void visitMethodInsn(int opcode, String owner, String calledName, String calledDescriptor, boolean isInterface) {
                        calls << "${owner}.${calledName}${calledDescriptor}".toString()
                    }

                }
            }

        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)

        when: 'every method reachable from the static initialiser inside the class is followed'
        Set<String> visited = []
        List<String> pending = callsByMethod.keySet().findAll { it.startsWith('<clinit>') }.toList()
        while (pending) {
            String method = pending.pop()
            if (visited.add(method)) {
                callsByMethod[method].each { String call ->
                    if (call.startsWith(internalName + '.')) {
                        pending << call.substring(internalName.length() + 1)
                    }
                }
            }
        }
        List<String> runtimeCalls = visited.collectMany { String method ->
            callsByMethod[method]
                    .findAll { it.startsWith(GROOVY_RUNTIME_PACKAGE) && !it.startsWith(ARRAY_ACCESS_HELPER) }
                    .collect { "${method} -> ${it}".toString() }
        }

        then:
        visited.any { it.startsWith('newMap(') }
        runtimeCalls.empty
    }

}
