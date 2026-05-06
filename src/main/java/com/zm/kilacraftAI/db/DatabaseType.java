package com.zm.kilacraftAI.db;

/**
 * 数据库类型枚举
 *
 * <p>H2 用于单服内嵌模式（零配置），MySQL 用于群组服远程共享模式。</p>
 *
 * @author Zm_Mmm
 */
public enum DatabaseType {

    /**
     * H2 内嵌数据库（单服模式，零配置）
     */
    H2,

    /**
     * MySQL 远程数据库（群组服 BungeeCord 模式）
     */
    MYSQL
}
