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
package org.grails.validation

import grails.config.Config
import grails.config.Settings
import grails.util.ClosureToMapPopulator
import groovy.transform.CompileStatic
import org.grails.core.lifecycle.ShutdownOperations

/**
 * Utility methods for configuring constraints
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
class ConstraintEvalUtils {

    static {
        ShutdownOperations.addOperation({
            clearDefaultConstraints()
        } as Runnable, true)
    }

    private static Map<String, Object> defaultConstraintsMap
    private static configId

    /**
     * Looks up the default configured constraints from the given configuration
     */
    static Map<String, Object> getDefaultConstraints(Config config) {
        def cid = System.identityHashCode(config)
        if (defaultConstraintsMap == null || configId != cid) {
            configId = cid
            def constraints = config.getProperty(Settings.GORM_DEFAULT_CONSTRAINTS, Closure)
            if (constraints) {
                defaultConstraintsMap = new ClosureToMapPopulator().populate((Closure<?>) constraints)
            }
            else {
                defaultConstraintsMap = Collections.emptyMap()
            }
        }
        return defaultConstraintsMap
    }

    static void clearDefaultConstraints() {
        defaultConstraintsMap =  null
        configId = null
    }
}
