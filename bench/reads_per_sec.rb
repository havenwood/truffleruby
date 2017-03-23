require_relative 'bench/common'

N = 500_000
CHUNK = 1000
SLICE = CHUNK + 24 # Some padding to avoid false sharingg

ary = SLICE.times.to_a * (1 + N_THREADS + 1)
# Pad to avoid the Array being too close to its backing array,
# and other threads could force loading a cache line from the 1st thread
SUM = CHUNK.times.reduce(:+)

def bench(ary, t)
  base = (1+t) * SLICE
  i = 0
  accesses = 0
  while i < N
    sum = 0
    j = 0
    while j < CHUNK
      sum += ary[base+j]
      accesses += 1
      j += 1
    end
    i += 1
  end
  raise sum.to_s unless sum == SUM
  accesses
end

Truffle::Array.set_strategy(ary, STRATEGY)

20.times {
  t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
  p bench(ary, 0)
  t1 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
  p (t1-t0)
}

