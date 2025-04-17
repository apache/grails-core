/*
 * Copyright 2006-2007 Graeme Rocher
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
package org.grails.gsp;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.grails.gsp.compiler.transform.GroovyPageInjectionOperation;

import java.security.CodeSource;

/**
 * A class loader that is aware of Groovy Pages and injection operations.
 *
 * @author Stephane Maldini
 * @since 2.0
 */
public class GroovyPageClassLoader extends GroovyClassLoader {

    public GroovyPageClassLoader() {
        // default
    }

    public GroovyPageClassLoader(ClassLoader loader) {
        super(loader);
    }

    public GroovyPageClassLoader(GroovyClassLoader parent) {
        super(parent);
    }

    public GroovyPageClassLoader(ClassLoader parent, CompilerConfiguration config, boolean useConfigurationClasspath) {
        super(parent, config, useConfigurationClasspath);
    }

    public GroovyPageClassLoader(ClassLoader loader, CompilerConfiguration config) {
        super(loader, config);
    }

    /**
     * @see groovy.lang.GroovyClassLoader#createCompilationUnit(org.codehaus.groovy.control.CompilerConfiguration, java.security.CodeSource)
     */
    @Override
    protected CompilationUnit createCompilationUnit(CompilerConfiguration config, CodeSource source) {
        CompilationUnit cu = super.createCompilationUnit(config, source);

        GroovyPageInjectionOperation operation;

        operation = new GroovyPageInjectionOperation();

        cu.addPhaseOperation(operation, Phases.CANONICALIZATION);
        return cu;
    }
}
