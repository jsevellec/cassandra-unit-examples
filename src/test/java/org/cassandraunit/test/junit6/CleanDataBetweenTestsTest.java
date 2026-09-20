package org.cassandraunit.test.junit6;

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
 * Getting back to a known state between tests without restarting Cassandra, with the
 * {@link CqlOperations} and {@link EmbeddedCassandraServerHelper} cleanup helpers.
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

    /** The trailing table names are the opt-out list - everything else is emptied. */
    @Test
    void truncate_a_keyspace_but_keep_the_reference_data() {
        CqlOperations.truncateKeyspace(session, KEYSPACE, "reference_data");

        assertThat(rowCount()).isZero();
        assertThat(referenceRowCount()).isEqualTo(1);
    }

    @Test
    void truncate_a_whole_keyspace() {
        CqlOperations.truncateKeyspace(session, KEYSPACE);

        assertThat(rowCount()).isZero();
        assertThat(referenceRowCount()).isZero();
    }

    /** An identifier that is not all lower-case has to be quoted or it cannot be named at all. */
    @Test
    void quote_only_quotes_identifiers_that_need_it() {
        assertThat(CqlOperations.quote("mytable")).isEqualTo("mytable");
        assertThat(CqlOperations.quote("MixedCase")).isEqualTo("\"MixedCase\"");
    }

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

    private Stream<String> keyspaceNames() {
        return session.getMetadata().getKeyspaces().values().stream()
                .map(keyspace -> keyspace.getName().toString())
                .filter(EmbeddedCassandraServerHelper.nonSystemKeyspaces());
    }
}
