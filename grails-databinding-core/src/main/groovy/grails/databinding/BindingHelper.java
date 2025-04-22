/*
 * Copyright 2014 the original author or authors.
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

/**
 * Classes which implement this interface may participate in the data binding
 * process.  Instances of this interface may be registered with the
 * data binder by applying the {@link BindUsing} annotation to a class.
 *
 * @author Jeff Brown
 * @since 3.0
 * @see BindUsing
 */
public interface BindingHelper<T> {

    /**
     * The value returned from this method will be bound to
     * the property specified by propertyName.
     *
     * @param obj The object that data binding is being applied to
     * @param propertyName The name of the property data binding is being applied to
     * @param source The Map containing all of the values being bound to this object
     * @return The value which should be bound to propertyName
     */
    T getPropertyValue(Object obj, String propertyName, DataBindingSource source);
}
