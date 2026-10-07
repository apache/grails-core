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
package org.grails.forge.web

import grails.compiler.GrailsCompileStatic
import grails.web.Controller
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.grails.forge.api.SelectOptionsDTO
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import tools.jackson.databind.json.JsonMapper

@Controller
@GrailsCompileStatic
@Tag(name = 'options', description = 'The options of the generator')
class SelectOptionsController {

    static allowedMethods = [index: 'GET']

    @Autowired
    MessageSource messageSource

    @Autowired
    JsonMapper jsonMapper

    @Operation(summary = 'The options of the generator', description = 'Every option group of the generator, with its choices and its default, for a UI to offer.')
    @ApiResponse(responseCode = '200', description = 'The option groups', content = @Content(mediaType = 'application/json', schema = @Schema(implementation = SelectOptionsDTO)))
    def index() {
        SelectOptionsDTO options = SelectOptionsDTO.make(messageSource, request.locale ?: Locale.ENGLISH)
        render text: jsonMapper.writeValueAsString(options), contentType: 'application/json'
    }
}
