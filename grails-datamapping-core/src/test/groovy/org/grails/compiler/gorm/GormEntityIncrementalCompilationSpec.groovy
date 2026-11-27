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
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import spock.lang.Specification
import spock.lang.TempDir

/**
 * The entity transformation must produce the same classes whether the classes an entity refers to are
 * compiled together with it or were compiled before, as happens under incremental compilation.
 */
class GormEntityIncrementalCompilationSpec extends Specification {

    @TempDir
    File dir

    void 'an enum declared under grails-app/domain is not treated as an association'() {
        given: 'an entity with a property of an enum type declared next to it under grails-app/domain'
        File domainDir = new File(dir, 'grails-app/domain')
        domainDir.mkdirs()
        File statusSource = new File(domainDir, 'Status.groovy')
        statusSource.text = 'enum Status { OPEN, CLOSED }'
        File bookSource = new File(domainDir, 'Book.groovy')
        bookSource.text = '''
            @grails.gorm.annotation.Entity
            class Book {
                String title
                Status status = Status.OPEN
            }
        '''

        when: 'the entity and the enum are compiled together'
        CompilationUnit together = compile(new File(dir, 'together'), null, [bookSource, statusSource])

        then: 'no association id getter is added for the enum property'
        !hasMethod(together, 'Book', 'getStatusId')

        when: 'the entity is compiled against the compiled enum'
        File classpath = new File(dir, 'together')
        new File(classpath, 'Book.class').delete()
        CompilationUnit recompiled = compile(new File(dir, 'recompiled'), classpath, [bookSource])

        then: 'the enum property is not an association either'
        !hasMethod(recompiled, 'Book', 'getStatusId')
    }

    void 'a nested class declared under grails-app/domain is an association only when it is an entity itself'() {
        given: 'an entity with properties of two classes nested in another entity under grails-app/domain, one of them a JPA entity'
        File domainDir = new File(dir, 'grails-app/domain')
        domainDir.mkdirs()
        File shelfSource = new File(domainDir, 'Shelf.groovy')
        shelfSource.text = '''
            @grails.gorm.annotation.Entity
            class Shelf {
                String code

                static class Position {
                    Integer row
                }

                @jakarta.persistence.Entity
                static class Label {
                    @jakarta.persistence.Id
                    Long id
                    String text
                }
            }
        '''
        File bookSource = new File(domainDir, 'Book.groovy')
        bookSource.text = '''
            @grails.gorm.annotation.Entity
            class Book {
                String title
                Shelf.Position position
                Shelf.Label label
            }
        '''

        when: 'the entity and the nested classes are compiled together'
        CompilationUnit together = compile(new File(dir, 'together'), null, [bookSource, shelfSource])

        then: 'only the property of the nested entity is an association'
        !hasMethod(together, 'Book', 'getPositionId')
        hasMethod(together, 'Book', 'getLabelId')

        when: 'the entity is compiled against the compiled nested classes'
        File classpath = new File(dir, 'together')
        new File(classpath, 'Book.class').delete()
        CompilationUnit recompiled = compile(new File(dir, 'recompiled'), classpath, [bookSource])

        then: 'the same property is an association'
        !hasMethod(recompiled, 'Book', 'getPositionId')
        hasMethod(recompiled, 'Book', 'getLabelId')
    }

    private CompilationUnit compile(File targetDirectory, File classpath, List<File> sources) {
        CompilationUnit unit = newCompilationUnit(targetDirectory, classpath)
        unit.addSources(sources as File[])
        unit.compile()
        unit
    }

    private CompilationUnit newCompilationUnit(File targetDirectory, File classpath) {
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        if (classpath != null) {
            loader.addClasspath(classpath.absolutePath)
        }
        new CompilationUnit(new CompilerConfiguration(targetDirectory: targetDirectory), null, loader)
    }

    private static boolean hasMethod(CompilationUnit unit, String className, String methodName) {
        classNode(unit, className).getMethod(methodName, Parameter.EMPTY_ARRAY) != null
    }

    private static ClassNode classNode(CompilationUnit unit, String className) {
        ClassNode classNode = unit.getAST().getClass(className)
        assert classNode != null : "${className} was not compiled"
        classNode
    }

}
