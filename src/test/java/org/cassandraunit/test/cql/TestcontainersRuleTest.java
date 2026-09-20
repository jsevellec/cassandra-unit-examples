package org.cassandraunit.test.cql;

import java.time.Duration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.CQLDataLoader.Isolation;
import org.cassandraunit.assertion.ExpectedCassandraDataSet;
import org.cassandraunit.assertion.ExpectedCassandraDataSetRule;
import org.cassandraunit.dataset.CQLDataSetFactory;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.cassandra.CassandraContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixtures into a container from JUnit 4. Two things have no JUnit 4 equivalent and are done by
 * hand here: cassandra-unit has no rule that takes a session you supply (only
 * {@code CqlDataSetExtension}, which is Jupiter-only), so this calls {@link CQLDataLoader}
 * directly; and Testcontainers 2.x dropped {@code @Rule} support, so the container is started and
 * stopped in {@code @BeforeClass} / {@code @AfterClass}.
 *
 * <p>{@code org.cassandraunit.test.junit6.TestcontainersFixtureTest} is the Jupiter version.
 */
public class TestcontainersRuleTest {

    private static final String KEYSPACE = "containerjunit4keyspace";

    private static final CassandraContainer cassandra = new CassandraContainer("cassandra:5.0")
            .withStartupTimeout(Duration.ofMinutes(5));

    private static CqlSession session;
    private static CQLDataLoader loader;

    /** Verifies @ExpectedCassandraDataSet; unlike the loaders, this rule does take a session. */
    @Rule
    public ExpectedCassandraDataSetRule expected = new ExpectedCassandraDataSetRule(() -> session);

    @BeforeClass
    public static void startContainer() {
        // JUnit 4 has no @Testcontainers(disabledWithoutDocker = true): an assumption skips instead.
        Assume.assumeTrue("Docker is not available", dockerAvailable());

        cassandra.start();
        session = CqlSession.builder()
                .addContactPoint(cassandra.getContactPoint())
                .withLocalDatacenter(cassandra.getLocalDatacenter())
                .build();
        loader = new CQLDataLoader(session);
        loader.load(CQLDataSetFactory.fromClassPathAll(KEYSPACE,
                "cql/assertionSchema.cql", "rows/assertion-data.yaml"));
    }

    @AfterClass
    public static void stopContainer() {
        if (session != null) {
            session.close();
        }
        cassandra.stop();
    }

    @Before
    public void reloadTheRows() {
        loader.load(CQLDataSetFactory.fromClassPath("rows/assertion-data.yaml", false, false, KEYSPACE),
                Isolation.TRUNCATE);
    }

    @Test
    public void loads_the_fixture_into_the_container() {
        Row row = session
                .execute("select label from " + KEYSPACE + ".widget where id = 11111111-1111-1111-1111-111111111111")
                .one();

        assertThat(row).isNotNull();
        assertThat(row.getString("label")).isEqualTo("ordered");
    }

    @Test
    public void truncate_puts_the_rows_back_before_each_test() {
        assertThat(rowCount()).isEqualTo(3);

        session.execute("truncate " + KEYSPACE + ".widget");

        assertThat(rowCount()).isZero();
    }

    @Test
    @ExpectedCassandraDataSet(value = "rows/expected-widget.yaml", keyspace = KEYSPACE,
            ignoreColumns = "created")
    public void shipping_a_widget_changes_its_label() {
        session.execute("update " + KEYSPACE + ".widget set label = 'shipped' "
                + "where id = 11111111-1111-1111-1111-111111111111");
    }

    /** Testcontainers' own detector, which catches Throwable - isDockerAvailable() does not. */
    private static boolean dockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static long rowCount() {
        return session.execute("select count(*) from " + KEYSPACE + ".widget").one().getLong(0);
    }
}
