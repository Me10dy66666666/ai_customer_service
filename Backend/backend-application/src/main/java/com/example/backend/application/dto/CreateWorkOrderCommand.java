package com.example.backend.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateWorkOrderCommand {
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不超过200字")
    private String title;

    @NotBlank(message = "描述不能为空")
    private String description;

    private String type = "after_sales";

    private String priority = "medium";

    private String sessionId;

    private Long creatorAgentId;
}
