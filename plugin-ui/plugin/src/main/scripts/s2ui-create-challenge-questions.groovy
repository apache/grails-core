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

import grails.util.GrailsNameUtils
import groovy.transform.Field

@Field String usageMessage = '''
grails s2ui-create-challenge-questions <domain-class-package> <challenge-qa-class-name> <user-domain-class-name> [number-of-questions]

domain-class-package is required and is the package where domainName will live 
challenge-qa-class-name  is required and is the Name of the domain class used to store this information
user-domain-class-name is required and is the package and name of the User class.  In most circumstance I would put this in the same package but it will work if they are different. 
number-of-questions is optional but if not given will default to 2. 

Example: s2ui-create-challenge-questions com.mycompany ChallengeQuestion com.mycompany.User  2
'''


@Field Map templateAttributes

description 'Creates Domian Objects, Service Listener, Controller, I18N and updates the application.groovy file so it can be used', {
	usage usageMessage
	argument name: 'Domain class package',     description: 'The package to use for the domain classes'
	argument name: 'Challenge QA class name',   description: 'The name of challenge questions and answers class'
	argument name: 'User class name',	       description: 'The name of the User/Person class'
	argument name: 'Number Of Questions', 	   description: 'The number of challenge questions generated', required: false
}


String domainPackage = args[0].toLowerCase()
String domainName =args[1]
Model saModel = model(domainPackage + '.' + domainName)
Integer numberOfQuestions = args.size() > 3 ? args[3].toInteger() : 2
Model userModel = args[2].indexOf('.') > -1 ? model(args[2]) : model(domainPackage + '.' + args[2])
File grailsApp = file('grails-app')

def chsa = saModel.simpleName.toCharArray()
chsa[0] = Character.toLowerCase(chsa[0])

def chup = userModel.modelName.toCharArray()
chup[0] = Character.toLowerCase(chup[0])

String camelCaseSaNamevar =  new String(chsa)

templateAttributes = [
		packageName: saModel.packageName,
		saClassName: saModel.simpleName,
		camelCaseSaName:  camelCaseSaNamevar,
		saClassProperty: saModel.modelName,
		numberOfQuestions: numberOfQuestions,
		userPropName: new String(chup),
		userDomainName: (userModel.packageName.toLowerCase() == saModel.packageName  ?  "" : userModel.packageName.toLowerCase() + '.') + userModel.modelName.toLowerCase().capitalize()
]

String directoryName = saModel.packageName.replaceAll('\\.', '/')
File serviceDestinationDirectory = new File(new File(grailsApp, 'services'), directoryName)
serviceDestinationDirectory.mkdirs()

render template('ChallengeQuestionsService.groovy.template'),
		new File(serviceDestinationDirectory, "${saModel.simpleName}Service.groovy"),
		templateAttributes, false

render template('ChallengeQuestionsListenerService.groovy.template'),
		new File(serviceDestinationDirectory, "${saModel.simpleName}ListenerService.groovy"),
		templateAttributes, false

File domainDestinationDirectory = new File(new File(grailsApp, 'domain'), directoryName)
domainDestinationDirectory.mkdirs()

render template('ChallengeQuestions.groovy.template'),
		new File(domainDestinationDirectory, "${saModel.simpleName}.groovy"),
		templateAttributes, false

File controllerDirectory = new File(new File(grailsApp, 'controllers'), directoryName)
controllerDirectory.mkdirs()

render template('ChallengeQuestionsController.groovy.template'),
		new File(controllerDirectory, "${saModel.simpleName}Controller.groovy"),
		templateAttributes, false

File viewDirectory = new File(new File(grailsApp,'views'), camelCaseSaNamevar)
viewDirectory.mkdirs()

render template('ChallengeQuestionsEdit.gsp.template'),
		new File(viewDirectory, 'edit.gsp'),
		templateAttributes, false

render template('ChallengeQuestionsCreate.gsp.template'),
		new File(viewDirectory, 'create.gsp'),
		templateAttributes, false

render template('ChallengeQuestionsIndex.gsp.template'),
		new File(viewDirectory,'index.gsp'),
		templateAttributes, false

file('grails-app/conf/application.groovy').withWriterAppend { BufferedWriter writer ->
	writer.newLine()
	writer.writeLine '// Added by the Spring Security UI plugin:'
	writer.writeLine "grails.plugin.springsecurity.ui.forgotPassword.forgotPasswordExtraValidationDomainClassName = '${saModel?.packageName}.${saModel?.simpleName}'"
	writer.writeLine "grails.plugin.springsecurity.ui.forgotPassword.forgotPasswordExtraValidation = ["
	for(int i = 1; i <= numberOfQuestions; i++) {
		writer.writeLine "\t[labelDomain: 'myQuestion$i', prop:'myAnswer$i'],"
	}
	writer.writeLine ']'
	writer.newLine()
}

file('grails-app/i18n/messages.properties').withWriterAppend { BufferedWriter writer ->
	writer.newLine()
	writer.writeLine "spring.security.ui.menu.${saModel.simpleName}=${saModel.simpleName} Questions"
	writer.newLine()
}
println("Finished s2ui-create-challenge-questions!")