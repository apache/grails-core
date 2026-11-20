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

description("Creates a scaffolded controller") {
  	usage 'create-scaffold-controller [controller name]'
    completer org.grails.cli.interactive.completers.DomainClassCompleter
    argument name:'Controller Name', description:"The name of controller", required:true
    flag name:'force', description:"Whether to overwrite existing files"
    flag name:'namespace', description:"The namespace for the controller"
    flag name:'service', description:"Use grails.plugin.scaffolding.RestfulServiceController instead of grails.rest.RestfulController"
    flag name:'extends', description:"The class to extend (default: grails.rest.RestfulController)"
 }

def modelInstance = model(args[0])
def overwrite = flag('force') ? true : false
def namespace = flag('namespace')
def useService = flag('service') ? true : false
def extendsClass = flag('extends')

def templateModel = modelInstance.asMap()
templateModel.put('useService', useService)
templateModel.put('namespace', namespace ?: '')
templateModel.put('extendsClass', extendsClass ?: '')
templateModel.put('extendsClassName', extendsClass ? extendsClass.substring(extendsClass.lastIndexOf('.') + 1) : '')

def destinationPath = "grails-app/controllers/${modelInstance.packagePath}"

if (namespace) {
    destinationPath = "${destinationPath}/${namespace}"
}

render 	 template: template('scaffolding/ScaffoldedController.groovy'),
	     destination: file("${destinationPath}/${modelInstance.convention("Controller")}.groovy"),
	     model: templateModel,
	     overwrite: overwrite

return true
