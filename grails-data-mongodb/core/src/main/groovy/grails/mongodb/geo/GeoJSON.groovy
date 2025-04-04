/* Copyright (C) 2014 SpringSource
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
package grails.mongodb.geo

/**
 * Marker interface for shapes that are GeoJSON shapes
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public interface GeoJSON {

    /**
     * Converts the GeoJSON shape into a coordinate list
     *
     * @return The coordinate list
     */
    abstract List<? extends Object> asList();
}