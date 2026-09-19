package org.cassandraunit.test.junit5;

import com.datastax.oss.driver.api.core.CqlSession;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.cassandraunit.utils.CqlOperations;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Getting back to a known state between tests, without paying for a Cassandra restart.
 *
 * <p>Three tools, cheapest first:
 * <ul>
 *   <li>{@link CqlOperations} - {@code Consumer<String>} factories for the individual
 *       operations ({@code truncateTable}, {@code dropKeyspace}, {@code createKeyspace},
 *       {@code use}, {@code execute}). They compose with streams, which is handy when the
 *       table list is computed.</li>
 *   <li>{@link EmbeddedCassandraServerHelper#cleanDataEmbeddedCassandra(String, String...)} -
 *       truncates every table in one keyspace, with an opt-out list. Reference data stays.</li>
 *   <li>{@link EmbeddedCassandraServerHelper#cleanEmbeddedCassandra()} - drops every
 *       non-system keyspace. The blunt one; you then have to reload the schema.</li>
 * </ul>
 *
 * <p>{@link EmbeddedCassandraServerHelper#nonSystemKeyspaces()} is the predicate that decides
 * what counts as "yours" rather than Cassandra's - useful when you write your own cleanup.
 */
class CleanDataBetweenTestsTest {

    private static final String KEYSPACE = "cleanupexample";

    private static CqlSession session;

    @BeforeAll
    static void startCassandra() throws Exception {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra();
        session = EmbeddedCassandraServerHelper.getSession();
    }

    @BeforeEach
    void reloadDataSet() {
        new CQLDataLoader(session).load(new ClassPathCQLDataSet("simple.cql", true, true, KEYSPACE));
    }

    @Test
    void truncate_one_table_with_cql_operations() {
        CqlOperations.truncateTable(session).accept(KEYSPACE + ".mytable");

        assertThat(rowCount()).isZero();
    }

    @Test
    void truncate_every_table_in_a_keyspace() {
        EmbeddedCassandraServerHelper.cleanDataEmbeddedCassandra(KEYSPACE);

        assertThat(rowCount()).isZero();
    }

    /**
     * Because {@code @BeforeEach} reloads it, the dataset is back for this test regardless of
     * what the other two did to it - which is the point of cleaning up.
     */
    @Test
    void the_dataset_is_intact_at_the_start_of_every_test() {
        assertThat(rowCount()).isEqualTo(2);

        assertThat(keyspaceNames()).contains(KEYSPACE).doesNotContain("system_schema");
    }

    private long rowCount() {
        return session.execute("select count(*) from " + KEYSPACE + ".mytable").one().getLong(0);
    }

    /**
     * Only the keyspaces you created - {@code nonSystemKeyspaces()} filters Cassandra's out.
     *
     * <p>Reading the driver's schema metadata is safe here because {@code @BeforeEach} has
     * already run DDL, and the driver waits for schema agreement before a DDL statement
     * returns. Straight after {@code startEmbeddedCassandra()}, with no DDL executed yet, the
     * metadata is legitimately still empty - query {@code system_schema.keyspaces} in that
     * situation instead.
     */
    private Stream<String> keyspaceNames() {
        return session.getMetadata().getKeyspaces().values().stream()
                .map(keyspace -> keyspace.getName().toString())
                .filter(EmbeddedCassandraServerHelper.nonSystemKeyspaces());
    }
}
