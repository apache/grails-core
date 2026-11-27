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

package org.grails.datastore.mapping.reflect

import java.lang.reflect.Modifier

import groovy.transform.Trait
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.ErrorCollector
import org.codehaus.groovy.control.SourceUnit
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Created by graemerocher on 19/04/2017.
 */
class AstUtilsSpec extends Specification {

    private static final String GORM_ENTITY = 'org.grails.datastore.gorm.GormEntity'

    void 'the domain class verdict belongs to the node, so same-named nodes of different compilations do not share it'() {
        given: 'two classes of one name, as two tests that compile a fixture from a string produce, one a domain class'
        ClassNode domain = new ClassNode('Book', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        domain.addInterface(ClassHelper.make('org.grails.datastore.gorm.GormEntity'))
        ClassNode plain = new ClassNode('Book', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)

        expect:
        domain == plain
        AstUtils.isDomainClass(domain)
        !AstUtils.isDomainClass(plain)
        AstUtils.isDomainClass(domain)
        !AstUtils.isDomainClass(plain)
        !AstUtils.isDomainClass(null)
    }

    void "test implements interface"() {
        given:
        ClassNode node = ClassHelper.make("Test")
        def itfc = ClassHelper.make("ITest")
        node.addInterface(itfc)

        expect:
        AstUtils.implementsInterface(node, itfc)
        AstUtils.implementsInterface(node, itfc.name)
        !AstUtils.implementsInterface(node, "Another")
    }

    @Unroll
    void '#kind declared under grails-app/domain that #declaration is a domain class: #domainClass'() {
        given: 'a class node whose source is under grails-app/domain'
        ClassNode classNode = declaredUnderDomainDirectory(kind)

        and: 'what the class itself declares'
        if (marker == GORM_ENTITY) {
            classNode.addInterface(ClassHelper.make(GORM_ENTITY))
        } else if (marker != null) {
            classNode.addAnnotation(new AnnotationNode(ClassHelper.make(marker)))
        }

        expect: 'only a class the entity transformation turns into an entity is one by its location alone'
        AstUtils.isDomainClassUncached(classNode) == domainClass

        and: 'a property type that refers to the class gets the same verdict'
        AstUtils.isDomainClassUncached(referenceTo(classNode)) == domainClass

        where:
        kind             | marker                          || domainClass
        'a class'        | null                            || true
        'an enum'        | null                            || false
        'an interface'   | null                            || false
        'a trait'        | null                            || false
        'a nested class' | null                            || false
        'a nested class' | GORM_ENTITY                     || true
        'a nested class' | 'jakarta.persistence.Entity'    || true
        'a nested class' | 'grails.gorm.annotation.Entity' || true
        'an interface'   | GORM_ENTITY                     || true
        'a trait'        | GORM_ENTITY                     || true

        declaration = marker == null ? 'declares nothing else' : marker == GORM_ENTITY ? 'implements GormEntity' : "is annotated with @${marker}"
    }

    private static ClassNode declaredUnderDomainDirectory(String kind) {
        CompilerConfiguration configuration = new CompilerConfiguration()
        SourceUnit sourceUnit = new SourceUnit('/app/grails-app/domain/Book.groovy', '', configuration, null, new ErrorCollector(configuration))
        ClassNode book = new ClassNode('Book', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        ClassNode classNode = book
        switch (kind) {
            case 'an enum':
                classNode = new ClassNode('Status', Modifier.PUBLIC | Opcodes.ACC_ENUM, ClassHelper.make(Enum))
                break
            case 'an interface':
                classNode = new ClassNode('Named', Modifier.PUBLIC | Modifier.ABSTRACT | Opcodes.ACC_INTERFACE, ClassHelper.OBJECT_TYPE)
                break
            case 'a trait':
                classNode = new ClassNode('Titled', Modifier.PUBLIC | Modifier.ABSTRACT | Opcodes.ACC_INTERFACE, ClassHelper.OBJECT_TYPE)
                classNode.addAnnotation(new AnnotationNode(ClassHelper.make(Trait)))
                break
            case 'a nested class':
                classNode = new InnerClassNode(book, 'Book$Detail', Modifier.PUBLIC | Modifier.STATIC, ClassHelper.OBJECT_TYPE)
                break
        }
        classNode.module = new ModuleNode(sourceUnit)
        classNode
    }

    private static ClassNode referenceTo(ClassNode classNode) {
        ClassNode reference = ClassHelper.makeWithoutCaching(classNode.name)
        reference.redirect = classNode
        reference
    }
}
