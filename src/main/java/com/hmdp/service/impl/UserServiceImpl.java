package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {

        // 1. 校验手机号

        if(RegexUtils.isPhoneInvalid(phone)){

            // 2. 如果不符合，返回错误信息

            return Result.fail("手机号格式有误");

        }

        // 3. 如果符合，生成验证码
        // 表示生成6位数的随机数
        String code = RandomUtil.randomNumbers(6);

        // 4. 保存验证码到session，login:code是key的前缀，2表示存两分钟，TimeUnit.MINUTES表示存储的时间单位
        //session.setAttribute("code", code);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 5. 发送验证码
        log.debug("发送验证码成功！验证码为：" + code);
        // 6. 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        // 1. 校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            // 如果不符合，返回错误信息
            return Result.fail("手机号格式有误");
        }

        // 2. 校验验证码
        // 从redis中取出发送的验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        // 从http的请求中取出用户填入的验证码
        String code = loginForm.getCode();
        // 判断session中的验证码是否为空，若为空，说明session中存的验证码过期了
        // 判断session中的验证码和用户输入的验证码是否一致，若不一致，提示错误
        if(cacheCode == null || !cacheCode.equals(code)){
            // 不一致，报错
            return Result.fail("验证码错误！");
        }

        // 4. 一致，根据手机号查询用户
        // 使用了mybatis plus，可以实现单表查询
        // ServiceImpl<UserMapper, User> 实现了ServiceImpl，告诉了mybatis plus对应的mapper和实体类
        // query就等于 select * from tb_user
        // eq等于where，one代表查一个用户，多个用户用list
        User user = query().eq("phone", phone).one();

        // 5. 判断用户是否存在

        if(user == null) {
            // 6. 不存在，创建新用户并保存，只要手机号正确就可以创建，其它的可以先设置为默认
            // 这里需要返回一个user，因为后续要存到session里面
            user = createUserWithPhone(phone);
        }

        // 7. 保存用户信息到redis
        // 随机生成token，作为登录令牌，true表示不带下划线
        String token = UUID.randomUUID().toString(true);
        // 将User对象转为hash存储，使用beanToMap将一个对象转化成map
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((filedName, filedValue) -> filedValue.toString()));

        // 存储，使用putAll可以存多个hash结构的数据
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.SECONDS);

        // 返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {

        // 创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

        // 保存用户
        // 使用mybatis plus的save方法
        save(user);

        return user;
    }


}
