require_relative 'common'

# 1 write / READS reads
# writes to the first N elements for per bench()

N = 100
READS = 1024
raise if N > READS

ary = READS.times.to_a * N_THREADS
SUM = READS.times.reduce(:+)

def bench(ary, t)
  base = t * READS
  i = 0
  while i < N
    ary[base+i] = i

    j = base
    last = base + READS
    sum = 0
    while j < last
      sum += ary[j]
      j += 1
    end
    i += 1
  end
  raise sum.to_s unless sum == SUM
  sum
end

Truffle::Array.set_strategy(ary, STRATEGY)
p measure_ops(ary) 
