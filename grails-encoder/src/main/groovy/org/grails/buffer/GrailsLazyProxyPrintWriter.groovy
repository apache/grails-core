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
package org.grails.buffer

import groovy.transform.CompileStatic

@CompileStatic
class GrailsLazyProxyPrintWriter extends GrailsPrintWriter {

    private DestinationFactory factory
    private boolean destinationActivated = false

    /**
     * Factory to lazily instantiate the destination.
     */
    static interface DestinationFactory {
        Writer activateDestination() throws IOException
    }

    GrailsLazyProxyPrintWriter(DestinationFactory factory) {
        super(null)
        this.factory = factory
    }

    @Override
    Writer getOut() {
        if (!destinationActivated) {
            try {
                super.setOut(factory.activateDestination())
            }
            catch (IOException e) {
                setError()
            }
            destinationActivated = true
        }
        return super.getOut()
    }

    @Override
    boolean isAllowUnwrappingOut() {
        return destinationActivated ? super.isAllowUnwrappingOut() : false
    }

    @Override
    Writer unwrap() {
        return destinationActivated ? super.unwrap() : this
    }

    void updateDestination(DestinationFactory f) {
        setDestinationActivated(false)
        this.factory = f
    }

    @Override
    boolean isDestinationActivated() {
        return destinationActivated
    }

    void setDestinationActivated(boolean destinationActivated) {
        this.destinationActivated = destinationActivated
        if (!this.destinationActivated) {
            super.setOut(null)
        }
    }
}
