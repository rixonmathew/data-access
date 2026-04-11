package com.rixon.learn.spring.data.oracle26ai;

import com.rixon.model.instrument.Instrument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(resolver = OSActiveProfilesResolver.class)
class OracleDBApplicationTests {

	private static final Logger LOGGER = LoggerFactory.getLogger(OracleDBApplicationTests.class);

	@LocalServerPort
	private int port;

	@Autowired
	private DatabaseClient databaseClient;

	private WebTestClient webTestClient;

	@BeforeEach
	void setUp() {
		webTestClient = WebTestClient.bindToServer()
				.baseUrl("http://localhost:" + port)
				.responseTimeout(Duration.ofSeconds(30))
				.build();
	}

	@Test
	void contextLoads() {
	}

	@Test
	void testR2dbcConnectivity() {
		databaseClient.sql("SELECT 'Hello from Oracle AI' FROM dual")
				.map((row, metadata) -> row.get(0, String.class))
				.one()
				.as(StepVerifier::create)
				.expectNext("Hello from Oracle AI")
				.verifyComplete();
	}

	@Test
	void testR2dbInstrumentQuery(){
		databaseClient.sql("select max(id) from instrument")
				.map((row, metadata) -> row.get(0, Long.class))
				.one()
				.as(StepVerifier::create)
				.expectNextMatches(id -> id >= 100)
				.verifyComplete();
	}

	@Test
	void testGetAllInstruments() {
		webTestClient.get().uri("/instruments")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentType(MediaType.APPLICATION_JSON)
				.expectBodyList(Instrument.class)
				.value(list -> assertThat(list).isNotEmpty());
	}

	@Test
	void testStreamInstruments() {
		webTestClient.get().uri("/instruments/stream")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
				.returnResult(Instrument.class)
				.getResponseBody()
				.take(5)
				.as(StepVerifier::create)
				.expectNextCount(5)
				.verifyComplete();
	}

	@Test
	void testGetInstrumentById() {

		Long id = databaseClient.sql("SELECT max(id) FROM instrument")
				.map((row, metadata) -> row.get(0, Long.class))
				.one()
				.block();
		assertThat(id).isNotNull();

		webTestClient.get().uri("/instruments/id/" + id)
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

		Instrument instrument = new Instrument();
		instrument.setName("Test Instrument");
		instrument.setType("Test Type");
		instrument.setMetadata("Test Metadata");

		webTestClient.post().uri("/instruments")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(instrument)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Instrument.class)
				.consumeWith(result -> {
					Instrument savedInstrument = result.getResponseBody();
					assertThat(savedInstrument).isNotNull();
					assertThat(savedInstrument.getId()).isNotNull();
				});

//		//delete the instrument created after test
//		webTestClient.delete().uri("/instruments/10951")
//				.exchange()
//				.expectStatus().isOk();
	}

}
