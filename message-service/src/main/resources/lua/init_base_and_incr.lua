-- key 不存在：SET base，再 INCR，返回 base+1
-- key 存在：直接 INCR
-- ARGV[1] = base, ARGV[2] = ttlSeconds
if redis.call('EXISTS', KEYS[1]) == 0 then
  redis.call('SET', KEYS[1], ARGV[1])
end
local v = redis.call('INCR', KEYS[1])
local ttl = tonumber(ARGV[2])
if ttl and ttl > 0 then
  redis.call('EXPIRE', KEYS[1], ttl)
end
return v
