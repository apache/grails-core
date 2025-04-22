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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This annotation may be applied to a a field to
 * customize initialization of object properties in the data binding process.
 *
 * When the annotation is applied to a field, the value assigned to the
 * annotation should be a Closure which accepts 1 parameter.  The 
 * parameter is the object that data binding is being applied to.  
 * The value returned by the Closure will be bound to the field.  The
 * following code demonstrates using this technique to bind a contact
 * to user with the same account as the user.
 *
<pre>
class Contact{
  Account account
  String firstName
} 
class User {
    &#064;BindInitializer({
        obj -&gt; new Contact(account:obj.account)
    })
    Contact contact
    Account account
}
</pre>
 
 *
 * @since 3.2.11
 * @see BindingHelper
 * @see DataBindingSource
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface BindInitializer {
    Class<?> value();
}
