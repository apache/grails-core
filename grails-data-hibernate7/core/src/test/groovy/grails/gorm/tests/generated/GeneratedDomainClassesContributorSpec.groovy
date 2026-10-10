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
package grails.gorm.tests.generated

import grails.persistence.Entity
import org.hibernate.boot.ResourceStreamLocator
import org.hibernate.boot.spi.AdditionalMappingContributions
import org.hibernate.boot.spi.AdditionalMappingContributor
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.Collection
import org.hibernate.mapping.PersistentClass
import spock.lang.Specification

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore

/**
 * A contributor that runs after the domain binder, such as Hibernate Envers building the audit mappings, reads the entities
 * bound so far. The generated classes are bound when the domain binder runs, with their second passes, so that such a
 * contributor finds the same bound model whether the domain binder or the generated classes bound the entities.
 */
class GeneratedDomainClassesContributorSpec extends Specification {

    HibernateDatastore datastore

    def setup() {
        RecordingMappingContributor.recording = true
        RecordingMappingContributor.seen.clear()
    }

    def cleanup() {
        RecordingMappingContributor.recording = false
        datastore?.close()
    }

    private HibernateDatastore boot(boolean generated) {
        datastore = new HibernateDatastore(
                DatastoreUtils.createPropertyResolver([
                        'dataSource.url'                  : "jdbc:h2:mem:gdcContributor${generated};LOCK_TIMEOUT=10000".toString(),
                        'dataSource.dbCreate'             : 'create-drop',
                        'hibernate.generatedDomainClasses': generated,
                ]), GdcContributorShelf, GdcContributorItem)
    }

    def "a contributor sees the bound entities, with their collections complete, in #mode mode"() {
        when:
        boot(generated)

        then: 'the entities are bound under the domain class names'
        RecordingMappingContributor.seen.keySet() == [GdcContributorShelf.name, GdcContributorItem.name].toSet()

        and: 'the collection of the owner has its element and key'
        RecordingMappingContributor.seen[GdcContributorShelf.name] == ['items:true']

        where:
        generated | mode
        true      | 'generated'
        false     | 'binder'
    }
}

/**
 * Records, while {@link #recording} is set, the entities bound and the completeness of their collections when it runs.
 */
class RecordingMappingContributor implements AdditionalMappingContributor {

    static volatile boolean recording
    static final Map<String, List<String>> seen = Collections.synchronizedMap(new LinkedHashMap<String, List<String>>())

    @Override
    String getContributorName() {
        return 'recording'
    }

    @Override
    void contribute(
            AdditionalMappingContributions contributions,
            InFlightMetadataCollector metadata,
            ResourceStreamLocator resourceStreamLocator,
            MetadataBuildingContext buildingContext) {
        if (!recording) {
            return
        }
        for (PersistentClass entity : metadata.entityBindings) {
            List<String> collections = []
            for (Object property : entity.declaredProperties) {
                Object value = ((org.hibernate.mapping.Property) property).value
                if (value instanceof Collection) {
                    collections << "${((org.hibernate.mapping.Property) property).name}:${((Collection) value).element != null}".toString()
                }
            }
            seen.put(entity.entityName, collections)
        }
    }
}

@Entity
class GdcContributorShelf {
    String name
    static hasMany = [items: GdcContributorItem]
}

@Entity
class GdcContributorItem {
    String label
    static belongsTo = [shelf: GdcContributorShelf]
}
