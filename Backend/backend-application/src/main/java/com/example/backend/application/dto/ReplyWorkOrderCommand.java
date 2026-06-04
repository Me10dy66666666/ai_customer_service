package com.example.backend.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReplyWorkOrderCommand {
    @NotBlank(message = "回复内容不能为空")
    private String content;

    private Long agentId;
}
