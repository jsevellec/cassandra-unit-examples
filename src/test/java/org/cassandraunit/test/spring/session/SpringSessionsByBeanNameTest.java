package org.cassandraunit.test.spring.session;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CqlDataSetExtension;
import org.cassandraunit.SpringSessions;
import org.cassandraunit.dataset.CQLDataSetFactory;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Naming the session, for a context with more than one {@code CqlSession} bean.
 * {@link SpringSessions#fromApplicationContext()} looks up by type and fails rather than guessing;
 * {@link SpringSessions#fromApplicationContext(String)} is the way out. See
 * {@link SpringSessionsFixtureTest} for the rest of the rules.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SpringSessionsByBeanNameTest.Config.class)
class SpringSessionsByBeanNameTest {

    private static final String KEYSPACE = "springsessionbynamekeyspace";

    @RegisterExtension
    static final CqlDataSetExtension fixtures = CqlDataSetExtension
            .using(SpringSessions.fromApplicationContext("widgetSession"))
            .schemaOnce(CQLDataSetFactory.fromClassPath("cql/widgetSchema.cql", KEYSPACE))
            .rowsPerTest(CQLDataSetFactory.fromClassPath("rows/widget.yaml", false, false, KEYSPACE))
            .build();

    @Autowired
    @Qualifier("widgetSession")
    private CqlSession widgetSession;

    @Autowired
    @Qualifier("reportingSession")
    private CqlSession reportingSession;

    @DynamicPropertySource
    static void embeddedCassandra(DynamicPropertyRegistry registry) throws Exception {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra();
        registry.add("spring.cassandra.contact-points", EmbeddedCassandraServerHelper::getHost);
        registry.add("spring.cassandra.port", EmbeddedCassandraServerHelper::getNativeTransportPort);
        registry.add("spring.cassandra.local-datacenter", () -> "datacenter1");
    }

    @Test
    void loads_into_the_bean_that_was_named() {
        assertThat(fixtures.getSession())
                .isSameAs(widgetSession)
                .isNotSameAs(reportingSession);
    }

    @Test
    void the_fixture_is_readable_through_that_bean() {
        Row row = widgetSession
                .execute("select label from " + KEYSPACE + ".widget"
                        + " where id = 11111111-1111-1111-1111-111111111111")
                .one();

        assertThat(row).isNotNull();
        assertThat(row.getString("label")).isEqualTo("1");
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {

        @Bean
        CqlSession widgetSession(Environment environment) {
            return session(environment);
        }

        @Bean
        CqlSession reportingSession(Environment environment) {
            return session(environment);
        }

        private static CqlSession session(Environment environment) {
            return CqlSession.builder()
                    .addContactPoint(new InetSocketAddress(
                            environment.getRequiredProperty("spring.cassandra.contact-points"),
                            environment.getRequiredProperty("spring.cassandra.port", Integer.class)))
                    .withLocalDatacenter(
                            environment.getRequiredProperty("spring.cassandra.local-datacenter"))
                    .build();
        }
    }
}
