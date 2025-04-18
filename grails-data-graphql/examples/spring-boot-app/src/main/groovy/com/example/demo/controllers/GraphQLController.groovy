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
