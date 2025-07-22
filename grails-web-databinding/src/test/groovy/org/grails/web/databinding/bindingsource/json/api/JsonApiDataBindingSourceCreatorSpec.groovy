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
package org.grails.web.databinding.bindingsource.json.api

import grails.databinding.DataBindingSource
import org.grails.web.databinding.bindingsource.JsonApiDataBindingSourceCreator
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * Created by jameskleeh on 9/29/16.
 */
class JsonApiDataBindingSourceCreatorSpec extends Specification {

    @Shared
    JsonApiDataBindingSourceCreator creator = new JsonApiDataBindingSourceCreator()

    void "test create single relationship"() {
        given:
        String json = '''{
            "data": {
                "type": "photos",
                "attributes": {
                    "title": "Ember Hamster",
                    "src": "http://example.com/images/productivity.png"
                },
                "relationships": {
                    "photographer": {
                        "data": {
                            "type": "people",
                            "id": "9"
                        }
                    }
                }
            }
        }'''

        when:
        def inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
        DataBindingSource source = creator.createDataBindingSource(null, null, inputStream)

        then:
        source.containsProperty("data")
        source.getPropertyValue("title") == "Ember Hamster"
        source.getPropertyValue("src") == "http://example.com/images/productivity.png"
        source.getPropertyValue("photographer") == "9"

    }

    void "test create list of relationships"() {
        given:
        String json = '''{
            "data": {
                "type": "photos",
                "attributes": {
                    "title": "Ember Hamster",
                    "src": "http://example.com/images/productivity.png"
                },
                "relationships": {
                    "photographer": {
                        "data": [
                            {
                                "type": "people",
                                "id": "9"
                            },
                            {
                                "type": "people",
                                "id": "10"
                            }
                        ]
                    }
                }
            }
        }'''

        when:
        def inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
        DataBindingSource source = creator.createDataBindingSource(null, null, inputStream)

        then:
        source.containsProperty("data")
        source.getPropertyValue("title") == "Ember Hamster"
        source.getPropertyValue("src") == "http://example.com/images/productivity.png"
        source.getPropertyValue("photographer") == ["9", "10"]

    }

    void "test create no relationships - with key"() {
        given:
        String json = '''{
            "data": {
                "type": "photos",
                "attributes": {
                    "title": "Ember Hamster",
                    "src": "http://example.com/images/productivity.png"
                },
                "relationships": {
                }
            }
        }'''

        when:
        def inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
        DataBindingSource source = creator.createDataBindingSource(null, null, inputStream)

        then:
        source.containsProperty("data")
        source.getPropertyValue("title") == "Ember Hamster"
        source.getPropertyValue("src") == "http://example.com/images/productivity.png"
    }

    void "test create no relationships - without key"() {
        given:
        String json = '''{
            "data": {
                "type": "photos",
                "attributes": {
                    "title": "Ember Hamster",
                    "src": "http://example.com/images/productivity.png"
                }
            }
        }'''

        when:
        def inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
        DataBindingSource source = creator.createDataBindingSource(null, null, inputStream)

        then:
        source.containsProperty("data")
        source.getPropertyValue("title") == "Ember Hamster"
        source.getPropertyValue("src") == "http://example.com/images/productivity.png"
    }

    void "test create no attributes - with key"() {
        given:
        String json = '''{
            "data": {
                "type": "photos",
                "attributes": {
                },
                "relationships": {
                }
            }
        }'''

        when:
        def inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
        DataBindingSource source = creator.createDataBindingSource(null, null, inputStream)

        then:
        source.containsProperty("data")
    }

    void "test create no attributes - without key"() {
        given:
        String json = '''{
            "data": {
                "type": "photos"
            }
        }'''

        when:
        def inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
        DataBindingSource source = creator.createDataBindingSource(null, null, inputStream)

        then:
        source.containsProperty("data")
    }

    void "test create data id"() {
        given:
        String json = '''{
            "data": {
                "type": "photos",
                "id": "foo"
            }
        }'''

        when:
        def inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
        DataBindingSource source = creator.createDataBindingSource(null, null, inputStream)

        then:
        source.containsProperty("data")
        source.getPropertyValue("id") == "foo"
    }
}