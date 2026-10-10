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
package org.apache.grails.startup

import java.util.function.Predicate
import java.util.function.Supplier

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import org.springframework.beans.factory.BeanFactoryUtils
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup
import org.springframework.boot.context.metrics.buffering.StartupTimeline
import org.springframework.core.SimpleAliasRegistry
import org.springframework.core.metrics.ApplicationStartup
import org.springframework.core.metrics.StartupStep

import grails.boot.StartupTask

/**
 * Reads the context's progress from the startup steps Spring records during a refresh, and from the
 * tasks applications and plugins report with {@link StartupTask}, while passing every step on to the
 * application startup the context already had, so a recorder such as the one behind the Actuator
 * {@code startup} endpoint sees the same steps it would without the progress page. Once the application
 * is ready it reads no more, so what the application creates from then on is no part of the report.
 */
@CompileStatic
final class StartupProgressApplicationStartup implements ApplicationStartup {

    private static final String BEAN_POST_PROCESS_STEP = 'spring.context.beans.post-process'

    private static final String BEAN_INSTANTIATE_STEP = 'spring.beans.instantiate'

    private static final String BEAN_NAME_TAG = 'beanName'

    private final ApplicationStartup delegate

    private final StartupProgress progress

    private final ConfigurableListableBeanFactory beanFactory

    /** The innermost bean being created on each thread, so a bean's own time can exclude its dependencies'. */
    private final ThreadLocal<BeanCreationStep> creating = new ThreadLocal<>()

    /** The innermost task running on each thread, which the items reported on that thread belong to. */
    private final ThreadLocal<TaskStep> running = new ThreadLocal<>()

    private StartupProgressApplicationStartup(ApplicationStartup delegate, StartupProgress progress, ConfigurableListableBeanFactory beanFactory) {
        this.delegate = delegate
        this.progress = progress
        this.beanFactory = beanFactory
    }

    /**
     * The application startup that reads the progress of a start while passing every step on to the given
     * one. When that one is a {@link BufferingApplicationStartup}, so is the result, because Spring Boot
     * Actuator only serves its {@code startup} endpoint for one, and the endpoint reports what the given one
     * recorded.
     */
    @PackageScope
    static ApplicationStartup create(ApplicationStartup delegate, StartupProgress progress, ConfigurableListableBeanFactory beanFactory) {
        StartupProgressApplicationStartup startup = new StartupProgressApplicationStartup(delegate, progress, beanFactory)
        return delegate instanceof BufferingApplicationStartup ? new Buffering((BufferingApplicationStartup) delegate, startup) : startup
    }

    @Override
    StartupStep start(String name) {
        StartupStep step = delegate.start(name)
        if (!progress.isReporting()) {
            return step
        }
        switch (name) {
            case BEAN_INSTANTIATE_STEP:
                return new BeanCreationStep(step)
            case BEAN_POST_PROCESS_STEP:
                progress.loadingDefinitions()
                return new BeanPostProcessStep(step)
            case StartupTask.TASK_STEP:
                return new TaskStep(step)
            case StartupTask.ITEM_STEP:
                return running.get() != null ? new ItemStep(step, running.get().task) : step
            default:
                return step
        }
    }

    /**
     * The beans the refresh will instantiate eagerly. It runs once the bean factory post-processors have
     * registered every definition, which is when the set stops changing.
     */
    private Set<String> eagerSingletons() {
        Set<String> names = new HashSet<>()
        for (String name : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition definition = beanFactory.getBeanDefinition(name)
            if (definition.isSingleton() && !definition.isAbstract() && !definition.isLazyInit()) {
                names.add(name)
            }
        }
        return names
    }

    /**
     * The name a bean is defined under, from the name it was asked for by, which may be an alias or name the
     * factory that makes it.
     */
    private String definedName(String requested) {
        String name = BeanFactoryUtils.transformedBeanName(requested)
        return beanFactory instanceof SimpleAliasRegistry ? ((SimpleAliasRegistry) beanFactory).canonicalName(name) : name
    }

    /**
     * Stands in for a {@link BufferingApplicationStartup} the application configured, recording into it and
     * reporting what it recorded.
     */
    private static final class Buffering extends BufferingApplicationStartup {

        private final BufferingApplicationStartup recording

        private final StartupProgressApplicationStartup progress

        Buffering(BufferingApplicationStartup recording, StartupProgressApplicationStartup progress) {
            super(1)
            this.recording = recording
            this.progress = progress
        }

        @Override
        StartupStep start(String name) {
            return progress.start(name)
        }

        @Override
        void startRecording() {
            recording.startRecording()
        }

        @Override
        void addFilter(Predicate<StartupStep> filter) {
            recording.addFilter(filter)
        }

        @Override
        StartupTimeline getBufferedTimeline() {
            return recording.getBufferedTimeline()
        }

        @Override
        StartupTimeline drainBufferedTimeline() {
            return recording.drainBufferedTimeline()
        }
    }

    private abstract static class ForwardingStep implements StartupStep {

        @PackageScope
        final StartupStep step

        ForwardingStep(StartupStep step) {
            this.step = step
        }

        @Override
        String getName() {
            return step.getName()
        }

        @Override
        long getId() {
            return step.getId()
        }

        @Override
        Long getParentId() {
            return step.getParentId()
        }

        @Override
        StartupStep tag(String key, String value) {
            step.tag(key, value)
            return this
        }

        @Override
        StartupStep tag(String key, Supplier<String> value) {
            step.tag(key, value)
            return this
        }

        @Override
        StartupStep.Tags getTags() {
            return step.getTags()
        }

        @Override
        void end() {
            step.end()
        }
    }

    private final class BeanPostProcessStep extends ForwardingStep {

        BeanPostProcessStep(StartupStep step) {
            super(step)
        }

        @Override
        void end() {
            super.end()
            StartupProgressApplicationStartup.this.progress.creatingBeans(StartupProgressApplicationStartup.this.eagerSingletons())
        }
    }

    private final class BeanCreationStep extends ForwardingStep {

        private final long startNanos = System.nanoTime()

        private final BeanCreationStep enclosing

        private long nestedNanos

        private String beanName

        BeanCreationStep(StartupStep step) {
            super(step)
            enclosing = StartupProgressApplicationStartup.this.creating.get()
            StartupProgressApplicationStartup.this.creating.set(this)
        }

        @Override
        StartupStep tag(String key, String value) {
            if (BEAN_NAME_TAG.equals(key) && value != null) {
                beanName = StartupProgressApplicationStartup.this.definedName(value)
                StartupProgressApplicationStartup.this.progress.beanCreationStarted(beanName)
            }
            return super.tag(key, value)
        }

        @Override
        void end() {
            super.end()
            long elapsedNanos = System.nanoTime() - startNanos
            if (beanName != null && StartupProgressApplicationStartup.this.beanFactory.containsBeanDefinition(beanName)) {
                StartupProgressApplicationStartup.this.progress.beanCreated(beanName, elapsedNanos - nestedNanos)
            }
            if (enclosing != null) {
                enclosing.@nestedNanos = enclosing.@nestedNanos + elapsedNanos
                StartupProgressApplicationStartup.this.creating.set(enclosing)
                // the bean this one was created for is being created again, not the one that has just finished
                if (enclosing.@beanName != null) {
                    StartupProgressApplicationStartup.this.progress.beanCreationStarted(enclosing.@beanName)
                }
            }
            else {
                StartupProgressApplicationStartup.this.creating.remove()
            }
        }
    }

    private final class TaskStep extends ForwardingStep {

        @PackageScope
        final StartupProgress.Task task = StartupProgressApplicationStartup.this.progress.taskStarted()

        private final TaskStep enclosing

        TaskStep(StartupStep step) {
            super(step)
            enclosing = StartupProgressApplicationStartup.this.running.get()
            StartupProgressApplicationStartup.this.running.set(this)
        }

        @Override
        StartupStep tag(String key, String value) {
            if (StartupTask.DESCRIPTION_TAG.equals(key)) {
                task.described(value)
            }
            else if (StartupTask.TOTAL_TAG.equals(key)) {
                task.totalled(value)
            }
            return super.tag(key, value)
        }

        @Override
        void end() {
            super.end()
            task.ended()
            if (enclosing != null) {
                StartupProgressApplicationStartup.this.running.set(enclosing)
            }
            else {
                StartupProgressApplicationStartup.this.running.remove()
            }
        }
    }

    private static final class ItemStep extends ForwardingStep {

        private final StartupProgress.Task task

        ItemStep(StartupStep step, StartupProgress.Task task) {
            super(step)
            this.task = task
        }

        @Override
        StartupStep tag(String key, String value) {
            if (StartupTask.ITEM_TAG.equals(key)) {
                task.itemStarted(value)
            }
            return super.tag(key, value)
        }

        @Override
        void end() {
            super.end()
            task.itemEnded()
        }
    }
}
