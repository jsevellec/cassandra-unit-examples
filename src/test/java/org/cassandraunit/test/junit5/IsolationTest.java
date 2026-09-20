package org.cassandraunit.test.junit5;

import com.datastax.oss.driver.api.core.CqlSession;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.CQLDataLoader.Isolation;
import org.cassandraunit.dataset.CQLDataSet;
import org.cassandraunit.dataset.CQLDataSetFactory;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a dataset load clears before it loads: the three {@link Isolation} modes.
 *
 * <p>{@code TRUNCATE} ignores the dataset's keyspace creation and deletion flags, so it only works
 * against a schema that was loaded separately - see {@link CQLDataLoader#loadIfKeyspaceAbsent}.
 */
class IsolationTest {

    private static final String KEYSPACE = "isolationkeyspace";
    private static final String STRAY = "99999999-9999-9999-9999-999999999999";

    private static CqlSession session;
    private static CQLDataLoader loader;

    @BeforeAll
    static void startCassandra() throws Exception {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra();
        session = EmbeddedCassandraServerHelper.getSession();
        loader = new CQLDataLoader(session);
    }

    @BeforeEach
    void loadTheFixtureAndAStrayRow() {
        loader.load(schemaAndRows());
        insertStrayRow();
    }

    @Test
    void dataset_drops_and_rebuilds_the_keyspace() {
        loader.load(schemaAndRows());

        assertThat(strayRowExists()).isFalse();
        assertThat(rowCount()).isEqualTo(4);
    }

    @Test
    void truncate_empties_the_tables_and_keeps_the_schema() {
        loader.load(rowsOnly(), Isolation.TRUNCATE);

        assertThat(strayRowExists()).isFalse();
        assertThat(rowCount()).isEqualTo(4);
    }

    @Test
    void none_clears_nothing() {
        loader.load(rowsOnly(), Isolation.NONE);

        assertThat(strayRowExists()).isTrue();
        assertThat(rowCount()).isEqualTo(5);
    }

    @Test
    void load_if_keyspace_absent_runs_at_most_once() {
        assertThat(loader.loadIfKeyspaceAbsent(schemaAndRows()))
                .as("the keyspace is already there, from @BeforeEach")
                .isFalse();

        session.execute("drop keyspace " + KEYSPACE);

        assertThat(loader.loadIfKeyspaceAbsent(schemaAndRows())).isTrue();
    }

    private static CQLDataSet schemaAndRows() {
        return CQLDataSetFactory.fromClassPathAll(KEYSPACE, "cql/widgetSchema.cql", "rows/widget.yaml");
    }

    /** The false, false are keyspace creation and deletion: leave the schema alone. */
    private static CQLDataSet rowsOnly() {
        return CQLDataSetFactory.fromClassPath("rows/widget.yaml", false, false, KEYSPACE);
    }

    private static void insertStrayRow() {
        session.execute("insert into " + KEYSPACE + ".widget (id, label) values (" + STRAY + ", 'stray')");
    }

    private static boolean strayRowExists() {
        return session.execute("select id from " + KEYSPACE + ".widget where id = " + STRAY).one() != null;
    }

    private static long rowCount() {
        return session.execute("select count(*) from " + KEYSPACE + ".widget").one().getLong(0);
    }
}
