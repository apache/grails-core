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
package grails.databinding;


/**
 * StructuredBindingEditors convert structured data in a Map
 * into an object.  Typically a structured editor will pull
 * several values out of the Map that are necessary to initialize
 * the state of the object.
<code>
class Address {
    String state
    String city
}
class StructuredAddressBindingEditor implements StructuredBindingEditor {

    public Object getPropertyValue(Object obj, String propertyName, Map&lt;String, Object&gt; source) {
        def address = new Address()

        address.state = source[propertyName + '_someState']
        address.city = source[propertyName + '_someCity']

        address
    }
}

def binder = new SimpleDataBinder()
binder.registerStructuredEditor Address, new StructuredAddressBindingEditor()
def resident = new Resident()
def bindingSource = [:]
bindingSource.name = 'Scott'
bindingSource.homeAddress_someCity = "Scott's Home City"
bindingSource.homeAddress_someState = "Scott's Home State"
bindingSource.workAddress_someState = "Scott's Work State"
bindingSource.workAddress = 'struct'
bindingSource.homeAddress = 'struct'

binder.bind resident, bindingSource

resident.name == 'Scott'
resident.homeAddress
assert resident.homeAddress.city == "Scott's Home City"
assert resident.homeAddress.state == "Scott's Home State"
assert resident.workAddress
assert resident.workAddress.state == "Scott's Work State"
assert resident.workAddress.city == null
</code>
 *
 * @author Jeff Brown
 * @since 3.0
 * @see SimpleDataBinder#registerStructuredEditor(Class, StructuredBindingEditor)
 */
public interface StructuredBindingEditor<T> extends BindingHelper<T> {
    /**
     * The value returned from this method will be bound to
     * the property specified by propertyName.
     *
     * @param obj The object that data binding is being applied to
     * @param propertyName The name of the property data binding is being applied to
     * @param source The source containing all of the values being bound to this object
     * @return The value which should be bound to propertyName
     */
    T getPropertyValue(Object obj, String propertyName, DataBindingSource source);
}
