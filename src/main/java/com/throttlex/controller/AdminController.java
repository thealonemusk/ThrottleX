package com.throttlex.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @GetMapping("/status")
    public String status() {
        return "ThrottleX running";
    }
}
