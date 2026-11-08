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

import groovy.transform.CompileStatic

import grails.boot.GrailsAppBuilder
import grails.boot.config.GrailsAutoConfiguration
import contexthierarchy.shared.SharedConfiguration

/**
 * A Grails application that starts as the child of a plain Spring parent context. The parent holds
 * the infrastructure the application shares with anything else that hangs beneath it, here an
 * {@link contexthierarchy.shared.AuditTrail}; the Grails plugin lifecycle, the controllers, the
 * services and GORM all live in the child, which is the context that serves web requests.
 */
@CompileStatic
class Application extends GrailsAutoConfiguration {

    static void main(String[] args) {
        new GrailsAppBuilder(SharedConfiguration)
                .child(Application)
                .run(args)
    }
}
