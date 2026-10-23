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
package org.apache.grails.data.core.querytail

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * SAP HANA: {@code #} and {@code @} are part of a name. Whether a line comment ends at a carriage
 * return that no line feed follows is not verified, so a statement with one is read both ways.
 */
@CompileStatic
@PackageScope
final class HanaSqlSyntax extends StandardSqlSyntax {

    private final boolean carriageReturnEndsLine

    HanaSqlSyntax(boolean carriageReturnEndsLine) {
        super('SAP HANA')
        this.carriageReturnEndsLine = carriageReturnEndsLine
    }

    @Override
    List<QueryTailSyntax> readings(String sql) {
        if (!hasLoneCarriageReturn(sql)) {
            return List.<QueryTailSyntax>of(this)
        }
        List.<QueryTailSyntax>of(new HanaSqlSyntax(false), new HanaSqlSyntax(true))
    }

    @Override
    protected boolean carriageReturnEndsLine() {
        carriageReturnEndsLine
    }

    @Override
    protected boolean isNamePart(char c) {
        super.isNamePart(c) || c == '#' || c == '@'
    }
}
