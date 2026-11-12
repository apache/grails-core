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
package org.grails.web.mapping.nsinference.admin

import grails.artefact.Artefact

/**
 * An {@code admin}-namespaced controller whose logical name {@code author} also exists at the root,
 * used to exercise the ambiguous root-plus-namespaced resolution case.
 */
@Artefact('Controller')
class AuthorController {
    static namespace = 'admin'
    def index() {}
    def list() {}
}

/**
 * An {@code admin}-namespaced controller whose logical name {@code book} exists only in the
 * {@code admin} namespace, used to exercise the unambiguous single-namespaced resolution case.
 */
@Artefact('Controller')
class BookController {
    static namespace = 'admin'
    def index() {}
    def list() {}
}

/**
 * An {@code admin}-namespaced controller whose logical name {@code report} also exists in the
 * {@code sales} namespace, used to exercise the ambiguous multiple-namespaced resolution case.
 */
@Artefact('Controller')
class ReportController {
    static namespace = 'admin'
    def index() {}
}
