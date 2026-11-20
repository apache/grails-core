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

package hierarchycli.command

import groovy.transform.CompileStatic

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import hierarchycli.VisitorService

/**
 * The plain Spring child of the Grails application: the command that does the work. It injects the
 * Grails service through the parent lookup and runs no plugin lifecycle of its own. Nothing in the
 * Grails application refers to it, so it is only ever a child.
 */
@Configuration(proxyBeanMethods = false)
@CompileStatic
class ReportingCommand {

    @Bean
    VisitorReportRunner visitorReportRunner(VisitorService visitorService) {
        new VisitorReportRunner(visitorService)
    }
}
