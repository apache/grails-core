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
package org.apache.grails.startup;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * The state of one application start, written by the startup steps and run listener callbacks on
 * the starting thread and read, as JSON, by whichever server is answering the progress page's polls,
 * and once the application is ready, by the startup report.
 */
final class StartupProgress {

    /**
     * Response header naming the current phase. The progress page treats any poll answered without it
     * as the application itself answering, which is its signal to reload.
     */
    static final String PHASE_HEADER = "Grails-Startup-Phase";

    /**
     * How recently a status poll must have arrived for a failure to be worth waiting on: a browser
     * showing the progress page polls well inside this window, so a quieter start has nobody watching.
     */
    private static final long WATCHED_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(3);

    /**
     * How long to keep answering after a page has been told about a failure, so the other pages open on the
     * application, which poll on their own schedule, are told too. It is a little over the page's polling
     * interval.
     */
    private static final long FAILURE_LINGER_MILLIS = 750;

    private static final int SLOWEST_BEAN_LIMIT = 10;

    /** How many of the tasks reported during a start are kept, which bounds what a start that reports many keeps. */
    private static final int TASK_LIMIT = 50;

    private static final int STACK_TRACE_LIMIT = 64 * 1024;

    /** The phases of a start, in order, with the progress reached when each begins. */
    enum Phase {
        PREPARING(2),
        LOADING_DEFINITIONS(5),
        CREATING_BEANS(15),
        STARTING_WEB_SERVER(92),
        INITIALIZING(96),
        READY(100),
        FAILED(100);

        private final int progress;

        Phase(int progress) {
            this.progress = progress;
        }
    }

    private final String runId = UUID.randomUUID().toString();
    private final String applicationName;
    private final String grailsVersion;
    private final long runStartNanos;
    private final AtomicLongArray phaseStarts = new AtomicLongArray(Phase.values().length);
    private final Set<String> createdBeans = ConcurrentHashMap.newKeySet();
    private final AtomicInteger createdExpectedBeans = new AtomicInteger();
    private final List<BeanTiming> slowestBeans = new ArrayList<>();
    private final CountDownLatch failureDelivered = new CountDownLatch(1);
    private final List<Task> tasks = new CopyOnWriteArrayList<>();

    private volatile Phase phase = Phase.PREPARING;
    private volatile Phase failedPhase;
    private volatile String activity;
    private volatile Set<String> expectedBeans;
    private volatile String failureType;
    private volatile String failureMessage;
    private volatile String failureStackTrace;
    private volatile long lastStatusNanos;
    private volatile long startedNanos;

    /**
     * @param runStartNanos when the run of the application began, so the times reported match the time
     *                      Spring Boot reports the application started in
     */
    StartupProgress(String applicationName, String grailsVersion, long runStartNanos) {
        this.applicationName = applicationName;
        this.grailsVersion = grailsVersion;
        this.runStartNanos = runStartNanos;
        phaseStarts.set(Phase.PREPARING.ordinal(), runStartNanos);
    }

    Phase getPhase() {
        return phase;
    }

    /** Whether the status endpoint still answers, which it stops doing once the application is ready. */
    boolean isReporting() {
        return phase != Phase.READY;
    }

    void loadingDefinitions() {
        advanceTo(Phase.LOADING_DEFINITIONS);
    }

    /**
     * Moves to bean creation, given the names of the beans the refresh will create eagerly. Beans created
     * before the names were known, such as post-processors, count towards them.
     */
    void creatingBeans(Set<String> expected) {
        int created = 0;
        for (String name : createdBeans) {
            if (expected.contains(name)) {
                created++;
            }
        }
        createdExpectedBeans.set(created);
        expectedBeans = expected;
        advanceTo(Phase.CREATING_BEANS);
    }

    void beanCreationStarted(String beanName) {
        activity = beanName;
    }

    /**
     * Records a created bean with the time spent creating it, not counting the beans created on its behalf.
     */
    void beanCreated(String beanName, long durationNanos) {
        Set<String> expected = expectedBeans;
        if (createdBeans.add(beanName) && expected != null && expected.contains(beanName)) {
            createdExpectedBeans.incrementAndGet();
        }
        synchronized (slowestBeans) {
            if (slowestBeans.size() < SLOWEST_BEAN_LIMIT || durationNanos > slowestBeans.get(slowestBeans.size() - 1).nanos) {
                slowestBeans.removeIf(timing -> timing.name.equals(beanName));
                slowestBeans.add(new BeanTiming(beanName, durationNanos));
                slowestBeans.sort(Comparator.comparingLong(BeanTiming::nanos).reversed());
                if (slowestBeans.size() > SLOWEST_BEAN_LIMIT) {
                    slowestBeans.remove(slowestBeans.size() - 1);
                }
            }
        }
    }

    /** Records a task reported with {@link grails.boot.StartupTask} beginning, and returns it to report on. */
    Task taskStarted() {
        Task task = new Task();
        if (tasks.size() < TASK_LIMIT) {
            tasks.add(task);
        }
        return task;
    }

    void startingWebServer() {
        activity = null;
        advanceTo(Phase.STARTING_WEB_SERVER);
    }

    void initializing() {
        advanceTo(Phase.INITIALIZING);
    }

    /**
     * Records that the application has started, which is the moment Spring Boot measures the time it
     * reports the application started in to, before the application runners run.
     */
    void started() {
        if (startedNanos == 0) {
            startedNanos = System.nanoTime();
        }
    }

    void ready() {
        advanceTo(Phase.READY);
    }

    void failed(Throwable exception) {
        if (phase == Phase.FAILED || phase == Phase.READY) {
            return;
        }
        failedPhase = phase;
        failureType = exception.getClass().getName();
        failureMessage = exception.getMessage();
        failureStackTrace = stackTrace(exception);
        phaseStarts.set(Phase.FAILED.ordinal(), System.nanoTime());
        phase = Phase.FAILED;
    }

    /**
     * The status to send to a polling page, taken as one snapshot so the phase header and the body agree.
     * Taking it records that a page is watching, which a failed start waits to tell.
     *
     * @param showDetails whether the page may see the details of the start
     * @param signInForDetails whether there are details the page would see if it signed in
     */
    Status report(boolean showDetails, boolean signInForDetails) {
        lastStatusNanos = System.nanoTime();
        return snapshot(showDetails, signInForDetails);
    }

    /**
     * The status as the startup report shows it. A client reading the report is not a page that would be
     * told about a failure, so a failed start does not wait for it.
     */
    Status snapshot(boolean showDetails, boolean signInForDetails) {
        Phase current = phase;
        return new Status(current, toJson(current, showDetails, signInForDetails));
    }

    /**
     * Records that a status has been written out in full, which for a failure means the page has been
     * told, so the failed start can carry on and release the port without cutting the response short.
     */
    void delivered(Status status) {
        if (status.phase() == Phase.FAILED) {
            failureDelivered.countDown();
        }
    }

    /**
     * Holds a failed start back until a page that is polling has been told about the failure, and briefly
     * after so any other open pages are told as well, or until the timeout passes. Returns at once when no
     * page has polled recently, so a start nobody is watching fails as quickly as it would without the
     * progress page.
     */
    void awaitFailureDelivery(long timeoutMillis) {
        long last = lastStatusNanos;
        if (last == 0 || System.nanoTime() - last > WATCHED_WINDOW_NANOS) {
            return;
        }
        try {
            if (failureDelivered.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                Thread.sleep(FAILURE_LINGER_MILLIS);
            }
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private int progress(Phase current) {
        if (current != Phase.CREATING_BEANS) {
            return current.progress;
        }
        Set<String> expected = expectedBeans;
        int span = Phase.STARTING_WEB_SERVER.progress - Phase.CREATING_BEANS.progress;
        if (expected == null || expected.isEmpty()) {
            return current.progress;
        }
        return current.progress + Math.min(span - 1, createdExpectedBeans.get() * span / expected.size());
    }

    private String toJson(Phase current, boolean showDetails, boolean signInForDetails) {
        long now = System.nanoTime();
        StringBuilder json = new StringBuilder(512).append('{');
        member(json, "runId").append(string(runId)).append(',');
        member(json, "application").append(string(applicationName)).append(',');
        member(json, "phase").append(string(current.name())).append(',');
        member(json, "progress").append(progress(current)).append(',');
        long end = now;
        if (current == Phase.READY && startedNanos != 0) {
            end = startedNanos;
        }
        else if (current == Phase.READY || current == Phase.FAILED) {
            end = phaseStarts.get(current.ordinal());
        }
        member(json, "elapsedMillis").append(TimeUnit.NANOSECONDS.toMillis(end - runStartNanos)).append(',');
        member(json, "showDetails").append(showDetails).append(',');
        member(json, "signInForDetails").append(signInForDetails);
        // how the start is going is for everyone; how the application is put together is a detail
        if (showDetails) {
            json.append(',');
            appendPhases(json, now);
            Set<String> expected = expectedBeans;
            json.append(',');
            member(json, "beansCreated").append(expected != null ? createdExpectedBeans.get() : createdBeans.size()).append(',');
            member(json, "beansExpected").append(expected != null ? expected.size() : -1).append(',');
            member(json, "grailsVersion").append(string(grailsVersion));
            String currentActivity = activity;
            if (current == Phase.CREATING_BEANS && currentActivity != null) {
                json.append(',');
                member(json, "activity").append(string(currentActivity));
            }
            json.append(',');
            appendTasks(json, now);
            json.append(',');
            member(json, "slowestBeans").append('[');
            List<BeanTiming> slowest;
            synchronized (slowestBeans) {
                slowest = List.copyOf(slowestBeans);
            }
            for (int i = 0; i < slowest.size(); i++) {
                BeanTiming timing = slowest.get(i);
                if (i > 0) {
                    json.append(',');
                }
                json.append('{');
                member(json, "name").append(string(timing.name)).append(',');
                member(json, "millis").append(TimeUnit.NANOSECONDS.toMillis(timing.nanos));
                json.append('}');
            }
            json.append(']');
        }
        if (current == Phase.FAILED) {
            json.append(',');
            if (showDetails) {
                member(json, "failedPhase").append(string(failedPhase.name())).append(',');
            }
            member(json, "failure").append('{');
            if (showDetails) {
                member(json, "type").append(string(failureType)).append(',');
                member(json, "message").append(string(failureMessage)).append(',');
                member(json, "stackTrace").append(string(failureStackTrace));
            }
            json.append('}');
        }
        return json.append('}').toString();
    }

    private void advanceTo(Phase next) {
        Phase current = phase;
        if (current != Phase.FAILED && current.ordinal() < next.ordinal()) {
            // the start time is in place before the phase is, so a reader seeing the phase sees when it began
            phaseStarts.set(next.ordinal(), System.nanoTime());
            phase = next;
        }
    }

    /** Each task reported, in the order they began, with how far each has got and how long it took. */
    private void appendTasks(StringBuilder json, long now) {
        member(json, "tasks").append('[');
        boolean first = true;
        for (Task task : tasks) {
            if (!first) {
                json.append(',');
            }
            first = false;
            long end = task.endNanos;
            json.append('{');
            member(json, "description").append(string(task.description)).append(',');
            member(json, "completed").append(task.completed.get()).append(',');
            member(json, "total").append(task.total).append(',');
            member(json, "running").append(end == 0).append(',');
            if (end == 0 && task.item != null) {
                member(json, "item").append(string(task.item)).append(',');
            }
            member(json, "millis").append(TimeUnit.NANOSECONDS.toMillis((end == 0 ? now : end) - task.startNanos));
            json.append('}');
        }
        json.append(']');
    }

    /** Each phase begun, in order, with how long it took, or has taken so far. */
    private void appendPhases(StringBuilder json, long now) {
        member(json, "phases").append('[');
        boolean first = true;
        for (Phase each : List.of(Phase.PREPARING, Phase.LOADING_DEFINITIONS, Phase.CREATING_BEANS, Phase.STARTING_WEB_SERVER, Phase.INITIALIZING)) {
            long start = phaseStarts.get(each.ordinal());
            if (start == 0) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            member(json, "name").append(string(each.name())).append(',');
            member(json, "millis").append(TimeUnit.NANOSECONDS.toMillis(endOf(each, now) - start));
            json.append('}');
        }
        json.append(']');
    }

    /**
     * When a phase ended, which is when the next phase to have begun began, or now if none has. The last
     * phase ends when the application has started, as Spring Boot measures it.
     */
    private long endOf(Phase phase, long now) {
        if (phase == Phase.INITIALIZING && startedNanos != 0) {
            return startedNanos;
        }
        for (int later = phase.ordinal() + 1; later < Phase.values().length; later++) {
            long start = phaseStarts.get(later);
            if (start != 0) {
                return start;
            }
        }
        return now;
    }

    private static String stackTrace(Throwable exception) {
        StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        String trace = writer.toString();
        return trace.length() > STACK_TRACE_LIMIT ? trace.substring(0, STACK_TRACE_LIMIT) + "\n\t..." : trace;
    }

    private static StringBuilder member(StringBuilder json, String name) {
        return json.append('"').append(name).append("\":");
    }

    /**
     * A JSON string literal, escaped so it can also sit inside an HTML {@code <script>} element.
     */
    static String string(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20 || c == '<' || c == '>' || c == '&' || c == '\u2028' || c == '\u2029') {
                        escaped.append(String.format("\\u%04x", (int) c));
                    }
                    else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }

    /**
     * A task reported with {@link grails.boot.StartupTask}: what it does, how many items it will do, how many
     * it has done, and the one it is doing.
     */
    static final class Task {

        private final long startNanos = System.nanoTime();
        private final AtomicInteger completed = new AtomicInteger();
        private volatile String description;
        private volatile int total = -1;
        private volatile String item;
        private volatile long endNanos;

        void described(String value) {
            description = value;
        }

        void totalled(String value) {
            try {
                total = Integer.parseInt(value);
            }
            catch (NumberFormatException ex) {
                total = -1;
            }
        }

        void itemStarted(String name) {
            item = name;
        }

        void itemEnded() {
            completed.incrementAndGet();
            item = null;
        }

        void ended() {
            item = null;
            endNanos = System.nanoTime();
        }
    }

    /** A status report: the phase it was taken in and its JSON body. */
    record Status(Phase phase, String json) {
    }

    private record BeanTiming(String name, long nanos) {
    }
}
