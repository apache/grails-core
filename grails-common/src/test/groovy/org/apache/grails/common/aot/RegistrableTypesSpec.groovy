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
package org.apache.grails.common.aot

import java.lang.reflect.Modifier

import org.springframework.asm.ClassWriter
import org.springframework.asm.ConstantDynamic
import org.springframework.asm.Handle
import org.springframework.asm.Label
import org.springframework.asm.MethodVisitor
import org.springframework.asm.Opcodes
import org.springframework.asm.Type

import spock.lang.Specification

/**
 * Covers which scanned types may be registered for reflection. Registering one that cannot be loaded
 * fails the image build rather than degrading at run time, which is why this is asked at all.
 */
class RegistrableTypesSpec extends Specification {

    ClassLoader loader = getClass().classLoader

    private InputStream bytecodeOf(Class<?> type) {
        loader.getResourceAsStream(type.name.replace('.', '/') + '.class')
    }

    void 'a type that loads may be registered'() {
        expect:
            RegistrableTypes.loads('java.lang.String', loader)
    }

    void 'a type that is absent may not'() {
        expect:
            !RegistrableTypes.loads('com.example.NotOnTheClasspath', loader)
    }

    void 'a nested type whose declaring class is absent may not'() {
        expect: 'this is the closure whose enclosing class extends something absent, which the ' +
                'closure reaches through invokedynamic and so never names itself'
            !RegistrableTypes.loads('com.example.Missing$_run_closure1', loader)
    }

    void 'a nested type whose declaring class loads may be'() {
        expect:
            RegistrableTypes.loads(Outer.Inner.name, loader)
    }

    void 'bytecode naming only types that load may be registered'() {
        expect:
            RegistrableTypes.referencesLoad(bytecodeOf(Outer), loader)
    }

    void 'bytecode is rejected when it cannot be read'() {
        expect:
            !RegistrableTypes.referencesLoad(new ByteArrayInputStream('not a class'.bytes), loader)
    }

    void 'a class loader without the framework on it accepts none of its types'() {
        given: 'a bootstrap-only loader, which still has the JDK but nothing else'
            ClassLoader empty = new URLClassLoader(new URL[0], null)

        expect:
            !RegistrableTypes.loads(RegistrableTypes.name, empty)
            RegistrableTypes.loads('java.lang.String', empty)
    }

    void 'bytecode is rejected wherever the absent type is named'() {
        expect: 'each of these names it once and nowhere else, so each stands or falls on its own'
            !RegistrableTypes.referencesLoad(namingAbsent(shape), loader)

        where:
            shape << Shape.values()
    }

    void 'the same shapes naming a type that loads are accepted'() {
        expect: 'so the rejections above are the absent type and not the shape it was named in'
            RegistrableTypes.referencesLoad(naming('java/lang/Number', shape), loader)

        where:
            shape << Shape.values()
    }

    void 'the utility class cannot be instantiated by accident'() {
        given:
            def constructor = RegistrableTypes.getDeclaredConstructor()

        expect:
            Modifier.isPrivate(constructor.modifiers)

        when:
            constructor.accessible = true
            constructor.newInstance()

        then:
            noExceptionThrown()
    }

    void 'bytecode is rejected when the absent type is named by a supertype'() {
        expect:
            !RegistrableTypes.referencesLoad(declaring(superName, interfaces), loader)

        where:
            superName            | interfaces
            'com/example/Absent' | null
            'java/lang/Object'   | ['com/example/Absent'] as String[]
    }

    void 'bytecode naming only types that load by its supertypes is accepted'() {
        expect:
            RegistrableTypes.referencesLoad(declaring('java/lang/Number', ['java/lang/Runnable'] as String[]), loader)
    }

    void 'bytecode is rejected when an instruction names the absent type'() {
        expect:
            !RegistrableTypes.referencesLoad(bodyOf(instruction), loader)

        where:
            instruction << [
                { MethodVisitor m -> m.visitTypeInsn(Opcodes.NEW, 'com/example/Absent') },
                { MethodVisitor m -> m.visitTypeInsn(Opcodes.ANEWARRAY, 'com/example/Absent') },
                { MethodVisitor m -> m.visitTypeInsn(Opcodes.CHECKCAST, '[[Lcom/example/Absent;') },
                { MethodVisitor m ->
                    m.visitFieldInsn(Opcodes.GETSTATIC, 'com/example/Absent', 'VALUE', 'Ljava/lang/Object;')
                },
                { MethodVisitor m ->
                    m.visitFieldInsn(Opcodes.GETSTATIC, 'java/lang/Object', 'VALUE', '[Lcom/example/Absent;')
                },
                { MethodVisitor m ->
                    m.visitMethodInsn(Opcodes.INVOKESTATIC, 'com/example/Absent', 'call', '()V', false)
                },
                { MethodVisitor m -> m.visitMultiANewArrayInsn('[[Lcom/example/Absent;', 2) },
                { MethodVisitor m -> m.visitLdcInsn(Type.getType('[Lcom/example/Absent;')) },
                { MethodVisitor m -> m.visitLdcInsn(Type.getMethodType('(Lcom/example/Absent;)V')) },
                { MethodVisitor m -> m.visitLdcInsn(handle('com/example/Absent', '()V')) }
            ]
    }

    void 'bytecode naming only types that load in those places is accepted'() {
        expect:
            RegistrableTypes.referencesLoad(bodyOf(instruction), loader)

        where:
            instruction << [
                { MethodVisitor m -> m.visitTypeInsn(Opcodes.NEW, 'java/lang/Object') },
                { MethodVisitor m -> m.visitTypeInsn(Opcodes.CHECKCAST, '[[Ljava/lang/String;') },
                { MethodVisitor m -> m.visitTypeInsn(Opcodes.CHECKCAST, '[I') },
                { MethodVisitor m ->
                    m.visitFieldInsn(Opcodes.GETSTATIC, 'java/lang/Boolean', 'TRUE', 'Ljava/lang/Boolean;')
                },
                { MethodVisitor m -> m.visitMultiANewArrayInsn('[[Ljava/lang/String;', 2) },
                { MethodVisitor m -> m.visitLdcInsn(Type.getMethodType('(Ljava/lang/String;)V')) },
                { MethodVisitor m -> m.visitLdcInsn('plain constant') },
                { MethodVisitor m -> m.visitLdcInsn(Type.getType(Number)) },
                { MethodVisitor m -> m.visitLdcInsn(handle('java/lang/Number', '()V')) }
            ]
    }

    void 'bytecode is rejected when a dynamic constant names the absent type'() {
        expect:
            !RegistrableTypes.referencesLoad(bodyOf { MethodVisitor m ->
                m.visitLdcInsn(dynamic(descriptor, owner, argument))
            }, loader)

        where:
            descriptor                 | owner                | argument
            'Lcom/example/Absent;'     | 'java/lang/Object'   | Type.getType(Number)
            'Ljava/lang/Object;'       | 'com/example/Absent' | Type.getType(Number)
            'Ljava/lang/Object;'       | 'java/lang/Object'   | Type.getObjectType('com/example/Absent')
    }

    void 'a dynamic constant naming only types that load is accepted'() {
        expect:
            RegistrableTypes.referencesLoad(bodyOf { MethodVisitor m ->
                m.visitLdcInsn(dynamic('Ljava/lang/Object;', 'java/lang/Object', Type.getType(Number)))
            }, loader)
    }

    private static Handle handle(String owner, String descriptor) {
        new Handle(Opcodes.H_INVOKESTATIC, owner, 'call', descriptor, false)
    }

    private static ConstantDynamic dynamic(String descriptor, String bootstrapOwner, Object argument) {
        new ConstantDynamic('constant', descriptor,
                new Handle(Opcodes.H_INVOKESTATIC, bootstrapOwner, 'bootstrap',
                        '(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;',
                        false),
                argument)
    }

    private InputStream declaring(String superName, String[] interfaces) {
        ClassWriter writer = new ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                'org/apache/grails/common/aot/Written', null, superName, interfaces)
        writer.visitEnd()
        new ByteArrayInputStream(writer.toByteArray())
    }

    private InputStream bodyOf(Closure<?> instruction) {
        ClassWriter writer = new ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                'org/apache/grails/common/aot/Written', null, 'java/lang/Object', null)
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, 'body', '()V', null, null)
        method.visitCode()
        instruction.call(method)
        method.visitInsn(Opcodes.RETURN)
        method.visitMaxs(0, 0)
        method.visitEnd()
        writer.visitEnd()
        new ByteArrayInputStream(writer.toByteArray())
    }

    /** The places a class file can name a type, one per shape. */
    private enum Shape {
        FIELD_TYPE, ARRAY_FIELD_TYPE, RETURN_TYPE, PARAMETER_TYPE, THROWN_TYPE,
        CLASS_LITERAL, CAUGHT_TYPE, CALL_ARGUMENT_TYPE, INVOKEDYNAMIC_ARGUMENT
    }

    private InputStream namingAbsent(Shape shape) {
        naming('com/example/Absent', shape)
    }

    /**
     * A class naming the given type in one place only.
     *
     * <p>Written rather than compiled because the point is the single mention: a fixture compiled
     * from source names its own package, its supertypes and whatever the compiler adds, and would
     * pass or fail for reasons other than the one under test.</p>
     */
    private InputStream naming(String internalName, Shape shape) {
        ClassWriter writer = new ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                'org/apache/grails/common/aot/Written', null, 'java/lang/Object', null)
        String descriptor = "L${internalName};"

        switch (shape) {
            case Shape.FIELD_TYPE ->
                    writer.visitField(Opcodes.ACC_PRIVATE, 'held', descriptor, null, null).visitEnd()
            case Shape.ARRAY_FIELD_TYPE ->
                    writer.visitField(Opcodes.ACC_PRIVATE, 'held', "[[${descriptor}", null, null).visitEnd()
            case Shape.RETURN_TYPE ->
                    writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                            'give', "()${descriptor}", null, null).visitEnd()
            case Shape.PARAMETER_TYPE ->
                    writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                            'take', "(${descriptor})V", null, null).visitEnd()
            case Shape.THROWN_TYPE ->
                    writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                            'fail', '()V', null, [internalName] as String[]).visitEnd()
            default -> withBody(writer, internalName, descriptor, shape)
        }

        writer.visitEnd()
        new ByteArrayInputStream(writer.toByteArray())
    }

    private void withBody(ClassWriter writer, String internalName, String descriptor, Shape shape) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, 'body', '()V', null, null)
        method.visitCode()
        switch (shape) {
            case Shape.CLASS_LITERAL -> {
                method.visitLdcInsn(Type.getObjectType(internalName))
                method.visitInsn(Opcodes.POP)
            }
            case Shape.CAUGHT_TYPE -> {
                Label start = new Label(), end = new Label(), handler = new Label()
                method.visitTryCatchBlock(start, end, handler, internalName)
                method.visitLabel(start)
                method.visitLabel(end)
                method.visitLabel(handler)
                method.visitInsn(Opcodes.POP)
            }
            case Shape.CALL_ARGUMENT_TYPE -> {
                // the owner is present; only what the call takes is not
                method.visitInsn(Opcodes.ACONST_NULL)
                method.visitInsn(Opcodes.ACONST_NULL)
                method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, 'java/lang/Object',
                        'equals', "(${descriptor})Z", false)
                method.visitInsn(Opcodes.POP)
            }
            case Shape.INVOKEDYNAMIC_ARGUMENT -> {
                Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC,
                        'java/lang/invoke/LambdaMetafactory', 'metafactory',
                        '(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;' +
                                'Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;' +
                                'Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)' +
                                'Ljava/lang/invoke/CallSite;',
                        false)
                method.visitInvokeDynamicInsn('run', '()Ljava/lang/Runnable;', bootstrap,
                        Type.getType('()V'), bootstrap, Type.getType("(${descriptor})V"))
                method.visitInsn(Opcodes.POP)
            }
            default -> throw new IllegalArgumentException("${shape} has no body")
        }
        method.visitInsn(Opcodes.RETURN)
        method.visitMaxs(3, 1)
        method.visitEnd()
    }

    static class Outer {

        String describe() {
            new Inner().toString()
        }

        static class Inner {
        }
    }
}
