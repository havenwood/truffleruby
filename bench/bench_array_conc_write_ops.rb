require_relative 'common'

# 100% writes

N = 100
WRITES = 1024 # 90 % reads

SLICE = WRITES # + 1024 # Some padding to avoid false sharing

ary = SLICE.times.to_a * N_THREADS

def bench(ary, t)
  base = t * SLICE
  i = 0
  while i < N
    j = 0
    while j < READS
      ary[base+j] = j
      j += 1
    end
    i += 1
  end
end

Truffle::Array.set_strategy(ary, STRATEGY)
p measure_ops(ary) 
