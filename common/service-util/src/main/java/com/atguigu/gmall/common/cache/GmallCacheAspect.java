package com.atguigu.gmall.common.cache;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.config.RedissonConfig;
import com.atguigu.gmall.common.constant.RedisConst;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁的切面类，不改变原有代码的情况下，添加功能，保证程序稳定，应对特殊情况
 */
// 组件
@Component
// aop切面
@Aspect
public class GmallCacheAspect {

    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    /**
     * 在环绕通知中处理业务逻辑 {实现分布式锁}
     *
     * @return
     */
    @SneakyThrows
    @Around("@annotation(com.atguigu.gmall.common.cache.GmallCache)")
    public Object cacheAroundAdvice(ProceedingJoinPoint joinPoint) {
        // 声明对象，获取注解标注方法属性
        Object object = new Object();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        GmallCache gmallCache = signature.getMethod().getAnnotation(GmallCache.class);
        // 获取到注解中前缀
        String prefix = gmallCache.prefix();
        // 获取注解标注方法的参数
        Object[] args = joinPoint.getArgs();
        // 设置redis缓存的数据的key为 prefix+参数,Arrays.asList的作用是将数组转化为list
        String key = prefix + Arrays.asList(args).toString();

        // 以上是分布式锁业务的基本准备
        try {
            // 1、从redis中获取缓存数据,这里提出方法便于复用
            object = cacheHit(key, signature);
            // 如果redis中没有数据就重新到数据库中去查
            if (object == null) {
                // 缓存击穿是指缓存中的某一个key失效，如果出现高并发，则直接查询数据库，会导致系统崩溃！
                // 分布式锁就是为了避免缓存击穿，即mysql数据因为突然的高访问量崩坏
                // 设置分布式锁的key值
                String lockKey = prefix + ":lock";
                // 准备上锁
                RLock lock = redissonClient.getLock(lockKey);
                // 使用Redisson尝试给数据访问上锁,tryLock参数是，最多等待1分钟，上锁以后1分钟自动解锁
                boolean flag = lock.tryLock(RedisConst.SKULOCK_EXPIRE_PX1, RedisConst.SKULOCK_EXPIRE_PX2, TimeUnit.SECONDS);
                // 成功上锁
                if (flag) {
                    try {
                        // 执行注解标注方法的业务逻辑，返回结果对象
                        object = joinPoint.proceed(args);
                        // 如果数据库中没有查询到结果，返回null
                        if (object == null) {
                            // 是指用户查询一个在数据库中根本不存在的数据{数据库中没有该记录}，
                            // 那么我们做缓存的时候，不向redis缓存中放入数据的话！会导致缓存穿透。
                            // 为防止缓存穿透，需要往redis中放入一个null的对象,设置过期时间为1小时
                            // 又因为往redis中放入数据需要满足两个条件，1，对象已经序列化，2，对象转换为jso n对象
                            Object object1 = new Object();
                            redisTemplate.opsForValue().set(key, JSON.toJSONString(object1), RedisConst.SECKILL__TIMEOUT, TimeUnit.SECONDS);
                            return object1;
                        } else {
                            redisTemplate.opsForValue().set(key, JSON.toJSONString(object), RedisConst.SECKILL__TIMEOUT, TimeUnit.SECONDS);
                            return object;
                        }
                    } finally {
                        // 执行完上锁的业务逻辑，释放锁
                        lock.unlock();
                    }
                } else {
                    // 上锁失败，即当前有人查数据
                    Thread.sleep(300);
                    // 自旋
                    cacheAroundAdvice(joinPoint);
                }
            } else {
                // 缓存中有数据
                return object;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        // 下下之策，数据库兜底
        return joinPoint.proceed(args);

    }

    /**
     * 从redis中获取缓存数据，并转换为gmallCache注解标注方法返回的对象类型
     *
     * @param key
     * @param signature
     * @return
     */
    private Object cacheHit(String key, MethodSignature signature) {
        // redis返回的是Object对象，平常写是直接可以强转成要返回的数据类型
        // 这里要通用，所以先强转成String
        String redisJson = (String) redisTemplate.opsForValue().get(key);
        // 验证空
        if (!StringUtils.isEmpty(redisJson)) {
            // 获取注解标注方法要返回的对象类型
            Class returnType = signature.getReturnType();
            // fastJson的强大功能
            return JSON.parseObject(redisJson, returnType);
        }
        // 如果没有就返回null
        return null;
    }
}
