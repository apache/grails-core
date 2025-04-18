package org.grails.gorm.graphql.fetcher

import graphql.language.Field
import graphql.language.SelectionSet
import graphql.schema.DataFetchingEnvironment
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.HibernateSpec
import org.grails.gorm.graphql.domain.general.GeneralPackage
import org.grails.gorm.graphql.domain.general.tomany.ToMany
import org.grails.gorm.graphql.domain.general.toone.EmbedOne
import org.grails.gorm.graphql.domain.general.toone.HasOne
import org.grails.gorm.graphql.domain.general.toone.ManyToOne
import org.grails.gorm.graphql.domain.general.toone.ToOne
import org.grails.gorm.graphql.testing.MockDataFetchingEnvironment

class DefaultGormDataFetcherSpec extends HibernateSpec {

    Package getPackage() {
        GeneralPackage.getPackage()
    }

    void "test should not join a toOne when the only field requested is ID"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(ToOne.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                one {
                    id
                }
            }
        }.empty
    }

    void "test should not join a manyToOne when the only field requested is ID"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(ManyToOne.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                toMany {
                    id
                }
            }
        }.empty
    }

    void "test should join a manyToOne when properties other than the ID are requested"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(ManyToOne.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                toMany {
                    strings
                }
            }
        } == ['toMany', 'toMany.strings'] as Set
    }

    void "test should join a toOne when more than the ID is requested"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(ToOne.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                one {
                    id
                    version
                }
            }
        } == ['one'] as Set
    }

    void "test should join a toOne with skipCollection true"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(ToOne.gormPersistentEntity, true)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                one {
                    id
                    version
                }
            }
        } == ['one'] as Set
    }

    void "test should not join a toOne when the field is an enum"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(ToOne.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                anEnum
            }
        }.empty
    }

    void "test should join a hasOne even if only the ID is requested"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(HasOne.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                one {
                    id
                }
            }
        } == ['one'] as Set
    }

    void "test should not join an embedded domain"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(EmbedOne.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                embed {
                    one { //to one in embedded
                        id //only requesting id so no need to join
                    }
                }
            }
        }.empty
    }


    void "test should join an embedded domain toOne property"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(EmbedOne.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                embed {
                    one { //to one in embedded
                        version //requesting non id so 'embed.one' should be added to the join list
                    }
                }
            }
        } == ['embed.one'] as Set
    }

    void "test should join an embedded domain toMany property"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(EmbedOne.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                embed {
                    many { //to many in embedded
                        id //should be added to join because foreign key is in a join table
                    }
                }
            }
        } == ['embed.many'] as Set
    }

    void "test should join an embedded domain with multiple properties"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(EmbedOne.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                embed {
                    one { //to one in embedded
                        version //requesting non id so 'embed.one' should be added to the join list
                    }
                    many { //to many in embedded
                        id //should be added to join because foreign key is in a join table
                    }
                }
            }
        } == ['embed.one', 'embed.many'] as Set
    }

    void "test should not join an embedded pogo"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(EmbedOne.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                embedNonEntity {
                    name
                }
            }
        }.empty
    }

    void "test toMany basic should join"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(ToMany.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                strings
            }
        } == ['strings'] as Set
    }

    void "test toMany basic enums should join"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(ToMany.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                enums
            }
        } == ['enums'] as Set
    }

    void "test unidirectional toMany should join"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(ToMany.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                many {
                    id
                }
            }
        } == ['many'] as Set
    }

    void "test unidirectional toMany should not join sub association if only ID requested"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(ToMany.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                many {
                    one {
                        id
                    }
                }
            }
        } == ['many'] as Set
    }

    void "test unidirectional toMany should join sub association"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(ToMany.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                many {
                    one {
                        id
                        version
                    }
                }
            }
        } == ['many', 'many.one'] as Set

        fetcher.joinProperties { ->
            someQueryName {
                many {
                    one {
                        version
                    }
                }
            }
        } == ['many', 'many.one'] as Set
    }

    void "test bidirectional toMany should join"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(ToMany.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                manyToOne {
                    id
                }
            }
        } == ['manyToOne'] as Set
    }

    void "test bidirectional toMany should not join sub association if only ID requested"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(ToMany.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                manyToOne {
                    one {
                        id
                    }
                }
            }
        } == ['manyToOne'] as Set
    }

    void "test bidirectional toMany should not join owning side association"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(ToMany.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                manyToOne {
                    toMany {
                        strings
                    }
                }
            }
        } == ['manyToOne', 'strings'] as Set

        fetcher.joinProperties { ->
            someQueryName {
                manyToOne {
                    toMany {
                        id
                    }
                }
            }
        } == ['manyToOne'] as Set
    }

    void "test bidirectional toMany should join sub association"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(ToMany.gormPersistentEntity)

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                manyToOne {
                    one {
                        id
                        version
                    }
                }
            }
        } == ['manyToOne', 'manyToOne.one'] as Set

        fetcher.joinProperties { ->
            someQueryName {
                manyToOne {
                    one {
                        version
                    }
                }
            }
        } == ['manyToOne', 'manyToOne.one'] as Set
    }

    void "test propertyName is prepended to all keys"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(ToMany.gormPersistentEntity, 'foo')

        expect:
        fetcher.joinProperties { ->
            someQueryName {
                manyToOne {
                    one {
                        id
                        version
                    }
                }
            }
        } == ['foo', 'foo.manyToOne', 'foo.manyToOne.one'] as Set

        fetcher.joinProperties { ->
            someQueryName {
                manyToOne {
                    one {
                        version
                    }
                }
            }
        } == ['foo', 'foo.manyToOne', 'foo.manyToOne.one'] as Set
    }

    void "test toMany properties are excluded with skipCollections with projection"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(ToMany.gormPersistentEntity, 'foo', true)

        expect: "the projection name is still included"
        fetcher.joinProperties { ->
            someQueryName {
                many {
                    id
                }
                manyToOne {
                    id
                }
                manyToMany {
                    id
                }
                enums
                strings
            }
        } == ['foo'] as Set
    }

    void "test toMany properties are excluded with skipCollections"() {
        given:
        TestingFetcher fetcher = new TestingFetcher(ToMany.gormPersistentEntity, true)

        expect: "the projection name is still included"
        fetcher.joinProperties { ->
            someQueryName {
                many {
                    id
                }
                manyToOne {
                    id
                }
                manyToMany {
                    id
                }
                enums
                strings
            }
        }.empty
    }

    class TestingFetcher extends DefaultGormDataFetcher {

        boolean skipCollections

        TestingFetcher(PersistentEntity entity, boolean skipCollections = false) {
            super(entity)
            this.skipCollections = skipCollections
        }

        TestingFetcher(PersistentEntity entity, String projectionName, boolean skipCollections = false) {
            super(entity, projectionName)
            this.skipCollections = skipCollections
        }

        Set<String> joinProperties(Closure c) {
            Map args = getFetchArguments(new MockDataFetchingEnvironment(fields: new MockFieldBuilder().build(c)), skipCollections)

            if (args.containsKey('fetch')) {
                args.fetch.keySet()
            }
            else {
                Collections.emptySet()
            }
        }

        @Override
        Object get(DataFetchingEnvironment environment) {
            return null
        }
    }

}

class MockFieldBuilder {

    List<Field> fields = []

    private Field.Builder newField(String name) {
        Field.newField(name)
    }

    List<Field> build(Closure closure) {
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.delegate = this
        closure.call()
        fields
    }

    def methodMissing(String name, args) {
        Field.Builder field = newField(name)
        if (args.length == 1 && args[0] instanceof Closure) {
            final SelectionSet selectionSet = SelectionSet.newSelectionSet(new MockFieldBuilder().build((Closure) args[0])).build()
            field.selectionSet(selectionSet)
        }
        fields.add(field.build())
    }

    def propertyMissing(String name) {
        fields.add(newField(name).build())
    }

}
