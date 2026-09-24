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
package grails.plugin.json.view.hallinks

import grails.artefact.Artefact
import grails.persistence.Entity

@Entity
class Film {
    String title
}

/**
 * Stands in for a generic REST controller base class, which declares the domain class a controller serves.
 */
abstract class ServingControllerBase<T> {
}

/**
 * Serves {@link Film} under another name, with no controller named after the domain class.
 */
@Artefact('Controller')
class MoviesController extends ServingControllerBase<Film> {
    def index() {}
    def show() {}
}
