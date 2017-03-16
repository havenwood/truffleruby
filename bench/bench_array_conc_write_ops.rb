require_relative 'common'

# 100% writes

N = 100
CHUNK = 1000
SLICE = CHUNK + 24 # Some padding to avoid false sharing

ary = SLICE.times.to_a * (1 + N_THREADS + 1)
# Pad to avoid the Array being too close to its backing array,
# and other threads could force loading a cache line from the 1st thread

def bench(ary, t)
  base = (1+t) * SLICE
  i = 0
  while i < N
    j = 0
    while j < CHUNK
      ary[base+j] = j
      j += 1
    end
    i += 1
  end
end

Truffle::Array.set_strategy(ary, STRATEGY)
p measure_ops(ary) 
