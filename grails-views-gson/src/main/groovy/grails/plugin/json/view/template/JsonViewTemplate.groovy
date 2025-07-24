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

package grails.plugin.json.view.template

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

import grails.plugin.json.builder.JsonGenerator
import grails.plugin.json.view.JsonViewWritableScript
import grails.plugin.json.view.api.jsonapi.JsonApiIdRenderStrategy
import grails.views.GrailsViewTemplate

@CompileStatic
@InheritConstructors
class JsonViewTemplate extends GrailsViewTemplate {

    JsonGenerator generator
    JsonApiIdRenderStrategy jsonApiIdRenderStrategy

    @Override
    Writable make(Map binding) {
        JsonViewWritableScript writableTemplate = (JsonViewWritableScript)super.make(binding)
        writableTemplate.setGenerator(generator)
        writableTemplate.setJsonApiIdRenderStrategy(jsonApiIdRenderStrategy)
        writableTemplate
    }
}
