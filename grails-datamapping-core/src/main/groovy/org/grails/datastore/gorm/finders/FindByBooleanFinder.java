/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.finders;

import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.model.MappingContext;

/**
 * 
 * <p>The "find&lt;booleanProperty&gt;By*" static persistent method. This method allows querying for
 * instances of grails domain classes based on a boolean property and any other arbitrary
 * properties. This method returns the first result of the query.</p>
 *
 * <pre><code>
 * eg.
 * Account.findActiveByHolder("Joe Blogs"); // Where class "Account" has a properties called "active" and "holder"
 * Account.findActiveByHolderAndBranch("Joe Blogs", "London"); // Where class "Account" has a properties called "active', "holder" and "branch"
 * </code></pre>
 *
 * <p>
 * In both of those queries, the query will only select Account objects where active=true.
 * </p>
 *
 * @author Graeme Rocher
 * @author Jeff Brown
 */
public class FindByBooleanFinder extends FindByFinder{
    public static final String METHOD_PATTERN = "(find)((\\w+)(By)([A-Z]\\w*)|(\\w++))";

    public FindByBooleanFinder(Datastore datastore) {
        super(datastore);
        setPattern(METHOD_PATTERN);
    }

    public FindByBooleanFinder(MappingContext mappingContext) {
        super(mappingContext);
        setPattern(METHOD_PATTERN);
    }

    @Override
    public boolean firstExpressionIsRequiredBoolean() {
        return true;
    }
}
