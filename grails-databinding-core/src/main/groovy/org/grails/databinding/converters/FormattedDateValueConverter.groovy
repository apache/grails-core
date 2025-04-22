/*
 * Copyright 2013 the original author or authors.
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
package org.grails.databinding.converters

import grails.databinding.converters.FormattedValueConverter;
import groovy.transform.CompileStatic

import java.text.SimpleDateFormat

/**
 * @author Jeff Brown
 * @since 2.3
 */
@CompileStatic
class FormattedDateValueConverter implements FormattedValueConverter {

    def convert(value, String format) {
        if(value instanceof Date) {
            return value
        }
        else if(value instanceof CharSequence) {
            String dateStr = value.toString()
            if(!dateStr) {
                return null
            }
            else {
                def fmt = new SimpleDateFormat(format)
                fmt.lenient = false
                fmt.parse((String) value)
            }
        }
    }

    Class<?> getTargetType() {
        Date
    }

}
