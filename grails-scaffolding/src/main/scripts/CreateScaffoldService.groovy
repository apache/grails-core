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

description("Creates a scaffolded service") {
  	usage 'create-scaffold-service [service name]'
    completer org.grails.cli.interactive.completers.DomainClassCompleter
    argument name:'Service Name', description:"The name of service", required:true
    flag name:'force', description:"Whether to overwrite existing files"
    flag name:'extends', description:"The class to extend (default: grails.plugin.scaffolding.GormService)"
 }

def modelInstance = model(args[0])
def overwrite = flag('force') ? true : false
def extendsClass = flag('extends')

def templateModel = modelInstance.asMap()
templateModel.put('extendsClass', extendsClass ?: '')
templateModel.put('extendsClassName', extendsClass ? extendsClass.substring(extendsClass.lastIndexOf('.') + 1) : '')

render 	 template: template('scaffolding/ScaffoldedService.groovy'),
	     destination: file("grails-app/services/${modelInstance.packagePath}/${modelInstance.convention("Service")}.groovy"),
	     model: templateModel,
	     overwrite: overwrite

return true
