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
package org.grails.datastore.gorm

import grails.gorm.api.GormInstanceOperations
import groovy.transform.CompileStatic

import org.codehaus.groovy.runtime.InvokerHelper
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingSupport
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionCallback
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.proxy.EntityProxy
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.mapping.validation.ValidationException

/**
 * Instance methods of the GORM API.
 *
 * @author Graeme Rocher
 * @param <D> the entity/domain class
 */
@CompileStatic
class GormInstanceApi<D> extends AbstractGormApi<D> implements GormInstanceOperations<D> {

    Class<? extends Exception> validationException = ValidationException
    boolean failOnError = false
    boolean markDirty = true

    GormInstanceApi(Class<D> persistentClass, Datastore datastore) {
        super(persistentClass, datastore)
        validationException = ValidationException.VALIDATION_EXCEPTION_TYPE
    }

    Object propertyMissing(D instance, String name) {
        try {

            def instanceApi = GormEnhancer.findInstanceApi(persistentClass, name)
            return new DelegatingGormEntityApi(instanceApi, instance)
        } catch (IllegalStateException ise) {
            throw new MissingPropertyException(name, persistentClass)
        }
    }

    /**
     * Proxy aware instanceOf implementation.
     */
    boolean instanceOf(D o, Class cls) {
        if (o instanceof EntityProxy) {
            o = (D)((EntityProxy)o).getTarget()
        }
        return o in cls
    }

    /**
     * Upgrades an existing persistence instance to a write lock
     * @return The instance
     */
    D lock(D instance) {
        execute({ Session session ->
            session.lock(instance)
            return instance
        } as SessionCallback)
    }

    /**
     * Locks the instance for updates for the scope of the passed closure
     *
     * @param callable The closure
     * @return The result of the closure
     */
    public <T> T mutex(D instance, Closure<T> callable) {
        execute({ Session session ->
            try {
                session.lock(instance)
                callable?.call()
            }
            finally {
                session.unlock(instance)
            }
        } as SessionCallback)
    }

    /**
     * Refreshes the state of the current instance
     * @param instance The instance
     * @return The instance
     */
    D refresh(D instance) {
        execute({ Session session ->
            session.refresh instance
            return instance
        } as SessionCallback)
    }

    /**
     * Saves an object the datastore
     * @param instance The instance
     * @return Returns the instance
     */
    D save(D instance) {
        save(instance, Collections.emptyMap())
    }

    /**
     * Forces an insert of an object to the datastore
     * @param instance The instance
     * @return Returns the instance
     */
    D insert(D instance) {
        insert(instance, Collections.emptyMap())
    }

    /**
     * Forces an insert of an object to the datastore
     * @param instance The instance
     * @return Returns the instance
     */
    D insert(D instance, Map params) {
        execute({ Session session ->
            doSave instance, params, session, true
        } as SessionCallback)
    }

    /**
     * Saves an object the datastore
     * @param instance The instance
     * @return Returns the instance
     */
    D merge(D instance) {
        save(instance, Collections.emptyMap())
    }

    /**
     * Saves an object the datastore
     * @param instance The instance
     * @return Returns the instance
     */
    D merge(D instance, Map params) {
        save(instance, params)
    }

    /**
     * Save method that takes a boolean which indicates whether to perform validation or not
     *
     * @param instance The instance
     * @param validate Whether to perform validation
     *
     * @return The instance or null if validation fails
     */
    D save(D instance, boolean validate) {
        save(instance, [validate: validate])
    }

    /**
     * Saves an object with the given parameters
     * @param instance The instance
     * @param params The parameters
     * @return The instance
     */
    D save(D instance, Map params) {
        execute({ Session session ->
            doSave instance, params, session
        } as SessionCallback)
    }

    /**
     * Returns the objects identifier
     */
    Serializable ident(D instance) {
        PersistentProperty identity = persistentEntity.getIdentity()
        if(identity != null) {
            return (Serializable)instance[identity.name]
        }
        else {
            PersistentProperty[] idProperties = persistentEntity.getCompositeIdentity()
            if(idProperties != null) {
                EntityReflector entityReflector = persistentEntity.getReflector()
                def idInstance = persistentEntity.newInstance()
                if(idInstance instanceof Serializable) {
                    for(prop in idProperties) {
                        String propertName = prop.name
                        entityReflector.setProperty(
                                idInstance, propertName, entityReflector.getProperty(instance, propertName)
                        )
                    }
                    return (Serializable)idInstance
                }
            }
        }
        return null
    }

    /**
     * Attaches an instance to an existing session. Requries a session-based model
     * @param instance The instance
     * @return
     */
    D attach(D instance) {
        execute({ Session session ->
            session.attach(instance)
            instance
        } as SessionCallback)
    }

    /**
     * No concept of session-based model so defaults to true
     */
    boolean isAttached(D instance) {
        execute({ Session session ->
            session.contains(instance)
        } as SessionCallback)
    }

    /**
     * Discards any pending changes. Requires a session-based model.
     */
    void discard(D instance) {
        execute({ Session session ->
            session.clear(instance)
        } as SessionCallback)
    }

    /**
     * Deletes an instance from the datastore
     * @param instance The instance to delete
     */
    void delete(D instance) {
        delete(instance, Collections.emptyMap())
    }

    /**
     * Deletes an instance from the datastore
     * @param instance The instance to delete
     */
    void delete(D instance, Map params) {
        execute({ Session session ->
            session.delete(instance)
            if (params?.flush) {
                session.flush()
            }
        } as SessionCallback)
    }

    /**
     * Checks whether a field is dirty
     *
     * @param instance The instance
     * @param fieldName The name of the field
     *
     * @return true if the field is dirty
     */
    boolean isDirty(D instance, String fieldName) {
        if(instance instanceof DirtyCheckable) {
            return ((DirtyCheckable)instance).hasChanged(fieldName)
        }
        return true
    }

    /**
     * Checks whether an entity is dirty
     *
     * @param instance The instance
     * @return true if it is dirty
     */
    boolean isDirty(D instance) {
        if(instance instanceof DirtyCheckable) {
            return ((DirtyCheckable)instance).hasChanged() || DirtyCheckingSupport.areAssociationsDirty(persistentEntity, instance)
        }
        return true
    }

    /**
     * Obtains a list of property names that are dirty
     *
     * @param instance The instance
     * @return A list of property names that are dirty
     */
    List getDirtyPropertyNames(D instance) {
        if(instance instanceof DirtyCheckable) {
            return ((DirtyCheckable)instance).listDirtyPropertyNames()
        }
        return []
    }

    /**
     * Gets the original persisted value of a field.
     *
     * @param fieldName The field name
     * @return The original persisted value
     */
    Object getPersistentValue(D instance, String fieldName) {
        if(instance instanceof DirtyCheckable) {
            return ((DirtyCheckable)instance).getOriginalValue(fieldName)
        }
        return null
    }

    protected D doSave(D instance, Map params, Session session, boolean isInsert = false) {
        boolean hasErrors = false
        boolean validate = params?.containsKey("validate") ? params.validate : true
        boolean shouldFlush = params?.flush ? params.flush : false
        if(instance instanceof GormValidateable) {

            def validateable = (GormValidateable) instance
            if (validate) {
                validateable.skipValidation(false)
                if(datastore instanceof ConnectionSourcesProvider) {
                    ConnectionSources connectionSources = ((ConnectionSourcesProvider) datastore).connectionSources
                    String connectionSourceName = connectionSources.defaultConnectionSource.name
                    if(connectionSourceName != ConnectionSource.DEFAULT) {
                        GormValidationApi<D> validationApi = GormEnhancer.findValidationApi((Class<D>)instance.getClass(), connectionSourceName)
                        hasErrors = !validationApi.validate((D)instance, params)
                    }
                    else {
                        hasErrors = !validateable.validate(params)
                    }
                }
                else {
                    hasErrors = !validateable.validate(params)
                }
                // don't revalidate
                if(shouldFlush) {
                    validateable.skipValidation(true)
                }

            } else {
                validateable.skipValidation(true)
                validateable.clearErrors()
            }
        }

        if (hasErrors) {
            boolean failOnErrorEnabled = params?.containsKey("failOnError") ? params.failOnError : failOnError
            if (failOnErrorEnabled) {
                throw validationException.newInstance("Validation error occurred during call to save()", InvokerHelper.getProperty(instance, "errors"))
            }
            return null
        }
        if (isInsert) {
            session.insert(instance)
        }
        else {
            if(instance instanceof DirtyCheckable && markDirty) {
                // since this is an explicit call to save() we mark the instance as dirty to ensure it happens
                instance.markDirty()
            }
            session.persist(instance)
        }
        if (shouldFlush) {
            session.flush()
        }
        return instance
    }
}
