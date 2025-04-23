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
package grails.core.support;

import org.springframework.beans.factory.Aware;

/**
 * Convenience interface that can be implemented by classes that are registered by plugins.
 *
 * @author Steven Devijver
 * @since 0.2
 */
public interface ClassLoaderAware extends Aware {

    /**
     * This method is called by the {@link org.springframework.context.ApplicationContext} that
     * loads the Grails application. The {@link ClassLoader} that loads the Grails application code
     * is injected.
     *
     * @param classLoader the {@link ClassLoader} that loads the Grails application code
     */
    void setClassLoader(ClassLoader classLoader);
}
