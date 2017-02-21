require_relative 'common'

N = 1_000 # 10_000_000 / 100 / 100
N_THREADS = 4
ary = []

def setup(name)
  eval <<EOR
def bench_#{name}(ary)
  i = 0
  while i < N
    ary << i
    i += 1
  end
  ary
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
    input.clear
    input << 0

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
p bench_first(ary).size

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
