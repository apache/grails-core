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

import groovy.transform.CompileStatic

import grails.web.mime.MimeType

/**
 * Creates DataBindingSource objects from JSON API in the request body
 *
 * @since 3.2.1
 *
 * @author James Kleeh
 *
 * @see grails.databinding.DataBindingSource
 * @see org.grails.databinding.bindingsource.DataBindingSourceCreator
 */
@CompileStatic
class JsonApiDataBindingSourceCreator extends JsonDataBindingSourceCreator {

    protected static final String DATA = "data"
    protected static final String RELATIONSHIPS = "relationships"
    protected static final String ID = "id"
    protected static final String ATTRIBUTES = "attributes"

    @Override
    MimeType[] getMimeTypes() {
        [MimeType.JSON_API] as MimeType[]
    }

    @Override
    protected Map createJsonMap(Object jsonElement) {
        if(jsonElement instanceof Map) {
            def jsonMap = (Map) jsonElement
            if(jsonMap.containsKey(DATA)) {
                jsonMap = new LinkedHashMap(jsonMap)
                def data = jsonMap.get(DATA)
                if (data instanceof Map) {
                    if (data.containsKey(ID)) {
                        jsonMap.put(ID, data.get(ID))
                    }
                    if (data.containsKey(ATTRIBUTES)) {
                        jsonMap.putAll((Map)data.get(ATTRIBUTES))
                    }
                    if (data.containsKey(RELATIONSHIPS)) {
                        Map relationships = (Map)data.get(RELATIONSHIPS)
                        relationships.each { key, val ->
                            if (val instanceof Map && ((Map)val).containsKey(DATA)) {
                                def rData = ((Map)val).get(DATA)
                                if (rData instanceof Map) {
                                    jsonMap.put(key, rData.get(ID))
                                } else if (rData instanceof List) {
                                    jsonMap.put(key, rData.collect { d -> d[ID] })
                                }
                            }
                        }
                    }
                }
            }
            return jsonMap
        }
        else {
            return super.createJsonMap(jsonElement)
        }
    }

}
