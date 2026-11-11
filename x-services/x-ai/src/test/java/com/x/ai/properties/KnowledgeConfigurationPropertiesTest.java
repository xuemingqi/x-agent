package com.x.ai.properties;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeConfigurationPropertiesTest {

    private static ValidatorFactory validatorFactory;

    private static Validator validator;

    @BeforeAll
    static void initializeValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void missingConnectionConfigurationIsRejectedInsteadOfUsingMachineDefaults() {
        Set<String> missing = validator.validate(new KnowledgeVectorProperties()).stream()
                .map(violation -> violation.getPropertyPath().toString()).collect(Collectors.toSet());

        assertEquals(Set.of("jdbcUrl", "username", "password"), missing);
    }

    @Test
    void missingEmbeddingConfigurationIsRejectedInsteadOfSelectingAModel() {
        Set<String> missing = validator.validate(new KnowledgeEmbeddingProperties()).stream()
                .map(violation -> violation.getPropertyPath().toString()).collect(Collectors.toSet());

        assertEquals(Set.of("baseUrl", "model", "dimensions", "batchSize", "timeoutSeconds"), missing);
    }

    @Test
    void missingIndexingConfigurationReportsAllRequiredFields() {
        Set<String> missing = validator.validate(new KnowledgeProperties()).stream()
                .map(violation -> violation.getPropertyPath().toString()).collect(Collectors.toSet());

        assertEquals(Set.of("maxUploadSizeBytes", "chunkSize", "chunkOverlap", "maxContentCharacters", "minScore"),
                missing);
    }

    @Test
    void indexingConfigurationRequiresOverlapSmallerThanChunkSize() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setMaxUploadSizeBytes(1024L);
        properties.setChunkSize(100);
        properties.setChunkOverlap(20);
        properties.setMaxContentCharacters(2000);
        properties.setMinScore(0.5);
        assertTrue(validator.validate(properties).isEmpty());

        properties.setChunkOverlap(100);
        assertEquals(Set.of("chunkOverlapValid"), validator.validate(properties).stream()
                .map(violation -> violation.getPropertyPath().toString()).collect(Collectors.toSet()));
    }
}
