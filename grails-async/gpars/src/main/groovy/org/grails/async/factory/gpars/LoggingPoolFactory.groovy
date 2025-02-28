/*
 * Copyright 2013 SpringSource
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
package org.grails.async.factory.gpars

import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import groovyx.gpars.scheduler.DefaultPool
import groovyx.gpars.scheduler.Pool
import groovyx.gpars.util.PoolFactory
import groovyx.gpars.util.PoolUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.reflect.Method
import java.util.concurrent.*

/**
 * A pool factory that logs error instead of printing them to standard err as is the default in GPars
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@AutoFinal
@CompileStatic
class LoggingPoolFactory implements PoolFactory {

    private static final long KEEP_ALIVE_TIME = 10L;
    public static final Logger LOG = LoggerFactory.getLogger(LoggingPoolFactory)

    public static Method createThreadNameMethod

    static {
        createThreadNameMethod = DefaultPool.getDeclaredMethod('createThreadName')
        createThreadNameMethod.setAccessible(true)
    }

    @Override
    Pool createPool() {
        return new DefaultPool(createResizeablePool(true, PoolUtils.retrieveDefaultPoolSize()))
    }

    @Override
    Pool createPool(boolean daemon) {
        return new DefaultPool(createResizeablePool(daemon, PoolUtils.retrieveDefaultPoolSize()))
    }

    @Override
    Pool createPool(int numberOfThreads) {
        return new DefaultPool(createResizeablePool(true, numberOfThreads))
    }

    @Override
    Pool createPool(boolean daemon, int numberOfThreads) {
        return new DefaultPool(createResizeablePool(daemon, numberOfThreads))
    }

    /**
     * Creates a fixed-thread pool of given size. Each thread will have the uncaught exception handler set
     * to print the unhandled exception to standard error output.
     *
     * @param daemon   Sets the daemon flag of threads in the pool.
     * @param poolSize The required pool size  @return The created thread pool
     * @return The newly created thread pool
     */
    private static ThreadPoolExecutor createResizeablePool(boolean daemon, int poolSize) {
        assert poolSize > 0;
        return new ThreadPoolExecutor(poolSize, 1000, KEEP_ALIVE_TIME, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new ThreadFactory() {
            @Override
            Thread newThread(Runnable r) {
                Thread thread = new Thread(r, createThreadNameMethod.invoke(DefaultPool).toString())
                thread.daemon = daemon
                thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    @Override
                    void uncaughtException(Thread t, Throwable e) {
                        LOG.error("Async execution error: ${e.message}", e)
                    }
                })
                return thread
            }
        }, new RejectedExecutionHandler() {
            @Override
            void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                throw new IllegalStateException("The thread pool executor cannot run the task. " +
                    "The upper limit of the thread pool size has probably been reached. " +
                    "Current pool size: $executor.poolSize Maximum pool size: $executor.maximumPoolSize")
            }
        })
    }
}
