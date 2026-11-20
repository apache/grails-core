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

package functionaltests.springevents

import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Service

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Service that listens for Spring application events.
 * Provides methods to track and verify event handling.
 */
@Service
class EventListenerService {

    // Thread-safe collections for tracking events
    private final CopyOnWriteArrayList<CustomApplicationEvent> customEvents = new CopyOnWriteArrayList<>()
    private final CopyOnWriteArrayList<UserActionEvent> userActionEvents = new CopyOnWriteArrayList<>()
    private final CopyOnWriteArrayList<String> orderedResults = new CopyOnWriteArrayList<>()
    private final AtomicInteger eventCount = new AtomicInteger(0)
    private final ConcurrentLinkedQueue<String> asyncResults = new ConcurrentLinkedQueue<>()
    
    // Track conditional events separately
    private final CopyOnWriteArrayList<String> conditionalResults = new CopyOnWriteArrayList<>()
    
    // Latch for async event testing
    private volatile CountDownLatch asyncLatch = new CountDownLatch(0)

    /**
     * Listens for CustomApplicationEvent.
     */
    @EventListener
    void handleCustomEvent(CustomApplicationEvent event) {
        customEvents.add(event)
        eventCount.incrementAndGet()
        
        // Manually handle conditional logic to avoid SpEL evaluation issues
        if (event.message?.startsWith('IMPORTANT')) {
            conditionalResults.add("CONDITIONAL:" + event.message)
        }
    }

    /**
     * Listens for UserActionEvent.
     */
    @EventListener
    void handleUserActionEvent(UserActionEvent event) {
        userActionEvents.add(event)
        eventCount.incrementAndGet()
    }

    /**
     * Listens for PriorityEvent with high priority (order 1).
     */
    @EventListener
    @Order(1)
    void handlePriorityEventFirst(PriorityEvent event) {
        orderedResults.add("first-${event.data}")
    }

    /**
     * Listens for PriorityEvent with medium priority (order 2).
     */
    @EventListener
    @Order(2)
    void handlePriorityEventSecond(PriorityEvent event) {
        orderedResults.add("second-${event.data}")
    }

    /**
     * Listens for PriorityEvent with low priority (order 3).
     */
    @EventListener
    @Order(3)
    void handlePriorityEventThird(PriorityEvent event) {
        orderedResults.add("third-${event.data}")
    }

    /**
     * Gets all received custom events.
     */
    List<CustomApplicationEvent> getCustomEvents() {
        new ArrayList<>(customEvents)
    }

    /**
     * Gets all received user action events.
     */
    List<UserActionEvent> getUserActionEvents() {
        new ArrayList<>(userActionEvents)
    }

    /**
     * Gets ordered results from priority event handling.
     */
    List<String> getOrderedResults() {
        new ArrayList<>(orderedResults)
    }

    /**
     * Gets total event count.
     */
    int getEventCount() {
        eventCount.get()
    }

    /**
     * Gets async results.
     */
    List<String> getAsyncResults() {
        new ArrayList<>(asyncResults)
    }

    /**
     * Gets conditional results.
     */
    List<String> getConditionalResults() {
        new ArrayList<>(conditionalResults)
    }

    /**
     * Prepare for async event testing.
     */
    void prepareAsyncLatch(int count) {
        asyncLatch = new CountDownLatch(count)
    }

    /**
     * Wait for async events.
     */
    boolean awaitAsyncEvents(long timeout, TimeUnit unit) {
        asyncLatch.await(timeout, unit)
    }

    /**
     * Clears all recorded events.
     */
    void clearEvents() {
        customEvents.clear()
        userActionEvents.clear()
        orderedResults.clear()
        asyncResults.clear()
        conditionalResults.clear()
        eventCount.set(0)
    }
}
