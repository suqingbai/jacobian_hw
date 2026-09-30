package com.jacobian.orders;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class OrderIntakeApplicationTests {

  @Test
  void contextLoads() {}
}
