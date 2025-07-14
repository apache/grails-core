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
package org.grails.cli.boot

import grails.util.Environment
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.grails.cli.compiler.AstUtils
import org.grails.cli.compiler.CompilerAutoConfiguration
import org.grails.cli.compiler.DependencyCustomizer

/**
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GrailsTestCompilerAutoConfiguration extends CompilerAutoConfiguration {

    public static final String[] DEFAULT_IMPORTS = [
            "spock.lang",
            "grails.test.mixin",
            "grails.test.mixin.integration",
            "grails.test.mixin.support",
            "grails.artefact"] as String[]

    ClassNode lastMatch = null

    @Override
    boolean matches(ClassNode classNode) {
        def matches = AstUtils.subclasses(classNode, "Specification")
        if (matches) {
            lastMatch = classNode
        }
        return matches
    }

    @Override
    void applyImports(ImportCustomizer imports) throws CompilationFailedException {
        imports.addStarImports(DEFAULT_IMPORTS);
    }

    @Override
    void applyDependencies(DependencyCustomizer dependencies) throws CompilationFailedException {
        if (lastMatch != null) {
            def annotation = GrailsApplicationCompilerAutoConfiguration.createGrabAnnotation("org.apache.grails.testing", "grails-testing-support-core", Environment.class.getPackage().getImplementationVersion(), null, null, true)
            lastMatch.addAnnotation(annotation);
        }
    }
}
