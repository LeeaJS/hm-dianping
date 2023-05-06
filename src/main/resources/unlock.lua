---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by lijunsheng.
--- DateTime: 2023/5/2 22:45
---

-- 获取锁的线程标识 get key
local id = redis.call('get', KEYS[1])

-- 比较线程标识和锁中的标识是否一致
if(id == ARGV[1]) then
    -- 释放锁
    return redis.call('del', KEYS[1])
end
return 0