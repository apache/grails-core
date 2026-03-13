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
import java.util.concurrent.atomic.AtomicBoolean

import functionaltests.services.AsyncProcessingService

import grails.converters.JSON

import static grails.async.web.WebPromises.task

/**
 * Controller demonstrating async request handling patterns in Grails.
 */
class AsyncTestController {

    static responseFormats = ['json', 'html']

    AsyncProcessingService asyncProcessingService

    /**
     * Simple async task that completes after a delay.
     */
    def simpleTask() {
        task {
            sleep 100
            render([status: 'completed', message: 'Task finished'] as JSON)
        }
    }

    /**
     * Async task returning a computed value.
     */
    def computeTask() {
        def input = params.int('value', 10)
        task {
            sleep 50
            def result = input * input
            render([input: input, result: result] as JSON)
        }
    }

    /**
     * Multiple async tasks - simulates parallel work, returns combined results.
     */
    def parallelTasks() {
        task {
            // Simulate parallel work by doing multiple operations
            def result1 = 'Task 1 result'
            def result2 = 'Task 2 result'
            def result3 = 'Task 3 result'
            sleep 50
            def results = [result1, result2, result3]
            render([
                status: 'completed',
                results: results
            ] as JSON)
        }
    }

    /**
     * Chained async tasks.
     */
    def chainedTasks() {
        def input = params.input ?: 'test'
        
        task {
            sleep 50
            return input.toUpperCase()
        }.then { result ->
            sleep 50
            return result.reverse()
        }.then { result ->
            render([
                original: input,
                final: result
            ] as JSON)
        }
    }

    /**
     * Async task with error handling.
     */
    def taskWithError() {
        def shouldFail = params.boolean('fail', false)
        
        task {
            sleep 50
            if (shouldFail) {
                throw new RuntimeException('Intentional async error')
            }
            return 'Success'
        }.onComplete { result ->
            render([
                status: 'success',
                result: result
            ] as JSON)
        }
    }

    /**
     * Timeout handling for async task.
     */
    def taskWithTimeout() {
        def delay = params.int('delay', 100)
        def timeout = params.int('timeout', 500)

        def startTime = System.currentTimeMillis()
        task {
            def status = 'completed'
            def message = 'Completed in time'
            def completed = new AtomicBoolean(false)

            def workThread = Thread.start {
                sleep(delay)
                completed.set(true)
            }
            workThread.join(timeout)

            if (!completed.get()) {
                workThread.interrupt()
                status = 'timeout'
                message = "Task exceeded timeout of ${timeout} ms"
            }

            return [
                    status: status,
                    message: message
            ]
        }.onComplete { taskResult ->
            def elapsed = System.currentTimeMillis() - startTime
            render([
                status: taskResult.status,
                result: taskResult.message,
                elapsedMs: elapsed
            ] as JSON)
        }
    }

    /**
     * Use the async service directly.
     */
    def useAsyncService() {
        def input = params.input ?: 'hello'
        
        def future = asyncProcessingService.processAsync(input)
        
        // Wait for result (up to 5 seconds)
        def result = future.get(10, TimeUnit.SECONDS)
        
        render([
            input: input,
            result: result
        ] as JSON)
    }

    /**
     * Async service with calculation.
     */
    def asyncCalculation() {
        def value = params.int('value', 5)
        
        def future = asyncProcessingService.calculateAsync(value)
        def result = future.get(10, TimeUnit.SECONDS)
        
        render([
            input: value,
            squared: result
        ] as JSON)
    }

    /**
     * Batch processing with async service.
     */
    def asyncBatch() {
        def items = params.list('items') ?: ['one', 'two', 'three']
        
        def future = asyncProcessingService.processBatchAsync(items)
        def results = future.get(10, TimeUnit.SECONDS)
        
        render([
            original: items,
            processed: results
        ] as JSON)
    }

    /**
     * Long-running operation.
     */
    def longRunning() {
        def taskId = params.taskId ?: UUID.randomUUID().toString()
        
        def future = asyncProcessingService.longRunningOperation(taskId)
        def result = future.get(10, TimeUnit.SECONDS)
        
        render(result as JSON)
    }

    /**
     * CompletableFuture composition.
     */
    def composeFutures() {
        def value1 = params.int('v1', 3)
        def value2 = params.int('v2', 4)
        
        def future1 = asyncProcessingService.calculateAsync(value1)
        def future2 = asyncProcessingService.calculateAsync(value2)
        
        def combined = future1.thenCombine(future2) { r1, r2 ->
            [
                value1Squared: r1,
                value2Squared: r2,
                sum: r1 + r2
            ]
        }
        
        def result = combined.get(10, TimeUnit.SECONDS)
        render(result as JSON)
    }

    /**
     * Async task that processes request data.
     */
    def processRequestData() {
        def data = request.JSON ?: [value: 'default']
        
        task {
            sleep 50
            def processed = data.collectEntries { k, v ->
                [(k): v?.toString()?.toUpperCase()]
            }
            return processed
        }.onComplete { result ->
            render([
                original: data,
                processed: result
            ] as JSON)
        }
    }

    /**
     * Demonstrates async response with multiple stages reporting.
     */
    def multiStageProcess() {
        task {
            def t1 = System.currentTimeMillis()
            sleep 30
            def t2 = System.currentTimeMillis()
            sleep 30
            def t3 = System.currentTimeMillis()
            
            def stages = [
                [stage: 1, action: 'initialize', time: t1],
                [stage: 2, action: 'process', time: t2],
                [stage: 3, action: 'finalize', time: t3]
            ]
            render([
                status: 'completed',
                stages: stages,
                totalStages: stages.size()
            ] as JSON)
        }
    }

    /**
     * Conditional async execution.
     */
    def conditionalAsync() {
        def useAsync = params.boolean('async', true)
        def input = params.input ?: 'test'
        
        if (useAsync) {
            task {
                sleep 50
                return "Async: ${input.toUpperCase()}"
            }.onComplete { result ->
                render([mode: 'async', result: result] as JSON)
            }
        } else {
            // Synchronous execution
            def result = "Sync: ${input.toUpperCase()}"
            render([mode: 'sync', result: result] as JSON)
        }
    }
}
