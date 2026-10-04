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
package com.example

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.timestamp.AuditorAware
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@CompileStatic
class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }

    def beans = {
        bean('auditorAware', AuditorAware, SpringSecurityAuditorAware)

        // Spring Security's login page declares no icon, so the browser falls back to /favicon.ico
        bean('securityFilterChain', SecurityFilterChain) { HttpSecurity http ->
            http.authorizeHttpRequests { requests ->
                requests.requestMatchers('/favicon.ico', '/assets/**').permitAll()
                        .anyRequest().authenticated()
            }
            http.formLogin(Customizer.withDefaults())
            http.httpBasic(Customizer.withDefaults())
            http.build()
        }
    }
}
