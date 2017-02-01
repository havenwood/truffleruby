require_relative 'common'

n = 10_000_000 / 5
ary = Array.new(n) { |i| i % 2 }

def setup(name, pre_barrier, post_barrier)
  eval <<EOR
def bench_#{name}(ary)
  n = ary.size
  i = 0
  sum = 0
  while i < n
    j = 0
    #{pre_barrier}
    while j < 10
      sum += ary.at(i+j)
      j += 1
    end
    i += 10
    #{post_barrier}
  end
  sum
end
EOR
end

def measure(input, name, pre_barrier = "", post_barrier = "")
  meth = setup(name, pre_barrier, post_barrier)
  n = 100
  results = eval <<EOR
  n.times.map do
    t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
    #{meth}(input)
    t1 = Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
    (t1-t0) / 1000.0
  end
EOR

  # [results.min, results.sort[results.size/2], results.max]
  results.min
end

puts Truffle::Debug.array_storage(ary)
p measure(ary, :local)
p bench_local(ary)

Thread.new {}
puts Truffle::Debug.array_storage(ary)
p measure(ary, :fixed)

ary << ary.pop
puts Truffle::Debug.array_storage(ary)
# p measure(ary, :synchd)
# p measure(ary, :synchd)

Truffle::Array.set_strategy(ary, :StampedLock)
puts Truffle::Debug.array_storage(ary)
p measure(ary, :stamped)

p measure(ary, :stamped2, "stamp = Truffle::Array.stamped_lock_optimistic_read(ary)",
                          "Truffle::Array.stamped_lock_validate(ary, stamp)")
p measure(ary, :stamped2, "stamp = Truffle::Array.stamped_lock_optimistic_read(ary)",
                          "Truffle::Array.stamped_lock_validate(ary, stamp)")

Truffle::Array.set_strategy(ary, :LayoutLock)
puts Truffle::Debug.array_storage(ary)
p measure(ary, :layout, "", "Truffle::Debug.volatile_read")
p measure(ary, :layout, "", "Truffle::Debug.volatile_read")
p measure(ary, :layout, "", "Truffle::Debug.volatile_read")
p measure(ary, :layout, "", "Truffle::Debug.volatile_read")
