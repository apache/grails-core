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
package org.grails.test.report.junit;

import java.io.OutputStream;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitResultFormatter;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;

/**
 * Simply propagates to the underlying reports.
 */
public class JUnitReports implements JUnitResultFormatter {

    protected JUnitResultFormatter[] reports;

    public JUnitReports(JUnitResultFormatter[] reports) {
        this.reports = reports;
    }

    public void setOutput(OutputStream out) {
        throw new IllegalStateException("This should not be reached");
    }

    public void startTestSuite(JUnitTest suite) {
        for (JUnitResultFormatter report : reports) {
            report.startTestSuite(suite);
        }
    }

    public void startTest(Test test) {
        for (JUnitResultFormatter report : reports) {
            report.startTest(test);
        }
    }

    public void addError(Test test, Throwable t) {
        for (JUnitResultFormatter report : reports) {
            report.addError(test, t);
        }
    }

    public void addFailure(Test test, AssertionFailedError t) {
        for (JUnitResultFormatter report : reports) {
            report.addFailure(test, t);
        }
    }

    public void endTest(Test test) {
        for (JUnitResultFormatter report : reports) {
            report.endTest(test);
        }
    }

    public void setSystemError(String err) {
        for (JUnitResultFormatter report : reports) {
            report.setSystemError(err);
        }
    }

    public void setSystemOutput(String out) {
        for (JUnitResultFormatter report : reports) {
            report.setSystemOutput(out);
        }
    }

    public void endTestSuite(JUnitTest suite) {
        for (JUnitResultFormatter report : reports) {
            report.endTestSuite(suite);
        }
    }
}
