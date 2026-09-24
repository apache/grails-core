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
package org.grails.web.mapping.domainlink.archive

import grails.artefact.Artefact
import org.grails.web.mapping.domainlink.Pamphlet
import org.grails.web.mapping.domainlink.ResourceControllerBase

/**
 * Declares {@code Pamphlet} under the same name in the {@code archive} namespace, but does not show it, so a
 * link to show a pamphlet resolves to the {@code print} controller while the name alone is ambiguous.
 */
@Artefact('Controller')
class PamphletsController extends ResourceControllerBase<Pamphlet> {
    static namespace = 'archive'
    def index() {}
}
