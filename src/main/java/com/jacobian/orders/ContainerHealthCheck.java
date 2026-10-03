package com.jacobian.orders;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Container healthcheck probe: exits 0 when the actuator health endpoint answers 200 (UP) and 1
 * otherwise. The distroless image has no shell or curl, so the Compose healthcheck runs this class
 * with the image's own {@code java}. It is a plain {@code main}, so it starts without Spring.
 */
public final class ContainerHealthCheck {

  static final URI DEFAULT_URI = URI.create("http://localhost:8080/actuator/health");

  private static final Duration TIMEOUT = Duration.ofSeconds(3);

  private ContainerHealthCheck() {}

  /** Probes the URI given as the only argument, or {@link #DEFAULT_URI}. */
  public static void main(String[] args) {
    URI uri = args.length > 0 ? URI.create(args[0]) : DEFAULT_URI;
    System.exit(isHealthy(uri) ? 0 : 1);
  }

  static boolean isHealthy(URI uri) {
    HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
    try {
      return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
