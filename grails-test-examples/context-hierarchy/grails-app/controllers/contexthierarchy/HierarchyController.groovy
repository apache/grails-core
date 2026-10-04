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

package contexthierarchy

import org.springframework.context.ApplicationContext

import grails.compiler.GrailsCompileStatic
import grails.converters.JSON
import grails.core.GrailsApplication
import contexthierarchy.shared.AuditTrail

/**
 * Exercises the hierarchy from inside the Grails application: a bean of the parent context is
 * injected like any other, the Grails service and GORM work as usual, and the shape of the
 * hierarchy is reported for the functional tests to check.
 */
@GrailsCompileStatic
class HierarchyController {

    /** Defined by the parent context, reached through the normal parent lookup. */
    AuditTrail auditTrail

    VisitorService visitorService

    GrailsApplication grailsApplication

    def contexts() {
        ApplicationContext context = grailsApplication.mainContext
        ApplicationContext parent = context.parent
        render([
                parentPresent         : parent != null,
                auditTrailInParent    : parent != null && parent.containsLocalBean('auditTrail'),
                auditTrailLocal       : context.containsLocalBean('auditTrail'),
                grailsApplicationLocal: context.containsLocalBean(GrailsApplication.APPLICATION_ID),
        ] as JSON)
    }

    Map<String, Object> greet(String name) {
        auditTrail.record("greeted ${name}")
        [name: name, auditEntries: auditTrail.entries]
    }

    def record(String name) {
        visitorService.register(name)
        auditTrail.record("registered ${name}")
        render([count: visitorService.count(), names: visitorService.names()] as JSON)
    }
}
