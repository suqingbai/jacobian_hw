package com.jacobian.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ContainerHealthCheckTests {

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void healthyWhenEndpointAnswers200() throws IOException {
    assertThat(ContainerHealthCheck.isHealthy(serve(200))).isTrue();
  }

  @Test
  void unhealthyWhenEndpointAnswers503() throws IOException {
    assertThat(ContainerHealthCheck.isHealthy(serve(503))).isFalse();
  }

  @Test
  void unhealthyWhenNothingListens() throws IOException {
    URI uri = serve(200);
    server.stop(0);
    server = null;

    assertThat(ContainerHealthCheck.isHealthy(uri)).isFalse();
  }

  private URI serve(int status) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/actuator/health",
        exchange -> {
          exchange.sendResponseHeaders(status, -1);
          exchange.close();
        });
    server.start();
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/actuator/health");
  }
}
