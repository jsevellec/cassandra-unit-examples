package org.cassandraunit.test;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Letting the OS pick the ports via the bundled
 * {@link EmbeddedCassandraServerHelper#CASSANDRA_RNDPORT_YML_FILE}, then asking the helper which
 * port it got. Needs its own JVM - see the {@code isolated-config-tests} execution in the pom.
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

        // A real query, not session metadata: schema metadata is refreshed asynchronously.
        Row row = session.execute("select cluster_name from system.local").one();

        assertThat(row).isNotNull();
        assertThat(EmbeddedCassandraServerHelper.getHost()).isNotBlank();

        String endPoint = session.getMetadata().getNodes().values().iterator().next()
                .getEndPoint().toString();
        assertThat(endPoint).endsWith(":" + EmbeddedCassandraServerHelper.getNativeTransportPort());
    }
}
