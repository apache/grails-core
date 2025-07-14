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
import grails.compiler.ast.SupportsClassNode
import grails.compiler.traits.TraitInjector
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.grails.core.artefact.ControllerArtefactHandler
import spock.lang.Issue
import spock.lang.Specification

/**
 * @author James Kleeh
 */
class ArtefactTypeAstTransformationSpec extends Specification {

    void "test resolveArtefactType with string literal"() {
        given:
        ArtefactTypeAstTransformation ast = new ArtefactTypeAstTransformation()
        ClassNode classNode = ClassHelper.make(Object)
        AnnotationNode annotationNode = new AnnotationNode(ClassHelper.make(Artefact))
        annotationNode.addMember("value", new ConstantExpression("ABC"))

        when:
        String returnValue = ast.resolveArtefactType(null, annotationNode, classNode)

        then:
        returnValue == "ABC"
    }

    void "test resolveArtefactType with property expression"() {
        given:
        ArtefactTypeAstTransformation ast = new ArtefactTypeAstTransformation()
        ClassNode classNode = ClassHelper.make(Object)
        AnnotationNode annotationNode = new AnnotationNode(ClassHelper.make(Artefact))
        annotationNode.addMember("value",
                new PropertyExpression(
                        new ClassExpression(ClassHelper.make(ControllerArtefactHandler)), "TYPE"))

        when:
        String returnValue = ast.resolveArtefactType(null, annotationNode, classNode)

        then:
        returnValue == "Controller"
    }

    void "test resolveArtefactType with null"() {
        given:
        ArtefactTypeAstTransformation ast = new ArtefactTypeAstTransformation()
        ClassNode classNode = ClassHelper.make(Object)
        AnnotationNode annotationNode = new AnnotationNode(ClassHelper.make(Artefact))
        annotationNode.addMember("value", null)

        when:
        ast.resolveArtefactType(null, annotationNode, classNode)

        then:
        thrown(RuntimeException)
    }

	@Issue("https://github.com/apache/grails-core/issues/10531")
	void "TraitInjector without SupportsClassNode gets applied to artefacts"() {
		setup:
		TraitInjectionUtils.@traitInjectors = [new TestTraitInjector()]
		GrailsAwareClassLoader gcl = new GrailsAwareClassLoader()

		Class clazz = gcl.parseClass """
			 	@grails.artefact.Artefact("Controller")
				class FooController {
			
				}
			"""

		when:
		def t = clazz.newInstance()

		then:
		t instanceof Test10531Trait
		t.hello10531() == "Hello"

		cleanup:
		TraitInjectionUtils.@traitInjectors = null
	}

	@Issue("https://github.com/apache/grails-core/issues/10531")
	void "TraitInjector with SupportsClassNode gets applied only if supports return true"() {
		setup:
		TraitInjectionUtils.@traitInjectors = [new TestTraitInjectorForSupportsClassNode(false)]
		GrailsAwareClassLoader gcl = new GrailsAwareClassLoader()

		Class clazz = gcl.parseClass """
			 	@grails.artefact.Artefact("Controller")
				class FooController {
			
				}
			"""

		when: "Supports returns false"
		def t = clazz.newInstance()

		then: "Trait is not applied"
		!(t instanceof Test10531Trait)

		when:
		t.hello10531()

		then:
		thrown(MissingMethodException)

		when: "Supports returns true"
		TraitInjectionUtils.@traitInjectors = [new TestTraitInjectorForSupportsClassNode(true)]
		clazz = gcl.parseClass """
			 	@grails.artefact.Artefact("Controller")
				class BarController {
			
				}
			"""

		t = clazz.getDeclaredConstructor().newInstance()

		then: "Trait is applied"
		t instanceof Test10531Trait
		t.hello10531() == "Hello"

		cleanup:
		TraitInjectionUtils.@traitInjectors = null

	}

    //Inclusion to verify compilation
    @Artefact("Controller")
    class Test {
    }

    @Artefact(ControllerArtefactHandler.TYPE)
    class Test2 {

    }

    class TestTraitInjector implements TraitInjector {

		@Override
		Class getTrait() {
			return Test10531Trait
		}

		@Override
		String[] getArtefactTypes() {
			return ["Controller"]
		}
	}

	class TestTraitInjectorForSupportsClassNode implements TraitInjector, SupportsClassNode {
		boolean shouldSupport

		TestTraitInjectorForSupportsClassNode(boolean support) {
			this.shouldSupport = support
		}

		@Override
		Class getTrait() {
			return Test10531Trait
		}

		@Override
		String[] getArtefactTypes() {
			return ["Controller"]
		}

		@Override
		boolean supports(ClassNode classNode) {
			return shouldSupport
		}
	}

    trait Test10531Trait {
        def hello10531() { return "Hello" }
    }

}
