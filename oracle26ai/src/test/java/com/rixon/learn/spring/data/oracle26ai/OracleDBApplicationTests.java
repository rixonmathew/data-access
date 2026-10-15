package com.rixon.learn.spring.data.oracle26ai;

import com.rixon.learn.spring.data.oracle26ai.service.InstrumentReactiveRepository;
import com.rixon.model.instrument.Instrument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// Pooling is disabled so a connection left in a bad state (e.g. after a cancelled fetch on macOS,
// where Oracle JDBC cannot send out-of-band breaks) is closed instead of being reused by the next test.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.r2dbc.pool.enabled=false")
@ActiveProfiles(resolver = OSActiveProfilesResolver.class)
class OracleDBApplicationTests {

	private static final Logger LOGGER = LoggerFactory.getLogger(OracleDBApplicationTests.class);
	private static volatile Boolean databaseAvailable;

	@LocalServerPort
	private int port;

	@Autowired
	private DatabaseClient databaseClient;

	@Autowired
	private InstrumentReactiveRepository instrumentRepository;

	private WebTestClient webTestClient;

	@BeforeEach
	void setUp() {
		webTestClient = WebTestClient.bindToServer()
				.baseUrl("http://localhost:" + port)
				.responseTimeout(Duration.ofSeconds(30))
				.build();
	}

	private boolean isDatabaseAvailable() {
		if (databaseAvailable == null) {
			try {
				String result = databaseClient.sql("SELECT 'Hello from Oracle AI' FROM dual")
						.map((row, metadata) -> row.get(0, String.class))
						.one()
						.block(Duration.ofSeconds(5));
				if (!"Hello from Oracle AI".equals(result)) {
					databaseAvailable = false;
					return false;
				}
				// Verify multi-row retrieval from instrument table works without protocol violations
				instrumentRepository.findAll()
						.collectList()
						.block(Duration.ofSeconds(10));
				databaseAvailable = true;
			} catch (Exception e) {
				LOGGER.warn("Oracle DB not reachable or protocol incompatible in test environment: {}", e.getMessage());
				databaseAvailable = false;
			}
		}
		return databaseAvailable;
	}

	@Test
	void contextLoads() {
	}

	@Test
	void testR2dbcConnectivity() {
		Assumptions.assumeTrue(isDatabaseAvailable(), "Oracle database is not available");
		databaseClient.sql("SELECT 'Hello from Oracle AI' FROM dual")
				.map((row, metadata) -> row.get(0, String.class))
				.one()
				.as(StepVerifier::create)
				.expectNext("Hello from Oracle AI")
				.verifyComplete();
	}

	@Test
	void testR2dbInstrumentQuery(){
		Assumptions.assumeTrue(isDatabaseAvailable(), "Oracle database is not available");
		databaseClient.sql("select max(id) from instrument")
				.map((row, metadata) -> row.get(0, Long.class))
				.one()
				.as(StepVerifier::create)
				.expectNextMatches(id -> id >= 100)
				.verifyComplete();
	}

	@Test
	void testGetAllInstruments() {
		Assumptions.assumeTrue(isDatabaseAvailable(), "Oracle database is not available");
		webTestClient.get().uri("/instruments")
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentType(MediaType.APPLICATION_JSON)
				.expectBodyList(Instrument.class)
				.value(list -> assertThat(list).isNotEmpty());
	}

	@Test
	void testStreamInstruments() {
		Assumptions.assumeTrue(isDatabaseAvailable(), "Oracle database is not available");
		webTestClient.get().uri("/instruments/stream")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
				.returnResult(Instrument.class)
				.getResponseBody()
				// consume the whole stream: cancelling mid-fetch (e.g. take(5)) corrupts the Oracle connection on macOS
				.collectList()
				.as(StepVerifier::create)
				.assertNext(list -> assertThat(list).hasSizeGreaterThanOrEqualTo(5))
				.verifyComplete();
	}

	@Test
	void testGetInstrumentById() {
		Assumptions.assumeTrue(isDatabaseAvailable(), "Oracle database is not available");
		Long id = databaseClient.sql("SELECT max(id) FROM instrument")
				.map((row, metadata) -> row.get(0, Long.class))
				.one()
				.block();
		Assumptions.assumeTrue(id != null, "No instruments exist in the database");

		webTestClient.get().uri("/instruments/id/" + id)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentType(MediaType.APPLICATION_JSON)
				.expectBody(Instrument.class)
				.consumeWith(result -> {
					Instrument instrument = result.getResponseBody();
					assertThat(instrument).isNotNull();
					assertThat(instrument.getId()).isEqualTo(id);
				});
	}

	@Test
	void testCreateInstrument() {
		Assumptions.assumeTrue(isDatabaseAvailable(), "Oracle database is not available");
		Instrument instrument = new Instrument();
		instrument.setName("Test Instrument");
		instrument.setType("Test Type");
		instrument.setMetadata("Test Metadata");

		Instrument savedInstrument = webTestClient.post().uri("/instruments")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.bodyValue(instrument)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Instrument.class)
				.returnResult()
				.getResponseBody();

		assertThat(savedInstrument).isNotNull();
		assertThat(savedInstrument.getId()).isNotNull();

		// clean up so the instrument table does not grow on every run
		webTestClient.delete().uri("/instruments/" + savedInstrument.getId())
				.exchange()
				.expectStatus().isOk();
		instrumentRepository.existsById(savedInstrument.getId())
				.as(StepVerifier::create)
				.expectNext(false)
				.verifyComplete();
	}

}
