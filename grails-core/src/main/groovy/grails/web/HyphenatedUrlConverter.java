/*
 * Copyright 2011 SpringSource.
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
package grails.web;


import org.springframework.util.StringUtils;

/**
 * URL converter that allows for hyphenated URLs
 *
 * @author Jeff Brown
 * @since 2.0
 */
public class HyphenatedUrlConverter implements UrlConverter {

    public String toUrlElement(String propertyOrClassName) {
        if (!StringUtils.hasText(propertyOrClassName)) {
            return propertyOrClassName;
        }

        StringBuilder builder = new StringBuilder();
        char[] charArray = propertyOrClassName.toCharArray();
        char lastChar = ' ';
        for (char c : charArray) {
            if (Character.isUpperCase(c)) {
                if (builder.length() > 0 && lastChar != '.') {
                    builder.append('-');
                }
                builder.append(Character.toLowerCase(c));
            } else {
                builder.append(c);
            }
            lastChar = c;
        }
        return builder.toString();
    }
}
