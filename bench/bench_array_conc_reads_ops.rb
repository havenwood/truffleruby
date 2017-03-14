require_relative 'common'

N = 100
CHUNK = 1000
SLICE = CHUNK + 24 # Some padding to avoid false sharingg

ary = SLICE.times.to_a * N_THREADS
SUM = CHUNK.times.reduce(:+)

def bench(ary, t)
  base = t * SLICE
  i = 0
  while i < N
    sum = 0
    j = 0
    while j < CHUNK
      sum += ary[base+j]
      j += 1
    end
    i += 1
  end
  raise sum.to_s unless sum == SUM
  sum
end

Truffle::Array.set_strategy(ary, STRATEGY)
p measure_ops(ary) 
