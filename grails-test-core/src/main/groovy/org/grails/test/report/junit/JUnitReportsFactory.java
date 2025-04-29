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

import groovy.lang.Binding;

import java.io.File;
import java.util.List;

import org.apache.tools.ant.taskdefs.optional.junit.JUnitResultFormatter;

public class JUnitReportsFactory {

    public static final String XML = "xml";
    public static final String PLAIN = "plain";

    protected final String phaseName;
    protected final String typeName;
    protected final File reportsDir;
    protected final List<String> formats;

    @SuppressWarnings("unchecked")
    public static JUnitReportsFactory createFromBuildBinding(Binding buildBinding) {
        // This is not great, the phase and type names probably shouldn't be sourced from the binding.
        return new JUnitReportsFactory(
                (String)buildBinding.getProperty("currentTestPhaseName"),
                (String)buildBinding.getProperty("currentTestTypeName"),
                (File)buildBinding.getProperty("testReportsDir"),
                (List<String>)buildBinding.getProperty("reportFormats"));
    }

    public JUnitReportsFactory(String phaseName, String typeName, File reportsDir, List<String> formats) {
        this.phaseName = phaseName;
        this.typeName = typeName;
        this.reportsDir = reportsDir;
        this.formats = formats;
    }

    public JUnitReports createReports(String name) {
        JUnitResultFormatter formatters[] = new JUnitResultFormatter[formats.size()];
        for (int i = 0; i < formats.size(); ++i) {
            formatters[i] = createReport(formats.get(i), name);
        }
        return new JUnitReports(formatters);
    }

    protected JUnitResultFormatter createReport(String format, String name) {
        String prefix = "TEST-" + phaseName + "-" + typeName + "-" + name;
        if (format.equals(PLAIN)) {
            return new PlainFormatter(prefix, new File(reportsDir, "plain/" + prefix + ".txt"));
        }

        if (format.equals(XML)) {
            return new XMLFormatter(new File(reportsDir, prefix + ".xml"));
        }

        throw new IllegalArgumentException("Unknown format type: " + format);
    }
}
