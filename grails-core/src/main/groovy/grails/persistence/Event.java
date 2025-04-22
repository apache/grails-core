/*
 * Copyright 2004-2005 the original author or authors.
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
package grails.persistence;

import java.util.ArrayList;
import java.util.List;

/**
 * Enum of the available events that Grails triggers.
 *
 * @author Graeme Rocher
 * @since 1.2
 */
public enum Event {
    onLoad, onSave, beforeLoad, beforeInsert, beforeUpdate, beforeDelete, afterLoad, afterInsert, afterUpdate, afterDelete;

    private static final String[] allEvents;
    static {
        List<String> events = new ArrayList<String>();
        for (Event e : values()) {
            events.add(e.toString());
        }
        allEvents = events.toArray(new String[events.size()]);

    }
    /**
     * @return The names of all persistence events
     */
    public static String[] getAllEvents() {
        return allEvents;
    }
}
