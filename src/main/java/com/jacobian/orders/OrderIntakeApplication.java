package com.jacobian.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the multi-tenant order intake service. */
@SpringBootApplication
public class OrderIntakeApplication {

  public static void main(String[] args) {
    SpringApplication.run(OrderIntakeApplication.class, args);
  }
}
