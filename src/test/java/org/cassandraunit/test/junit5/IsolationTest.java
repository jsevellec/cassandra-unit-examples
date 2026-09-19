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
 * What a dataset load clears before it loads - {@link Isolation}, new in 5.1.0.
 *
 * <table border="1">
 *   <caption>The three modes</caption>
 *   <tr><td>{@link Isolation#DATASET}</td>
 *       <td>Honour the dataset's own {@code keyspaceCreation} / {@code keyspaceDeletion} flags,
 *           which normally means dropping the keyspace and building it again. The default, and
 *           what every release before 5.1.0 did.</td></tr>
 *   <tr><td>{@link Isolation#TRUNCATE}</td>
 *       <td>Keep the keyspace and its schema; empty every table instead.</td></tr>
 *   <tr><td>{@link Isolation#NONE}</td>
 *       <td>Clear nothing. The keyspace is selected if it exists, so unqualified statements still
 *           land where they should.</td></tr>
 * </table>
 *
 * <p>{@code TRUNCATE} is dramatically cheaper - the library measures a median 940ms against 1.6ms
 * for two tables, and 1640ms against 10.7ms for fifty, because a schema rebuild is not free. What
 * keeps it from being the default is not speed: it is <b>not</b> a drop-in. It ignores the
 * dataset's creation and deletion flags, so a per-test dataset that builds its own schema breaks
 * under it. Pair it with a schema loaded once - {@code schemaOnce} on
 * {@link org.cassandraunit.CqlDataSetExtension}, or {@link CQLDataLoader#loadIfKeyspaceAbsent} by
 * hand - and keep the per-test dataset to rows.
 *
 * <p>On the extensions: {@code CassandraUnitExtension#withIsolation(...)} and
 * {@code CqlDataSetExtension.Builder#isolation(...)}. This example drives the loader directly so
 * the three modes can sit side by side.
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

    /** Every test starts from the schema, the fixture, and one row nothing put in the fixture. */
    @BeforeEach
    void loadTheFixtureAndAStrayRow() {
        loader.load(schemaAndRows());
        insertStrayRow();
    }

    /**
     * The default. The dataset's flags say create and delete, so the keyspace is dropped and
     * rebuilt - and the stray row goes with it, along with everything else.
     */
    @Test
    void dataset_drops_and_rebuilds_the_keyspace() {
        loader.load(schemaAndRows());

        assertThat(strayRowExists()).isFalse();
        assertThat(rowCount()).isEqualTo(4);
    }

    /**
     * Same end state for the data, but the keyspace and its tables are never dropped: they are
     * truncated. Note the dataset - rows only, with both keyspace flags off, because the schema
     * has to survive.
     */
    @Test
    void truncate_empties_the_tables_and_keeps_the_schema() {
        loader.load(rowsOnly(), Isolation.TRUNCATE);

        assertThat(strayRowExists()).isFalse();
        assertThat(rowCount()).isEqualTo(4);
    }

    /** Nothing is cleared, so the load is a plain upsert on top of what was already there. */
    @Test
    void none_clears_nothing() {
        loader.load(rowsOnly(), Isolation.NONE);

        assertThat(strayRowExists()).isTrue();
        assertThat(rowCount()).isEqualTo(5);
    }

    /**
     * The "schema once" half, by hand: it loads only when the keyspace is absent, and returns
     * whether it did. This is what {@code CqlDataSetExtension.schemaOnce} calls.
     */
    @Test
    void load_if_keyspace_absent_runs_at_most_once() {
        assertThat(loader.loadIfKeyspaceAbsent(schemaAndRows()))
                .as("the keyspace is already there, from @BeforeEach")
                .isFalse();

        session.execute("drop keyspace " + KEYSPACE);

        assertThat(loader.loadIfKeyspaceAbsent(schemaAndRows())).isTrue();
    }

    /** Schema and rows, with the chain's keyspace dropped and created once. */
    private static CQLDataSet schemaAndRows() {
        return CQLDataSetFactory.fromClassPathAll(KEYSPACE, "cql/widgetSchema.cql", "rows/widget.yaml");
    }

    /** Rows only, touching neither the keyspace nor the schema - what TRUNCATE and NONE need. */
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
