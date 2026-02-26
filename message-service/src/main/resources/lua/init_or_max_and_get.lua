-- 回填 lastSeq 时只允许变大，不允许倒退
-- ARGV[1] = candidate, ARGV[2] = ttlSeconds
local old = redis.call('GET', KEYS[1])
if (not old) then
  redis.call('SET', KEYS[1], ARGV[1])
  local ttl = tonumber(ARGV[2])
  if ttl and ttl > 0 then
    redis.call('EXPIRE', KEYS[1], ttl)
  end
  return tonumber(ARGV[1])
end

if (tonumber(ARGV[1]) > tonumber(old)) then
  redis.call('SET', KEYS[1], ARGV[1])
  local ttl = tonumber(ARGV[2])
  if ttl and ttl > 0 then
    redis.call('EXPIRE', KEYS[1], ttl)
  end
  return tonumber(ARGV[1])
end

-- 即便不更新值，也可以选择“触摸 TTL”，让热点 key 保持缓存
local ttl = tonumber(ARGV[2])
if ttl and ttl > 0 then
  redis.call('EXPIRE', KEYS[1], ttl)
end
return tonumber(old)
