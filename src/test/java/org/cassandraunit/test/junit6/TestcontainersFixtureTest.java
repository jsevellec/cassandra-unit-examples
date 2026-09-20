package org.cassandraunit.test.junit6;

import java.time.Duration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.CqlDataSetExtension;
import org.cassandraunit.dataset.CQLDataSetFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CqlDataSetExtensionTest} against a container rather than the embedded server: same dataset
 * code, different session supplier. The only example that wants Docker, and it skips without one.
 */
@Testcontainers(disabledWithoutDocker = true)
class TestcontainersFixtureTest {

    private static final String KEYSPACE = "containerkeyspace";

    // org.testcontainers.cassandra, not org.testcontainers.containers - that one is deprecated.
    @Container
    static final CassandraContainer cassandra = new CassandraContainer("cassandra:5.0")
            .withStartupTimeout(Duration.ofMinutes(5));

    @RegisterExtension
    static final CqlDataSetExtension fixtures = CqlDataSetExtension
            // Inside the supplier, not the field initialiser: Jupiter runs @Testcontainers before
            // @RegisterExtension, so by the time this is called the container is up.
            .using(() -> CqlSession.builder()
                    .addContactPoint(cassandra.getContactPoint())
                    .withLocalDatacenter(cassandra.getLocalDatacenter())
                    .build())
            // This session is ours, so it is ours to close.
            .closingSession()
            .schemaOnce(CQLDataSetFactory.fromClassPathAll(KEYSPACE,
                    "cql/widgetSchema.cql", "cql/preexistingWidget.cql"))
            .rowsPerTest(CQLDataSetFactory.fromClassPath("rows/widget.yaml", false, false, KEYSPACE))
            .isolation(CQLDataLoader.Isolation.TRUNCATE)
            .build();

    @Test
    void loads_the_fixture_into_the_container(CqlSession session) {
        Row row = session
                .execute("select label from " + KEYSPACE + ".widget where id = 11111111-1111-1111-1111-111111111111")
                .one();

        assertThat(row).isNotNull();
        assertThat(row.getString("label")).isEqualTo("1");
    }

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
    void talks_to_the_container_and_not_to_an_embedded_server() {
        assertThat(fixtures.getSession().getMetadata().getNodes()).hasSize(1);
        assertThat(cassandra.getContactPoint().getPort()).isEqualTo(cassandra.getMappedPort(9042));
    }

    private static long rowCount(CqlSession session) {
        return session.execute("select count(*) from " + KEYSPACE + ".widget").one().getLong(0);
    }
}
