package com.voidvvv.kz_auto_chess_n.config;

/**
 * 加载即校验失败（data_schema §二.3 / §九.7）：缺必填字段、枚举非法、引用悬空、数值越界等
 * 启动期直接抛错即死，不带病运行。消息格式：{@code 文件#条目id/字段路径: 问题}。
 */
public class DataValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DataValidationException(String message) {
        super(message);
    }
}
