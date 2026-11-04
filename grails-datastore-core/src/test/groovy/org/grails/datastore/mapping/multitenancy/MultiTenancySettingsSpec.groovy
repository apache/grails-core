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
package org.grails.datastore.mapping.multitenancy

import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.Handle
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes

import spock.lang.Specification

import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode
import org.grails.datastore.mapping.multitenancy.resolvers.NoTenantResolver

/**
 * Covers the multi tenancy settings every mapping context reads as it is created.
 */
class MultiTenancySettingsSpec extends Specification {

    void 'settings with nothing configured mean no multi tenancy'() {
        when:
            def settings = new MultiTenancySettings()

        then:
            settings.mode == MultiTenancyMode.NONE
            settings.tenantResolver instanceof NoTenantResolver
    }

    void 'a configured resolver class is created when the resolver is asked for'() {
        when:
            def settings = new MultiTenancySettings().tenantResolverClass(NoTenantResolver)

        then:
            settings.tenantResolver instanceof NoTenantResolver
    }

    void 'a mode sharing a connection resolves every tenant to the default connection'() {
        expect:
            MultiTenancySettings.resolveConnectionForTenantId(mode, 'tenant') == connection

        where:
            mode                              || connection
            MultiTenancyMode.DATABASE         || 'tenant'
            MultiTenancyMode.SCHEMA           || ConnectionSource.DEFAULT
            MultiTenancyMode.DISCRIMINATOR    || ConnectionSource.DEFAULT
    }

    void 'the modes are created without dynamic dispatch'() {
        when: 'what runs while the mode enum is initialized is read: its static initializer and the constructors it calls'
            def dynamicCalls = dynamicCallsWhileInitializing(MultiTenancyMode)

        then: 'every mapping context reads the mode as it is created, and a native image cannot resolve a ' +
                'dynamic call for a JDK class nothing registered -- it failed creating the map each constant is built with'
            !dynamicCalls.contains('init')
    }

    private static List<String> dynamicCallsWhileInitializing(Class<?> type) {
        List<String> calls = []
        InputStream input = type.getResourceAsStream('/' + type.name.replace('.', '/') + '.class')
        try {
            new ClassReader(input.bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name != '<clinit>' && name != '<init>') {
                        return null
                    }
                    new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        void visitInvokeDynamicInsn(String methodName, String methodDescriptor, Handle bootstrap, Object... bootstrapArguments) {
                            calls << methodName
                        }
                    }
                }
            }, 0)
        }
        finally {
            input.close()
        }
        calls
    }
}
