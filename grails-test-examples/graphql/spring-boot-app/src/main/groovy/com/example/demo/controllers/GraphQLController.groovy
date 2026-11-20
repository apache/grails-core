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

package com.example.demo.controllers

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
class GraphQLController {

    @Autowired
    GraphQL graphQL

    @RequestMapping(path = "/graphql", method = RequestMethod.POST)
    @ResponseBody Map index(@RequestBody String payload) {
        Map<String, Object> result = new LinkedHashMap<>()

        ExecutionResult executionResult = graphQL.execute(ExecutionInput.newExecutionInput()
                .query(payload)
                .context([locale: LocaleContextHolder.getLocale()]))

        if (executionResult.errors.size() > 0) {
            result.put('errors', executionResult.errors)
        }
        result.put('data', executionResult.data)

        result
    }
}
