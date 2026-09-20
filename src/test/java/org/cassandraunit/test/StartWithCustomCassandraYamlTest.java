package org.cassandraunit.test;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.metadata.Node;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starting the embedded server with your own cassandra.yaml (a classpath resource name) instead
 * of the bundled {@code cu-cassandra.yaml}. Needs its own JVM - see the
 * {@code isolated-config-tests} surefire execution in the pom.
 */
class StartWithCustomCassandraYamlTest {

    @BeforeAll
    static void startCassandra() throws Exception {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra("another-cassandra.yaml");
    }

    @Test
    void should_have_started_on_the_port_from_my_own_yaml() {
        assertThat(EmbeddedCassandraServerHelper.getNativeTransportPort()).isEqualTo(9143);
        assertThat(EmbeddedCassandraServerHelper.getClusterName()).isEqualTo("My Own Test Cluster");
    }

    @Test
    void should_be_reachable_by_the_driver_on_that_port() {
        CqlSession session = EmbeddedCassandraServerHelper.getSession();

        // A real query, not session metadata: schema metadata is refreshed asynchronously.
        Row row = session.execute("select cluster_name from system.local").one();
        assertThat(row).isNotNull();
        assertThat(row.getString("cluster_name")).isEqualTo("My Own Test Cluster");

        Node node = session.getMetadata().getNodes().values().iterator().next();
        assertThat(node.getEndPoint().toString()).endsWith(":9143");
    }
}
