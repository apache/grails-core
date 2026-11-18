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
package org.apache.grails.common.aot;

import org.springframework.context.aot.AbstractAotProcessor;
import org.springframework.core.SpringProperties;

/**
 * Whether the application's code is being written out rather than run.
 *
 * <p>A bean definition is read here to be turned into source, so anything it holds is written into
 * the artifact and carried wherever that artifact goes. Two kinds of value must not be: one that
 * describes the machine doing the writing -- a directory it built in, the environment it had -- and
 * one that cannot be expressed as code at all.</p>
 *
 * <p>This is not a question about the environment. An application generates its definitions in
 * production and is still being generated rather than served, and the things that must not be
 * written down are the same either way: the sources are on disk while generating, so everything
 * that asks whether they are answers yes.</p>
 *
 * <p>Spring sets the flag around its own processing and does not offer a predicate for it, so the
 * reading of it lives here rather than in each place that has to ask.</p>
 *
 * @since 8.0
 */
public final class AheadOfTimeProcessing {

    private AheadOfTimeProcessing() {
    }

    /**
     * @return whether this is running inside ahead-of-time processing rather than in an application
     */
    public static boolean isGeneratingCode() {
        return SpringProperties.getFlag(AbstractAotProcessor.AOT_PROCESSING);
    }

}
