package com.example.backend.common.constant;

public final class MessageConstants {

    private MessageConstants() {
    }

    public static final String SUCCESS = "Success";

    public static final String SYSTEM_ERROR = "系统繁忙，请稍后重试";
    public static final String INTERNAL_ERROR = "Internal server error";
    public static final String FILE_OPERATION_FAILED = "文件操作失败";
    public static final String VALIDATION_FAILED = "Validation failed";

    public static final String AGENT_JOINED = "客服已接入，为您服务";
    public static final String WAITING_POSITION = "您正在排队中，前方还有 ";
    public static final String WAITING_POSITION_SUFFIX = " 位用户，预计等待 ";
    public static final String WAITING_POSITION_SECONDS = " 秒";
    public static final String QUEUE_POSITION_PREFIX = "您已在排队中，前方还有 ";
    public static final String QUEUE_POSITION_SUFFIX = " 位用户";
    public static final String QUEUE_ENTERED = "您已进入排队，客服将尽快接入...";
    public static final String QUEUE_CANCELLED = "已取消排队，返回AI服务";
    public static final String NOT_IN_QUEUE = "当前不在排队状态";
    public static final String HUMAN_ENDED = "人工服务已结束，已切换回AI服务";
    public static final String SATISFACTION_PROMPT = "请对本次服务进行评价";
    public static final String SERVICE_ENDED = "人工服务已结束";
    public static final String NOT_IN_HUMAN = "当前不在人工服务状态";
    public static final String USER_ENDED = "用户已结束服务";
    public static final String IN_HUMAN_SESSION = "您正在与客服沟通中，AI 已暂停响应";
    public static final String AGENT_REQUEST_HUMAN = "用户请求转人工，等待客服接入";
    public static final String INVALID_AGENT_ID = "无效的客服ID，请重新登录";
    public static final String SESSION_CLAIMED_BY_OTHER = "该会话已被其他客服认领";
    public static final String TRANSFERRED_TO_OTHER = "已为您转接其他客服";
    public static final String SESSION_CLOSED = "当前服务已结束";
    public static final String SERVICE_ENDED_BACK_TO_AI = "当前服务已结束，已转回AI";

    public static final String CLAIM_SUCCESS = "认领成功";
    public static final String CLAIM_FAILED = "该工单已被其他客服认领或状态已变更";
    public static final String IDS_EMPTY = "ids不能为空";
    public static final String NOTE_EMPTY = "备注内容不能为空";
    public static final String WORK_ORDER_NOT_FOUND = "工单不存在";
    public static final String WORK_ORDER_NO_SESSION = "工单未关联会话";
    public static final String CLOSE_SESSION_FAILED = "关闭会话失败";
}
