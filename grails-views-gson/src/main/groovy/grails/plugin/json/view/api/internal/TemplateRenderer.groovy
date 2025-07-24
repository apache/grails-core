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

package grails.plugin.json.view.api.internal

import groovy.transform.CompileStatic

import grails.plugin.json.view.api.GrailsJsonViewHelper
import grails.util.GrailsNameUtils

/**
 * Handles the template namespace
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class TemplateRenderer {

    final @Delegate GrailsJsonViewHelper jsonViewHelper

    TemplateRenderer(GrailsJsonViewHelper jsonViewHelper) {
        this.jsonViewHelper = jsonViewHelper
    }

    @Override
    Object invokeMethod(String name, Object args) {
        Object[] argArray = (Object[]) args


        def absolute = name.lastIndexOf('/')
        String modelName = absolute > -1 ? name.substring(absolute+1, name.length()) : name
        int len = argArray.length
        if(len == 1) {
            def val = argArray[0]
            if(val == null) {
                return null
            }
            if(val instanceof Map) {
                return jsonViewHelper.render(template:name, model:val)
            }
            else if(val instanceof Iterable) {
                return jsonViewHelper.render(template:name, var:modelName, collection:val)
            }
            else {
                def model = [(modelName): val]
                model.put(GrailsNameUtils.getPropertyName(val.getClass()), val)
                return jsonViewHelper.render(template:name, model: model)
            }
        }
        else if(len == 2) {
            def var = argArray[0]
            def coll = argArray[1]
            if (var instanceof Iterable) {
                if (coll instanceof Map) {
                    return jsonViewHelper.render(template:name, var:modelName, collection:var, model:coll)
                }
            }
            else if (coll instanceof Iterable) {
                return jsonViewHelper.render(template:name, var:var.toString(), collection:coll)
            }
        }
        else if(len == 3) {
            def var = argArray[0]
            def coll = argArray[1]
            def model = (Map)argArray[2]
            jsonViewHelper.render(template:name, model:model, collection:coll, var:var.toString())
        }

    }
}
