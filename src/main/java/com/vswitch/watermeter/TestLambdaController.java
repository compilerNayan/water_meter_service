package com.vswitch.watermeter;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestLambdaController {

    @GetMapping("/testlamda")
    public String testLambda() {
        return "Hello world";
    }
}
