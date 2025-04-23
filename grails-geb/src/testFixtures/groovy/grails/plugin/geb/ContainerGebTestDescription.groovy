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
package grails.plugin.geb

import groovy.transform.CompileStatic
import org.spockframework.runtime.model.IterationInfo
import org.testcontainers.lifecycle.TestDescription

/**
 * Implements {@link org.testcontainers.lifecycle.TestDescription} to customize recording names.
 *
 * @author James Daugherty
 * @since 4.1
 */
@CompileStatic
class ContainerGebTestDescription implements TestDescription {

    String testId
    String filesystemFriendlyName

    ContainerGebTestDescription(IterationInfo testInfo) {
        testId = [
                testInfo.feature.spec.displayName,
                testInfo.feature.displayName,
                testInfo.displayName != testInfo.feature.displayName ? testInfo.displayName : null,
                testInfo.displayName != testInfo.feature.displayName ? testInfo.iterationIndex : null
        ].findAll(/* Remove nulls */).join(' ')

        String safeName = testId.replaceAll('\\W+', '_')
        filesystemFriendlyName = safeName
    }
}
