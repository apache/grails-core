/*
 * Copyright 2011 SpringSource
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
package grails.validation

import org.springframework.validation.BeanPropertyBindingResult

/**
 * Models validation errors in a Grails application.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
class ValidationErrors extends BeanPropertyBindingResult {

    ValidationErrors(Object target) {
        super(target, target.getClass().name)
    }

    ValidationErrors(Object target, String objectName) {
        super(target, objectName)
    }

    def getAt(String field) {
        getFieldError(field)
    }

    def putAt(String field, String errorCode) {
        rejectValue(field, errorCode)
    }
}
