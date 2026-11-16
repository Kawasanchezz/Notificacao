package com.taskscheduler.viacep;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.taskscheduler.config.Properties;
import com.taskscheduler.exception.Exceptions.BadRequestException;
import com.taskscheduler.exception.Exceptions.ExternalServiceException;
import com.taskscheduler.exception.Exceptions.NotFoundException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Component
public class ViaCepClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ViaCepResponse(String cep, String logradouro, String complemento, String bairro,
                                 String localidade, String uf, Boolean erro) {}

    private final RestClient client;

    public ViaCepClient(Properties.ViaCep props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build();
    }

    /** Returns the address for a CEP; the returned cep is normalized to 8 digits (no hyphen). */
    public ViaCepResponse lookup(String rawCep) {
        String cep = rawCep == null ? "" : rawCep.replaceAll("\\D", "");
        if (cep.length() != 8) throw new BadRequestException("CEP must have 8 digits");
        ViaCepResponse body;
        try {
            body = client.get().uri("/{cep}/json/", cep).retrieve().body(ViaCepResponse.class);
        } catch (RestClientException e) {
            throw new ExternalServiceException("ViaCEP is unavailable, try again later", e);
        }
        if (body == null || Boolean.TRUE.equals(body.erro())) throw new NotFoundException("CEP not found");
        return new ViaCepResponse(cep, body.logradouro(), body.complemento(), body.bairro(),
                body.localidade(), body.uf(), null);
    }
}
