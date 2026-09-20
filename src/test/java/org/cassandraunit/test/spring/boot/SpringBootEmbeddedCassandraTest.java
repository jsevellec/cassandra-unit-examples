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
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot with nothing wired up. New in 5.3.0: {@code @EmbeddedCassandra} publishes
 * {@code spring.cassandra.*} before the context refreshes, so Boot's auto-configured session finds
 * the embedded node.
 *
 * <p>{@code MERGE_WITH_DEFAULTS} is mandatory, or dependency injection is switched off. Tables are
 * qualified because the dataset's {@code USE} applied to cassandra-unit's session, not Boot's bean.
 */
@SpringBootTest(classes = SpringBootEmbeddedCassandraTest.BootApplication.class)
@TestExecutionListeners(value = CassandraUnitTestExecutionListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@EmbeddedCassandra
@CassandraDataSet(value = {"cql/widgetSchema.cql", "rows/widget.yaml"},
        keyspace = SpringBootEmbeddedCassandraTest.KEYSPACE)
@TestPropertySource(properties = {
        "spring.cassandra.port=9042",
        "spring.cassandra.contact-points=example.invalid"})
class SpringBootEmbeddedCassandraTest {

    static final String KEYSPACE = "springbootkeyspace";

    @Autowired
    private CqlSession bootSession;

    @Autowired
    private Environment environment;

    @Test
    void boot_auto_configures_a_session_against_the_embedded_node() {
        long rows = bootSession.execute("select count(*) from " + KEYSPACE + ".widget")
                .one().getLong(0);

        assertThat(rows).isEqualTo(4);
    }

    /** The published source is added first, so it outranks {@code @TestPropertySource}. */
    @Test
    void the_real_address_wins_over_the_one_this_test_asked_for() {
        assertThat(environment.getProperty("spring.cassandra.port", Integer.class))
                .isEqualTo(EmbeddedCassandraServerHelper.getNativeTransportPort())
                .isNotEqualTo(9042);
        assertThat(environment.getProperty("spring.cassandra.contact-points"))
                .isEqualTo(EmbeddedCassandraServerHelper.getHost())
                .isNotEqualTo("example.invalid");
    }

    @Test
    void the_local_datacenter_is_published_too() {
        assertThat(environment.getProperty("spring.cassandra.local-datacenter"))
                .isEqualTo("datacenter1");
    }

    @SpringBootApplication
    static class BootApplication {
    }
}
