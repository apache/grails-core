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

package org.grails.compiler.boot

import grails.util.Environment
import org.grails.compiler.injection.GrailsAwareClassLoader
import spock.lang.Specification

/**
 * Created by graemerocher on 26/08/2016.
 */
class BootInitializerClassInjectorSpec extends Specification {

    void "test compile application class"() {
        when:"An application class is compiled"
        def gcl = new GrailsAwareClassLoader()
        Class applicationClass = gcl.parseClass('''
import grails.boot.GrailsApp
class Application extends grails.boot.config.GrailsAutoConfiguration {
    static void main(String[] args) {
        println "foo"
    }
}
''')

        applicationClass.main()

        then:""
        Boolean.getBoolean(Environment.STANDALONE)
        Environment.isStandalone()
        !Environment.isStandaloneDeployed()
    }
}
