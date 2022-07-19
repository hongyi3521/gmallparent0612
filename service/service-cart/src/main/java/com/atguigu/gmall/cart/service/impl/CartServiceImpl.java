package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartAsyncService;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl extends ServiceImpl<CartInfoMapper, CartInfo> implements CartService {
    @Autowired
    private CartInfoMapper cartInfoMapper;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private CartAsyncService cartAsyncService;


    /**
     * 总体来说业务就是，用户点击加入购物车，有就加数量，没有就创建购物车信息
     * 数据库一份，redis缓存一份
     *
     * @param skuId
     * @param userId
     * @param skuNum
     */
    @Override
    public void addToCart(Long skuId, String userId, Integer skuNum) {
        // 得到缓存的key
        String cartKey = getCartKey(userId);
        Boolean aBoolean = redisTemplate.hasKey(cartKey);
        if (!aBoolean) {
            // 缓存中没有数据，可能是redis中数据过期了，需要从数据库中查出数据，同步到redis中
            List<CartInfo> cartInfoList = loadCartCache(userId);
        }
        // 1、redis中查出userId下skuId中的数据
        CartInfo cartInfoExist = (CartInfo) redisTemplate.boundHashOps(cartKey).get(skuId.toString());
        // 用户该商品已加入购物车,更新数据库数据+skuNum
        if (null != cartInfoExist) {
            // 改数量
            cartInfoExist.setSkuNum(cartInfoExist.getSkuNum() + skuNum);
            // 更新价格
            cartInfoExist.setCartPrice(productFeignClient.getSkuPrice(skuId));
            // 更新修改时间
            cartInfoExist.setUpdateTime(new Timestamp(new Date().getTime()));
            // 默认选中
            cartInfoExist.setIsChecked(1);
            // 数据库更新，采用异步方式
            cartAsyncService.updateCartInfo(cartInfoExist);
        } else {
            // 此时redis为null
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);

            CartInfo cartInfo = new CartInfo();
            cartInfo.setUserId(userId);
            cartInfo.setSkuId(skuId);
            // 加入购物车是价格
            cartInfo.setCartPrice(skuInfo.getPrice());
            // 数据库不存在，只存在redis中，表示实时价格
            cartInfo.setSkuPrice(skuInfo.getPrice());
            // 设置第一次加入购物车商品数量
            cartInfo.setSkuNum(skuNum);
            // 设置商品默认显示图片
            cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());
            // 设置商品名
            cartInfo.setSkuName(skuInfo.getSkuName());
            cartInfo.setCreateTime(new Timestamp(new Date().getTime()));
            cartInfo.setUpdateTime(new Timestamp(new Date().getTime()));
            cartInfoExist = cartInfo;
            // 创建购物车数据库数据
            cartAsyncService.saveCartInto(cartInfoExist);
        }
        // 2、统一将数据加载到redis
        redisTemplate.boundHashOps(cartKey).put(skuId.toString(), cartInfoExist);
        // 设置redis中缓存数据过期时间,每次添加购物车后，重新设置过期时间
        setCartKeyExpire(cartKey);
    }

    @Override
    public List<CartInfo> getCartList(String userId, String userTempId) {
        // 根据用户有没有登录，决定用户什么id
        List<CartInfo> cartInfoList = new ArrayList<>();
        // 没有用户id，即没有登录，直接返回临时用户数据
        if (StringUtils.isEmpty(userId)) {
            cartInfoList = getCartList(userTempId);
            return cartInfoList;
        } else {
            // 用户已登录
            // 1、得到临时用户数据
            List<CartInfo> cartInfoNoLoginList = getCartList(userTempId);
            // 有临时用户数据
            if (!CollectionUtils.isEmpty(cartInfoNoLoginList)) {
                // 传递用户id，并合并数据后返回
                cartInfoList = this.mergeToCartList(cartInfoNoLoginList, userId);
                // 合并后删除临时用户数据
                this.deleteCartList(userTempId);
            }
            // 用户未登录情况下没有添加购物车，没有生成临时id，就不需要合并,
            if (StringUtils.isEmpty(userTempId) || CollectionUtils.isEmpty(cartInfoNoLoginList)) {
                cartInfoList = getCartList(userId);
            }
            return cartInfoList;
        }
    }

    @Override
    public void checkCart(String userId, Integer isChecked, Long skuId) {
        // 修改数据选中状态
        cartAsyncService.checkCart(userId, isChecked, skuId);
        // 修改redis数据
        String cartKey = getCartKey(userId);
        BoundHashOperations boundHashOperations = redisTemplate.boundHashOps(cartKey);
        Boolean aBoolean = boundHashOperations.hasKey(skuId.toString());
        if (aBoolean) {
            CartInfo cartInfo = (CartInfo) boundHashOperations.get(skuId.toString());
            cartInfo.setIsChecked(isChecked);
            boundHashOperations.put(skuId.toString(), cartInfo);
            // 设置缓存过期时间
            this.setCartKeyExpire(cartKey);
        }
    }

    @Override
    public void deleteCart(String userId, Long skuId) {
        // 删除数据库数据
        cartAsyncService.deleteCart(userId, skuId);
        String cartKey = getCartKey(userId);
        Boolean aBoolean = redisTemplate.boundHashOps(cartKey).hasKey(skuId.toString());
        if (aBoolean) {
            redisTemplate.boundHashOps(cartKey).delete(skuId.toString());
        }
    }

    @Override
    public List<CartInfo> getCartCheckedList(String userId) {
        // 先取出有购物车中的信息，可以去结算，说明缓存中经过查询已经有数据了
        List<CartInfo> list = redisTemplate.opsForHash().values(getCartKey(userId));
        // 选中的购物车数据，就是cartinfo中isChecked数据为1的数据
        List<CartInfo> cartInfoList = new ArrayList<>();
        // 验证空
        if (list.size() > 0 && null != list)
            for (CartInfo cartInfo : list) {
                if (cartInfo.getIsChecked().intValue() == 1) {
                    cartInfoList.add(cartInfo);
                }
            }
        return cartInfoList;
    }

    /**
     * 删除临时数据
     *
     * @param userTempId
     */
    private void deleteCartList(String userTempId) {
        // 1、删除数据库
        cartAsyncService.deleteCartInfo(userTempId);
        // 2、删除redis
        String cartKey = getCartKey(userTempId);
        // 先判断redis中有没有这个key
        if (redisTemplate.hasKey(cartKey)) {
            redisTemplate.delete(cartKey);
        }
    }

    /**
     * 利用用户id，先查出数据，在和临时数据合并
     *
     * @param cartInfoNoLoginList
     * @param userId
     * @return
     */
    private List<CartInfo> mergeToCartList(List<CartInfo> cartInfoNoLoginList, String userId) {
        List<CartInfo> cartInfoLoginList = getCartList(userId);
        // 1、根据购物车skuId 提炼用户已登录数据，为map
        Map<Long, CartInfo> longCartInfoMap = cartInfoLoginList.stream()
                .collect(Collectors.toMap(CartInfo::getSkuId, cartInfo -> cartInfo));
        // 遍历临时用户数据
        for (CartInfo cartInfo : cartInfoNoLoginList) {
            // 根据临时用户数据里的skuid判断是否存在该商品id，数量相加
            Long skuId = cartInfo.getSkuId();
            // 登录的数据中有同样的商品数据
            if (longCartInfoMap.containsKey(skuId)) {
                CartInfo loginCartInfo = longCartInfoMap.get(skuId);
                // 购买数量相加
                loginCartInfo.setSkuNum(loginCartInfo.getSkuNum() + cartInfo.getSkuNum());
                // 更新修改时间
                loginCartInfo.setUpdateTime(new Timestamp(new Date().getTime()));
                // 更新选中状态，未登录选中了就更新登录状态的
                if (cartInfo.getIsChecked() == 1) {
                    loginCartInfo.setIsChecked(1);
                }
                // 合并后数据同步到mysql
                QueryWrapper<CartInfo> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("user_id", userId);
                queryWrapper.eq("sku_id", skuId);
                cartInfoMapper.update(loginCartInfo, queryWrapper);
            } else {
                // 商品数据中没有，就直接添加到登录中
                cartInfo.setUserId(userId);
                // 合并的时候正式加入购物车，设置时间
                cartInfo.setCreateTime(new Timestamp(new Date().getTime()));
                cartInfo.setUpdateTime(new Timestamp(new Date().getTime()));
                // 添加数据到数据库
                cartInfoMapper.insert(cartInfo);
            }
        }
        // 统一查询数据库返回数据,并不是两个集合合并，只是数据筛选，添加修改数据库
        // 因异步更新数据库可能会在查询方法后面执行，导致查不到更新后的数据，所以这里用同步更新
        List<CartInfo> cartInfoList = loadCartCache(userId);
        return cartInfoList;
    }

    /**
     * 重载一个方法，都是从数据库中查数据
     *
     * @param userId
     * @return
     */
    public List<CartInfo> getCartList(String userId) {
        List<CartInfo> cartInfoList = new ArrayList<>();

        String cartKey = getCartKey(userId);
        // 先从缓存中取数据
        cartInfoList = redisTemplate.opsForHash().values(cartKey);
        // 判断缓存中有没有
        if (!CollectionUtils.isEmpty(cartInfoList)) {
            // 缓存中有数据，就对缓存中的数据进行排序
            cartInfoList = cartInfoList.stream()
                    .sorted(Comparator.comparing(CartInfo::getUpdateTime).reversed())
                    .collect(Collectors.toList());
//            cartInfoList.sort(new Comparator<CartInfo>() {
//                @Override
//                public int compare(CartInfo o1, CartInfo o2) {
//                    return DateUtil.truncatedCompareTo(o1.getUpdateTime(),o2.getUpdateTime(),Calendar.SECOND);
//                }
//            });
            return cartInfoList;
        } else {
            // 缓存中没有从数据库中取出，并放入缓存
            cartInfoList = loadCartCache(userId);
        }
        return cartInfoList;
    }

    /**
     * 同步mysql和redis的数据
     *
     * @param userId
     * @return
     */
    public List<CartInfo> loadCartCache(String userId) {
        // 通过userId查询购物车数据
        QueryWrapper<CartInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.orderByDesc("update_time");

        List<CartInfo> cartInfoList = cartInfoMapper.selectList(queryWrapper);
        // 数据库中没有数据，redis就不用同步了
        if (CollectionUtils.isEmpty(cartInfoList)) {
            return new ArrayList<CartInfo>();
        } else {
            // 同步数据到redis
            String cartKey = getCartKey(userId);
            Map<String, Object> map = new HashMap<>();
            for (CartInfo cartInfo : cartInfoList) {
                // 商品id
                Long skuId = cartInfo.getSkuId();
                // 当前商品实时价格不存入数据库，只存加入购物车时的价格,因为随时会变
                BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
                cartInfo.setSkuPrice(skuPrice);
                map.put(skuId.toString(), cartInfo);
            }
            // 将购物车商品数据批量放入缓存
            redisTemplate.opsForHash().putAll(cartKey, map);
            // 设置过期时间
            setCartKeyExpire(cartKey);
            // 集合存的是数据内存地址
            return cartInfoList;
        }
    }

    // 设置过期时间7天
    private void setCartKeyExpire(String cartKey) {
        redisTemplate.expire(cartKey, RedisConst.USER_CART_EXPIRE, TimeUnit.SECONDS);
    }

    // 获取购物车的key , 因为会反复使用，所以提炼出来
    private String getCartKey(String userId) {
        //定义key user:userId:cart
        return RedisConst.USER_KEY_PREFIX + userId + RedisConst.USER_CART_KEY_SUFFIX;
    }

}
