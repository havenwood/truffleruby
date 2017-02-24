require_relative 'common'

# 1 write / READS reads
# writes to the first N elements for per bench()

N = 100
STRATEGY = ARGV[0].to_sym || raise("First argument must be the strategy")
N_THREADS = Integer(ARGV[1] || 4)
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

$run = false

THREADS = N_THREADS.times.map { |t|
  q = Queue.new
  ret = Queue.new
  Thread.new {
    while job = q.pop
      Thread.pass until $go
      ops = 0
      while $run
        job.call(t)
        ops += 1
      end
      ret.push ops
    end
  }
  [q, ret]
}

def measure(input)
  n = 9
  results = n.times.map do
    $go = false
    $run = true
    THREADS.each { |q,ret| q.push -> t { bench(input, t) } }
    t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
    $go = true

    sleep 2

    $run = false
    results = THREADS.map { |q,ret| ret.pop }
    t1 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
    ops = results.reduce(:+)
    dt = (t1-t0) / 1_000_000_000.0
    ops /= dt
    p ops
    ops
  end

  [results.min, results.sort[results.size/2], results.max]
  # results.min
end

Truffle::Array.set_strategy(ary, STRATEGY)
p measure(ary)
