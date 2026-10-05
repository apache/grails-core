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
package grails.io

import org.grails.io.support.Resource
import spock.lang.Specification

class IOUtilsSpec extends Specification {

    void "Test findClassResource finds a class resource"() {
        expect:
        IOUtils.findClassResource(Resource)
        IOUtils.findClassResource(Resource).path.contains('grails-gradle/model')
    }

    void "Test findJarResource finds a JAR resource"() {
        expect:
        IOUtils.findJarResource(Specification)
        IOUtils.findJarResource(Specification).path.endsWith('spock-core-2.4-groovy-4.0.jar!/')
    }

    void 'findRootResourcesURL - appends / if not present'() {
        when:
        def result = IOUtils.findRootResourcesURL(IOUtils).toString()

        then:
        result.startsWith('file:')
        result.endsWith('/')
    }

    void 'a class root in a build maps to the resources root of the same build: #classRoot'() {
        expect:
        IOUtils.resourcesRootFor(classRoot, classesPath, resourcesPath) == resourcesRoot

        where:
        classRoot                                               | classesPath                                   | resourcesPath                           | resourcesRoot
        'file:/app/build/classes/groovy/main'                   | 'build/classes/groovy/main'                   | 'build/resources/main'                  | 'file:/app/build/resources/main'
        'file:/app/build-parent/build-8070/classes/groovy/main' | 'build-parent/build-8070/classes/groovy/main' | 'build-parent/build-8070/resources/main' | 'file:/app/build-parent/build-8070/resources/main'
        'file:/app/build/classes/groovy/test'                   | 'build/classes/groovy/main'                   | 'build/resources/main'                  | 'file:/app/build/classes/groovy/test'
        'file:/app/mybuild/classes/groovy/main'                 | 'build/classes/groovy/main'                   | 'build/resources/main'                  | 'file:/app/mybuild/classes/groovy/main'
        'jar:file:/lib/plugin.jar!'                             | 'build/classes/groovy/main'                   | 'build/resources/main'                  | 'jar:file:/lib/plugin.jar!'
    }
}
