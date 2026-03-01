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
package functionaltests.async

import java.util.concurrent.TimeUnit

import groovy.json.JsonSlurper

import functionaltests.services.AsyncProcessingService
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import spock.lang.Specification
import spock.lang.Unroll

import org.springframework.beans.factory.annotation.Autowired

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.httpclient.HttpClientSupport

/**
 * Integration tests for async/promise functionality in Grails.
 * Tests various async patterns including tasks, promises, chaining, and error handling.
 */
@Integration
class AsyncPromiseSpec extends Specification implements HttpClientSupport {

    @Autowired
    AsyncProcessingService asyncProcessingService

    // ========== Basic Async Task Tests ==========

    def "simple async task completes successfully"() {
        when: "calling simple task endpoint"
        def response = httpClient.exchange(
            '/asyncTest/simpleTask',
            String
        )

        then: "task completes with success status"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.status == 'completed'
        json.message == 'Task finished'
    }

    def "compute task returns calculated value"() {
        given: "an input value"
        def value = 7

        when: "calling compute endpoint"
        def response = httpClient.exchange(
            "/asyncTest/computeTask?value=${value}",
            String
        )

        then: "computed result is correct"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.input == value
        json.result == value * value
    }

    def "parallel tasks complete and return all results"() {
        when: "calling parallel tasks endpoint"
        def response = httpClient.exchange(
            '/asyncTest/parallelTasks',
            String
        )

        then: "all tasks complete"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.status == 'completed'
        json.results.size() == 3
        json.results.contains('Task 1 result')
        json.results.contains('Task 2 result')
        json.results.contains('Task 3 result')
    }

    def "chained tasks process data through multiple stages"() {
        given: "an input string"
        def input = 'hello'

        when: "calling chained endpoint"
        def response = httpClient.exchange(
            "/asyncTest/chainedTasks?input=${input}",
            String
        )

        then: "data is processed through all stages"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.original == input
        // HELLO reversed is OLLEH
        json.final == input.toUpperCase().reverse()
    }

    // ========== Error Handling Tests ==========

    def "async task handles success without error"() {
        when: "calling task that should succeed"
        def response = httpClient.exchange(
            '/asyncTest/taskWithError?fail=false',
            String
        )

        then: "success response returned"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.status == 'success'
        json.result == 'Success'
    }

    @Unroll
    def "async task with timeout completes within time limit"(int delay, int timeout, int elapsedMin, String status, String result) {
        when: "calling task with reasonable timeout"
        def response = httpClient.exchange(
            "/asyncTest/taskWithTimeout?delay=$delay&timeout=$timeout",
            String
        )

        then: "task completes as expected"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.status == status
        json.result.startsWith(result)
        json.elapsedMs >= elapsedMin

        where:
        delay | timeout | elapsedMin | status      | result
        100   | 500     | 100        | 'completed' | 'Completed in time'
        100   | 10      | 10         | 'timeout'   | 'Task exceeded timeout of '
    }

    // ========== Async Service Tests ==========

    def "async service processes string input"() {
        given: "an input string"
        def input = 'test'

        when: "calling async service endpoint"
        def response = httpClient.exchange(
            "/asyncTest/useAsyncService?input=${input}",
            String
        )

        and: "service processes input correctly"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.input == input
        json.result == "Processed: ${input.toUpperCase()}"
    }

    def "async service calculates square"() {
        given: "a numeric value"
        def value = 6

        when: "calling async calculation endpoint"
        def response = httpClient.exchange(
            "/asyncTest/asyncCalculation?value=${value}",
            String
        )

        then: "calculation is correct"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.input == value
        json.squared == value * value
    }

    def "async batch processing reverses all items"() {
        when: "calling batch endpoint with default items"
        def response = httpClient.exchange(
            '/asyncTest/asyncBatch',
            String
        )
        then: "all items are reversed"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.original.size() == json.processed.size()
        json.original.eachWithIndex { item, idx ->
            assert json.processed[idx] == item.reverse()
        }
    }

    def "long running operation completes with task info"() {
        given: "a task ID"
        def taskId = 'task-123'

        when: "calling long running endpoint"
        def response = httpClient.exchange(
            "/asyncTest/longRunning?taskId=${taskId}",
            String
        )

        then: "operation completes with expected info"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.taskId == taskId
        json.status == 'completed'
        json.durationMs >= 200
        json.completedAt != null
    }

    def "CompletableFuture composition combines results"() {
        given: "two input values"
        def v1 = 3
        def v2 = 4

        when: "calling compose endpoint"
        def response = httpClient.exchange(
            "/asyncTest/composeFutures?v1=${v1}&v2=${v2}",
            String
        )

        then: "both futures are combined correctly"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.value1Squared == v1 * v1  // 9
        json.value2Squared == v2 * v2  // 16
        json.sum == (v1 * v1) + (v2 * v2)  // 25
    }

    // ========== Request Data Processing Tests ==========

    def "async task processes JSON request body"() {
        given: "JSON request body"
        def body = '{"name": "test", "value": "hello"}'

        when: "posting to process endpoint"
        def response = httpClient.exchange(
            HttpRequest.POST('/asyncTest/processRequestData', body)
                .contentType(MediaType.APPLICATION_JSON),
            String
        )
        then: "data is processed correctly"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.original.name == 'test'
        json.processed.name == 'TEST'
        json.processed.value == 'HELLO'
    }

    def "multi-stage process reports all stages"() {
        when: "calling multi-stage endpoint"
        def response = httpClient.exchange(
            '/asyncTest/multiStageProcess',
            String
        )

        then: "all stages are reported"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.status == 'completed'
        json.totalStages == 3
        json.stages.size() == 3
        json.stages[0].action == 'initialize'
        json.stages[1].action == 'process'
        json.stages[2].action == 'finalize'
    }

    def "stages execute in correct order"() {
        when: "calling multi-stage endpoint"
        def response = httpClient.exchange(
            '/asyncTest/multiStageProcess',
            String
        )

        then: "stages have increasing timestamps"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        def times = json.stages.collect { it.time as Long }
        times[0] <= times[1]
        times[1] <= times[2]
    }

    // ========== Conditional Execution Tests ==========

    def "conditional async uses async mode when requested"() {
        given: "input value"
        def input = 'grails'

        when: "calling with async=true"
        def response = httpClient.exchange(
            "/asyncTest/conditionalAsync?async=true&input=${input}",
            String
        )

        then: "async mode is used"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.mode == 'async'
        json.result.contains('Async')
        json.result.contains(input.toUpperCase())
    }

    def "conditional async uses sync mode when requested"() {
        given: "input value"
        def input = 'grails'

        when: "calling with async=false"
        def response = httpClient.exchange(
            "/asyncTest/conditionalAsync?async=false&input=${input}",
            String
        )

        then: "sync mode is used"
        response.status == HttpStatus.OK
        def json = new JsonSlurper().parseText(response.body())
        json.mode == 'sync'
        json.result.contains('Sync')
        json.result.contains(input.toUpperCase())
    }

    // ========== Direct Service Tests ==========

    def "asyncProcessingService.processAsync returns correct result"() {
        given: "an input"
        def input = 'direct'

        when: "calling service directly"
        def future = asyncProcessingService.processAsync(input)
        def result = future.get(5, TimeUnit.SECONDS)

        then: "result is correct"
        result == "Processed: ${input.toUpperCase()}"
    }

    def "asyncProcessingService.calculateAsync squares value"() {
        given: "a numeric value"
        def value = 8

        when: "calling calculation service"
        def future = asyncProcessingService.calculateAsync(value)
        def result = future.get(5, TimeUnit.SECONDS)

        then: "value is squared"
        result == value * value
    }

    def "asyncProcessingService.processBatchAsync handles empty list"() {
        given: "empty list"
        def items = []

        when: "processing empty batch"
        def future = asyncProcessingService.processBatchAsync(items)
        def result = future.get(5, TimeUnit.SECONDS)

        then: "empty result returned"
        result != null
        result.isEmpty()
    }

    def "asyncProcessingService.processBatchAsync handles single item"() {
        given: "single item list"
        def items = ['single']

        when: "processing single item batch"
        def future = asyncProcessingService.processBatchAsync(items)
        def result = future.get(5, TimeUnit.SECONDS)

        then: "single reversed item returned"
        result.size() == 1
        result[0] == 'elgnis'
    }
}
