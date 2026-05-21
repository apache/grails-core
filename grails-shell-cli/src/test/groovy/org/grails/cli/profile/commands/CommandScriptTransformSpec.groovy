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
package org.grails.cli.profile.commands

import org.grails.cli.interactive.completers.DomainClassCompleter
import org.grails.cli.profile.commands.factory.GroovyScriptCommandFactory
import org.grails.cli.profile.commands.script.GroovyScriptCommand
import spock.lang.Specification

/**
 * @author graemerocher
 */
class CommandScriptTransformSpec extends Specification {


    void "Test that the CommandScriptTransform correctly populates the description"() {
        given:"A GroovyClassLoader with the CommandScriptTransform applied"
            def gcl = GroovyScriptCommandFactory.createGroovyScriptCommandClassLoader()

        when:"A script is parsed"
            def script = (GroovyScriptCommand)(gcl.parseClass('''
import org.grails.cli.interactive.completers.DomainClassCompleter

description("example script") {
    usage "example usage"
    completer DomainClassCompleter
    argument name: 'controllerName', description:'The name of the controller'
    flag name:'test', description:'Do something'

}


println "Hello!"
''', "MyScript").getDeclaredConstructor().newInstance())

        then:"The scripts description is correctly populated"
            script.description.name == 'my-script'
            script.description.description == 'example script'
            script.description.usage == 'example usage'
            script.description.arguments.size() == 1
            script.description.arguments[0].name == 'controllerName'
            script.description.arguments[0].description == 'The name of the controller'

            script.description.flags.size() == 1
            script.description.flags[0].name == 'test'
            script.description.flags[0].description == 'Do something'
            script.description.completer instanceof DomainClassCompleter
    }
}
