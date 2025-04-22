/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.dev.support

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ConfigurableApplicationContext

/**
 * Registers a shutdown hook to close the application context when CTRL+C is hit in dev mode.
 *
 * @author Graeme Rocher
 * @since 1.1.1
 */
class DevelopmentShutdownHook implements ApplicationContextAware {

    public static final String INSTALLED = "grails.shutdown.hook.installed"

    @CompileStatic
    void setApplicationContext(ApplicationContext applicationContext) {
        if (Boolean.getBoolean(INSTALLED)) {
            return
        }

        Runtime.runtime.addShutdownHook {
            try {
                Thread.start {
                    ((ConfigurableApplicationContext)applicationContext).close()
                }.join(2000)
            } catch (Throwable e) {
                LoggerFactory.getLogger(DevelopmentShutdownHook).warn("Error shutting down application: ${e.message}", e)
            }
        }

        System.setProperty(INSTALLED, "true")
    }
}
