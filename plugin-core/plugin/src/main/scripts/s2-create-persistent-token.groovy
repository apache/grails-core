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

import grails.codegen.model.Model

description 'Creates a persistent token domain class for the Spring Security Core plugin', {
	usage '''
grails s2-create-persistent-token [DOMAIN CLASS NAME]

Example: grails s2-create-persistent-token com.yourapp.PersistentLogin
'''

	argument name: 'Domain class name', description: 'The domain class full name with package'
}

String fullClassName = args[0]
Model model = model(fullClassName)

addStatus "\nCreating persistent token class $fullClassName"

render template: template('PersistentLogin.groovy.template'),
       destination: file("grails-app/domain/$model.packagePath/${model.simpleName}.groovy"),
       model: model, overwrite: false

file('grails-app/conf/application.groovy').withWriterAppend { BufferedWriter writer ->
	writer.newLine()
	writer.writeLine 'grails.plugin.springsecurity.rememberMe.persistent = true'
	writer.writeLine "grails.plugin.springsecurity.rememberMe.persistentToken.domainClassName = '$fullClassName'"
	writer.newLine()
}
