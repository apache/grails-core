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

import java.time.Duration
import java.util.concurrent.TimeUnit

import functionaltests.services.AsyncProcessingService
import spock.lang.Specification
import spock.lang.Unroll

import org.springframework.beans.factory.annotation.Autowired

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Integration tests for async/promise functionality in Grails.
 * Tests various async patterns including tasks, promises, chaining, and error handling.
 */
@Integration
class AsyncPromiseSpec extends Specification implements HttpClientSupport {

    @Autowired AsyncProcessingService asyncProcessingService

    // ========== Basic Async Task Tests ==========

    def "simple async task completes successfully"() {
        when: "calling simple task endpoint"
        def response = http('/asyncTest/simpleTask')

        then: "task completes with success status"
        response.expectJson(200, [
                status: 'completed',
                message: 'Task finished'
        ])
    }

    def "compute task returns calculated value"() {
        given: "an input value"
        def value = 7

        when: "calling compute endpoint"
        def response = http("/asyncTest/computeTask?value=${value}")

        then: "computed result is correct"
        response.expectJson(200, [
                input: value,
                result: value * value
        ])
    }

    def "parallel tasks complete and return all results"() {
        when: "calling parallel tasks endpoint"
        def response = http('/asyncTest/parallelTasks')

        then: "all tasks complete"
        response.expectJson(200, [
                status: 'completed',
                results: [
                        'Task 1 result',
                        'Task 2 result',
                        'Task 3 result'
                ]
        ])
    }

    def "chained tasks process data through multiple stages"() {
        given: "an input string"
        def input = 'hello'

        when: "calling chained endpoint"
        def response = http("/asyncTest/chainedTasks?input=${input}")

        then: "data is processed through all stages"
        response.expectJson(200, [
                original: input,
                final: input.toUpperCase().reverse()
        ])
    }

    // ========== Error Handling Tests ==========

    def "async task handles success without error"() {
        when: "calling task that should succeed"
        def response = http('/asyncTest/taskWithError?fail=false')

        then: "success response returned"
        response.expectJson(200, [
                status: 'success',
                result: 'Success'
        ])
    }

    @Unroll
    def "async task with timeout completes within time limit"(int delay, int timeout, int elapsedMin, String status, String result) {
        when: "calling task with reasonable timeout"
        def response = http("/asyncTest/taskWithTimeout?delay=$delay&timeout=$timeout")

        then: "task completes as expected"
        response.expectStatus(200)
        with(response.json()) {
            it.status == status
            it.result.startsWith(result)
            elapsedMs >= elapsedMin
        }

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
        def response = http("/asyncTest/useAsyncService?input=${input}")

        then: "service processes input correctly"
        response.expectJson(200, [
                input: input,
                result: "Processed: ${input.toUpperCase()}"
        ])
    }

    def "async service calculates square"() {
        given: "a numeric value"
        def value = 6

        when: "calling async calculation endpoint"
        def response = http("/asyncTest/asyncCalculation?value=${value}")

        then: "calculation is correct"
        response.expectJson(200, [
                input: value,
                squared: value * value
        ])
    }

    def "async batch processing reverses all items"() {
        when: "calling batch endpoint with default items"
        def response = http('/asyncTest/asyncBatch')

        then: "all items are reversed"
        response.expectStatus(200)
        def json = response.json()
        def original = json['original'] as List<String>
        def processed = json['processed'] as List<String>
        original.size() == processed.size()
        original.eachWithIndex { item, idx ->
            assert processed[idx] == item.reverse()
        }
    }

    def "long running operation completes with task info"() {
        given: "a task ID"
        def taskId = 'task-123'

        when: "calling long running endpoint"
        def response = http("/asyncTest/longRunning?taskId=${taskId}")

        then: "operation completes with expected info"
        response.expectStatus(200)
        with(response.json()) {
            it.taskId == taskId
            status == 'completed'
            durationMs >= 200
            completedAt != null
        }
    }

    def "CompletableFuture composition combines results"() {
        given: "two input values"
        def v1 = 3
        def v2 = 4

        when: "calling compose endpoint"
        def response = http("/asyncTest/composeFutures?v1=${v1}&v2=${v2}")

        then: "both futures are combined correctly"
        response.expectJson(200, [
                value1Squared: v1 * v1,  // 9
                value2Squared: v2 * v2,  // 16
                sum: (v1 * v1) + (v2 * v2)  // 25
        ])
    }

    // ========== Request Data Processing Tests ==========

    def "async task processes JSON request body"() {
        given: "JSON request body"
        def body = '{"name": "test", "value": "hello"}'

        when: "posting to process endpoint"
        def response = httpPostJson('/asyncTest/processRequestData', body)

        then: "data is processed correctly"
        response.expectJson(200, [
                original: [
                        name: 'test',
                        value: 'hello'
                ],
                processed: [
                        name: 'TEST',
                        value: 'HELLO'
                ]
        ])
    }

    def "multi-stage process reports all stages"() {
        when: "calling multi-stage endpoint"
        def response = http('/asyncTest/multiStageProcess')

        then: "all stages are reported"
        response.expectJsonContains(200, [
                status: 'completed',
                totalStages: 3,
                stages: [
                        [action: 'initialize'],
                        [action: 'process'],
                        [action: 'finalize']
                ]
        ])
    }

    def "stages execute in correct order"() {
        when: "calling multi-stage endpoint"
        def response = http('/asyncTest/multiStageProcess')

        then: "stages have increasing timestamps"
        response.expectStatus(200)
        def times = response.json().stages.collect { it['time'] as Long }
        times[0] <= times[1]
        times[1] <= times[2]
    }

    // ========== Conditional Execution Tests ==========

    def "conditional async uses async mode when requested"() {
        given: "input value"
        def input = 'grails'

        when: "calling with async=true"
        def response = http("/asyncTest/conditionalAsync?async=true&input=${input}")

        then: "async mode is used"
        response.expectJson(200, [
                mode: 'async',
                result: "Async: ${input.toUpperCase()}"
        ])
    }

    def "conditional async uses sync mode when requested"() {
        given: "input value"
        def input = 'grails'

        when: "calling with async=false"
        def response = http("/asyncTest/conditionalAsync?async=false&input=${input}")

        then: "sync mode is used"
        response.expectJson(200, [
                mode: 'sync',
                result: "Sync: ${input.toUpperCase()}"
        ])
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
