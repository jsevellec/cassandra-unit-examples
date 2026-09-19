package org.cassandraunit.test;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Letting the OS pick the ports, so parallel builds on one machine cannot collide.
 *
 * <p>{@link EmbeddedCassandraServerHelper#CASSANDRA_RNDPORT_YML_FILE} is a bundled yaml with
 * all three ports set to {@code 0}. Ask the helper what it actually got - do not assume 9142.
 *
 * <p>Like {@link StartWithCustomCassandraYamlTest}, this needs its own JVM; see the
 * {@code isolated-config-tests} surefire execution in the pom.
 */
class StartWithRandomPortTest {

    @BeforeAll
    static void startCassandra() throws Exception {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra(
                EmbeddedCassandraServerHelper.CASSANDRA_RNDPORT_YML_FILE);
    }

    @Test
    void should_have_started_on_a_port_the_os_chose() {
        int port = EmbeddedCassandraServerHelper.getNativeTransportPort();

        assertThat(port).isPositive();
        assertThat(port).isNotEqualTo(9142);
    }

    @Test
    void should_be_reachable_by_the_driver_on_whatever_port_it_got() {
        CqlSession session = EmbeddedCassandraServerHelper.getSession();

        // A real query, not session.getMetadata().getKeyspaces(): schema metadata is
        // refreshed asynchronously and is legitimately empty until something creates a
        // keyspace, so asserting on it here would be a race.
        Row row = session.execute("select cluster_name from system.local").one();

        assertThat(row).isNotNull();
        assertThat(EmbeddedCassandraServerHelper.getHost()).isNotBlank();

        String endPoint = session.getMetadata().getNodes().values().iterator().next()
                .getEndPoint().toString();
        assertThat(endPoint).endsWith(":" + EmbeddedCassandraServerHelper.getNativeTransportPort());
    }
}
