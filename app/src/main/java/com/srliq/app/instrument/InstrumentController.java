package com.srliq.app.instrument;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {

    private final InstrumentRepository instruments;

    public InstrumentController(InstrumentRepository instruments) {
        this.instruments = instruments;
    }

    @GetMapping
    public List<Instrument> list() {
        return instruments.findAll();
    }
}
