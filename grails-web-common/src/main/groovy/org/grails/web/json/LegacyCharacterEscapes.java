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
package org.grails.web.json;

import tools.jackson.core.SerializableString;
import tools.jackson.core.io.CharacterEscapes;
import tools.jackson.core.io.SerializedString;

/**
 * Escapes the control characters that have no short escape as {@code grails.converters.JSON} escaped them in Grails 8:
 * with lower-case hexadecimal digits, such as <code>&#92;u001b</code>, but the vertical tab, which it wrote as
 * <code>&#92;u000B</code>. Jackson writes upper-case digits. Every other character is escaped as Jackson escapes it.
 *
 * @since 9.0
 */
final class LegacyCharacterEscapes extends CharacterEscapes {

    static final LegacyCharacterEscapes INSTANCE = new LegacyCharacterEscapes();

    private static final long serialVersionUID = 1L;

    private final int[] asciiEscapes;

    private final SerializedString[] controlEscapes = new SerializedString[0x20];

    private LegacyCharacterEscapes() {
        asciiEscapes = standardAsciiEscapesForJSON();
        for (int c = 0; c < controlEscapes.length; c++) {
            if (asciiEscapes[c] == ESCAPE_STANDARD) {
                asciiEscapes[c] = ESCAPE_CUSTOM;
                controlEscapes[c] = new SerializedString(c == 0x0B ? "\\u000B" : String.format("\\u%04x", c));
            }
        }
    }

    @Override
    public int[] getEscapeCodesForAscii() {
        return asciiEscapes;
    }

    @Override
    public SerializableString getEscapeSequence(int ch) {
        return ch >= 0 && ch < controlEscapes.length ? controlEscapes[ch] : null;
    }
}
