package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryShopType() {
        // 从redis中查询商铺类型数据
        List<String> shopTypeJsonList = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);

        // 判断集合是否为空
        if(shopTypeJsonList != null && !shopTypeJsonList.isEmpty()){
            List<ShopType> typeList = new ArrayList<>();
            for(String shopType : shopTypeJsonList){
                typeList.add(JSONUtil.toBean(shopType, ShopType.class));
            }

            return Result.ok(typeList);
        }

        // 不存在，查询数据库
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();

        // 数据库中不存在，返回错误

        if(shopTypeList == null || shopTypeList.isEmpty()){
            return Result.fail("查询数据不存在");
        }
        // 数据库中存在，写入到redis中

        for(ShopType shopType: shopTypeList){
            stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(shopType));
        }

        // 返回
        return Result.ok(shopTypeList);
    }
}
