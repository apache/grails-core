/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.json.parser;

/**
 * @author Siegfried Puchbauer
 *
 * Adapted from the Google GSON Parser http://google-gson.googlecode.com/
 */
final class StringUnmarshaller {

    private StringUnmarshaller() {
    }

    static String unmarshall(String str) {
        str = str.substring(1, str.length() - 1);

        int len = str.length();
        StringBuilder sb = new StringBuilder(len);
        int i = 0;
        while (i < len) {
            char c = str.charAt(i);
            ++i;
            if (c == '\\') {
                char c1 = str.charAt(i);
                ++i;
                if (c1 == 'u') { // This is a unicode escape
                    int codePoint = getCodePoint(str, i);
                    sb.appendCodePoint(codePoint);
                    i += 4;
                }
                else {
                    char escapedChar = getEscapedChar(str, c1);
                    sb.append(escapedChar);
                }
            }
            else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int getCodePoint(String str, int i) {
        String s = str.substring(i, i + 4);
        return Integer.parseInt(s, 16);
    }

    private static char getEscapedChar(String str, char c) {
        switch (c) {
            case 'n':  return '\n';
            case 'b':  return '\b';
            case 'f':  return '\f';
            case 't':  return '\t';
            case 'r':  return '\r';
            case '\"': return '\"';
            case '\'': return '\'';
            case '\\': return '\\';
            case '/':  return '/';
            default:  throw new IllegalStateException("Unexpected character: " + c + " in " + str);
        }
    }
}
