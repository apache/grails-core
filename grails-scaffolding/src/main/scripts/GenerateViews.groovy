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

import org.grails.cli.interactive.completers.DomainClassCompleter

description("Generates GSP views for the specified domain class") {
    usage "grails generate-views [DOMAIN CLASS]|*"
    argument name: 'Domain Class', description: "The name of the domain class, or '*' for all", required: true
    completer DomainClassCompleter
    flag name: 'force', description: "Whether to overwrite existing files"
}

if (args) {
    def classNames = args
    if (args[0] == '*') {
        classNames = resources("file:grails-app/domain/**/*.groovy").collect { className(it) }
    }
    def viewNames = resources("file:src/main/templates/scaffolding/*.gsp")
            .collect {
                it.filename
            }
    if (!viewNames) {
        viewNames = resources("classpath*:META-INF/templates/scaffolding/*.gsp")
                .collect {
                    it.filename
                }
    }

    for (arg in classNames) {
        def sourceClass = source(arg)
        def overwrite = flag('force') ? true : false
        if (sourceClass) {
            def model = model(sourceClass)
            viewNames.each {
                render template: template('scaffolding/' + it),
                        destination: file("grails-app/views/${model.propertyName}/" + it),
                        model: model,
                        overwrite: overwrite
            }

            addStatus "Views generated for ${projectPath(sourceClass)}"
        } else {
            error "Domain class not found for name $arg"
        }
    }
} else {
    error "No domain class specified"
}
