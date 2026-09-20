package org.cassandraunit.test.assertion;

import com.datastax.oss.driver.api.core.CqlSession;
import org.cassandraunit.CassandraUnitExtension;
import org.cassandraunit.assertion.ExpectedCassandraDataSet;
import org.cassandraunit.assertion.MatchMode;
import org.cassandraunit.assertion.Scope;
import org.cassandraunit.dataset.CQLDataSetFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Asserting what the database holds after a test: {@link CassandraUnitExtension} verifies
 * {@link ExpectedCassandraDataSet} once the method has run, and only if it passed. Strict by
 * default - a table the file names must hold exactly the rows it lists.
 *
 * @see ExpectedDataSetFluentTest for the same thing without the annotation
 */
class ExpectedCassandraDataSetAnnotationTest {

    private static final String KEYSPACE = "assertionkeyspace";

    @RegisterExtension
    static CassandraUnitExtension cassandra = new CassandraUnitExtension(
            CQLDataSetFactory.fromClassPathAll(KEYSPACE,
                    "cql/assertionSchema.cql", "rows/assertion-data.yaml"));

    /** {@code created} is a timestamp the code under test writes, so it is ignored rather than compared. */
    @Test
    @ExpectedCassandraDataSet(value = "rows/expected-widget.yaml", keyspace = KEYSPACE,
            ignoreColumns = "created")
    void shipping_a_widget_changes_its_label(CqlSession session) {
        session.execute("update " + KEYSPACE + ".widget set label = 'shipped', created = toTimestamp(now()) "
                + "where id = 11111111-1111-1111-1111-111111111111");
    }

    @Test
    @ExpectedCassandraDataSet(value = "rows/expected-widget-contains.yaml", keyspace = KEYSPACE,
            mode = MatchMode.CONTAINS)
    void contains_ignores_rows_the_expectation_does_not_list(CqlSession session) {
        session.execute("update " + KEYSPACE + ".widget set label = 'shipped' "
                + "where id = 11111111-1111-1111-1111-111111111111");
        session.execute("insert into " + KEYSPACE + ".widget (id, label) "
                + "values (44444444-4444-4444-4444-444444444444, 'new arrival')");
    }

    /** {@code checkClusteringOrder} additionally asserts row order within a partition - off by default. */
    @Test
    @ExpectedCassandraDataSet(value = "rows/expected-event-partition.yaml", keyspace = KEYSPACE,
            scope = Scope.MENTIONED_PARTITIONS, checkClusteringOrder = true)
    void a_write_to_another_partition_is_out_of_scope(CqlSession session) {
        session.execute("insert into " + KEYSPACE + ".event (day, at, kind) "
                + "values ('2026-09-20', '2026-09-20T11:00:00Z', 'retry')");
    }

    @Test
    @ExpectedCassandraDataSet(value = {"rows/expected-widget.yaml", "rows/expected-event-partition.yaml"},
            keyspace = KEYSPACE, ignoreColumns = "created", scope = Scope.MENTIONED_PARTITIONS)
    void verifies_every_listed_file(CqlSession session) {
        session.execute("update " + KEYSPACE + ".widget set label = 'shipped' "
                + "where id = 11111111-1111-1111-1111-111111111111");
    }
}
