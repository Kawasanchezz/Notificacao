package com.taskscheduler.viacep;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cep")
@Tag(name = "CEP")
public class CepController {

    private final ViaCepClient viaCep;

    public CepController(ViaCepClient viaCep) { this.viaCep = viaCep; }

    @GetMapping("/{cep}")
    @Operation(summary = "Look up an address by CEP (via ViaCEP)")
    public ViaCepClient.ViaCepResponse lookup(@PathVariable String cep) { return viaCep.lookup(cep); }
}
