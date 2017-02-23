require_relative 'common'

# 1 write / READS reads
# writes to the first N elements for per bench()

N = 100
N_THREADS = Integer(ARGV[0] || 4)
READS = 1024
raise if N > READS
ary = READS.times.to_a * N_THREADS
SUM = READS.times.reduce(:+)

def setup(name)
  eval <<EOR, nil, __FILE__, __LINE__
def bench_#{name}(ary, t)
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
# alias bench bench_#{name}
# :bench_#{name}
EOR
end

$run = false

THREADS = N_THREADS.times.map { |t|
  q = Queue.new
  ret = Queue.new
  Thread.new {
    while job = q.pop
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

def measure(input, name)
  meth = setup(name)
  n = 5
  results = eval <<EOR
  n.times.map do
    ops = 0
    t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
    $run = true
    THREADS.each { |q,ret| q.push -> t { #{meth}(input, t) } }
    sleep 2
    $run = false
    THREADS.each { |q,ret| ops += ret.pop }
    t1 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
    p ops
    dt = (t1-t0) # / 1_000_000_000.0
    ops
  end
EOR

  [results.min, results.sort[results.size/2], results.max]
  # results.min
end

setup(:first)
p bench_first(ary, 0)

Truffle::Array.set_strategy(ary, :FixedSize)
puts Truffle::Debug.array_storage(ary)
p measure(ary, :fixed)

Truffle::Array.set_strategy(ary, :Synchronized)
puts Truffle::Debug.array_storage(ary)
p measure(ary, :synchd)

Truffle::Array.set_strategy(ary, :ReentrantLock)
puts Truffle::Debug.array_storage(ary)
p measure(ary, :reentrant)

Truffle::Array.set_strategy(ary, :CustomLock)
puts Truffle::Debug.array_storage(ary)
p measure(ary, :custom)

Truffle::Array.set_strategy(ary, :StampedLock)
puts Truffle::Debug.array_storage(ary)
p measure(ary, :stamped)

Truffle::Array.set_strategy(ary, :LayoutLock)
puts Truffle::Debug.array_storage(ary)
p measure(ary, :layout)

Truffle::Array.set_strategy(ary, :FastLayoutLock)
puts Truffle::Debug.array_storage(ary)
p measure(ary, :fast_layout)
