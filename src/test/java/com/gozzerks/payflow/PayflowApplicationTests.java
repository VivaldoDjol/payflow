package com.gozzerks.payflow;

import com.gozzerks.payflow.config.TestSecurityConfig;
import com.gozzerks.payflow.config.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import({TestcontainersConfiguration.class, TestSecurityConfig.class})
@SpringBootTest
class PayflowApplicationTests {

    @Test
    void contextLoads() {
    }
}