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
   grails init-oauth2 <domain-class-package> <user-class-name> <oauthid-class-name>

   Creates an OAuthID class in the specified package

Example: grails init-oauth2 com.yourapp User OAuthID
or grails init-oauth2 com.yourapp com.yourapp.user.User OAuthID
'''

description 'Creates domain class and update the config settings fpr the oauth2 plugin', {
    usage usageMessage

    argument name: 'Domain class package', description: 'The package to use for the domain classes', required: false
    argument name: 'User class name', description: 'The  full name of the User class of th SpringSecurityPlugin', required: false
    argument name: 'OAuthID class name', description: 'The name of the OAuthID class', required: false
}

if (args.size() < 3) {
    error 'Usage:' + usageMessage
    return false
}

Model userClassModel
Model oAuthIDClassModel

String oAuthIDPackageName = args[0]
userClassModel = model(args[1].contains('.') ? args[1] : oAuthIDPackageName + '.' + args[1])
oAuthIDClassModel = model(oAuthIDPackageName + '.' + args[2])

String message = "Creating OAuthID class '" + oAuthIDClassModel.simpleName + "'"
addStatus message

templateAttributes = [
        userClassFullName : userClassModel.fullName,
        userClassName     : userClassModel.simpleName,
        oAuthIDPackageName: oAuthIDPackageName,
        oAuthIDClassName  : oAuthIDClassModel.simpleName,
]

generateFile 'OAuthID', oAuthIDClassModel.packagePath, oAuthIDClassModel.simpleName

file('grails-app/conf/application.groovy').withWriterAppend { BufferedWriter writer ->
    writer.newLine()
    writer.newLine()
    writer.writeLine("// Added by the Spring Security OAuth2 Google Plugin:")
    writer.writeLine("grails.plugin.springsecurity.oauth2.domainClass = '${oAuthIDPackageName}.${oAuthIDClassModel.simpleName}'")
}

addStatus '''
************************************************************
* Created  domain classe.                                  *
* Your grails-app/conf/application.groovy has been updated *
* with the class name of the configured domain class;      *
* please verify that the values are correct.               *
************************************************************
'''

private void generateFile(String templateName, String packagePath, String className) {
    render template(templateName + '.groovy.template'),
            file("grails-app/domain/$packagePath/${className}.groovy"),
            templateAttributes, false
}