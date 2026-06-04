package com.example.backend.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransferWorkOrderCommand {
    @NotNull(message = "目标处理人ID不能为空")
    private Long targetHandlerId;

    private String reason;
}
