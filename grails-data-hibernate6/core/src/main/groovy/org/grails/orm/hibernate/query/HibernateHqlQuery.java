package org.grails.orm.hibernate.query;

import jakarta.persistence.FlushModeType;
import org.apache.groovy.parser.antlr4.util.StringUtils;
import org.grails.datastore.gorm.finders.DynamicFinder;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.event.PostQueryEvent;
import org.grails.datastore.mapping.query.event.PreQueryEvent;
import org.grails.orm.hibernate.HibernateDatastore;
import org.grails.orm.hibernate.HibernateSession;
import org.grails.orm.hibernate.exceptions.GrailsQueryException;
import org.hibernate.FlushMode;
import org.hibernate.SessionFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * A query implementation for HQL queries
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class HibernateHqlQuery extends Query {
    private org.hibernate.query.Query query;

    public HibernateHqlQuery(Session session, PersistentEntity entity, org.hibernate.query.Query query) {
        super(session, entity);
        this.query = query;
    }

    @Override
    protected void flushBeforeQuery() {
        // do nothing, hibernate handles this
    }

    @Override
    protected List executeQuery(PersistentEntity entity, Junction criteria) {
        Datastore datastore = getSession().getDatastore();
        ApplicationEventPublisher applicationEventPublisher = datastore.getApplicationEventPublisher();
        PreQueryEvent preQueryEvent = new PreQueryEvent(datastore, this);
        applicationEventPublisher.publishEvent(preQueryEvent);

        if(uniqueResult) {
            query.setMaxResults(1);
            List results = query.list();
            applicationEventPublisher.publishEvent(new PostQueryEvent(datastore, this, results));
            return results;
        }
        else {

            List results = query.list();
            applicationEventPublisher.publishEvent(new PostQueryEvent(datastore, this, results));
            return results;
        }
    }


    public static HibernateHqlQuery createHqlQuery(
            org.hibernate.Session session
            , HibernateDatastore dataStore
            , SessionFactory sessionFactory
            , PersistentEntity persistentEntity
            , String sqlString
            , boolean isNative,
            boolean isUpdate) {

        // Normalize only for HQL (not for native SQL)
        String hqlToUse = isNative ? sqlString : normalizeNonAliasedSelect(sqlString);
        var clazz = getTarget(hqlToUse, persistentEntity.getJavaClass());
        org.hibernate.query.Query q = null;
        if (StringUtils.isEmpty(hqlToUse)) {
           q = session.createQuery("from " + clazz.getName(), clazz);
        } else if (isUpdate) {
            q = session.createQuery(hqlToUse);
        } else {
            q = isNative ? session.createNativeQuery(hqlToUse, clazz) : session.createQuery(hqlToUse, clazz);
        }
        var hibernateSession = new HibernateSession( dataStore, sessionFactory);
        HibernateHqlQuery hibernateHqlQuery = new HibernateHqlQuery(hibernateSession, null, q);
        hibernateHqlQuery.setFlushMode(session.getHibernateFlushMode());
        return hibernateHqlQuery;
    }

    /**
     * Determine the number of top-level projections in the HQL query.
     * Returns 0 if there is no explicit SELECT clause (implicit entity projection),
     * 1 if there is a single top-level projection expression (including constructs like DISTINCT x or NEW map(...)),
     * and 2 if there are two or more top-level projection expressions (e.g. "select a, b from ...").
     *
     * Notes:
     * - Commas within parentheses or string literals are ignored.
     * - Constructor expressions like "new map(a as n, b as m)" count as a single projection.
     * - Aggregate and function calls with commas in their argument lists are handled by parentheses tracking.
     */
    static int countHqlProjections(CharSequence hql) {
        if (hql == null) return 0;
        String s = hql.toString().trim();
        if (s.isEmpty()) return 0;
        // Find select and from in a case-insensitive way
        String lower = s.toLowerCase();
        int selectIdx = lower.indexOf("select ");
        if (selectIdx < 0) {
            // no explicit select -> implicit single entity projection following "from"
            return 0;
        }
        // Ensure this select occurs before the corresponding from
        int fromIdx = lower.indexOf(" from ", selectIdx);
        if (fromIdx < 0) {
            // malformed or incomplete query; treat as one projection if select exists
            fromIdx = s.length();
        }
        int selectStart = selectIdx + "select".length();
        // Extract the select clause between 'select' and 'from'
        String sel = s.substring(selectStart, fromIdx).trim();
        if (sel.isEmpty()) return 0;
        // Strip leading DISTINCT/ALL keywords
        String selLower = sel.toLowerCase();
        if (selLower.startsWith("distinct ")) {
            sel = sel.substring("distinct ".length()).trim();
            selLower = sel.toLowerCase();
        } else if (selLower.startsWith("all ")) {
            sel = sel.substring("all ".length()).trim();
            selLower = sel.toLowerCase();
        }
        // Now count top-level commas ignoring those within parentheses and string literals
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int topLevelCommas = 0;
        char singleQuote = '\'';
        char doubleQuote = '"';
        char leftParen = '(';        // Left parenthesis
        char rightParen = ')';       // Right parenthesis
        char comma = ',';            // Comma



        for (int i = 0; i < sel.length(); i++) {
            char c = sel.charAt(i);
            // handle quotes (simple handling: toggle on quote not escaped by another same quote)
            if (!inDoubleQuote && c ==singleQuote) {
                // handle doubled single quotes inside strings
                if (inSingleQuote) {
                    if (i + 1 < sel.length() && sel.charAt(i + 1) == singleQuote) {
                        i++ ;// skip escaped quote
                        continue;
                    } else {
                        inSingleQuote = false;
                        continue;
                    }
                } else {
                    inSingleQuote = true;
                    continue;
                }
            }
            if (!inSingleQuote && c == doubleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (inSingleQuote || inDoubleQuote) continue;
            if (c == leftParen) { depth++ ; continue ;}
            if (c == rightParen && depth > 0) { depth-- ; continue; }
            if (c == comma && depth == 0) { topLevelCommas++; }
        }
        if (topLevelCommas == 0) return 1;
        return 2;
    }


    static Class getTarget(CharSequence hql, Class clazz) {
        // Normalize non-aliased queries to an aliased form, then reuse the logic
        String normalized = normalizeNonAliasedSelect(hql == null ? null : hql.toString());

        int projections = countHqlProjections(normalized);
        switch(projections) {
            case 0:
                return clazz; // No explicit SELECT - implicit entity projection
            case 1:
                // Single projection - property vs entity
                if (isPropertyProjection(normalized)) {
                    return Object.class; // Scalar result
                } else {
                    return clazz;        // Entity result
                }
            default:
                return Object[].class; // Multiple projections
        }
    }

    /**
     * If the HQL query has no alias in the FROM clause, inject a synthetic alias ("e")
     * and qualify the SELECT projection accordingly. Only SELECT is adjusted; the rest
     * of the query is left intact (WHERE/JOIN conditions are valid as-is in HQL).
     *
     * Examples:
     *   "select nameType from NameType where lower(nameType)=:nameType"
     *      -> "select e.nameType from NameType e where lower(nameType)=:nameType"
     *   "select NameType from NameType"
     *      -> "select e from NameType e"
     *   "select name from Person"
     *      -> "select e.name from Person e"
     */
    private static String normalizeNonAliasedSelect(String hql) {
        if (hql == null) return null;
        String s = hql.trim();
        if (s.isEmpty()) return s;

        String lower = s.toLowerCase();
        int selectIdx = lower.indexOf("select ");
        if (selectIdx < 0) {
            // No explicit select -> nothing to normalize for target detection
            return s;
        }
        int fromIdx = lower.indexOf(" from ", selectIdx);
        if (fromIdx < 0) {
            // Malformed or incomplete; leave as-is
            return s;
        }

        // Extract SELECT clause original text
        int selectStart = selectIdx + "select ".length();
        String selectClauseOrig = s.substring(selectStart, fromIdx).trim();
        String selectClauseLower = lower.substring(selectStart, fromIdx).trim();

        // Extract FROM head to detect alias: "from Entity [as] alias ..."
        int afterFrom = fromIdx + " from ".length();
        int endOfFromHead = afterFrom;
        // read entity name token
        while (endOfFromHead < s.length()) {
            char ch = s.charAt(endOfFromHead);
            if (Character.isWhitespace(ch)) break;
            endOfFromHead++;
        }
        String entityName = s.substring(afterFrom, endOfFromHead).trim();
        if (entityName.isEmpty()) return s;

        // Skip spaces
        int cur = endOfFromHead;
        while (cur < s.length() && Character.isWhitespace(s.charAt(cur))) cur++;

        // Optional "as"
        boolean hasAlias = false;
        String alias = null;
        int aliasStart = cur;
        if (cur + 2 < s.length()) {
            String nextWord = s.substring(cur, Math.min(cur + 2, s.length())).toLowerCase();
        }
        if (cur + 2 < s.length() && s.substring(cur, Math.min(cur + 2, s.length())).equalsIgnoreCase("as")) {
            cur += 2;
            while (cur < s.length() && Character.isWhitespace(s.charAt(cur))) cur++;
        }

        // Try to read alias token unless we hit a clause keyword
        int aliasEnd = cur;
        while (aliasEnd < s.length()) {
            char ch = s.charAt(aliasEnd);
            if (Character.isWhitespace(ch)) break;
            aliasEnd++;
        }
        String maybeAlias = (cur < aliasEnd) ? s.substring(cur, aliasEnd) : "";

        // Keywords that indicate no alias present
        String maybeAliasLower = maybeAlias.toLowerCase();
        boolean isClauseKeyword = maybeAliasLower.isEmpty()
                || maybeAliasLower.equals("where")
                || maybeAliasLower.equals("join")
                || maybeAliasLower.equals("left")
                || maybeAliasLower.equals("right")
                || maybeAliasLower.equals("inner")
                || maybeAliasLower.equals("outer")
                || maybeAliasLower.equals("group")
                || maybeAliasLower.equals("order")
                || maybeAliasLower.equals("having");

        if (!isClauseKeyword) {
            hasAlias = true;
            alias = maybeAlias;
        }

        if (hasAlias) {
            // Already aliased; no normalization needed
            return s;
        }

        // Inject synthetic alias
        String syntheticAlias = "e";

        // Adjust SELECT clause:
        // Preserve DISTINCT/ALL prefix
        String prefix = "";
        String projOrig = selectClauseOrig;
        String projLower = selectClauseLower;
        if (projLower.startsWith("distinct ")) {
            prefix = selectClauseOrig.substring(0, selectClauseOrig.length() - projOrig.substring("distinct ".length()).length());
            projOrig = selectClauseOrig.substring("distinct ".length()).trim();
            projLower = projLower.substring("distinct ".length()).trim();
            prefix = "distinct ";
        } else if (projLower.startsWith("all ")) {
            prefix = "all ";
            projOrig = selectClauseOrig.substring("all ".length()).trim();
            projLower = projLower.substring("all ".length()).trim();
        }

        String adjustedProjection = projOrig;
        // If projection equals entity name -> "select e"
        if (projLower.equals(entityName.toLowerCase())) {
            adjustedProjection = syntheticAlias;
        }
        // If projection has no dot, treat as property -> qualify with alias
        else if (!projLower.contains("(") && !projLower.contains(".") && !projLower.startsWith("new ")) {
            adjustedProjection = syntheticAlias + "." + projOrig;
        }
        // else leave as-is (functions, constructor expr, already-qualified, etc.)

        // Build normalized SELECT ... FROM ... (inject alias right after entity name)
        StringBuilder out = new StringBuilder();
        out.append("select ").append(prefix).append(adjustedProjection);
        // Original FROM and the rest
        String tail = s.substring(fromIdx); // starts with " from "
        // Insert alias after entity name in tail
        StringBuilder tailOut = new StringBuilder();
        tailOut.append(" from ").append(entityName).append(" ").append(syntheticAlias);
        // Append the remainder after entity name in the FROM head
        tailOut.append(s.substring(endOfFromHead));
        out.append(tailOut);
        return out.toString();
    }

    private static boolean isPropertyProjection(CharSequence hql) {
        String s = hql.toString().toLowerCase().trim();
        int selectIdx = s.indexOf("select ");
        if (selectIdx < 0) return false;

        int fromIdx = s.indexOf(" from ", selectIdx);
        if (fromIdx < 0) fromIdx = s.length();

        String selectClause = s.substring(selectIdx + "select ".length(), fromIdx).trim();

        // Remove DISTINCT/ALL if present
        if (selectClause.startsWith("distinct ")) {
            selectClause = selectClause.substring("distinct ".length()).trim();
        } else if (selectClause.startsWith("all ")) {
            selectClause = selectClause.substring("all ".length()).trim();
        }

        // Only return true for clear property projections (containing dots)
        // This is the safest approach - only treat selections with dots as scalar projections
        return selectClause.contains(".");
    }




    public void setFlushMode(FlushMode flushMode) {
        session.setFlushMode(flushMode == FlushMode.AUTO || flushMode == FlushMode.ALWAYS ?
                FlushModeType.AUTO : FlushModeType.COMMIT);
    }

    public void populateQuerySettings(Map args) {
        Optional.ofNullable(args.remove(DynamicFinder.ARGUMENT_MAX))
                .map(Object::toString)
                .map(Integer::parseInt)
                .ifPresent(query::setMaxResults);
        Optional.ofNullable(args.remove(DynamicFinder.ARGUMENT_OFFSET))
                .map(Object::toString)
                .map(Integer::parseInt)
                .ifPresent(query::setFirstResult);
        Optional.ofNullable(args.remove(DynamicFinder.ARGUMENT_CACHE))
                .map(Object::toString)
                .map(Boolean::parseBoolean)
                .ifPresent(query::setCacheable);
        Optional.ofNullable(args.remove(DynamicFinder.ARGUMENT_FETCH_SIZE))
                .map(Object::toString)
                .map(Integer::parseInt)
                .ifPresent(query::setFetchSize);
        Optional.ofNullable(args.remove(DynamicFinder.ARGUMENT_TIMEOUT))
                .map(Object::toString)
                .map(Integer::parseInt)
                .ifPresent(query::setTimeout);
        Optional.ofNullable(args.remove(DynamicFinder.ARGUMENT_READ_ONLY))
                .map(Object::toString)
                .map(Boolean::parseBoolean)
                .ifPresent(query::setReadOnly);
        Optional.ofNullable(args.remove(DynamicFinder.ARGUMENT_FLUSH_MODE))
                .filter(FlushMode.class::isInstance)
                .map(FlushMode.class::cast)
                .ifPresent(query::setHibernateFlushMode);

    }

    public void populateQueryWithNamedArguments(Map queryNamedArgs) {
        Optional.ofNullable(queryNamedArgs).ifPresent( map -> {
            map.forEach((key, value) -> {
                if (key instanceof CharSequence) {
                    String stringKey = key.toString();
                    if(value == null) {
                        query.setParameter(stringKey, null);
                    } else if (value instanceof CharSequence) {
                        query.setParameter(stringKey, value.toString());
                    } else if (List.class.isAssignableFrom(value.getClass())) {
                        query.setParameterList(stringKey, (List) value);
                    } else if (Set.class.isAssignableFrom(value.getClass())) {
                        query.setParameterList(stringKey, (Set) value);
                    } else if (value.getClass().isArray()) {
                        query.setParameterList( stringKey, (Object[]) value);
                    } else {
                        query.setParameter(stringKey, value);
                    }
                } else {
                    throw new GrailsQueryException("Named parameter's name must be String: $queryNamedArgs");
                }
            });
        });
    }

    public void populateQueryWithIndexedArguments(List params) {
        Optional.ofNullable(params).ifPresent( collection -> {
            IntStream.range(1, collection.size() + 1)
                    .forEach( index -> {
                        var val = collection.get(index - 1);
                        if (val instanceof CharSequence) {
                            query.setParameter(index, val.toString());
                        } else {
                            query.setParameter(index, val);
                        }
                    });
        });
    }


    public org.hibernate.query.Query getQuery() {
        return query;
    }

    public int executeUpdate() {
        return query.executeUpdate();
    }

}
