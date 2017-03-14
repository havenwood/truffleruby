require_relative 'common'

# 100% writes

N = 100
CHUNK = 1000
SLICE = CHUNK + 24 # Some padding to avoid false sharingg

ary = SLICE.times.to_a * N_THREADS

def bench(ary, t)
  base = t * SLICE
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
