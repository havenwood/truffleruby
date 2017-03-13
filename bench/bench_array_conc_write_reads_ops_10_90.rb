require_relative 'common'

N = 100
READS = 1024 # 90 % reads
WRITES = 102 # 10 % writes

SLICE = READS + 1024 # Some padding to avoid false sharing

ary = SLICE.times.to_a * N_THREADS

def bench(ary, t)
  base = t * SLICE
  i = 0
  while i < N
    sum = 0
    j = 0
    while j < READS
      if j % WRITES == 0
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
