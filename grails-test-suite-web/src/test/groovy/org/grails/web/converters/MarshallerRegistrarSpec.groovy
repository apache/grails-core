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
package org.grails.web.converters

import grails.artefact.Artefact
import grails.converters.JSON
import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest

import jakarta.annotation.PostConstruct
import spock.lang.Specification

class MarshallerRegistrarSpec extends Specification implements ControllerUnitTest<JsonMarshallerController>, DomainUnitTest<Post> {

    Closure doWithSpring() {{ ->
        marshallerRegistrar(MarshallerRegistrar)
    }}
    
    def "should use custom marshaller"() {
        when:
        controller.show()
        then:
        response.contentAsString == '{"content":"Content","custom":true}'
    }
    
}

@Artefact("Controller")
class JsonMarshallerController {
    def show() {
        def post = new Post()
        post.content = 'Content'
        post.dateCreated = new Date()
        render post as JSON
    }
}

class MarshallerRegistrar {
    @PostConstruct
    void registerMarshallers() {
        println "Registering custom marshallers"
        JSON.registerObjectMarshaller(Post) { Post p ->
            return [ content: p.content, custom: true ]
        }
    }
}

@Entity
class Post {
    String content
    Date dateCreated
}