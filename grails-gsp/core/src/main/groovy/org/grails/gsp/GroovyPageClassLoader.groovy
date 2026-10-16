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
package org.grails.gsp

import java.security.CodeSource

import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases

import org.grails.gsp.compiler.transform.GroovyPageInjectionOperation

/**
 * A class loader that is aware of Groovy Pages and injection operations.
 *
 * @author Stephane Maldini
 * @since 2.0
 */
@CompileStatic
class GroovyPageClassLoader extends GroovyClassLoader {

    GroovyPageClassLoader() {
        // default
    }

    GroovyPageClassLoader(ClassLoader loader) {
        super(loader)
    }

    GroovyPageClassLoader(GroovyClassLoader parent) {
        super(parent)
    }

    GroovyPageClassLoader(ClassLoader parent, CompilerConfiguration config, boolean useConfigurationClasspath) {
        super(parent, config, useConfigurationClasspath)
    }

    GroovyPageClassLoader(ClassLoader loader, CompilerConfiguration config) {
        super(loader, config)
    }

    /**
     * @see groovy.lang.GroovyClassLoader#createCompilationUnit(org.codehaus.groovy.control.CompilerConfiguration, java.security.CodeSource)
     */
    @Override
    protected CompilationUnit createCompilationUnit(CompilerConfiguration config, CodeSource source) {
        CompilationUnit cu = super.createCompilationUnit(config, source)

        GroovyPageInjectionOperation operation

        operation = new GroovyPageInjectionOperation()

        cu.addPhaseOperation(operation, Phases.CANONICALIZATION)
        return cu
    }

}
