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

import java.lang.reflect.Modifier

import groovy.transform.Canonical
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.SourceUnit
import org.springframework.core.Ordered
import spock.lang.Specification

import grails.compiler.ast.AstTransformer
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.reflect.AstUtils

class GormTransformerSpec extends Specification {

    GormTransformer transformer = new GormTransformer()

    void "the transformer runs first and targets domain artefacts"() {
        expect:
        transformer.order == Ordered.HIGHEST_PRECEDENCE
        transformer.artefactTypes == ['Domain'] as String[]
        GormTransformer.getAnnotation(AstTransformer) != null
        transformer.shouldInject(new URL('file:/app/grails-app/domain/demo/Book.groovy'))
        !transformer.shouldInject(new URL('file:/app/grails-app/services/demo/BookService.groovy'))
        !transformer.shouldInject(new URL('file:/app/src/main/groovy/demo/Book.groovy'))
        GormTransformer.knownEntityNames.toList() == AstUtils.knownEntityNames.toList()
    }

    void "domain artefacts are enhanced into gorm entities"() {
        when:
        Class cls = new GroovyClassLoader().parseClass('''
            @grails.artefact.Artefact('Domain')
            class GtBook {
                String title
            }
        ''')

        then:
        GormEntity.isAssignableFrom(cls)
        cls.getAnnotation(grails.gorm.annotation.Entity) != null
    }

    void "canonical domain classes are rejected with a compilation error"() {
        given:
        SourceUnit source = SourceUnit.create('GtCanonicalBook', 'class GtCanonicalBook { String title }')
        ClassNode node = new ClassNode('GtCanonicalBook', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        node.addAnnotation(new AnnotationNode(ClassHelper.make(Canonical)))

        when:
        transformer.performInjection(source, node)

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('Class [GtCanonicalBook] is marked with @groovy.transform.Canonical which is not supported for GORM entities.')

        when:
        transformer.performInjection(SourceUnit.create('GtCanonicalBook', 'class GtCanonicalBook { }'), null, node)

        then:
        e = thrown()
        e.message.contains('Class [GtCanonicalBook] is marked with @groovy.transform.Canonical which is not supported for GORM entities.')

        when:
        transformer.performInjectionOnAnnotatedClass(SourceUnit.create('GtCanonicalBook', 'class GtCanonicalBook { }'), node)

        then:
        e = thrown()
        e.message.contains('Class [GtCanonicalBook] is marked with @groovy.transform.Canonical which is not supported for GORM entities.')
    }

}
