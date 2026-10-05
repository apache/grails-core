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
package openapiapp

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse

import grails.rest.RestfulController

@ApiResponse(responseCode = '401', description = 'Not signed in')
class BookController extends RestfulController<Book> {

    static responseFormats = ['json']

    BookController() {
        super(Book)
    }

    @Operation(summary = 'List the catalog')
    @Parameter(name = 'genre', in = ParameterIn.QUERY, description = 'Only books of this genre')
    @Override
    Object index(Integer max) {
        super.index(max)
    }

    @Parameter(name = 'id', in = ParameterIn.PATH, description = 'The identifier of the book')
    @Override
    Object show() {
        super.show()
    }
}
