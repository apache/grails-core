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
package grails.build.logging;

import jline.Terminal;
import jline.UnsupportedTerminal;

import java.io.IOException;

/**
 * This class is meant to keep changes made in support of Eclipse separate from
 * the standard GrailsConsole implementation.
 * <p>
 * It is activated by setting system property "grails.console.class" to the
 * fully qualified name of this class.
 * <p>
 * Having the changes in a class triggered by system property also leaves open
 * the option to have Eclipse provide a different version of the class at
 * runtime, to allow for further customisation to support not yet
 * anticipated needs.
 *
 * @author Kris De Volder
 *
 * @since 2.0.0.M2
 */
public class GrailsEclipseConsole extends GrailsConsole {

    private static final boolean DEBUG = boolProp("grails.console.eclipse.debug");
    private static final String ECLIPSE_SUPPORTS_ANSI_PROP = "grails.console.eclipse.ansi";

    private Boolean eclipseSupportsAnsi = null; //lazy initialized because implicitly used from super constructor.

    /**
     * Create a GrailsConsole that has some customizations to work better with STS. Note that
     * this console implicitly captures and redirects System.out, System.err and System.in in the
     * super class. So extreme care must be taken to ensure these streams are setup correctly
     * before creating an instance.
     */
    public GrailsEclipseConsole() throws IOException {
        super();
    }

    private boolean eclipseSupportsAnsi() {
        if (eclipseSupportsAnsi == null) {
            eclipseSupportsAnsi = boolProp(ECLIPSE_SUPPORTS_ANSI_PROP);
        }
        return eclipseSupportsAnsi;
    }

    private static Boolean boolProp(String propName) {
        try {
            String prop =  System.getProperty(propName);
            return prop != null && Boolean.valueOf(prop);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected Terminal createTerminal() {
        // unix or windows terminal have no relation at all to the behavior of an Eclipse console.
        return new UnsupportedTerminal();
    }
}
