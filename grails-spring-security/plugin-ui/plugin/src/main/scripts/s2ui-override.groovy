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
grails s2ui-override <type> [controllerPackage]

type can be one of aclclass, aclentry, aclobjectidentity, aclsid, persistentlogin,
register, registrationcode, requestmap, role, securityinfo, user, auth, or layout (not case-sensitive)

controllerPackage is required for any type that has a controller (i.e. all but 'auth' and 'layout')

Example: grails s2ui-override user com.yourcompany.yourapp
'''

description 'Copies plugin controllers and GSPs to the application so they can be overridden', {
	usage usageMessage

	argument name: 'type',              description: 'The group of files to copy'
	argument name: 'controllerPackage', description: 'The package name for the controller', required: false
}

String type = args[0].toLowerCase()
String controllerPackage = args.size() > 1 ? args[1] : ''
File grailsApp = file('grails-app')
File controllersDir = new File(grailsApp, 'controllers')
File viewsDir = new File(grailsApp, 'views')
File layoutsDir = new File(viewsDir, 'layouts')

def controllers = [aclclass:          'AclClass',
                   aclentry:          'AclEntry',
                   aclobjectidentity: 'AclObjectIdentity',
                   aclsid:            'AclSid',
                   persistentlogin:   'PersistentLogin',
                   register:          'Register',
                   registrationcode:  'RegistrationCode',
                   requestmap:        'Requestmap',
                   role:              'Role',
                   securityinfo:      'SecurityInfo',
                   user:              'User']

if (!controllerPackage) {
	if ('layout' == type) {
		copy template('views/layouts/springSecurityUI.gsp'), file('grails-app/views/layouts/')
		copy template('views/includes/_ajaxLogin.gsp'), new File(viewsDir, 'includes')
		return
	}

	if ('auth' == type) {
		copy template('views/login/auth.gsp'), new File(viewsDir, 'login')
		return
	}
}

String controller = controllers[type]
if (!controller) {
	error "Unknown type '${args[0]}'\n\nUsage:\n$usageMessage"
	return false
}

if (!controllerPackage) {
	error "The controller package is required for type '${args[0]}'\n\nUsage:\n$usageMessage"
	return false
}

addStatus "Copying ${args[0]} resources"

if ('grails.plugin.springsecurity.ui' == controllerPackage) {
	error 'The controller package cannot be the same as the plugin controller'
	return false
}

// generate the controller
String directoryName = controllerPackage.replaceAll('\\.', '/')
File destinationDirectory = new File(controllersDir, directoryName)
destinationDirectory.mkdirs()

render template(controller + 'Controller.groovy.template'),
       new File(destinationDirectory, controller + 'Controller.groovy'),
       [packageDeclaration: "package $controllerPackage"], false

// copy the GSPs
directoryName = GrailsNameUtils.getPropertyName(controller)
File gspDirectory = new File(viewsDir, directoryName)
for (resource in resources(directoryName + '/*.gsp')) {
	copy resource, gspDirectory
}

switch ( directoryName ) {
	case 'aclClass':
		copy template('views/aclClass/create.gsp'), new File(viewsDir, 'aclClass')
		copy template('views/aclClass/edit.gsp'), new File(viewsDir, 'aclClass')
		copy template('views/aclClass/search.gsp'), new File(viewsDir, 'aclClass')
		break
	case 'aclEntry':
		copy template('views/aclEntry/create.gsp'), new File(viewsDir, 'aclEntry')
		copy template('views/aclEntry/edit.gsp'), new File(viewsDir, 'aclEntry')
		copy template('views/aclEntry/search.gsp'), new File(viewsDir, 'aclEntry')
		break
	case 'aclObjectIdentity':
		copy template('views/aclObjectIdentity/create.gsp'), new File(viewsDir, 'aclObjectIdentity')
		copy template('views/aclObjectIdentity/edit.gsp'), new File(viewsDir, 'aclObjectIdentity')
		copy template('views/aclObjectIdentity/search.gsp'), new File(viewsDir, 'aclObjectIdentity')
		break
	case 'aclSid':
		copy template('views/aclSid/create.gsp'), new File(viewsDir, 'aclSid')
		copy template('views/aclSid/edit.gsp'), new File(viewsDir, 'aclSid')
		copy template('views/aclSid/search.gsp'), new File(viewsDir, 'aclSid')
		break
	case 'persistentLogin':
		copy template('views/persistentLogin/search.gsp'), new File(viewsDir, 'persistentLogin')
		copy template('views/persistentLogin/edit.gsp'), new File(viewsDir, 'persistentLogin')
		break
	case 'register':
		copy template('views/register/forgotPassword.gsp'), new File(viewsDir, 'register')
		copy template('views/register/register.gsp'), new File(viewsDir, 'register')
		copy template('views/register/resetPassword.gsp'), new File(viewsDir, 'register')
		copy template('views/register/_forgotPasswordMail.gsp'), new File(viewsDir, 'register')
		copy template('views/register/_verifyRegistrationMail.gsp'), new File(viewsDir, 'register')
		copy template('views/register/securityQuestions.gsp'), new File(viewsDir, 'register')
		break
	case 'registrationCode':
		copy template('views/registrationCode/search.gsp'), new File(viewsDir, 'registrationCode')
		copy template('views/registrationCode/edit.gsp'), new File(viewsDir, 'registrationCode')
		break
	case 'requestmap':
		copy template('views/requestmap/create.gsp'), new File(viewsDir, 'requestmap')
		copy template('views/requestmap/edit.gsp'), new File(viewsDir, 'requestmap')
		copy template('views/requestmap/search.gsp'), new File(viewsDir, 'requestmap')
		break
	case 'role':
		copy template('views/role/create.gsp'), new File(viewsDir, 'role')
		copy template('views/role/edit.gsp'), new File(viewsDir, 'role')
		copy template('views/role/search.gsp'), new File(viewsDir, 'role')
		break
	case 'securityInfo':
		copy template('views/securityInfo/config.gsp'), new File(viewsDir, 'securityInfo')
		copy template('views/securityInfo/currentAuth.gsp'), new File(viewsDir, 'securityInfo')
		copy template('views/securityInfo/filterChains.gsp'), new File(viewsDir, 'securityInfo')
		copy template('views/securityInfo/logoutHandlers.gsp'), new File(viewsDir, 'securityInfo')
		copy template('views/securityInfo/mappings.gsp'), new File(viewsDir, 'securityInfo')
		copy template('views/securityInfo/providers.gsp'), new File(viewsDir, 'securityInfo')
		copy template('views/securityInfo/secureChannel.gsp'), new File(viewsDir, 'securityInfo')
		copy template('views/securityInfo/usercache.gsp'), new File(viewsDir, 'securityInfo')
		copy template('views/securityInfo/voters.gsp'), new File(viewsDir, 'securityInfo')
		break
	case 'user':
		copy template('views/user/create.gsp'), new File(viewsDir, 'user')
		copy template('views/user/edit.gsp'), new File(viewsDir, 'user')
		copy template('views/user/search.gsp'), new File(viewsDir, 'user')
		break
}

if ('register' == type) {
	copy template('views/layouts/register.gsp'), file('grails-app/views/layouts/')
	copy template('views/layouts/email.gsp'), file('grails-app/views/layouts/')
}

private void copy(pathOrResource, File destinationDirectory) {
	if ( !destinationDirectory.exists() ) {
		destinationDirectory.mkdirs()
	}

	copy resource(pathOrResource), destinationDirectory
}
