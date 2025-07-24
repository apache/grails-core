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

package grails.testing.spock

import groovy.transform.AnnotationCollector

import org.junit.jupiter.api.BeforeEach

/**
 * This annotation may be applied to fixture methods in a Spock Spec that should
 * be run once and only once before any test methods are run.  Methods marked
 * with this interface will automatically be marked with @org.junit.Before and
 * {@link grails.testing.spock.RunOnce}.
 *
 * @see org.junit.jupiter.api.BeforeEach
 * @see grails.testing.spock.RunOnce
 * @deprecated Use Spock native setup/cleanup/…​ fixture methods instead.
 */
@Deprecated
@AnnotationCollector([BeforeEach, RunOnce])
@interface OnceBefore {
}
