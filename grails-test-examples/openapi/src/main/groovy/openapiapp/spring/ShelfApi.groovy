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
package openapiapp.spring

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

import openapiapp.Book
import openapiapp.Internal
import openapiapp.Magazine

/**
 * Spring MVC endpoints beside the Grails controllers, which Jackson renders and springdoc
 * describes. They are left out of the default document, and served in the shelf group.
 */
@RestController
@RequestMapping('/spring')
class ShelfApi {

    @Internal
    @GetMapping(path = '/books/{id}', produces = 'application/json')
    Book book(@PathVariable('id') Long id) {
        Book.get(id)
    }

    @Internal
    @GetMapping(path = '/magazines', produces = 'application/json')
    List<Magazine> magazines() {
        Magazine.list()
    }

    @Internal
    @PostMapping(path = '/checks', produces = 'application/json')
    ValidationErrors check() {
        new ValidationErrors(count: 0, problems: [])
    }
}
