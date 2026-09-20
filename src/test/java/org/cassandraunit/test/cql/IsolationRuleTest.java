package org.cassandraunit.test.cql;

import com.datastax.oss.driver.api.core.CqlSession;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.CQLDataLoader.Isolation;
import org.cassandraunit.dataset.CQLDataSet;
import org.cassandraunit.dataset.CQLDataSetFactory;
import org.cassandraunit.utils.CqlOperations;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three {@link Isolation} modes on JUnit 4. {@link CQLDataLoader} is framework-agnostic, so
 * this needs no rule at all - see {@code org.cassandraunit.test.junit6.IsolationTest} for the
 * identical Jupiter version.
 */
public class IsolationRuleTest {

    private static final String KEYSPACE = "isolationjunit4keyspace";
    private static final String STRAY = "99999999-9999-9999-9999-999999999999";

    private static CqlSession session;
    private static CQLDataLoader loader;

    @BeforeClass
    public static void startCassandra() throws Exception {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra();
        session = EmbeddedCassandraServerHelper.getSession();
        loader = new CQLDataLoader(session);
    }

    @Before
    public void loadTheFixtureAndAStrayRow() {
        loader.load(schemaAndRows());
        session.execute("insert into " + KEYSPACE + ".widget (id, label) values (" + STRAY + ", 'stray')");
    }

    @Test
    public void dataset_drops_and_rebuilds_the_keyspace() {
        loader.load(schemaAndRows());

        assertThat(strayRowExists()).isFalse();
        assertThat(rowCount()).isEqualTo(4);
    }

    @Test
    public void truncate_empties_the_tables_and_keeps_the_schema() {
        loader.load(rowsOnly(), Isolation.TRUNCATE);

        assertThat(strayRowExists()).isFalse();
        assertThat(rowCount()).isEqualTo(4);
    }

    @Test
    public void none_clears_nothing() {
        loader.load(rowsOnly(), Isolation.NONE);

        assertThat(strayRowExists()).isTrue();
        assertThat(rowCount()).isEqualTo(5);
    }

    /** What TRUNCATE calls underneath, if you want the emptying without a load. */
    @Test
    public void truncate_keyspace_can_be_called_on_its_own() {
        CqlOperations.truncateKeyspace(session, KEYSPACE);

        assertThat(rowCount()).isZero();
    }

    private static CQLDataSet schemaAndRows() {
        return CQLDataSetFactory.fromClassPathAll(KEYSPACE, "cql/widgetSchema.cql", "rows/widget.yaml");
    }

    private static CQLDataSet rowsOnly() {
        return CQLDataSetFactory.fromClassPath("rows/widget.yaml", false, false, KEYSPACE);
    }

    private static boolean strayRowExists() {
        return session.execute("select id from " + KEYSPACE + ".widget where id = " + STRAY).one() != null;
    }

    private static long rowCount() {
        return session.execute("select count(*) from " + KEYSPACE + ".widget").one().getLong(0);
    }
}
