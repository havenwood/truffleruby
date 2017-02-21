require_relative 'common'

N = 10_000_000 / 5 # / 100 # for interp
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
EOR
end

def measure(input, name)
  meth = setup(name)
  n = 100
  results = eval <<EOR
  n.times.map do
    input.clear
    input << 0

    t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
    #{meth}(input)
    t1 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
    (t1-t0) / 1000
  end
EOR

  # [results.min, results.sort[results.size/2], results.max]
  results.min
end

setup(:first)
p bench_first(ary).size

puts Truffle::Debug.array_storage(ary)
p measure(ary, :local)

Thread.new {}.join
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
