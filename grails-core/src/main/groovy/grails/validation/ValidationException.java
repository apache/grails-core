/*
 * Copyright 2009 the original author or authors.
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
package grails.validation;

import org.grails.core.exceptions.GrailsException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;

/**
 * Thrown when validation fails during a .save().
 *
 * @author Jeff Brown
 * @since 1.2
 */
public class ValidationException extends GrailsException {
    private static final long serialVersionUID = 1L;
    private Errors errors;
    private String fullMessage;

    public ValidationException(String msg, Errors e) {
        super(msg);
        errors = e;
        fullMessage = formatErrors(e, msg);
    }

    public Errors getErrors() {
        return errors;
    }

    public String getMessage() { 
        return fullMessage; 
    }
    
    public static String formatErrors(Errors errors) {
        return formatErrors(errors, null);
    }
    
    public static String formatErrors(Errors errors, String msg) {
        StringBuilder b = new StringBuilder();
        if(msg != null && msg.length() > 0) {
            b.append(msg).append(":\n");
        }
        for (ObjectError error : errors.getAllErrors()) {
            b.append("- ").append(error).append("\n");
        }
        return b.toString();
    }
}
