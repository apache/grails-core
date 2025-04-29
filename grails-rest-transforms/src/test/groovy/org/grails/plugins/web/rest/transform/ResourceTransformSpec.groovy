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
package org.grails.plugins.web.rest.transform

import grails.artefact.Artefact
import grails.gorm.transactions.Transactional
import grails.rest.RestfulController
import grails.web.Action

import java.lang.reflect.Method

import org.codehaus.groovy.control.CompilerConfiguration

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author Graeme Rocher
 */
class ResourceTransformSpec extends Specification {
    protected GroovyClassLoader createGroovyClassLoader() {
        new GroovyClassLoader(Thread.currentThread().getContextClassLoader(), createCompilerConfiguration())
    }

    protected CompilerConfiguration createCompilerConfiguration() {
        CompilerConfiguration compilerConfig = new CompilerConfiguration()
        File targetDir = new File(System.getProperty("java.io.tmpdir"), "classes_" + this.getClass().getSimpleName())
        if(targetDir.exists()) {
            targetDir.deleteDir()
        }
        targetDir.mkdirs()
        // keep compiled bytecode in targetDirectory for debugging purposes
        compilerConfig.targetDirectory = targetDir
        return compilerConfig
    }

    @Unroll
    void "Test that the resource transform creates a controller class when super class is #superClass"() {
         given:"A parsed class with a @Resource annotation"
            def gcl = createGroovyClassLoader()
            gcl.parseClass("""
import grails.rest.*
import grails.persistence.*

@Entity
@Resource(formats=['html','xml']${superClass ? (', superClass=' + superClass) : ''})
class Book {
}
""")
            def superClazz = superClass ? gcl.loadClass(superClass) : RestfulController
        when:"The controller class is loaded"
            def domain = gcl.loadClass("Book")
            def ctrl = gcl.loadClass('BookController')

        then:"It exists"
            ctrl != null
            getMethod(ctrl, "index", Integer.class)
            getMethod(ctrl, "index")
            getMethod(ctrl, "index").getAnnotation(Action)
            getMethod(ctrl, "show")
            getMethod(ctrl, "edit")
            getMethod(ctrl, "create")
            getMethod(ctrl, "save")
            getMethod(ctrl, "update")
            getMethod(ctrl, "delete")

            ctrl.scope == "singleton"
       
        then:"The superClass is correct"
            ctrl.getSuperclass() == superClazz

        when:"A link is added"
            def book = domain.getDeclaredConstructor().newInstance()
            book.link(rel:'foos', href:"/foo")
            def links = book.links()

        then:"The link is added to the available links"
            links[0].href == '/foo'

        where:
            superClass << ['', RestfulController.name, SubclassRestfulController.name]
    }


    @Unroll
    void "Test that the resource transform creates a controller class when namespace is #namespace"() {
        given:"A parsed class with a @Resource annotation"
        def gcl = createGroovyClassLoader()


        gcl.parseClass("""
import grails.rest.*
import grails.persistence.*
@Entity
@Resource(formats=['html','xml'], namespace='${namespace}')
class Book {
}
""")

        when:"The controller class is loaded"
        def ctrl = gcl.loadClass('BookController')

        then:"It exists"
        ctrl != null
        getMethod(ctrl, "index", Integer.class)
        getMethod(ctrl, "index")
        getMethod(ctrl, "index").getAnnotation(Action)
        getMethod(ctrl, "show")
        getMethod(ctrl, "edit")
        getMethod(ctrl, "create")
        getMethod(ctrl, "save")
        getMethod(ctrl, "update")
        getMethod(ctrl, "delete")

        ctrl.scope == "singleton"

        then:"The namespace is correct"
        ctrl.namespace == namespace


        where:
        namespace = 'v2'
    }

    @Issue("GRAILS-10741")
    @Unroll
    void "Test that the resource transform creates a read only controller class when super class is #superClass"() {
        given:"A parsed class with a @Resource annotation"
        def gcl = createGroovyClassLoader()
        gcl.parseClass("""
import grails.rest.*
import grails.persistence.*

@Entity
@Resource(formats=['html','xml'], readOnly=true${superClass ? (', superClass=' + superClass) : ''})
class Book {
}
""")
            def superClazz = superClass ? gcl.loadClass(superClass) : RestfulController
        when:"The controller class is loaded"
            def domain = gcl.loadClass("Book")
            def ctrl = gcl.loadClass('BookController')

        then:"It exists"
            ctrl != null
            getMethod(ctrl, "index", Integer.class)
            getMethod(ctrl, "index")
            getMethod(ctrl, "index").getAnnotation(Action)
            getMethod(ctrl, "show")
            ctrl.scope == "singleton"

        then:"The superClass is correct"
            ctrl.getSuperclass() == superClazz

        when:"A link is added"
            def book = domain.getDeclaredConstructor().newInstance()
            book.link(rel:'foos', href:"/foo")
            def links = book.links()

        then:"The link is added to the available links"
            links[0].href == '/foo'
        
        where:
            superClass << ['', RestfulController.name, SubclassRestfulController.name]
    }

    void "Test that the resource transform creates a controller class with the correct default formats"() {
        given:"A parsed class with a @Resource annotation"
        def gcl = createGroovyClassLoader()
        gcl.parseClass("""
import grails.rest.*
import grails.persistence.*

@Entity
@Resource()
class Book {
}
""")

        when:"The controller class is loaded"
        def ctrl = gcl.loadClass('BookController')

        then:"It exists"
        ctrl != null
        ctrl.responseFormats == ["json", "xml"] as String[]
    }

    private Method getMethod(Class clazz, String methodName, Class[] paramTypes) {
        try {
            clazz.getMethod(methodName, paramTypes)
        } catch (NoSuchMethodException e) {
            return null
        }
    }
}

@Artefact("Controller")
@Transactional(readOnly = true)
class SubclassRestfulController<T> extends RestfulController<T> {
    SubclassRestfulController(Class<T> resource) {
        this(resource, false)
    }

    SubclassRestfulController(Class<T> resource, boolean readOnly) {
        super(resource, readOnly)
    }
}
