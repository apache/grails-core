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

package grails.plugin.json.builder;

import java.io.IOException;

import groovy.json.JsonException;
import groovy.lang.Writable;

import org.grails.buffer.FastStringWriter;

/**
 * Class responsible for the actual String serialization of the possible values of a JSON structure.
 * This class can also be used as a category, to add <code>toJson()</code> methods to various types.
 *
 * @since 1.8.0
 */
public class JsonOutput extends groovy.json.JsonOutput {

    public static final char OPEN_BRACE = '{';
    public static final char OPEN_BRACKET = '[';
    public static final char CLOSE_BRACKET = ']';
    public static final char CLOSE_BRACE = '}';
    public static final char COLON = ':';
    public static final char COMMA = ',';

    public static final String NULL_VALUE = "null";

    /**
     * Represents unescaped JSON
     */
    public static abstract class JsonWritable implements Writable, CharSequence {

        protected boolean inline = false;
        protected boolean first = true;

        public void setInline(boolean inline) {
            this.inline = inline;
        }

        public void setFirst(boolean first) {
            this.first = first;
        }

        @Override
        public String toString() {
            FastStringWriter out = new FastStringWriter();
            try {
                writeTo(out);
            } catch (IOException e) {
                throw new JsonException("Error writing JSON writable: " + e.getMessage(), e);
            }
            return out.toString();
        }

        @Override
        public int length() {
            return toString().length();
        }

        @Override
        public char charAt(int index) {
            return toString().charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return toString().subSequence(start, end);
        }
    }
}
