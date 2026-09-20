package org.cassandraunit.test.spring.session;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.CqlDataSetExtension;
import org.cassandraunit.SpringSessions;
import org.cassandraunit.dataset.CQLDataSetFactory;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Loading fixtures through the {@link CqlSession} bean the Spring context already has. New in
 * 5.3.0, and the path for a Cassandra that is not the embedded one - Testcontainers, a shared
 * cluster, Astra. The embedded server stands in for the container here, so {@code mvn test} needs
 * no Docker; against a container only the property source changes.
 *
 * <p>{@link SpringSessions} lives in {@code cassandra-unit-dataset}, so it needs none of the
 * surefire {@code argLine} and has no JDK ceiling.
 *
 * <p>Three things bite. Never add {@code closingSession()} - the bean belongs to the context, and
 * Jupiter runs {@code afterAll} in reverse registration order. Leave the {@code CqlSession}
 * parameter bare, or {@link SpringExtension} claims it too and Jupiter reports competing
 * {@code ParameterResolver}s. {@code @DirtiesContext} is fine per class, not per method: the
 * session is resolved once for the class.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SpringSessionsFixtureTest.Config.class)
class SpringSessionsFixtureTest {

    private static final String KEYSPACE = "springsessionkeyspace";

    @RegisterExtension
    static final CqlDataSetExtension fixtures = CqlDataSetExtension
            .using(SpringSessions.fromApplicationContext())
            .schemaOnce(CQLDataSetFactory.fromClassPath("cql/widgetSchema.cql", KEYSPACE))
            .rowsPerTest(CQLDataSetFactory.fromClassPath("rows/widget.yaml", false, false, KEYSPACE))
            .isolation(CQLDataLoader.Isolation.TRUNCATE)
            .build();

    @Autowired
    private CqlSession contextSession;

    @DynamicPropertySource
    static void embeddedCassandra(DynamicPropertyRegistry registry) throws Exception {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra();
        registry.add("spring.cassandra.contact-points", EmbeddedCassandraServerHelper::getHost);
        registry.add("spring.cassandra.port", EmbeddedCassandraServerHelper::getNativeTransportPort);
        registry.add("spring.cassandra.local-datacenter", () -> "datacenter1");
    }

    @Test
    void loads_the_fixture_into_the_context_session() {
        Row row = contextSession
                .execute("select label, tags from " + KEYSPACE + ".widget"
                        + " where id = 11111111-1111-1111-1111-111111111111")
                .one();

        assertThat(row).isNotNull();
        assertThat(row.getString("label")).isEqualTo("1");
        assertThat(row.getSet("tags", String.class)).containsExactlyInAnyOrder("alpha", "beta");
    }

    /** The extension resolved the bean, not a second session pointed at the same node. */
    @Test
    void uses_the_context_bean_itself() {
        assertThat(fixtures.getSession()).isSameAs(contextSession);
    }

    @Test
    void resolves_a_bare_session_parameter(CqlSession session) {
        assertThat(session).isSameAs(contextSession);
    }

    @Test
    void reloads_the_rows_before_every_test() {
        assertThat(rowCount()).isEqualTo(4);

        contextSession.execute("truncate " + KEYSPACE + ".widget");

        assertThat(rowCount()).isZero();
    }

    @Test
    void the_fixture_is_intact_at_the_start_of_every_test() {
        assertThat(rowCount()).isEqualTo(4);
    }

    private long rowCount() {
        return contextSession.execute("select count(*) from " + KEYSPACE + ".widget")
                .one().getLong(0);
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {

        @Bean
        CqlSession cqlSession(Environment environment) {
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
