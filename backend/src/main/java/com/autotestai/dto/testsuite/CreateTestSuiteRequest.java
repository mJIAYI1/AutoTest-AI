package com.autotestai.dto.testsuite;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTestSuiteRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 4000) String description,
        @NotNull Boolean stopOnFailure,
        @NotEmpty @Size(max = 500) List<@Valid TestSuiteCaseRequest> cases) {
}
