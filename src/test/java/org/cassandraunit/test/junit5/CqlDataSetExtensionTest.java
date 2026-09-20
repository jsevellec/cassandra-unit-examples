package org.cassandraunit.test.junit5;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.CqlDataSetExtension;
import org.cassandraunit.dataset.CQLDataSetFactory;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loading fixtures through a session you already own: {@link CqlDataSetExtension} starts nothing.
 *
 * <p>Build the session inside the supplier, not in a field: it is called when the extension starts,
 * which is late enough for a container to be up.
 */
class CqlDataSetExtensionTest {

    private static final String KEYSPACE = "ownsessionkeyspace";

    @RegisterExtension
    static final CqlDataSetExtension fixtures = CqlDataSetExtension
            .using(CqlDataSetExtensionTest::sessionWeAlreadyHave)
            .schemaOnce(CQLDataSetFactory.fromClassPathAll(KEYSPACE,
                    "cql/widgetSchema.cql", "cql/preexistingWidget.cql"))
            // Both flags off: the per-test load must not drop the schema.
            .rowsPerTest(CQLDataSetFactory.fromClassPath("rows/widget.yaml", false, false, KEYSPACE))
            .isolation(CQLDataLoader.Isolation.TRUNCATE)
            .build();

    /** The extension is a {@code ParameterResolver}, exactly like the embedded-server one. */
    @Test
    void loads_the_fixture_through_a_session_it_did_not_open(CqlSession session) {
        Row row = session
                .execute("select label from " + KEYSPACE + ".widget where id = 11111111-1111-1111-1111-111111111111")
                .one();

        assertThat(row).isNotNull();
        assertThat(row.getString("label")).isEqualTo("1");
    }

    /** {@code TRUNCATE} empties the tables before each load, so the {@code schemaOnce} row is gone too. */
    @Test
    void reloads_the_rows_before_every_test(CqlSession session) {
        assertThat(rowCount(session)).isEqualTo(4);

        session.execute("truncate " + KEYSPACE + ".widget");

        assertThat(rowCount(session)).isZero();
    }

    @Test
    void the_fixture_is_intact_at_the_start_of_every_test(CqlSession session) {
        assertThat(rowCount(session)).isEqualTo(4);
    }

    @Test
    void exposes_the_session_it_was_given() {
        assertThat(fixtures.getSession()).isSameAs(EmbeddedCassandraServerHelper.getSession());
    }

    private static long rowCount(CqlSession session) {
        return session.execute("select count(*) from " + KEYSPACE + ".widget").one().getLong(0);
    }

    /** Stands in for "a session you own". */
    private static CqlSession sessionWeAlreadyHave() {
        try {
            EmbeddedCassandraServerHelper.startEmbeddedCassandra();
        } catch (Exception e) {
            throw new IllegalStateException("could not start the embedded Cassandra", e);
        }
        return EmbeddedCassandraServerHelper.getSession();
    }
}
