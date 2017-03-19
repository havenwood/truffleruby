require_relative 'common'

WRITE_PERCENTS = 20

N = 100
CHUNK = 1000
SLICE = CHUNK + 24 # Some padding to avoid false sharing
WRITES_MODULO = 100 / WRITE_PERCENTS

ary = SLICE.times.to_a * (1 + N_THREADS + 1)
# Pad to avoid the Array being too close to its backing array,
# and other threads could force loading a cache line from the 1st thread

def bench(ary, t)
  base = (1+t) * SLICE
  i = 0
  while i < N
    sum = 0
    j = 0
    while j < CHUNK
      if j % WRITES_MODULO == 0
        ary[base+j] = sum
      else
        sum += ary[base+j]
      end
      j += 1
    end
    i += 1
  end
  sum
end

Truffle::Array.set_strategy(ary, STRATEGY)
p measure_ops(ary) 
