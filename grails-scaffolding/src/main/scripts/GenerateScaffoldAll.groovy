/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

description('Generates a scaffolded service and controller') {
  	usage 'generate-scaffold-all [domain class name]'
    completer org.grails.cli.interactive.completers.DomainClassCompleter
    argument name:'Domain Class Name', description: 'The name of domain class', required: true
    flag name:'force', description: 'Whether to overwrite existing files'
    flag name:'namespace', description: 'The namespace for the controller'
    flag name:'serviceExtends', description: 'The class to extend for the service (default: grails.plugin.scaffolding.GormService)'
    flag name:'controllerExtends', description: 'The class to extend for the controller (default: grails.plugin.scaffolding.RestfulServiceController)'
 }

def modelInstance = model(args[0])
def overwrite = flag('force') ? true : false
def namespace = flag('namespace')
def serviceExtends = flag('serviceExtends')
def controllerExtends = flag('controllerExtends')

// Generate scaffolded service
def serviceTemplateModel = modelInstance.asMap()
serviceTemplateModel.put('extendsClass', serviceExtends ?: '')
serviceTemplateModel.put('extendsClassName', serviceExtends ? serviceExtends.substring(serviceExtends.lastIndexOf('.') + 1) : '')

render 	 template: template('scaffolding/ScaffoldedService.groovy'),
	     destination: file("grails-app/services/${modelInstance.packagePath}/${modelInstance.convention('Service')}.groovy"),
	     model: serviceTemplateModel,
	     overwrite: overwrite

// Generate scaffolded controller with service reference
def controllerTemplateModel = modelInstance.asMap()
controllerTemplateModel.put('useService', true)
controllerTemplateModel.put('namespace', namespace ?: '')
controllerTemplateModel.put('extendsClass', controllerExtends ?: '')
controllerTemplateModel.put('extendsClassName', controllerExtends ? controllerExtends.substring(controllerExtends.lastIndexOf('.') + 1) : '')

def controllerDestinationPath = "grails-app/controllers/${modelInstance.packagePath}"

if (namespace) {
    controllerDestinationPath = "${controllerDestinationPath}/${namespace}"
}

render 	 template: template('scaffolding/ScaffoldedController.groovy'),
	     destination: file("${controllerDestinationPath}/${modelInstance.convention('Controller')}.groovy"),
	     model: controllerTemplateModel,
	     overwrite: overwrite

return true
