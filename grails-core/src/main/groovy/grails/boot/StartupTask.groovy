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
package grails.boot

import groovy.transform.CompileStatic

import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.metrics.ApplicationStartup
import org.springframework.core.metrics.StartupStep

/**
 * Reports the progress of a piece of work an application or plugin does while the application starts,
 * such as running database migrations or loading reference data, so that whatever follows the start can
 * show how far it has got: the startup progress page and report, or the {@code startup} endpoint of
 * Spring Boot Actuator.
 *
 * <p>A task has a description and, when it is known, the number of items of work it will do. Each item
 * is begun with a name and ended when it is done:</p>
 *
 * <pre>
 * try (StartupTask task = StartupTask.start(applicationContext, 'Loading reference data', files.size())) {
 *     for (File file in files) {
 *         task.startItem(file.name)
 *         load(file)
 *         task.endItem()
 *     }
 * }
 * </pre>
 *
 * <p>The task and its items are recorded as steps of the context's {@link ApplicationStartup}, named
 * {@value #TASK_STEP} and {@value #ITEM_STEP}, so a task costs nothing when nothing records the start.
 * Work done only to report a task, such as counting its items, can be skipped then, which
 * {@link #isRecorded(ApplicationContext)} tells. A task is reported from the thread that does its work.</p>
 *
 * @since 8.1
 */
@CompileStatic
final class StartupTask implements AutoCloseable {

    /** The name of the startup step that records a task. */
    public static final String TASK_STEP = 'grails.startup.task'

    /** The name of the startup step that records one item of a task's work. */
    public static final String ITEM_STEP = 'grails.startup.task.item'

    /** The tag of a task's step that describes the task. */
    public static final String DESCRIPTION_TAG = 'description'

    /** The tag of a task's step that says how many items the task will do, when that is known. */
    public static final String TOTAL_TAG = 'total'

    /** The tag of an item's step that names the item. */
    public static final String ITEM_TAG = 'item'

    private final ApplicationStartup startup

    private final StartupStep step

    private StartupStep item

    private StartupTask(ApplicationStartup startup, String description, int total) {
        this.startup = startup
        StartupStep taskStep = startup.start(TASK_STEP).tag(DESCRIPTION_TAG, description)
        if (total >= 0) {
            taskStep.tag(TOTAL_TAG, String.valueOf(total))
        }
        this.step = taskStep
    }

    /**
     * Begins a task, recorded by the application startup of the given context.
     *
     * @param context the context that is starting
     * @param description what the task does, such as {@code Running database migrations}
     * @param total how many items the task will do, or a negative number when that is not known
     */
    static StartupTask start(ApplicationContext context, String description, int total) {
        return start(applicationStartup(context), description, total)
    }

    /**
     * Begins a task, recorded by the given application startup.
     *
     * @param startup the application startup recording the start
     * @param description what the task does, such as {@code Running database migrations}
     * @param total how many items the task will do, or a negative number when that is not known
     */
    static StartupTask start(ApplicationStartup startup, String description, int total) {
        return new StartupTask(startup, description, total)
    }

    /**
     * Whether anything records the start of the given context, without which a task can skip work done
     * only to report it.
     */
    static boolean isRecorded(ApplicationContext context) {
        return !applicationStartup(context).is(ApplicationStartup.DEFAULT)
    }

    /** Begins the next item of work, ending the one before if it has not been ended. */
    void startItem(String name) {
        endItem()
        item = startup.start(ITEM_STEP).tag(ITEM_TAG, name)
    }

    /** Ends the item of work in progress, if there is one. */
    void endItem() {
        if (item != null) {
            item.end()
            item = null
        }
    }

    /** Ends the task, and the item of work in progress if there is one. */
    @Override
    void close() {
        endItem()
        step.end()
    }

    private static ApplicationStartup applicationStartup(ApplicationContext context) {
        return context instanceof ConfigurableApplicationContext ? ((ConfigurableApplicationContext) context).getApplicationStartup() : ApplicationStartup.DEFAULT
    }
}
