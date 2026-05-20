package com.medicology.dictionary;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "jwt.secret=0123456789abcdef0123456789abcdef")
class ApplicationTests {

    @Test
    void contextLoads() {
    }
}
