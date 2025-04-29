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
package org.grails.web.databinding.bindingsource

import grails.databinding.DataBindingSource;
import groovy.transform.CompileStatic

import grails.web.mime.MimeType


/**
 * Creates DataBindingSource objects from HAL JSON in the request body
 *
 * @since 2.3
 *
 * @author Jeff Brown
 * @author Graeme Rocher
 *
 * @see DataBindingSource
 * @see DataBindingSourceCreator
 */
@CompileStatic
class HalJsonDataBindingSourceCreator extends JsonDataBindingSourceCreator {

    public static final String HAL_EMBEDDED_ELEMENT = "_embedded"

    @Override
    MimeType[] getMimeTypes() {
        [MimeType.HAL_JSON] as MimeType[]
    }

    @Override
    protected Map createJsonMap(Object jsonElement) {
        if(jsonElement instanceof Map) {

            def jsonMap = (Map) jsonElement
            if(jsonMap.containsKey(HAL_EMBEDDED_ELEMENT)) {
                jsonMap = new LinkedHashMap(jsonMap)
                def embedded = jsonMap.get(HAL_EMBEDDED_ELEMENT)
                if(embedded instanceof Map) {
                    jsonMap.putAll((Map)embedded)
                }
            }
            return jsonMap
        }
        else {
            return super.createJsonMap(jsonElement)
        }
    }

}
