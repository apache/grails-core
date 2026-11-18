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
package org.grails.datastore.gorm.events;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertyResolver;

import org.grails.datastore.gorm.timestamp.AuditorAware;
import org.grails.datastore.gorm.timestamp.DefaultTimestampProvider;
import org.grails.datastore.gorm.timestamp.TimestampProvider;
import org.grails.datastore.mapping.config.AuditMetadataType;
import org.grails.datastore.mapping.config.Entity;
import org.grails.datastore.mapping.config.Settings;
import org.grails.datastore.mapping.core.AbstractDatastore;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.connections.ConnectionSources;
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider;
import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent;
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener;
import org.grails.datastore.mapping.engine.event.EventType;
import org.grails.datastore.mapping.engine.event.PreInsertEvent;
import org.grails.datastore.mapping.engine.event.PreUpdateEvent;
import org.grails.datastore.mapping.model.AuditMetadataUtils;
import org.grails.datastore.mapping.model.ClassMapping;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;

/**
 * An event listener that adds support for GORM-style auto-timestamping and auditing
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class AutoTimestampEventListener extends AbstractPersistenceEventListener implements MappingContext.Listener, ApplicationContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(AutoTimestampEventListener.class);

    // if false, will not set timestamp on insert event if value is not null
    @Value("${" + Settings.SETTING_AUTO_TIMESTAMP_INSERT_OVERWRITE + ":true}")
    public boolean insertOverwrite = true;

    // if false, will not cache auto-timestamp annotation metadata (for development/class reloading)
    @Value("${" + Settings.SETTING_AUTO_TIMESTAMP_CACHE_ANNOTATIONS + ":true}")
    public boolean cacheAutoTimestampAnnotations = true;

    public static final String DATE_CREATED_PROPERTY = "dateCreated";
    public static final String LAST_UPDATED_PROPERTY = "lastUpdated";

    protected Map<String, Optional<Set<String>>> entitiesWithDateCreated = new ConcurrentHashMap<>();
    protected Map<String, Optional<Set<String>>> entitiesWithLastUpdated = new ConcurrentHashMap<>();
    protected Map<String, Optional<Set<String>>> entitiesWithCreatedBy = new ConcurrentHashMap<>();
    protected Map<String, Optional<Set<String>>> entitiesWithUpdatedBy = new ConcurrentHashMap<>();
    protected Collection<String> uninitializedEntities = new ConcurrentLinkedQueue<>();

    private final ThreadLocal<DisabledTimestamps> disabledDateCreated = new ThreadLocal<>();
    private final ThreadLocal<DisabledTimestamps> disabledLastUpdated = new ThreadLocal<>();

    private TimestampProvider timestampProvider = new DefaultTimestampProvider();
    private volatile AuditorAware<?> auditorAware;

    // Whether auditorAware holds the outcome of a lookup, or a value that was set; until then it is looked up from
    // the application context of the datastore when an entity is saved.
    private volatile boolean auditorAwareResolved;

    // Guards the writes of auditorAware and auditorAwareResolved, so that an auditor set while a lookup runs is not
    // replaced by what the lookup finds.
    private final Object auditorAwareLock = new Object();

    /**
     * Creates the listener of the datastore, which reads {@link Settings#SETTING_AUTO_TIMESTAMP_INSERT_OVERWRITE} and
     * {@link Settings#SETTING_AUTO_TIMESTAMP_CACHE_ANNOTATIONS} from the datastore's configuration. Unless an
     * {@link AuditorAware} is set, or the listener is given an application context, it looks the auditor up in the
     * application context of the datastore when an entity is first saved.
     *
     * @param datastore The datastore whose entities the listener handles
     */
    public AutoTimestampEventListener(final Datastore datastore) {
        super(datastore);

        // Only a listener that is a Spring bean gets the @Value settings injected, and that is the listener of the
        // default connection only; the listener of every other connection, and of a tenant's database or schema,
        // takes them from the configuration of its datastore.
        applySettings(datastore);
        MappingContext mappingContext = datastore.getMappingContext();
        initForMappingContext(mappingContext);
    }

    protected AutoTimestampEventListener(final MappingContext mappingContext) {
        super(null);

        initForMappingContext(mappingContext);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // Spring calls this after it has set the bean's properties, so an auditor the listener was configured with
        // is kept when the context has none of its own.
        AuditorAware<?> contextAuditorAware = lookUpAuditorAware(applicationContext);
        synchronized (auditorAwareLock) {
            if (contextAuditorAware != null) {
                this.auditorAware = contextAuditorAware;
            }
            this.auditorAwareResolved = true;
        }
    }

    private void applySettings(Datastore datastore) {
        if (!(datastore instanceof ConnectionSourcesProvider<?, ?> connectionSourcesProvider)) {
            return;
        }
        ConnectionSources<?, ?> connectionSources = connectionSourcesProvider.getConnectionSources();
        PropertyResolver configuration = connectionSources != null ? connectionSources.getBaseConfiguration() : null;
        if (configuration != null) {
            insertOverwrite = configuration.getProperty(Settings.SETTING_AUTO_TIMESTAMP_INSERT_OVERWRITE, Boolean.class, insertOverwrite);
            cacheAutoTimestampAnnotations = configuration.getProperty(Settings.SETTING_AUTO_TIMESTAMP_CACHE_ANNOTATIONS, Boolean.class, cacheAutoTimestampAnnotations);
        }
    }

    private static AuditorAware<?> lookUpAuditorAware(ApplicationContext applicationContext) {
        try {
            return applicationContext.getBean(AuditorAware.class);
        } catch (BeansException ignore) {
            return null;
        }
    }

    /**
     * @return the application context of the datastore, once it is active, from the datastore or else from its
     * event publisher, or {@code null}
     */
    private ApplicationContext activeApplicationContextOfDatastore() {
        ApplicationContext applicationContext = null;
        if (datastore instanceof AbstractDatastore abstractDatastore) {
            applicationContext = abstractDatastore.getApplicationContext();
        }
        if (applicationContext == null && datastore != null) {
            ApplicationEventPublisher publisher = datastore.getApplicationEventPublisher();
            if (publisher instanceof ConfigurableApplicationContextEventPublisher contextEventPublisher) {
                applicationContext = contextEventPublisher.getApplicationContext();
            }
        }
        if (applicationContext instanceof ConfigurableApplicationContext configurableApplicationContext &&
                !configurableApplicationContext.isActive()) {
            return null;
        }
        return applicationContext;
    }

    protected void initForMappingContext(MappingContext mappingContext) {
        for (PersistentEntity persistentEntity : mappingContext.getPersistentEntities()) {
            storeDateCreatedAndLastUpdatedInfo(persistentEntity);
        }

        mappingContext.addMappingContextListener(this);
    }

    @Override
    protected void onPersistenceEvent(final AbstractPersistenceEvent event) {
        if (event.getEntity() == null) return;

        if (event.getEventType() == EventType.PreInsert) {
            beforeInsert(event.getEntity(), event.getEntityAccess());
        } else if (event.getEventType() == EventType.PreUpdate) {
            beforeUpdate(event.getEntity(), event.getEntityAccess());
        }
    }

    public boolean supportsEventType(@NonNull Class<? extends ApplicationEvent> eventType) {
        return PreInsertEvent.class.isAssignableFrom(eventType) || PreUpdateEvent.class.isAssignableFrom(eventType);
    }

    public boolean beforeInsert(PersistentEntity entity, EntityAccess ea) {
        final String name = entity.getName();
        initializeIfNecessary(entity, name);
        Class<?> dateCreatedType = null;
        Object timestamp = null;
        Set<String> props = getDateCreatedPropertyNames(name);
        if (props != null) {
            for (String prop : props) {
                if (insertOverwrite || ea.getPropertyValue(prop) == null) {
                    dateCreatedType = ea.getPropertyType(prop);
                    timestamp = timestampProvider.createTimestamp(dateCreatedType);
                    ea.setProperty(prop, timestamp);
                }
            }
        }
        props = getLastUpdatedPropertyNames(name);
        if (props != null) {
            for (String prop : props) {
                if (insertOverwrite || ea.getPropertyValue(prop) == null) {
                    Class<?> lastUpdateType = ea.getPropertyType(prop);
                    if (dateCreatedType == null || !lastUpdateType.isAssignableFrom(dateCreatedType)) {
                        timestamp = timestampProvider.createTimestamp(lastUpdateType);
                    }
                    ea.setProperty(prop, timestamp);
                }
            }
        }

        // Handle auditor fields
        AuditorAware<?> currentAuditorAware = getAuditorAware();
        if (currentAuditorAware != null) {
            Optional<?> currentAuditor = currentAuditorAware.getCurrentAuditor();
            if (currentAuditor.isPresent()) {
                Object auditor = currentAuditor.get();

                props = getCreatedByPropertyNames(name);
                if (props != null) {
                    for (String prop : props) {
                        if (insertOverwrite || ea.getPropertyValue(prop) == null) {
                            ea.setProperty(prop, auditor);
                        }
                    }
                }

                props = getUpdatedByPropertyNames(name);
                if (props != null) {
                    for (String prop : props) {
                        if (insertOverwrite || ea.getPropertyValue(prop) == null) {
                            ea.setProperty(prop, auditor);
                        }
                    }
                }
            }
        }

        return true;
    }

    private void initializeIfNecessary(PersistentEntity entity, String name) {
        if (uninitializedEntities.contains(name)) {
            storeDateCreatedAndLastUpdatedInfo(entity);
            uninitializedEntities.remove(name);
        }
    }

    public boolean beforeUpdate(PersistentEntity entity, EntityAccess ea) {
        Set<String> props = getLastUpdatedPropertyNames(entity.getName());
        if (props != null) {
            for (String prop : props) {
                Class<?> lastUpdateType = ea.getPropertyType(prop);
                Object timestamp = timestampProvider.createTimestamp(lastUpdateType);
                ea.setProperty(prop, timestamp);
            }
        }

        // Handle auditor fields
        AuditorAware<?> currentAuditorAware = getAuditorAware();
        if (currentAuditorAware != null) {
            Optional<?> currentAuditor = currentAuditorAware.getCurrentAuditor();
            if (currentAuditor.isPresent()) {
                Object auditor = currentAuditor.get();

                props = getUpdatedByPropertyNames(entity.getName());
                if (props != null) {
                    for (String prop : props) {
                        ea.setProperty(prop, auditor);
                    }
                }
            }
        }

        return true;
    }

    protected Set<String> getLastUpdatedPropertyNames(String entityName) {
        if (isDisabled(disabledLastUpdated, entityName)) {
            return null;
        }
        Optional<Set<String>> properties = entitiesWithLastUpdated.get(entityName);
        return properties == null ? null : properties.orElse(null);
    }

    protected Set<String> getDateCreatedPropertyNames(String entityName) {
        if (isDisabled(disabledDateCreated, entityName)) {
            return null;
        }
        Optional<Set<String>> properties = entitiesWithDateCreated.get(entityName);
        return properties == null ? null : properties.orElse(null);
    }

    protected Set<String> getCreatedByPropertyNames(String entityName) {
        Optional<Set<String>> properties = entitiesWithCreatedBy.get(entityName);
        return properties == null ? null : properties.orElse(null);
    }

    protected Set<String> getUpdatedByPropertyNames(String entityName) {
        Optional<Set<String>> properties = entitiesWithUpdatedBy.get(entityName);
        return properties == null ? null : properties.orElse(null);
    }

    protected void storeDateCreatedAndLastUpdatedInfo(PersistentEntity persistentEntity) {
        if (persistentEntity.isInitialized()) {
            ClassMapping<?> classMapping = persistentEntity.getMapping();
            Entity<?> mappedForm = classMapping.getMappedForm();
            if (mappedForm == null || mappedForm.isAutoTimestamp()) {
                for (PersistentProperty<?> property : persistentEntity.getPersistentProperties()) {
                    if (property.getName().equals(LAST_UPDATED_PROPERTY)) {
                        storeTimestampAvailability(entitiesWithLastUpdated, persistentEntity, property);
                    } else if (property.getName().equals(DATE_CREATED_PROPERTY)) {
                        storeTimestampAvailability(entitiesWithDateCreated, persistentEntity, property);
                    } else {
                        AuditMetadataType auditMetadataType = AuditMetadataUtils.getAuditMetadataType(property, cacheAutoTimestampAnnotations);
                        if (auditMetadataType == AuditMetadataType.CREATED) {
                            storeTimestampAvailability(entitiesWithDateCreated, persistentEntity, property);
                        } else if (auditMetadataType == AuditMetadataType.UPDATED) {
                            storeTimestampAvailability(entitiesWithLastUpdated, persistentEntity, property);
                        } else if (auditMetadataType == AuditMetadataType.CREATED_BY) {
                            storeAuditorAvailability(entitiesWithCreatedBy, persistentEntity, property);
                        } else if (auditMetadataType == AuditMetadataType.UPDATED_BY) {
                            storeAuditorAvailability(entitiesWithUpdatedBy, persistentEntity, property);
                        }
                    }
                }
            }
        } else {
            uninitializedEntities.add(persistentEntity.getName());
        }
    }

    protected void storeTimestampAvailability(Map<String, Optional<Set<String>>> timestampAvailabilityMap, PersistentEntity persistentEntity, PersistentProperty<?> property) {
        if (property != null && timestampProvider.supportsCreating(property.getType())) {
            timestampAvailabilityMap.computeIfAbsent(persistentEntity.getName(), k -> Optional.of(new HashSet<>()))
                    .ifPresent(propertyNames -> propertyNames.add(property.getName()));
        }
    }

    protected void storeAuditorAvailability(Map<String, Optional<Set<String>>> auditorAvailabilityMap, PersistentEntity persistentEntity, PersistentProperty<?> property) {
        if (property != null) {
            auditorAvailabilityMap.computeIfAbsent(persistentEntity.getName(), k -> Optional.of(new HashSet<>()))
                    .ifPresent(propertyNames -> propertyNames.add(property.getName()));
        }
    }

    public void persistentEntityAdded(PersistentEntity entity) {
        storeDateCreatedAndLastUpdatedInfo(entity);
    }

    public TimestampProvider getTimestampProvider() {
        return timestampProvider;
    }

    public void setTimestampProvider(TimestampProvider timestampProvider) {
        this.timestampProvider = timestampProvider;
    }

    /**
     * @return the auditor that the auditor properties are set from: the one that was set, the one of the application
     * context the listener was given, or else the one of the datastore's application context, which is looked up
     * once that context is active; {@code null} when there is none
     */
    public AuditorAware<?> getAuditorAware() {
        if (!auditorAwareResolved) {
            ApplicationContext applicationContext = activeApplicationContextOfDatastore();
            if (applicationContext != null) {
                // The lookup runs outside the lock, and what it finds is kept only if no auditor was set meanwhile.
                AuditorAware<?> contextAuditorAware = lookUpAuditorAware(applicationContext);
                synchronized (auditorAwareLock) {
                    if (!auditorAwareResolved) {
                        auditorAware = contextAuditorAware;
                        auditorAwareResolved = true;
                    }
                }
            }
        }
        return auditorAware;
    }

    public void setAuditorAware(AuditorAware<?> auditorAware) {
        synchronized (auditorAwareLock) {
            this.auditorAware = auditorAware;
            this.auditorAwareResolved = true;
        }
    }

    private static boolean isDisabled(final ThreadLocal<DisabledTimestamps> disabledTimestamps, final String entityName) {
        DisabledTimestamps disabled = disabledTimestamps.get();
        return disabled != null && disabled.isDisabled(entityName);
    }

    private static DisabledTimestamps getOrCreateDisabled(final ThreadLocal<DisabledTimestamps> disabledTimestamps) {
        DisabledTimestamps disabled = disabledTimestamps.get();
        if (disabled == null) {
            disabled = new DisabledTimestamps();
            disabledTimestamps.set(disabled);
        }
        return disabled;
    }

    private static void removeIfEmpty(final ThreadLocal<DisabledTimestamps> disabledTimestamps, final DisabledTimestamps disabled) {
        if (disabled.isEmpty()) {
            disabledTimestamps.remove();
        }
    }

    private static void runWithAllDisabled(final ThreadLocal<DisabledTimestamps> disabledTimestamps, final Runnable runnable) {
        DisabledTimestamps disabled = getOrCreateDisabled(disabledTimestamps);
        disabled.allDisabledDepth++;
        try {
            runnable.run();
        } finally {
            disabled.allDisabledDepth--;
            removeIfEmpty(disabledTimestamps, disabled);
        }
    }

    private static void runWithDisabled(final ThreadLocal<DisabledTimestamps> disabledTimestamps, final List<Class<?>> classes, final Runnable runnable) {
        // only the names this scope newly disables may be re-enabled on exit; a name already
        // disabled by an enclosing scope on this thread must survive this scope's finally
        List<String> added = new ArrayList<>(classes.size());
        DisabledTimestamps disabled = getOrCreateDisabled(disabledTimestamps);
        try {
            for (Class<?> clazz : classes) {
                String entityName = clazz.getName();
                if (disabled.entityNames.add(entityName)) {
                    added.add(entityName);
                }
            }
            runnable.run();
        } finally {
            added.forEach(disabled.entityNames::remove);
            removeIfEmpty(disabledTimestamps, disabled);
        }
    }

    /**
     * Temporarily disables the last updated processing during the execution of the runnable.
     * The processing is only disabled for the calling thread; concurrently executing threads
     * are unaffected.
     *
     * @param runnable The code to execute while the last updated listener is disabled
     */
    public void withoutLastUpdated(final Runnable runnable) {
        runWithAllDisabled(disabledLastUpdated, runnable);
    }

    /**
     * Temporarily disables the last updated processing only on the provided classes during the
     * execution of the runnable. The processing is only disabled for the calling thread;
     * concurrently executing threads are unaffected.
     *
     * @param classes Which classes to disable the last updated processing for
     * @param runnable The code to execute while the last updated listener is disabled
     */
    public void withoutLastUpdated(final List<Class<?>> classes, final Runnable runnable) {
        runWithDisabled(disabledLastUpdated, classes, runnable);
    }

    /**
     * Temporarily disables the last updated processing only on the provided class during the
     * execution of the runnable. The processing is only disabled for the calling thread;
     * concurrently executing threads are unaffected.
     *
     * @param clazz Which class to disable the last updated processing for
     * @param runnable The code to execute while the last updated listener is disabled
     */
    public void withoutLastUpdated(final Class<?> clazz, final Runnable runnable) {
        ArrayList<Class<?>> list = new ArrayList<>(1);
        list.add(clazz);
        withoutLastUpdated(list, runnable);
    }

    /**
     * Temporarily disables the date created processing during the execution of the runnable.
     * The processing is only disabled for the calling thread; concurrently executing threads
     * are unaffected.
     *
     * @param runnable The code to execute while the date created listener is disabled
     */
    public void withoutDateCreated(final Runnable runnable) {
        runWithAllDisabled(disabledDateCreated, runnable);
    }

    /**
     * Temporarily disables the date created processing only on the provided classes during the
     * execution of the runnable. The processing is only disabled for the calling thread;
     * concurrently executing threads are unaffected.
     *
     * @param classes Which classes to disable the date created processing for
     * @param runnable The code to execute while the date created listener is disabled
     */
    public void withoutDateCreated(final List<Class<?>> classes, final Runnable runnable) {
        runWithDisabled(disabledDateCreated, classes, runnable);
    }

    /**
     * Temporarily disables the date created processing only on the provided class during the
     * execution of the runnable. The processing is only disabled for the calling thread;
     * concurrently executing threads are unaffected.
     *
     * @param clazz Which class to disable the date created processing for
     * @param runnable The code to execute while the date created listener is disabled
     */
    public void withoutDateCreated(final Class<?> clazz, final Runnable runnable) {
        ArrayList<Class<?>> list = new ArrayList<>(1);
        list.add(clazz);
        withoutDateCreated(list, runnable);
    }

    /**
     * Temporarily disables the timestamp processing during the execution of the runnable.
     * The processing is only disabled for the calling thread; concurrently executing threads
     * are unaffected.
     *
     * @param runnable The code to execute while the timestamp listeners are disabled
     */
    public void withoutTimestamps(final Runnable runnable) {
        withoutDateCreated(() -> withoutLastUpdated(runnable));
    }

    /**
     * Temporarily disables the timestamp processing only on the provided classes during the
     * execution of the runnable. The processing is only disabled for the calling thread;
     * concurrently executing threads are unaffected.
     *
     * @param classes Which classes to disable the timestamp processing for
     * @param runnable The code to execute while the timestamp listeners are disabled
     */
    public void withoutTimestamps(final List<Class<?>> classes, final Runnable runnable) {
        withoutDateCreated(classes, () -> withoutLastUpdated(classes, runnable));
    }

    /**
     * Temporarily disables the timestamp processing during the execution of the runnable.
     * The processing is only disabled for the calling thread; concurrently executing threads
     * are unaffected.
     *
     * @param clazz Which class to disable the timestamp processing for
     * @param runnable The code to execute while the timestamp listeners are disabled
     */
    public void withoutTimestamps(final Class<?> clazz, final Runnable runnable) {
        withoutDateCreated(clazz, () -> withoutLastUpdated(clazz, runnable));
    }

    /**
     * Captures the timestamp suppression state currently active on the calling thread, so that
     * it can be re-applied on another thread with
     * {@link #withTimestampSuppression(TimestampSuppression, Runnable)}. Because the
     * {@code withoutTimestamps}, {@code withoutDateCreated} and {@code withoutLastUpdated}
     * windows only affect the calling thread, work handed off to other threads (an executor,
     * a reactive scheduler) is not suppressed unless the captured state is propagated to the
     * executing thread.
     *
     * <p>The returned snapshot is immutable and may be applied on any number of threads,
     * concurrently, including after the originating window has closed.
     *
     * @return the suppression state active on the calling thread at the time of the call
     */
    public TimestampSuppression captureTimestampSuppression() {
        return new TimestampSuppression(disabledDateCreated.get(), disabledLastUpdated.get());
    }

    /**
     * Executes the runnable with the given captured suppression state installed on the calling
     * thread, restoring the thread's previous suppression state afterwards, even when the
     * runnable throws. The snapshot replaces any suppression already active on the calling
     * thread for the duration of the runnable.
     *
     * @param suppression The state captured by {@link #captureTimestampSuppression()}
     * @param runnable The code to execute with the captured suppression state applied
     */
    public void withTimestampSuppression(final TimestampSuppression suppression, final Runnable runnable) {
        Objects.requireNonNull(suppression, "suppression must not be null");
        DisabledTimestamps previousDateCreated = installSuppression(disabledDateCreated, suppression.dateCreated);
        DisabledTimestamps previousLastUpdated = installSuppression(disabledLastUpdated, suppression.lastUpdated);
        try {
            runnable.run();
        } finally {
            restoreSuppression(disabledLastUpdated, previousLastUpdated);
            restoreSuppression(disabledDateCreated, previousDateCreated);
        }
    }

    private static DisabledTimestamps installSuppression(final ThreadLocal<DisabledTimestamps> disabledTimestamps, final DisabledTimestamps snapshot) {
        DisabledTimestamps previous = disabledTimestamps.get();
        if (snapshot == null) {
            disabledTimestamps.remove();
        } else {
            // install a copy: nested scopes inside the runnable mutate their thread's instance,
            // which must not corrupt the shared snapshot or other threads applying it
            disabledTimestamps.set(new DisabledTimestamps(snapshot));
        }
        return previous;
    }

    private static void restoreSuppression(final ThreadLocal<DisabledTimestamps> disabledTimestamps, final DisabledTimestamps previous) {
        if (previous == null) {
            disabledTimestamps.remove();
        } else {
            disabledTimestamps.set(previous);
        }
    }

    /**
     * An immutable snapshot of a thread's temporary timestamp suppression state, captured with
     * {@link AutoTimestampEventListener#captureTimestampSuppression()} and applied on another
     * thread with {@link AutoTimestampEventListener#withTimestampSuppression(TimestampSuppression, Runnable)}.
     */
    public static final class TimestampSuppression {

        private final DisabledTimestamps dateCreated;

        private final DisabledTimestamps lastUpdated;

        private TimestampSuppression(DisabledTimestamps dateCreated, DisabledTimestamps lastUpdated) {
            this.dateCreated = dateCreated == null || dateCreated.isEmpty() ? null : new DisabledTimestamps(dateCreated);
            this.lastUpdated = lastUpdated == null || lastUpdated.isEmpty() ? null : new DisabledTimestamps(lastUpdated);
        }

    }

    /**
     * The entities for which timestamp processing is temporarily disabled on the current thread.
     * All entities are disabled while {@code allDisabledDepth} is greater than zero; otherwise
     * only the entities named in {@code entityNames} are disabled.
     */
    private static final class DisabledTimestamps {

        private int allDisabledDepth;

        private final Set<String> entityNames = new HashSet<>();

        private DisabledTimestamps() {
        }

        private DisabledTimestamps(DisabledTimestamps source) {
            allDisabledDepth = source.allDisabledDepth;
            entityNames.addAll(source.entityNames);
        }

        private boolean isDisabled(String entityName) {
            return allDisabledDepth > 0 || entityNames.contains(entityName);
        }

        private boolean isEmpty() {
            return allDisabledDepth == 0 && entityNames.isEmpty();
        }

    }

}
