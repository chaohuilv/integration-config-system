package com.integration.config.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

/**
 * 雪花ID生成器
 */
public class SnowflakeUtil {

    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(1, 1);

    /**
     * 生成下一个雪花ID
     */
    public static long nextId() {
        return SNOWFLAKE.nextId();
    }

    /**
     * 生成下一个雪花ID字符串
     */
    public static String nextIdStr() {
        return String.valueOf(SNOWFLAKE.nextId());
    }
}
