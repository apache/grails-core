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
package org.grails.datastore.gorm.services.transform

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.TempDir

import org.apache.grails.common.compiler.GroovyTransformOrder
import org.grails.datastore.gorm.services.Implemented
import org.grails.datastore.gorm.services.transform.support.ProbeServiceImplementer
import spock.lang.Specification

/**
 * Covers compile-time behaviour of {@link ServiceTransformation} that the higher level
 * {@code @Service} usage specs (eg. {@code grails.gorm.services.ServiceTransformSpec}) don't
 * otherwise exercise: constructor validation on abstract data services, resolution of methods
 * through a {@code ServiceImplementerAdapter}, the {@code META-INF/services} descriptor writer, and
 * the transform's declared priority.
 */
class ServiceTransformationSpec extends Specification {

    @TempDir
    File targetDirectory

    void "test an abstract data service with an explicit constructor produces a clean compile error"() {
        when: 'an abstract data service declares a constructor'
        new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Service(Foo)
abstract class FooService {

    FooService() {
    }

    abstract Foo find(Serializable id)
}
@Entity
class Foo {
    String title
}
''')

        then: 'a clean compilation error is raised rather than a crash'
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('Abstract data Services should not define constructors')
    }

    void "test an abstract data service without a constructor compiles cleanly"() {
        when: 'an abstract data service declares no constructor'
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Service(Foo)
abstract class FooServiceNoCtor {

    abstract Foo find(Serializable id)
}
@Entity
class Foo {
    String title
}
''')

        then: 'no error is raised'
        !service.isInterface()
    }

    void "test a method resolved through a ServiceImplementerAdapter is annotated with the adapted implementer"() {
        when: 'a @Service interface declares a method that only a ServiceLoader-registered adapter can implement'
        Class service = new GroovyClassLoader().parseClass("""
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Service(Foo)
interface ProbeService {
    Object ${ProbeServiceImplementer.TARGET_METHOD_NAME}()
}
@Entity
class Foo {
    String title
}
""")

        then: 'the interface compiles cleanly'
        service.isInterface()

        when: 'the implementation is loaded'
        Class impl = service.classLoader.loadClass('$ProbeServiceImplementation')

        then: 'the method was implemented via the adapted implementer'
        impl.getMethod(ProbeServiceImplementer.TARGET_METHOD_NAME)
                .getAnnotation(Implemented)
                .by() == ProbeServiceImplementer
    }

    void "test a concrete @Service class generates a descriptor without going through the interface/abstract-class impl path"() {
        when: 'a @Service annotation is applied directly to a concrete (non-abstract) class, compiled dynamically'
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service

@Service
class ConcreteProbeService {
    void doStuff() {
    }
}
''')

        then: 'the class is used as-is, with no separate $...Implementation class generated'
        !service.isInterface()
        org.grails.datastore.mapping.services.Service.isAssignableFrom(service)
    }

    void "test priority returns the data service transform order"() {
        expect:
        new ServiceTransformation().priority() == GroovyTransformOrder.DATA_SERVICE_ORDER
    }

    void "test an exposed @Service writes a META-INF services descriptor to the compiler's target directory"() {
        given: 'a source file compiled with a real target directory, so the file-writing descriptor path runs'
        File sourceFile = new File(targetDirectory, 'DescriptorProbeServices.groovy')
        sourceFile.text = '''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Service(Foo)
interface DescriptorFooService {
    Foo find(Serializable id)
}

@Service(Bar)
interface DescriptorBarService {
    Bar find(Serializable id)
}

@Entity
class Foo {
    String title
}
@Entity
class Bar {
    String title
}
'''
        File outputDirectory = new File(targetDirectory, 'classes-out')
        outputDirectory.mkdirs()
        CompilerConfiguration config = new CompilerConfiguration()
        config.setTargetDirectory(outputDirectory)
        GroovyClassLoader gcl = new GroovyClassLoader(getClass().classLoader, config)

        when: 'the source file is compiled'
        gcl.parseClass(sourceFile)

        then: 'a META-INF/services descriptor is written under the configured target directory'
        File descriptor = new File(outputDirectory, 'META-INF/services/org.grails.datastore.mapping.services.Service')
        descriptor.exists()

        and: 'it lists both generated implementation classes, appended rather than overwritten'
        List<String> entries = descriptor.text.split('\\r?\\n') as List<String>
        entries.contains('$DescriptorFooServiceImplementation')
        entries.contains('$DescriptorBarServiceImplementation')
        entries.size() == 2
    }
}
