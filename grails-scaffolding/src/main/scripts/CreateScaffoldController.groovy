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
    usage 'create-controller [controller name]'
    completer org.grails.cli.interactive.completers.DomainClassCompleter
    argument name: 'Controller Name', description: "The name of controller", required: true
    flag name: 'force', description: "Whether to overwrite existing files"
}

def model = model(args[0])
def overwrite = flag('force') ? true : false

render template: template('scaffolding/ScaffoldedController.groovy'),
        destination: file("grails-app/controllers/${model.packagePath}/${model.convention("Controller")}.groovy"),
        model: model,
        overwrite: overwrite

return true
