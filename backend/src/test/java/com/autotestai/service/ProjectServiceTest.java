package com.autotestai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.autotestai.dto.project.CreateProjectRequest;
import com.autotestai.dto.project.ProjectResponse;
import com.autotestai.entity.ProjectEntity;
import com.autotestai.exception.ApiException;
import com.autotestai.mapper.ProjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectMapper projectMapper;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void createNormalizesFieldsAndBindsOwnerFromJwtUserId() {
        CreateProjectRequest request = new CreateProjectRequest(
                " RAG Platform ",
                " Enterprise APIs ",
                "https://test.example.com/api");
        when(projectMapper.findByUserIdAndName(7L, "RAG Platform")).thenReturn(Optional.empty());
        when(projectMapper.insert(any(ProjectEntity.class))).thenAnswer(invocation -> {
            ProjectEntity project = invocation.getArgument(0);
            project.setId(10L);
            return 1;
        });
        ProjectEntity persisted = project(10L, 7L, "RAG Platform");
        persisted.setDescription("Enterprise APIs");
        persisted.setBaseUrl("https://test.example.com/api");
        when(projectMapper.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(persisted));

        ProjectResponse response = projectService.create(7L, request);

        ArgumentCaptor<ProjectEntity> captor = ArgumentCaptor.forClass(ProjectEntity.class);
        verify(projectMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getName()).isEqualTo("RAG Platform");
        assertThat(captor.getValue().getDescription()).isEqualTo("Enterprise APIs");
        assertThat(response.id()).isEqualTo(10L);
    }

    @Test
    void getDoesNotExposeAProjectOwnedByAnotherUser() {
        when(projectMapper.findByIdAndUserId(99L, 8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.get(8L, 99L))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiException.getCode()).isEqualTo("PROJECT_NOT_FOUND");
                });
    }

    @Test
    void createRejectsNonHttpBaseUrlBeforeInsert() {
        when(projectMapper.findByUserIdAndName(7L, "Unsafe")).thenReturn(Optional.empty());
        CreateProjectRequest request = new CreateProjectRequest("Unsafe", null, "file:///etc/passwd");

        assertThatThrownBy(() -> projectService.create(7L, request))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiException.getCode()).isEqualTo("INVALID_BASE_URL");
                });
        verify(projectMapper, never()).insert(any(ProjectEntity.class));
    }

    private static ProjectEntity project(long id, long userId, String name) {
        ProjectEntity project = new ProjectEntity();
        project.setId(id);
        project.setUserId(userId);
        project.setName(name);
        return project;
    }
}
