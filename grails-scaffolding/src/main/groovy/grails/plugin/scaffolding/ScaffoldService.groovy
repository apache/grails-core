/*
 * Copyright 2024 the original author or authors.
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
package grails.plugin.scaffolding

/**
 * Scaffold service interface providing CRUD operations for domain entities.
 *
 * <p>This interface defines the complete contract for scaffold services.
 * It is completely datastore-agnostic and can be implemented for any persistence backend:
 * GORM (Hibernate, MongoDB, Neo4j), JPA, JDBC, REST clients, or custom implementations.</p>
 *
 * <h3>Use Cases</h3>
 * <ul>
 * <li>Standard CRUD services with full create, read, update, delete capabilities</li>
 * <li>Domain services that need both query and mutation operations</li>
 * <li>Services wrapping any data access technology</li>
 * <li>Read-only services using the {@code readOnly} flag to throw exceptions on mutations</li>
 * </ul>
 *
 * <h3>Usage with @Scaffold Annotation</h3>
 * <pre>{@code
 * // Default GORM implementation
 * @Scaffold(Car)
 * class CarService {}
 * // AST transformation generates implementation automatically
 *
 * // Read-only service (save/delete are no-ops)
 * @Scaffold(domain = Report, readOnly = true)
 * class ReportService {}
 * }</pre>
 *
 * @param <T> The entity/domain type
 * @param <ID> The identifier type (Long, String, UUID, composite keys, etc.)
 *
 * @author Scott Murphy Heiberg
 * @since 7.1.0
 */
interface ScaffoldService<T, ID extends Serializable> {

    /**
     * Retrieve a single entity by its identifier.
     *
     * @param id The entity identifier
     * @return The entity instance, or null if not found
     */
    T get(ID id)

    /**
     * List entities with optional pagination, sorting, and filtering.
     *
     * <p>The args map typically contains:</p>
     * <ul>
     * <li><code>max</code> - Maximum number of results to return</li>
     * <li><code>offset</code> - Starting offset for pagination</li>
     * <li><code>sort</code> - Field name to sort by</li>
     * <li><code>order</code> - Sort order ('asc' or 'desc')</li>
     * <li>Additional domain-specific filter parameters</li>
     * </ul>
     *
     * @param args Map containing query parameters for pagination, sorting, and filtering
     * @return List of entities matching the criteria
     */
    List<T> list(Map args)

    /**
     * Count the total number of entities matching the given criteria.
     *
     * @param args Map containing filtering criteria
     * @return Total count of matching entities
     */
    Long count(Map args)

    /**
     * Save (create or update) an entity.
     *
     * <p>Implementations should:</p>
     * <ul>
     * <li>Create a new entity if it doesn't have an ID</li>
     * <li>Update an existing entity if it has an ID</li>
     * <li>Perform validation if applicable</li>
     * <li>Flush changes to the datastore</li>
     * </ul>
     *
     * @param instance The entity instance to save
     * @return The saved entity (with generated ID if new), or the original instance if readOnly mode
     * @throws ValidationException if validation fails
     */
    T save(T instance)

    /**
     * Delete an entity by its identifier.
     *
     * <p>Implementations should handle the case where the entity doesn't exist gracefully
     * (either silently succeed or throw a specific exception).</p>
     *
     * <p>In readOnly mode, this operation is silently ignored (no-op).</p>
     *
     * @param id The identifier of the entity to delete
     */
    void delete(ID id)
}
