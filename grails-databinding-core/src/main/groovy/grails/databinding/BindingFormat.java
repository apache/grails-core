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
package grails.databinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

/**
 * Apply BindingFormat to a field to provide a format
 * to be used when binding a String to this field.
 *
<pre>
class DateContainer {
    &#064;BindingFormat('MMddyyyy')
    Date someDate
}
</pre>
 *
 * @author Jeff Brown
 * @since 2.3
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@GroovyASTTransformationClass("org.grails.databinding.compiler.BindingFormatASTTransformation")
public @interface BindingFormat {
    String value() default "";
    String code() default "";
}
