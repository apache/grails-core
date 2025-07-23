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

package org.grails.core.util;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;

/**
 * Based on the Spring StopWatch class, but supporting nested tasks
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since May 2, 2001
 */
public class StopWatch {

    /**
     * Identifier of this stop watch.
     * Handy when we have output from multiple stop watches
     * and need to distinguish between them in log or console output.
     */
    private final String id;

    private final Deque<TaskInfo> runningTasks = new LinkedList<TaskInfo>();
    private final Deque<TaskInfo> taskList = new LinkedList<TaskInfo>();

    /** Is the stop watch currently running? */
    private boolean running;

    /** Name of the current task */
    private String currentTaskName;

    private TaskInfo lastTaskInfo;

    private int taskCount;

    /** Total running time */
    private long totalTimeMillis;


    /**
     * Construct a new stop watch. Does not start any task.
     */
    public StopWatch() {
        this.id = "";
    }

    /**
     * Construct a new stop watch with the given id.
     * Does not start any task.
     * @param id identifier for this stop watch.
     * Handy when we have output from multiple stop watches
     * and need to distinguish between them.
     */
    public StopWatch(String id) {
        this.id = id;
    }



    /**
     * Start an unnamed task. The results are undefined if {@link #stop()}
     * or timing methods are called without invoking this method.
     * @see #stop()
     */
    public void start() throws IllegalStateException {
        start("");
    }

    /**
     * Start a named task. The results are undefined if {@link #stop()}
     * or timing methods are called without invoking this method.
     * @param taskName the name of the task to start
     * @see #stop()
     */
    public void start(String taskName) throws IllegalStateException {
        this.lastTaskInfo = new TaskInfo(taskName, System.currentTimeMillis());
        this.runningTasks.push(lastTaskInfo);
        ++this.taskCount;
        this.running = true;
        this.currentTaskName = taskName;
    }

    /**
     * Stop the current task. The results are undefined if timing
     * methods are called without invoking at least one pair
     * {@link #start()} / {@link #stop()} methods.
     * @see #start()
     */
    public void stop() throws IllegalStateException {
        if (!this.running) {
            throw new IllegalStateException("Can't stop StopWatch: it's not running");
        }

        if(!runningTasks.isEmpty()) {

            TaskInfo lastTask = runningTasks.pop();
            lastTask.stop();
            taskList.add(lastTask);
            this.currentTaskName = null;
            this.totalTimeMillis += lastTask.getTimeMillis();
        }
    }

    public void complete() {
        this.running = false;
    }

    /**
     * Return whether the stop watch is currently running.
     */
    public boolean isRunning() {
        return this.running;
    }


    /**
     * Return the time taken by the last task.
     */
    public long getLastTaskTimeMillis() throws IllegalStateException {
        if (this.lastTaskInfo == null) {
            throw new IllegalStateException("No tasks run: can't get last task interval");
        }
        return this.lastTaskInfo.getTimeMillis();
    }

    /**
     * Return the name of the last task.
     */
    public String getLastTaskName() throws IllegalStateException {
        if (this.lastTaskInfo == null) {
            throw new IllegalStateException("No tasks run: can't get last task name");
        }
        return this.lastTaskInfo.getTaskName();
    }

    /**
     * Return the last task as a TaskInfo object.
     */
    public TaskInfo getLastTaskInfo() throws IllegalStateException {
        if (this.lastTaskInfo == null) {
            throw new IllegalStateException("No tasks run: can't get last task info");
        }
        return this.lastTaskInfo;
    }


    /**
     * Return the total time in milliseconds for all tasks.
     */
    public long getTotalTimeMillis() {
        return this.totalTimeMillis;
    }

    /**
     * Return the total time in seconds for all tasks.
     */
    public double getTotalTimeSeconds() {
        return this.totalTimeMillis / 1000.0;
    }

    /**
     * Return the number of tasks timed.
     */
    public int getTaskCount() {
        return this.taskCount;
    }

    /**
     * Return an array of the data for tasks performed.
     */
    public TaskInfo[] getTaskInfo() {
        return this.taskList.toArray(new TaskInfo[this.taskList.size()]);
    }


    /**
     * Return a short description of the total running time.
     */
    public String shortSummary() {
        return "StopWatch '" + this.id + "': running time (millis) = " + getTotalTimeMillis();
    }

    /**
     * Return a string with a table describing all tasks performed.
     * For custom reporting, call getTaskInfo() and use the task info directly.
     */
    public String prettyPrint() {
        StringBuilder sb = new StringBuilder(shortSummary());
        sb.append('\n');
        sb.append("-----------------------------------------\n");
        sb.append("ms     %     Task name\n");
        sb.append("-----------------------------------------\n");
        NumberFormat nf = NumberFormat.getNumberInstance();
        nf.setMinimumIntegerDigits(5);
        nf.setGroupingUsed(false);
        NumberFormat pf = NumberFormat.getPercentInstance();
        pf.setMinimumIntegerDigits(3);
        pf.setGroupingUsed(false);
        final TaskInfo[] taskInfos = getTaskInfo();
        Arrays.sort(taskInfos, new Comparator<TaskInfo>() {
            @Override
            public int compare(TaskInfo o1, TaskInfo o2) {
                return Long.compare(o1.getTimeMillis(), o2.getTimeMillis());
            }
        });
        for (TaskInfo task : taskInfos) {
            sb.append(nf.format(task.getTimeMillis())).append("  ");
            sb.append(pf.format(task.getTimeSeconds() / getTotalTimeSeconds())).append("  ");
            sb.append(task.getTaskName()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Return an informative string describing all tasks performed
     * For custom reporting, call {@code getTaskInfo()} and use the task info directly.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(shortSummary());
        for (TaskInfo task : getTaskInfo()) {
            sb.append("; [").append(task.getTaskName()).append("] took ").append(task.getTimeMillis());
            long percent = Math.round((100.0 * task.getTimeSeconds()) / getTotalTimeSeconds());
            sb.append(" = ").append(percent).append("%");
        }
        return sb.toString();
    }


    /**
     * Inner class to hold data about one task executed within the stop watch.
     */
    public static final class TaskInfo {

        private final String taskName;

        private final long startTime;
        private long endTime;


        TaskInfo(String taskName, long startTime) {
            this.taskName = taskName;
            this.startTime = startTime;
        }

        public void stop() {
            this.endTime = System.currentTimeMillis();
        }

        /**
         * Return the name of this task.
         */
        public String getTaskName() {
            return this.taskName;
        }

        /**
         * Return the time in milliseconds this task took.
         */
        public long getTimeMillis() {
            return this.endTime - this.startTime;
        }

        /**
         * Return the time in seconds this task took.
         */
        public double getTimeSeconds() {
            return this.getTimeMillis() / 1000.0;
        }
    }

}
