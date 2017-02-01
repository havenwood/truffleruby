require_relative 'common'

N = 10_000_000 / 100
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

THREADS = N_THREADS.times.map {
  q = Queue.new
  ret = Queue.new
  Thread.new {
    while job = q.pop
      ret.push job.call
    end
  }
  [q, ret]
}

def measure(input, name)
  meth = setup(name)
  n = 100
  results = eval <<EOR
  n.times.map do
    input.clear
    input << 0

    t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
    THREADS.each { |q,ret| q.push -> { #{meth}(input) } }
    THREADS.each { |q,ret| ret.pop }
    t1 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
    (t1-t0) / 1000
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
