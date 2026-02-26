-- read 游标 max(old,new)
-- ARGV[1] = field(convId), ARGV[2] = value(seq), ARGV[3] = ttlSeconds
local old = redis.call('HGET', KEYS[1], ARGV[1])
if (not old) then
  redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
  local ttl = tonumber(ARGV[3])
  if ttl and ttl > 0 then
    redis.call('EXPIRE', KEYS[1], ttl)
  end
  return tonumber(ARGV[2])
end

if (tonumber(ARGV[2]) > tonumber(old)) then
  redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
  local ttl = tonumber(ARGV[3])
  if ttl and ttl > 0 then
    redis.call('EXPIRE', KEYS[1], ttl)
  end
  return tonumber(ARGV[2])
end

local ttl = tonumber(ARGV[3])
if ttl and ttl > 0 then
  redis.call('EXPIRE', KEYS[1], ttl)
end
return tonumber(old)
