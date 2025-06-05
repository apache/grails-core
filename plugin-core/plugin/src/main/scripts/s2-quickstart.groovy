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
import groovy.transform.Field

@Field String usageMessage = '''
   grails s2-quickstart <domain-class-package> <user-class-name> <role-class-name> [requestmap-class-name] [--groupClassName=group-class-name]
or grails s2-quickstart --uiOnly

Example: grails s2-quickstart com.yourapp User Role
Example: grails s2-quickstart com.yourapp User Role --groupClassName=RoleGroup
Example: grails s2-quickstart com.yourapp Person Authority Requestmap
Example: grails s2-quickstart --uiOnly
'''

@Field Map templateAttributes
@Field boolean uiOnly
@Field boolean salt

description 'Creates domain classes and updates config settings for the Spring Security plugin', {

	usage usageMessage

	argument name: 'Domain class package',  description: 'The package to use for the domain classes', required: false
	argument name: 'User class name',       description: 'The name of the User/Person class',         required: false
	argument name: 'Role class name',       description: 'The name of the Role class',                required: false
	argument name: 'Requestmap class name', description: 'The name of the Requestmap class',          required: false

	flag name: 'groupClassName', description: 'If specified, role/group classes will also be generated using the flag value as the role-group name'
	flag name: 'uiOnly', description: 'If specified, no domain classes are created but the plugin settings are initialized (useful with LDAP, Mock, Shibboleth, etc.)'
}

Model userModel
Model roleModel
Model requestmapModel
Model groupModel
uiOnly = flag('uiOnly')
salt = flag('salt')
if (uiOnly) {
	addStatus '\nConfiguring Spring Security; not generating domain classes'
}
else {

	if (args.size() < 3) {
		error 'Usage:' + usageMessage
		return false
	}

	String packageName = args[0]
	String groupClassName = flag('groupClassName')
	String groupClassNameMessage = ''
	if (groupClassName) {
		groupModel = model(packageName + '.' + groupClassName)
		groupClassNameMessage = ", and role/group classes for '" + groupModel.simpleName + "'"
	}

	userModel = model(packageName + '.' + args[1])
	roleModel = model(packageName + '.' + args[2])

	String message = "Creating User class '" + userModel.simpleName + "'"
	if (4 == args.size()) {
		requestmapModel = model(packageName + '.' + args[3])
		message += ", Role class '" + roleModel.simpleName + "', and Requestmap class '" + requestmapModel.simpleName + "'" + groupClassNameMessage
	}
	else {
		message += " and Role class '" + roleModel.simpleName + "'" + groupClassNameMessage
	}
	message += " in package '" + packageName + "'"
	addStatus message

	templateAttributes = [
		packageName: userModel.packageName,
		userClassName: userModel.simpleName,
		userClassProperty: userModel.modelName,
		roleClassName: roleModel.simpleName,
		roleClassProperty: roleModel.modelName,
		requestmapClassName: requestmapModel?.simpleName,
		groupClassName: groupModel?.simpleName,
		groupClassProperty: groupModel?.modelName]

	createDomains userModel, roleModel, requestmapModel, groupModel
}

updateConfig userModel?.simpleName, roleModel?.simpleName, requestmapModel?.simpleName, userModel?.packageName, groupModel != null

if (uiOnly) {
	addStatus '''
************************************************************
* Your grails-app/conf/application.groovy has been updated *
* with security settings; please verify that the           *
* values are correct.                                      *
************************************************************
'''
}
else {
	addStatus '''
************************************************************
* Created security-related domain classes. Your            *
* grails-app/conf/application.groovy has been updated with *
* the class names of the configured domain classes;        *
* please verify that the values are correct.               *
************************************************************
'''
}

private Map extractVersion(String versionString) {
	def arr = versionString.split('\\.')
	def v = [mayor: 0, minor: 0, bug: 0]
	try {
		if ( arr.size() >= 1) {
			v.mayor = arr[0].toInteger()
		}
		if ( arr.size() >= 2) {
			v.minor = arr[1].toInteger()
		}
		if ( arr.size() >= 3) {
			v.bug = arr[2].toInteger()
		}
	} catch ( Exception e ) {
		v = [mayor: 0, minor: 0, bug: 0]
	}
	v
}

private boolean versionAfterOrEqualsToThreshold(String threshold, String value) {
	if ( value == null ) {
		return false
	}
	if ( value.startsWith(threshold) ) {
		return true
	}

	def va = extractVersion(value)
	def vb = extractVersion(threshold)
	def l = [va, vb]
	l.sort { Map a, Map b ->
		def compare = a.mayor <=> b.mayor
		if ( compare != 0 ) {
			return compare
		}
		compare = a.minor <=> b.minor
		if ( compare != 0 ) {
			return compare
		}
		a.bug <=> b.bug
	}
	def sortedValue = l[0].collect { k, v -> v }.join('.')
	threshold.startsWith(sortedValue)
}

private void createDomains(Model userModel, Model roleModel, Model requestmapModel, Model groupModel) {

	def props = new Properties()
	file("gradle.properties")?.withInputStream { props.load(it) }

	final threshold = '6.0.10'

	boolean gormVersionAfterThreshold = versionAfterOrEqualsToThreshold(threshold, props.gormVersion ?: props.getProperty("gorm.version"))

	if ( gormVersionAfterThreshold ) {
		generateFile 'PersonWithoutInjection', userModel.packagePath, userModel.simpleName
		if ( salt ) {
			generateFile 'PersonPasswordEncoderListenerWithSalt', userModel.packagePath, userModel.simpleName, "${userModel.simpleName}PasswordEncoderListener", 'src/main/groovy'
		} else {
			generateFile 'PersonPasswordEncoderListener', userModel.packagePath, userModel.simpleName, "${userModel.simpleName}PasswordEncoderListener", 'src/main/groovy'
		}
		def beansList = [[import: "import ${userModel.packageName}.${userModel.simpleName}PasswordEncoderListener", definition: "${userModel.propertyName}PasswordEncoderListener(${userModel.simpleName}PasswordEncoderListener)"]]
		addBeans(beansList, 'grails-app/conf/spring/resources.groovy')

	} else {
		if ( salt ) {
			generateFile 'PersonWithSalt', userModel.packagePath, userModel.simpleName
		} else {
			generateFile 'Person', userModel.packagePath, userModel.simpleName
		}
	}

	generateFile 'Authority', roleModel.packagePath, roleModel.simpleName
	generateFile 'PersonAuthority', roleModel.packagePath, userModel.simpleName + roleModel.simpleName

	if (requestmapModel) {
		generateFile 'Requestmap', requestmapModel.packagePath, requestmapModel.simpleName
	}

	if (groupModel) {
		generateFile 'AuthorityGroup', groupModel.packagePath, groupModel.simpleName
		generateFile 'PersonAuthorityGroup', groupModel.packagePath, userModel.simpleName + groupModel.simpleName
		generateFile 'AuthorityGroupAuthority', groupModel.packagePath, groupModel.simpleName + roleModel.simpleName
	}
}

private void updateConfig(String userClassName, String roleClassName, String requestmapClassName, String packageName, boolean useRoleGroups) {

	file('grails-app/conf/application.groovy').withWriterAppend { BufferedWriter writer ->
		writer.newLine()
		writer.newLine()
		writer.writeLine '// Added by the Spring Security Core plugin:'
		if (!uiOnly) {
			writer.writeLine "grails.plugin.springsecurity.userLookup.userDomainClassName = '${packageName}.$userClassName'"
			writer.writeLine "grails.plugin.springsecurity.userLookup.authorityJoinClassName = '${packageName}.$userClassName$roleClassName'"
			writer.writeLine "grails.plugin.springsecurity.authority.className = '${packageName}.$roleClassName'"
		}
		if (useRoleGroups) {
			writer.writeLine "grails.plugin.springsecurity.authority.groupAuthorityNameField = 'authorities'"
			writer.writeLine 'grails.plugin.springsecurity.useRoleGroups = true'
		}
		if (requestmapClassName) {
			writer.writeLine "grails.plugin.springsecurity.requestMap.className = '${packageName}.$requestmapClassName'"
			writer.writeLine "grails.plugin.springsecurity.securityConfigType = 'Requestmap'"
		}
		writer.writeLine 'grails.plugin.springsecurity.controllerAnnotations.staticRules = ['
		writer.writeLine "\t[pattern: '/',               access: ['permitAll']],"
		writer.writeLine "\t[pattern: '/error',          access: ['permitAll']],"
		writer.writeLine "\t[pattern: '/index',          access: ['permitAll']],"
		writer.writeLine "\t[pattern: '/index.gsp',      access: ['permitAll']],"
		writer.writeLine "\t[pattern: '/shutdown',       access: ['permitAll']],"
		writer.writeLine "\t[pattern: '/assets/**',      access: ['permitAll']],"
		writer.writeLine "\t[pattern: '/**/js/**',       access: ['permitAll']],"
		writer.writeLine "\t[pattern: '/**/css/**',      access: ['permitAll']],"
		writer.writeLine "\t[pattern: '/**/images/**',   access: ['permitAll']],"
		writer.writeLine "\t[pattern: '/**/favicon.ico', access: ['permitAll']]"
		writer.writeLine ']'
		writer.newLine()

		writer.writeLine 'grails.plugin.springsecurity.filterChain.chainMap = ['
		writer.writeLine "\t[pattern: '/assets/**',      filters: 'none'],"
		writer.writeLine "\t[pattern: '/**/js/**',       filters: 'none'],"
		writer.writeLine "\t[pattern: '/**/css/**',      filters: 'none'],"
		writer.writeLine "\t[pattern: '/**/images/**',   filters: 'none'],"
		writer.writeLine "\t[pattern: '/**/favicon.ico', filters: 'none'],"
		writer.writeLine "\t[pattern: '/**',             filters: 'JOINED_FILTERS']"
		writer.writeLine ']'
		writer.newLine()
	}
}

private void generateFile(String templateName, String packagePath, String className, String fileName = null, String folder = 'grails-app/domain') {
	render template(templateName + '.groovy.template'),
	       file("${folder}/$packagePath/${fileName ?: className}.groovy"),
	       templateAttributes, false
}

private void addBeans(List<Map> beans, String pathname) {
	def f = new File(pathname)
	def lines = []
	beans.each { Map bean ->
		lines << bean.import
	}
	if ( f.exists() ) {
		f.eachLine { line, nb ->
			lines << line
			if ( line.contains('beans = {') ) {
				beans.each { Map bean ->
					lines << '    ' + bean.definition
				}
			}
		}
	} else {
		lines << 'beans = {'
		beans.each { Map bean ->
			lines << '    ' + bean.definition
		}
		lines << '}'
	}

	f.withWriter('UTF-8') { writer ->
		lines.each { String line ->
			writer.write "${line}${System.lineSeparator()}"
		}
	}
}
