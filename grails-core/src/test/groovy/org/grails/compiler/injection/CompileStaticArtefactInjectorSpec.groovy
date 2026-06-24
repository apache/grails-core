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

import grails.artefact.Artefact
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import spock.lang.Specification

/**
 * Verifies that {@link CompileStaticArtefactInjector} opts controllers and services into
 * {@code @GrailsCompileStatic} only when the corresponding build opt-in is enabled, while honouring an
 * explicit per-class {@code @CompileDynamic} opt-out.
 *
 * <p>The injector is exercised the same way the production global transform invokes it: the
 * {@code @Artefact} annotation is stamped onto the class node and
 * {@link CompileStaticArtefactInjector#performInjectionOnAnnotatedClass} is called during the
 * canonicalization phase, then compilation continues so static type checking runs. A method call on a
 * strongly typed receiver that does not exist ({@code Integer#noSuchMethodHere()}) compiles fine
 * dynamically but is rejected by static compilation, which makes it a reliable probe for whether
 * {@code @GrailsCompileStatic} was applied. It is invoked on a typed local (not {@code this}) so the
 * controller tag library type checking extension does not silence it.</p>
 */
class CompileStaticArtefactInjectorSpec extends Specification {

    void cleanup() {
        System.clearProperty(CompileStaticArtefactInjector.COMPILE_STATIC_CONTROLLERS_PROPERTY)
        System.clearProperty(CompileStaticArtefactInjector.COMPILE_STATIC_SERVICES_PROPERTY)
        System.clearProperty(CompileStaticArtefactInjector.COMPILE_STATIC_TAGLIBS_PROPERTY)
    }

    void 'a controller is compiled statically when the controllers opt-in is enabled'() {
        given:
        System.setProperty(CompileStaticArtefactInjector.COMPILE_STATIC_CONTROLLERS_PROPERTY, 'true')

        when:
        compileArtefact('Controller', 'StaticController')

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('noSuchMethodHere')
    }

    void 'a controller is compiled dynamically when the controllers opt-in is disabled'() {
        when:
        compileArtefact('Controller', 'DynamicController')

        then:
        noExceptionThrown()
    }

    void 'a controller may opt out with @CompileDynamic even when the opt-in is enabled'() {
        given:
        System.setProperty(CompileStaticArtefactInjector.COMPILE_STATIC_CONTROLLERS_PROPERTY, 'true')

        when:
        compileArtefact('Controller', 'OptedOutController', '@groovy.transform.CompileDynamic')

        then:
        noExceptionThrown()
    }

    void 'a service is compiled statically when the services opt-in is enabled'() {
        given:
        System.setProperty(CompileStaticArtefactInjector.COMPILE_STATIC_SERVICES_PROPERTY, 'true')

        when:
        compileArtefact('Service', 'StaticService')

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('noSuchMethodHere')
    }

    void 'a service is not affected by the controllers opt-in'() {
        given: 'only the controllers opt-in is enabled'
        System.setProperty(CompileStaticArtefactInjector.COMPILE_STATIC_CONTROLLERS_PROPERTY, 'true')

        when:
        compileArtefact('Service', 'UnaffectedService')

        then:
        noExceptionThrown()
    }

    void 'a tag library is compiled statically when the taglibs opt-in is enabled'() {
        given:
        System.setProperty(CompileStaticArtefactInjector.COMPILE_STATIC_TAGLIBS_PROPERTY, 'true')

        when:
        compileArtefact('TagLib', 'StaticTagLib')

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('noSuchMethodHere')
    }

    void 'a tag library is compiled dynamically when the taglibs opt-in is disabled'() {
        when:
        compileArtefact('TagLib', 'DynamicTagLib')

        then:
        noExceptionThrown()
    }

    void 'a tag library may opt out with @CompileDynamic even when the opt-in is enabled'() {
        given:
        System.setProperty(CompileStaticArtefactInjector.COMPILE_STATIC_TAGLIBS_PROPERTY, 'true')

        when:
        compileArtefact('TagLib', 'OptedOutTagLib', '@groovy.transform.CompileDynamic')

        then:
        noExceptionThrown()
    }

    void 'a tag library is not affected by the controllers opt-in'() {
        given: 'only the controllers opt-in is enabled'
        System.setProperty(CompileStaticArtefactInjector.COMPILE_STATIC_CONTROLLERS_PROPERTY, 'true')

        when:
        compileArtefact('TagLib', 'UnaffectedTagLib')

        then:
        noExceptionThrown()
    }

    /**
     * Compiles a single artefact class, reproducing how the production global transform drives the
     * injector: stamp {@code @Artefact(type)} and run the injector at canonicalization, then compile
     * through to class generation so any applied static type checking is enforced. Throws
     * {@link MultipleCompilationErrorsException} if static compilation rejects the class.
     */
    private void compileArtefact(String type, String className, String extraAnnotation = '') {
        CompilationUnit cu = new CompilationUnit(new GroovyClassLoader())
        cu.addSource(className, """
            ${extraAnnotation}
            class ${className} {
                def execute() {
                    Integer number = 5
                    number.noSuchMethodHere()
                }
            }
        """)
        CompileStaticArtefactInjector injector = new CompileStaticArtefactInjector()
        cu.addPhaseOperation(new CompilationUnit.PrimaryClassNodeOperation() {
            @Override
            void call(SourceUnit source, GeneratorContext context, ClassNode cn) throws CompilationFailedException {
                if (cn.nameWithoutPackage == className) {
                    AnnotationNode artefact = new AnnotationNode(ClassHelper.make(Artefact))
                    artefact.addMember('value', new ConstantExpression(type))
                    cn.addAnnotation(artefact)
                    injector.performInjectionOnAnnotatedClass(source, cn)
                }
            }
        }, Phases.CANONICALIZATION)
        cu.compile(Phases.CLASS_GENERATION)
    }
}
