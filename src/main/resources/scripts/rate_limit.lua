local current = redis.call('INCR', KEYS[1])
if current == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end
local ttl = redis.call('TTL', KEYS[1])
if current > tonumber(ARGV[2]) then return {0, ttl} end
return {1, ttl}
