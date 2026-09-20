package org.cassandraunit.test.spring.boot;

import com.datastax.oss.driver.api.core.CqlSession;
import org.cassandraunit.spring.CassandraDataSet;
import org.cassandraunit.spring.CassandraUnitTestExecutionListener;
import org.cassandraunit.spring.EmbeddedCassandra;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestExecutionListeners;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The case a properties file cannot express: {@code cu-cassandra-rndport.yaml} asks the OS for a
 * free port, so no value exists to write down until the node is up.
 *
 * <p>Runs in its own JVM - a JVM is pinned to the first Cassandra configuration it starts. See the
 * {@code isolated-config-tests} surefire execution in the pom.
 */
@SpringBootTest(classes = SpringBootRandomPortTest.BootApplication.class)
@TestExecutionListeners(value = CassandraUnitTestExecutionListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@EmbeddedCassandra(configuration = "cu-cassandra-rndport.yaml")
@CassandraDataSet(value = {"cql/widgetSchema.cql", "rows/widget.yaml"},
        keyspace = SpringBootRandomPortTest.KEYSPACE)
class SpringBootRandomPortTest {

    static final String KEYSPACE = "springbootrndportkeyspace";

    @Autowired
    private CqlSession bootSession;

    @Autowired
    private Environment environment;

    @Test
    void boot_connects_on_a_port_that_did_not_exist_until_startup() {
        long rows = bootSession.execute("select count(*) from " + KEYSPACE + ".widget")
                .one().getLong(0);

        assertThat(rows).isEqualTo(4);
    }

    @Test
    void the_published_port_is_the_one_the_os_chose() {
        int actual = EmbeddedCassandraServerHelper.getNativeTransportPort();

        assertThat(environment.getProperty("spring.cassandra.port", Integer.class))
                .isEqualTo(actual);
        assertThat(actual).isNotEqualTo(9042).isNotEqualTo(9142);
    }

    @SpringBootApplication
    static class BootApplication {
    }
}
