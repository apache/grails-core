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
package enablemvccheck

import groovy.transform.CompileStatic

import org.springframework.web.servlet.config.annotation.EnableWebMvc

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration

/**
 * Declares {@code @EnableWebMvc} explicitly. Grails 7 auto-injected this annotation into
 * every Application class; Grails 8 no longer does. This application opts back in so the
 * integration tests can verify that the {@code @EnableWebMvc} behavior is identical
 * whether the annotation is auto-injected by the framework or declared by the application.
 */
@EnableWebMvc
@CompileStatic
class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }
}
