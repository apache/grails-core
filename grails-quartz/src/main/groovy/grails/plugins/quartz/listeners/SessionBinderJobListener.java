/*
 * Copyright (c) 2011-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugins.quartz.listeners;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.listeners.JobListenerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grails.persistence.support.PersistenceContextInterceptor;

/**
 * JobListener implementation which wraps the execution of a Quartz Job in a
 * persistence context, via the persistenceInterceptor.
 *
 * @author Sergey Nebolsin (nebolsin@gmail.com)
 * @since 0.2
 */
public class SessionBinderJobListener extends JobListenerSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SessionBinderJobListener.class);

    public static final String NAME = "sessionBinderListener";

    private PersistenceContextInterceptor persistenceInterceptor;

    public String getName() {
        return NAME;
    }

    /**
     * It is used by the Spring to inject a persistence interceptor.
     * @return the reference of the currently active bean implementation of persistenceInterceptor
     */
    @SuppressWarnings("UnusedDeclaration")
    public PersistenceContextInterceptor getPersistenceInterceptor() {
        return persistenceInterceptor;
    }

    /**
     * It is used by the Spring to inject a persistence interceptor.
     * @param persistenceInterceptor - Normally applied by bean injection to set the reference to the persistenceInterceptor
     */
    @SuppressWarnings("UnusedDeclaration")
    public void setPersistenceInterceptor(PersistenceContextInterceptor persistenceInterceptor) {
        this.persistenceInterceptor = persistenceInterceptor;
    }

    /**
     * Before job executing. Init persistence context.
     */
    public void jobToBeExecuted(JobExecutionContext context) {
        if (persistenceInterceptor != null) {
            persistenceInterceptor.init();
            LOG.debug("Persistence session is opened.");
        }
    }

    /**
     * After job executing. Flush and destroy persistence context.
     */
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException exception) {
        if (persistenceInterceptor != null) {
            try {
                persistenceInterceptor.flush();
                persistenceInterceptor.clear();
                LOG.debug("Persistence session is flushed.");
            } catch (Exception e) {
                LOG.error("Failed to flush session after job: " + context.getJobDetail().getDescription(), e);
            } finally {
                try {
                    persistenceInterceptor.destroy();
                } catch (Exception e) {
                    LOG.error("Failed to finalize session after job: " + context.getJobDetail().getDescription(), e);
                }
            }
        }
    }
}
