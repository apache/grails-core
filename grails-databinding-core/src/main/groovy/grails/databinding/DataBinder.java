/*
 * Copyright 2014-2024 the original author or authors.
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

import grails.databinding.events.DataBindingListener;
import groovy.xml.slurpersupport.GPathResult;

import java.util.List;

/**
 * @author Jeff Brown
 * @since 3.0
 */
public interface DataBinder {

    String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.S";

    /**
     * 
     * @param obj The object being bound to
     * @param source The data binding source
     * @param filter Only properties beginning with filter will be included in the
     * data binding.  For example, if filter is &quot;person&quot; and the binding
     * source contains data for properties &quot;person.name&quot; and &quot;author.name&quot;
     * the value of &quot;person.name&quot; will be bound to obj.name.  The value of
     * &quot;author.name&quot; will be ignored.
     * @param whiteList A list of property names to be included during this 
     * data binding.  All other properties represented in the binding source 
     * will be ignored
     * @param blackList A list of properties names to be excluded during
     * this data binding.  
     * @param listener A listener which will be notified of data binding events triggered
     * by this binding
     * @see DataBindingSource
     * @see DataBindingListener
     */
    void bind(Object obj, DataBindingSource source, String filter, List<String> whiteList,
              List<String> blackList, DataBindingListener listener);

    /**
     * 
     * @param obj The object being bound to
     * @param source The data binding source
     * @param filter Only properties beginning with filter will be included in the
     * data binding.  For example, if filter is &quot;person&quot; and the binding
     * source contains data for properties &quot;person.name&quot; and &quot;author.name&quot;
     * the value of &quot;person.name&quot; will be bound to obj.name.  The value of
     * &quot;author.name&quot; will be ignored.
     * @param whiteList A list of property names to be included during this 
     * data binding.  All other properties represented in the binding source 
     * will be ignored
     * @param blackList A list of properties names to be excluded during
     * this data binding.  
     * @see DataBindingSource
     */
    void bind(Object obj, DataBindingSource source, String filter, List<String> whiteList,
              List<String> blackList);

    /**
     * 
     * @param obj The object being bound to
     * @param gpath A GPathResult which represents the data being bound.  
     * @see DataBindingSource
     */
    void bind(Object obj, GPathResult gpath);

    /**
     * 
     * @param obj The object being bound to
     * @param source The data binding source
     * @param whiteList A list of property names to be included during this 
     * data binding.  All other properties represented in the binding source 
     * will be ignored
     * @param blackList A list of properties names to be excluded during
     * this data binding.  
     * @see DataBindingSource
     */
    void bind(Object obj, DataBindingSource source, List<String> whiteList,
              List<String> blackList);

    /**
     * 
     * @param obj The object being bound to
     * @param source The data binding source
     * @param whiteList A list of property names to be included during this 
     * data binding.  All other properties represented in the binding source 
     * will be ignored
     * @see DataBindingSource
     */
    void bind(Object obj, DataBindingSource source, List<String> whiteList);

    /**
     * 
     * @param obj The object being bound to
     * @param source The data binding source
     * @param listener A listener which will be notified of data binding events triggered
     * by this binding
     * @see DataBindingSource
     * @see DataBindingListener
     */
    void bind(Object obj, DataBindingSource source, DataBindingListener listener);

    /**
     * 
     * @param obj The object being bound to
     * @param source The data binding source
     * @see DataBindingSource
     */
    void bind(Object obj, DataBindingSource source);

}
