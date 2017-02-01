N = 100
READS = 100 # 1000
N_THREADS = 4
ary = READS.times.to_a

Thread.abort_on_exception = true

unless defined?(Truffle)
  module Truffle
    module Array
      def self.set_strategy(ary, strategy)
        ary
      end
    end
    module Debug
      def self.array_storage(ary)
        ary.class.to_s
      end
    end
  end
end

def setup(name)
  eval <<EOR, nil, __FILE__, __LINE__
def bench_#{name}(ary)
  i = 0
  while i < N
    ary[i] = i

    j = 0
    sum = 0
    while j < READS
      sum += ary[j]
      j += 1
    end
    i += 1
  end
  raise sum.to_s unless sum == 4950 # 499500
  sum
end
# alias bench bench_#{name}
# :bench_#{name}
EOR
end

$run = false

THREADS = N_THREADS.times.map {
  q = Queue.new
  ret = Queue.new
  Thread.new {
    while job = q.pop
      ops = 0
      while $run
        job.call
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
    THREADS.each { |q,ret| q.push -> { #{meth}(input) } }
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
p bench_first(ary)

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
