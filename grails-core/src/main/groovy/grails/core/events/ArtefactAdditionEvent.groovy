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

package grails.core.events

import grails.core.GrailsClass
import groovy.transform.CompileStatic
import org.springframework.context.ApplicationEvent

/**
 * An event triggered for the addition of a new artefact
 */
@CompileStatic
class ArtefactAdditionEvent extends ApplicationEvent {
    /**
     * Create a new ApplicationEvent.
     * @param source the component that published the event (never {@code null})
     */
    ArtefactAdditionEvent(GrailsClass artefact) {
        super(artefact)
    }

    GrailsClass getArtefact() {
        (GrailsClass) source
    }
}
