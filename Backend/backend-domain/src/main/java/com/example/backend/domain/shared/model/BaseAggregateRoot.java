package com.example.backend.domain.shared.model;

import java.time.LocalDateTime;

public abstract class BaseAggregateRoot {
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    protected void markCreated() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    protected void markUpdated() {
        this.updateTime = LocalDateTime.now();
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
