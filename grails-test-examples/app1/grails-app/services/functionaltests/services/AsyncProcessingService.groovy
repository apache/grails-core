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

package functionaltests.services

import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.AsyncResult
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

import java.util.concurrent.Future
import java.util.concurrent.CompletableFuture

/**
 * Service demonstrating async operations.
 */
@Slf4j
class AsyncProcessingService {

    /**
     * Simple async method returning Future
     */
    @Async
    Future<String> processAsync(String input) {
        log.info("Starting async processing for: ${input}")
        // Simulate processing time
        Thread.sleep(100)
        def result = "Processed: ${input.toUpperCase()}"
        log.info("Completed async processing: ${result}")
        return new AsyncResult<String>(result)
    }

    /**
     * Async method returning CompletableFuture
     */
    @Async
    CompletableFuture<Integer> calculateAsync(Integer value) {
        log.info("Starting async calculation for: ${value}")
        Thread.sleep(50)
        def result = value * value
        log.info("Completed async calculation: ${result}")
        return CompletableFuture.completedFuture(result)
    }

    /**
     * Async method that processes a list
     */
    @Async
    Future<List<String>> processBatchAsync(List<String> items) {
        log.info("Starting batch processing for ${items.size()} items")
        def results = items.collect { it.reverse() }
        Thread.sleep(100)
        log.info("Completed batch processing")
        return new AsyncResult<List<String>>(results)
    }

    /**
     * Async method with database operation
     */
    @Async
    @Transactional
    Future<Long> countItemsAsync() {
        log.info("Starting async count")
        Thread.sleep(50)
        def count = InventoryItem.count()
        log.info("Completed async count: ${count}")
        return new AsyncResult<Long>(count as Long)
    }

    /**
     * Async method that may throw exception
     */
    @Async
    Future<String> processWithPossibleError(String input, boolean shouldFail) {
        log.info("Processing with possible error: ${input}, shouldFail: ${shouldFail}")
        Thread.sleep(50)
        if (shouldFail) {
            throw new RuntimeException("Async processing failed for: ${input}")
        }
        return new AsyncResult<String>("Success: ${input}")
    }

    /**
     * Long-running async operation
     */
    @Async
    CompletableFuture<Map<String, Object>> longRunningOperation(String taskId) {
        log.info("Starting long-running operation: ${taskId}")
        def startTime = System.currentTimeMillis()
        
        // Simulate work
        Thread.sleep(200)
        
        def endTime = System.currentTimeMillis()
        Map<String, Object> result = [
            taskId: taskId,
            status: 'completed',
            durationMs: endTime - startTime,
            completedAt: new Date()
        ] as Map<String, Object>
        
        log.info("Completed long-running operation: ${taskId}")
        return CompletableFuture.completedFuture(result)
    }
}
