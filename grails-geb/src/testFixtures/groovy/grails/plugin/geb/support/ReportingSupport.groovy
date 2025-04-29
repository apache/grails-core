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
package grails.plugin.geb.support

import geb.report.CompositeReporter
import geb.report.PageSourceReporter
import geb.report.Reporter
import geb.report.ScreenshotReporter
import grails.plugin.geb.ContainerGebSpec
import groovy.transform.CompileStatic
import groovy.transform.SelfType

/**
 * Enables reporting support for ContainerGebSpec.
 *
 * @author James Daugherty
 * @author Mattias Reichel
 * @since 4.2
 */
@CompileStatic
@SelfType(ContainerGebSpec)
trait ReportingSupport {

    void report(String message) {
        testManager.report(message)
    }

    /**
     * The reporter that Geb should use when reporting is enabled.
     */
    Reporter createReporter() {
        return new CompositeReporter(new PageSourceReporter(), new ScreenshotReporter())
    }
}
