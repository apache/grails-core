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
package org.grails.compiler.injection

import groovy.transform.CompileStatic
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.Handle
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes

/**
 * The calls a class makes in its static initializer, as {@code INVOKESTATIC owner.name} for a direct
 * static call and {@code INVOKEDYNAMIC name} for a call dispatched dynamically.
 *
 * <p>A native image runs a class's static initializer before anything could register reflection
 * metadata for what it calls, so a call there that goes through Groovy's dynamic dispatch fails in an
 * image however the application was traced. Reading the bytecode is the one way to see which kind a
 * call compiled to.</p>
 */
@CompileStatic
class StaticInitializerCalls {

    static List<String> of(byte[] classFile) {
        List<String> calls = []
        new ClassReader(classFile).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name != '<clinit>') {
                    return null
                }
                new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                        calls << "${opcode == Opcodes.INVOKESTATIC ? 'INVOKESTATIC' : 'INVOKE'} ${owner}.${methodName}".toString()
                    }

                    @Override
                    void visitInvokeDynamicInsn(String methodName, String methodDescriptor, Handle bootstrap, Object... bootstrapArguments) {
                        calls << "INVOKEDYNAMIC ${methodName}".toString()
                    }
                }
            }
        }, 0)
        calls
    }

    static List<String> of(Class<?> type) {
        InputStream input = type.getResourceAsStream('/' + type.name.replace('.', '/') + '.class')
        try {
            return of(input.bytes)
        }
        finally {
            input.close()
        }
    }
}
