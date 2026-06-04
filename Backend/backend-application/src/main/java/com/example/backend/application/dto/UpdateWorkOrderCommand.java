package com.example.backend.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateWorkOrderCommand {
    @NotBlank(message = "状态不能为空")
    private String status;

    private Long handlerId;

    private String result;
}
