package org.cassandraunit.test;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.metadata.Node;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starting the embedded server with your own cassandra.yaml instead of the bundled
 * {@code cu-cassandra.yaml}.
 *
 * <p>The argument is a <em>classpath resource name</em>; there are
 * {@code startEmbeddedCassandra(File, ...)} overloads if you have a path on disk instead.
 *
 * <p>This test runs in its own JVM - see the {@code isolated-config-tests} surefire execution
 * in the pom. One Cassandra per JVM is a hard constraint: {@code DatabaseDescriptor},
 * {@code Schema} and {@code StorageService} hold static state that cannot be reset in-process,
 * so a JVM is pinned to the first configuration it starts. Sharing a fork with the other
 * examples would silently give this test the default configuration instead of its own.
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

        // Query the node rather than inspecting session.getMetadata().getKeyspaces(): the
        // driver refreshes schema metadata asynchronously, so right after startup - before
        // anything has created a keyspace - it is legitimately still empty.
        Row row = session.execute("select cluster_name from system.local").one();
        assertThat(row).isNotNull();
        assertThat(row.getString("cluster_name")).isEqualTo("My Own Test Cluster");

        Node node = session.getMetadata().getNodes().values().iterator().next();
        assertThat(node.getEndPoint().toString()).endsWith(":9143");
    }
}
