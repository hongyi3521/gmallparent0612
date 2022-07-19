package com.atguigu.gmall.common.cache;

import java.lang.annotation.*;

// 在方法上使用
@Target({ElementType.METHOD})
// 编译成字节码和jvm中加载都生效
@Retention(RetentionPolicy.RUNTIME)
public @interface GmallCache {

    /**
     * redis缓存key的前缀,每个方法添加注解GmallCache时可以加
     * @return
     */
    String prefix() default "cache";
}
