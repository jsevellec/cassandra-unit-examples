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
 * Loading fixtures into a Cassandra that is <b>not</b> cassandra-unit's to start.
 *
 * <p>{@link CqlDataSetExtension} starts nothing. You hand it a session - a Testcontainers
 * container, a node your CI already runs, a managed service - and it loads datasets through that.
 * The fixture layer it uses lives in its own artifact, {@code org.cassandraunit:cassandra-unit-dataset},
 * which has no {@code cassandra-all} dependency, no jamm agent and no JDK ceiling: none of the
 * surefire {@code argLine} in this project's pom is needed for it. That block is the price of the
 * embedded server, not of the fixtures.
 *
 * <p>This example supplies the embedded server's session so that {@code mvn test} needs no Docker.
 * Against a container it is the same code with a different supplier:
 *
 * <pre>
 * &#64;Testcontainers
 * class WidgetIT {
 *
 *     &#64;Container
 *     static final CassandraContainer cassandra =
 *             new CassandraContainer("cassandra:5.0").withReuse(true);
 *
 *     &#64;RegisterExtension
 *     static final CqlDataSetExtension fixtures = CqlDataSetExtension
 *             .using(() -&gt; CqlSession.builder()
 *                     .addContactPoint(cassandra.getContactPoint())
 *                     .withLocalDatacenter(cassandra.getLocalDatacenter())
 *                     .build())
 *             .closingSession()
 *             .schemaOnce(CQLDataSetFactory.fromClassPath("cql/widgetSchema.cql", "mykeyspace"))
 *             .rowsPerTest(CQLDataSetFactory.fromClassPath("rows/widget.yaml", false, false, "mykeyspace"))
 *             .build();
 * }
 * </pre>
 *
 * <p>Two things that bite:
 * <ul>
 *   <li><b>Build the session inside the lambda</b>, not in a field a {@code @BeforeAll} fills.
 *       Jupiter runs declaratively registered extensions - {@code @Testcontainers} among them -
 *       before {@code @RegisterExtension} ones, and {@code @BeforeAll} methods after all of them.
 *       A supplier is called when the extension starts, which is late enough for the container to
 *       be up and early enough to load fixtures.</li>
 *   <li><b>The extension never closes a session it did not create.</b> {@code closingSession()}
 *       opts in, and is right when the supplier built the session - as above. It is left off here
 *       because the embedded server owns this one.</li>
 * </ul>
 */
class CqlDataSetExtensionTest {

    private static final String KEYSPACE = "ownsessionkeyspace";

    @RegisterExtension
    static final CqlDataSetExtension fixtures = CqlDataSetExtension
            .using(CqlDataSetExtensionTest::sessionWeAlreadyHave)
            // Loaded once for the class, and only if the keyspace is not already there.
            .schemaOnce(CQLDataSetFactory.fromClassPathAll(KEYSPACE,
                    "cql/widgetSchema.cql", "cql/preexistingWidget.cql"))
            // Loaded before every test method. Neither flag set: it must not drop the schema.
            .rowsPerTest(CQLDataSetFactory.fromClassPath("rows/widget.yaml", false, false, KEYSPACE))
            // Empty the tables between tests instead of dropping and rebuilding the keyspace.
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

    /**
     * {@code rowsPerTest} reloads for every method, so a test that wrecks the data does not
     * affect the next one. With {@code Isolation.TRUNCATE} that costs a truncate per table rather
     * than a keyspace drop and a schema rebuild.
     *
     * <p>Note what {@code TRUNCATE} means for the pre-existing row: the tables are emptied before
     * each load, so the row {@code preexistingWidget.cql} inserted at {@code schemaOnce} time is
     * gone, and the fixture's own row for that id is all that remains.
     */
    @Test
    void reloads_the_rows_before_every_test(CqlSession session) {
        assertThat(rowCount(session)).isEqualTo(4);

        session.execute("truncate " + KEYSPACE + ".widget");

        assertThat(rowCount(session)).isZero();
    }

    /** The same again, to prove the previous test's damage did not leak into this one. */
    @Test
    void the_fixture_is_intact_at_the_start_of_every_test(CqlSession session) {
        assertThat(rowCount(session)).isEqualTo(4);
    }

    /** Also reachable from the field, for a test that takes no parameter. */
    @Test
    void exposes_the_session_it_was_given() {
        assertThat(fixtures.getSession()).isSameAs(EmbeddedCassandraServerHelper.getSession());
    }

    private static long rowCount(CqlSession session) {
        return session.execute("select count(*) from " + KEYSPACE + ".widget").one().getLong(0);
    }

    /**
     * Stands in for "a session you own". Called by the extension when it starts, not when this
     * class is initialised.
     */
    private static CqlSession sessionWeAlreadyHave() {
        try {
            EmbeddedCassandraServerHelper.startEmbeddedCassandra();
        } catch (Exception e) {
            throw new IllegalStateException("could not start the embedded Cassandra", e);
        }
        return EmbeddedCassandraServerHelper.getSession();
    }
}
