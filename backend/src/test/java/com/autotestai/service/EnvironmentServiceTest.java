package com.autotestai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import com.autotestai.dto.environment.CreateEnvironmentRequest;
import com.autotestai.dto.environment.EnvironmentResponse;
import com.autotestai.entity.EnvironmentEntity;
import com.autotestai.entity.ProjectEntity;
import com.autotestai.exception.ApiException;
import com.autotestai.mapper.EnvironmentMapper;
import com.autotestai.mapper.ProjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class EnvironmentServiceTest {

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private EnvironmentMapper environmentMapper;

    private EnvironmentService environmentService;

    @BeforeEach
    void setUp() {
        environmentService = new EnvironmentService(
                projectMapper,
                environmentMapper,
                new ObjectMapper());
    }

    @Test
    void createBindsOwnedProjectAndSerializesConfiguration() {
        when(projectMapper.findByIdAndUserId(20L, 7L))
                .thenReturn(Optional.of(project(20L, 7L)));
        when(environmentMapper.findByNameOwned(20L, 7L, "Development"))
                .thenReturn(Optional.empty());
        when(environmentMapper.insert(any(EnvironmentEntity.class))).thenAnswer(invocation -> {
            EnvironmentEntity environment = invocation.getArgument(0);
            environment.setId(30L);
            return 1;
        });
        EnvironmentEntity stored = environment(30L, 20L, "Development");
        stored.setBaseUrl("http://localhost:8081");
        stored.setHeadersJson("{\"Content-Type\":\"application/json\"}");
        stored.setVariablesJson("{\"token\":\"dev-token\"}");
        when(environmentMapper.findByIdOwned(30L, 20L, 7L)).thenReturn(Optional.of(stored));

        EnvironmentResponse response = environmentService.create(
                7L,
                20L,
                new CreateEnvironmentRequest(
                        " Development ",
                        " http://localhost:8081 ",
                        Map.of("Content-Type", "application/json"),
                        Map.of("token", "dev-token")));

        ArgumentCaptor<EnvironmentEntity> captor = ArgumentCaptor.forClass(EnvironmentEntity.class);
        verify(environmentMapper).insert(captor.capture());
        assertThat(captor.getValue().getProjectId()).isEqualTo(20L);
        assertThat(captor.getValue().getName()).isEqualTo("Development");
        assertThat(captor.getValue().getHeadersJson()).contains("Content-Type");
        assertThat(response.variables()).containsEntry("token", "dev-token");
    }

    @Test
    void listDoesNotRevealAnotherUsersProject() {
        when(projectMapper.findByIdAndUserId(20L, 8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> environmentService.list(8L, 20L))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiException.getCode()).isEqualTo("PROJECT_NOT_FOUND");
                });
        verifyNoInteractions(environmentMapper);
    }

    @Test
    void createRejectsHeaderInjectionBeforeInsert() {
        when(projectMapper.findByIdAndUserId(20L, 7L))
                .thenReturn(Optional.of(project(20L, 7L)));
        when(environmentMapper.findByNameOwned(20L, 7L, "Unsafe"))
                .thenReturn(Optional.empty());
        CreateEnvironmentRequest request = new CreateEnvironmentRequest(
                "Unsafe",
                "https://api.example.com",
                Map.of("X-Unsafe", "ok\r\nInjected: value"),
                Map.of());

        assertThatThrownBy(() -> environmentService.create(7L, 20L, request))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiException.getCode()).isEqualTo("INVALID_ENVIRONMENT_CONFIG");
                });
        verify(environmentMapper, never()).insert(any(EnvironmentEntity.class));
    }

    private static ProjectEntity project(long id, long userId) {
        ProjectEntity project = new ProjectEntity();
        project.setId(id);
        project.setUserId(userId);
        project.setName("Payment APIs");
        return project;
    }

    private static EnvironmentEntity environment(long id, long projectId, String name) {
        EnvironmentEntity environment = new EnvironmentEntity();
        environment.setId(id);
        environment.setProjectId(projectId);
        environment.setName(name);
        return environment;
    }
}
