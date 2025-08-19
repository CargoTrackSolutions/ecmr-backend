/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.openlogisticsfoundation.ecmr.persistence.entities.MailSuffixEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.MailSuffixRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class MailSuffixService {

    @Value("${olf.websuffix.url}")
    private String webSuffixUrl;

    private final MailSuffixRepository mailSuffixRepository;

    public Optional<String> getUrl(String mailSuffix) {
        return this.mailSuffixRepository.findByMailSuffix(mailSuffix).map(MailSuffixEntity::getUrl);
    }

    public void loadAndReplaceMailSuffixes() {
        try {
            //get Mail Suffixes without duplicates
            List<MailSuffixEntity> loadedMailSuffixes = new ArrayList<>(
                    this.loadMailSuffixes()
                            .stream()
                            .collect(Collectors.toMap(
                                    MailSuffixEntity::getMailSuffix,
                                    Function.identity(),
                                    (existing, replacement) -> replacement
                            )).values()
            );
            //Find Existing MailSuffixes
            Set<String> existingMailSuffixStrings = mailSuffixRepository.findAll().stream()
                    .map(MailSuffixEntity::getMailSuffix)
                    .collect(Collectors.toSet());

            //Filter existing Mailsuffixes from loaded mailsuffixes
            List<MailSuffixEntity> mailsuffixesToSave = loadedMailSuffixes.stream()
                    .filter(mailSuffix -> !existingMailSuffixStrings.contains(mailSuffix.getMailSuffix()))
                    .toList();
            mailSuffixRepository.saveAll(mailsuffixesToSave);
            log.info("MailSuffix Job -- Loaded {} Mailsuffixes --- saved {} new MailSuffixes", loadedMailSuffixes.size(), mailsuffixesToSave.size());
        } catch (DecodingException e) {
            log.error("Error while loading MailSuffixes: {}", e.getMessage());
        }
    }

    private List<MailSuffixEntity> loadMailSuffixes() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                .jackson2JsonDecoder(new Jackson2JsonDecoder(new ObjectMapper(), MediaType.TEXT_PLAIN)))
                .build();

        WebClient webClient = WebClient.builder().baseUrl(webSuffixUrl).exchangeStrategies(strategies).build();

        return webClient.get()
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<MailSuffixEntity>>() {})
                .block();
    }
}
