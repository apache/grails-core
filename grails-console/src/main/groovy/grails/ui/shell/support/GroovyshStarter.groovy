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
package grails.ui.shell.support

import groovy.transform.CompileStatic

import java.lang.reflect.Method

/**
 * Invokes {@code org.apache.groovy.groovysh.Main.start(Map)} via reflection.
 * <p>
 * The {@code start(Map)} entry point was added in Groovy 5.0.4 (GROOVY-11839).
 * This repository pins Groovy to 5.0.3 to avoid an ASM 9.9.1 bytecode bug
 * introduced in Groovy 5.0.4, so the method is not available at compile
 * time. Invocation is deferred to runtime where a user's application may
 * provide Groovy 5.0.4+ on the classpath.
 */
@CompileStatic
final class GroovyshStarter {

    private GroovyshStarter() {
    }

    static void start(Map<String, Object> bindings) {
        Class<?> mainClass
        try {
            mainClass = Class.forName('org.apache.groovy.groovysh.Main')
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException('Groovysh is not on the classpath', e)
        }
        Method startMethod
        try {
            startMethod = mainClass.getMethod('start', Map)
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    'Groovysh Main.start(Map) requires Groovy 5.0.4 or later on the runtime classpath',
                    e
            )
        }
        startMethod.invoke(null, bindings)
    }
}
