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

package org.grails.databinding.compiler

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.SourceUnit

import grails.databinding.BindingFormat
import org.apache.grails.common.compiler.GroovyTransformOrder
import spock.lang.Specification

class BindingFormatASTTransformationSpec extends Specification {

    private SourceUnit newSource() {
        SourceUnit.create('Test.groovy', 'class Test {}')
    }

    private FieldNode newField() {
        def owner = new ClassNode('com.demo.SomeClass', 1, ClassHelper.OBJECT_TYPE)
        owner.addField('someProperty', 1, ClassHelper.STRING_TYPE, null)
    }

    void 'visit rejects nodes that are not an annotation and a field'() {
        given:
        def transformation = new BindingFormatASTTransformation()

        when:
        transformation.visit([ClassHelper.STRING_TYPE, newField()] as ASTNode[], newSource())

        then:
        RuntimeException e = thrown()
        e.message.startsWith('Internal error: wrong types')
    }

    void 'visit rejects a non field parent node'() {
        given:
        def transformation = new BindingFormatASTTransformation()

        when:
        transformation.visit([new AnnotationNode(ClassHelper.make(BindingFormat)), ClassHelper.STRING_TYPE] as ASTNode[], newSource())

        then:
        RuntimeException e = thrown()
        e.message.startsWith('Internal error: wrong types')
    }

    void 'visit reports an error for an annotation without members'() {
        given:
        def transformation = new BindingFormatASTTransformation()

        when:
        transformation.visit([new AnnotationNode(ClassHelper.make(BindingFormat)), newField()] as ASTNode[], newSource())

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('The @BindingFormat annotation on the field [someProperty] in class [com.demo.SomeClass] must provide a value for either the value() or code() attribute.')
    }

    void 'visit accepts an annotation with a code or value member'() {
        given:
        def transformation = new BindingFormatASTTransformation()
        def source = newSource()
        def annotation = new AnnotationNode(ClassHelper.make(BindingFormat))
        annotation.addMember(member, new ConstantExpression('foo'))

        when:
        transformation.visit([annotation, newField()] as ASTNode[], source)

        then:
        source.errorCollector.errorCount == 0

        where:
        member << ['code', 'value']
    }

    void 'priority is the binding format transform order'() {
        expect:
        new BindingFormatASTTransformation().priority() == GroovyTransformOrder.BINDING_FORMAT_ORDER
    }
}
