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
package org.grails.compiler.gorm

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import org.grails.core.artefact.DomainClassArtefactHandler
import spock.lang.Specification

/**
 * Exercises {@link GormTransformer} the same way the production global transform drives it: the injector's
 * {@code performInjection} is invoked directly on the parsed {@link ClassNode} during canonicalization, then
 * compilation continues to class generation.
 */
class GormTransformerSpec extends Specification {

    void "getArtefactTypes returns the domain class artefact type"() {
        expect:
        new GormTransformer().artefactTypes == [DomainClassArtefactHandler.TYPE] as String[]
    }

    void "a class marked with @Canonical fails compilation"() {
        when:
        compile('CanonicalBook', '''
            @groovy.transform.Canonical
            class CanonicalBook {
                String title
            }
        ''')

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('@groovy.transform.Canonical')
    }

    void "getKnownEntityNames includes a class after performInjection has visited it"() {
        given:
        String className = 'GormTransformerSpecKnownEntity'

        when:
        compileToPhase(className, """
            class ${className} {
                String name
            }
        """, Phases.CANONICALIZATION)

        then:
        GormTransformer.getKnownEntityNames().contains(className)
    }

    private void compile(String className, String source) {
        compileToPhase(className, source, Phases.CLASS_GENERATION)
    }

    private void compileToPhase(String className, String source, int phase) {
        CompilationUnit cu = new CompilationUnit(new GroovyClassLoader())
        cu.addSource(className, source)
        GormTransformer transformer = new GormTransformer()
        cu.addPhaseOperation(new CompilationUnit.PrimaryClassNodeOperation() {
            @Override
            void call(SourceUnit src, GeneratorContext context, ClassNode cn) throws CompilationFailedException {
                if (cn.nameWithoutPackage == className) {
                    transformer.performInjection(src, cn)
                }
            }
        }, Phases.CANONICALIZATION)
        cu.compile(phase)
    }
}
