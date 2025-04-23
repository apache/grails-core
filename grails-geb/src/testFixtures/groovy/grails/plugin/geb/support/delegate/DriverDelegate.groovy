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
package grails.plugin.geb.support.delegate

import grails.plugin.geb.ContainerGebSpec
import groovy.transform.CompileStatic
import groovy.transform.SelfType

/**
 * Handles delegation to select methods of the driver instance for end user convenience.
 *
 * @author Mattias Reichel
 * @since 4.2
 */
@CompileStatic
@SelfType(ContainerGebSpec)
trait DriverDelegate {

    /**
     * Get the source of the last loaded page. If the page has been modified after loading (for
     * example, by Javascript) there is no guarantee that the returned text is that of the modified
     * page. Please consult the documentation of the particular driver being used to determine whether
     * the returned text reflects the current state of the page or the text last sent by the web
     * server. The page source returned is a representation of the underlying DOM: do not expect it to
     * be formatted or escaped in the same way as the response sent from the web server. Think of it
     * as an artist's impression.
     *
     * <p>See <a href="https://w3c.github.io/webdriver/#get-page-source">W3C WebDriver
     * specification</a> for more details.
     *
     * @return The source of the current page
     */
    String getPageSource() {
        testManager.browser.driver.pageSource
    }

}