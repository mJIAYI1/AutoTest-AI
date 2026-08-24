package com.autotestai.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autotestai.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenApiParserServiceTest {

    private OpenApiParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new OpenApiParserService(new ObjectMapper());
    }

    @Test
    void parsesOpenApiYamlAndMergesPathAndOperationParameters() {
        String yaml = """
                openapi: 3.0.3
                info:
                  title: Demo API
                  version: 1.0.0
                paths:
                  /users/{id}:
                    parameters:
                      - name: id
                        in: path
                        required: true
                        schema:
                          type: integer
                    get:
                      operationId: getUser
                      summary: Get a user
                      tags: [Users]
                      parameters:
                        - name: verbose
                          in: query
                          schema:
                            type: boolean
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema:
                                type: object
                """;

        ParsedOpenApiDocument document = parserService.parse(yaml);

        assertThat(document.title()).isEqualTo("Demo API");
        assertThat(document.version()).isEqualTo("1.0.0");
        assertThat(document.definitions()).hasSize(1);
        ParsedApiDefinition api = document.definitions().get(0);
        assertThat(api.method()).isEqualTo("GET");
        assertThat(api.path()).isEqualTo("/users/{id}");
        assertThat(api.tagsJson()).contains("Users");
        assertThat(api.parametersJson()).contains("id", "verbose");
        assertThat(api.responseSchemaJson()).contains("200", "application/json");
    }

    @Test
    void convertsSwaggerTwoDocumentIntoInternalDefinition() {
        String swagger = """
                {
                  "swagger": "2.0",
                  "info": {"title": "Legacy API", "version": "1.0"},
                  "paths": {
                    "/legacy": {
                      "get": {
                        "operationId": "getLegacy",
                        "responses": {"200": {"description": "OK"}}
                      }
                    }
                  }
                }
                """;

        ParsedOpenApiDocument document = parserService.parse(swagger);

        assertThat(document.definitions()).singleElement()
                .satisfies(api -> {
                    assertThat(api.method()).isEqualTo("GET");
                    assertThat(api.path()).isEqualTo("/legacy");
                });
    }

    @Test
    void rejectsDocumentWithoutOperations() {
        String yaml = """
                openapi: 3.0.3
                info:
                  title: Empty API
                  version: 1.0.0
                paths: {}
                """;

        assertThatThrownBy(() -> parserService.parse(yaml))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getCode())
                        .isEqualTo("OPENAPI_NO_OPERATIONS"));
    }
}
