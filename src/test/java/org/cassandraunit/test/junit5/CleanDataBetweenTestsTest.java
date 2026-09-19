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
 *   <li>{@link CqlOperations#truncateKeyspace(CqlSession, String, String...)} - empties every
 *       table in a keyspace, with an opt-out list, through any session. Public API as of 5.1.0,
 *       promoted from a private method on {@link EmbeddedCassandraServerHelper}, so it now works
 *       against a Testcontainers node or a real cluster and not only the embedded one.</li>
 *   <li>{@link CqlOperations} - {@code Consumer<String>} factories for the individual
 *       operations ({@code truncateTable}, {@code dropKeyspace}, {@code createKeyspace},
 *       {@code use}, {@code execute}). They compose with streams, which is handy when the
 *       table list is computed.</li>
 *   <li>{@link EmbeddedCassandraServerHelper#cleanDataEmbeddedCassandra(String, String...)} -
 *       the same thing against the embedded server's own session.</li>
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

        // Stands in for data a suite seeds once and does not want wiped between tests.
        session.execute("create table " + KEYSPACE + ".reference_data (id varchar primary key)");
        session.execute("insert into " + KEYSPACE + ".reference_data (id) values ('ref01')");
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
     * The same, one level down and with no embedded server involved: {@code truncateKeyspace}
     * reads the table list from {@code system_schema.tables} and truncates what it finds, so it
     * works through whatever session you hand it.
     *
     * <p>The trailing table names are the opt-out list - everything else is emptied.
     */
    @Test
    void truncate_a_keyspace_but_keep_the_reference_data() {
        CqlOperations.truncateKeyspace(session, KEYSPACE, "reference_data");

        assertThat(rowCount()).isZero();
        assertThat(referenceRowCount()).isEqualTo(1);
    }

    /** Without the opt-out, nothing is spared. */
    @Test
    void truncate_a_whole_keyspace() {
        CqlOperations.truncateKeyspace(session, KEYSPACE);

        assertThat(rowCount()).isZero();
        assertThat(referenceRowCount()).isZero();
    }

    /**
     * {@code quote} is what those helpers use to build their statements, and is public for the
     * same reason: an identifier that is not all lower-case has to be quoted or it cannot be
     * named at all. It quotes only when quoting changes the meaning.
     */
    @Test
    void quote_only_quotes_identifiers_that_need_it() {
        assertThat(CqlOperations.quote("mytable")).isEqualTo("mytable");
        assertThat(CqlOperations.quote("MixedCase")).isEqualTo("\"MixedCase\"");
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

    private long referenceRowCount() {
        return session.execute("select count(*) from " + KEYSPACE + ".reference_data").one().getLong(0);
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
