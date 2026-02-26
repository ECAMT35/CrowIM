-- 只有 key 存在才 INCR；不存在返回 -1
-- ARGV[1] = ttlSeconds
if redis.call('EXISTS', KEYS[1]) == 1 then
  local v = redis.call('INCR', KEYS[1])
  local ttl = tonumber(ARGV[1])
  if ttl and ttl > 0 then
    redis.call('EXPIRE', KEYS[1], ttl)
  end
  return v
end
return -1
