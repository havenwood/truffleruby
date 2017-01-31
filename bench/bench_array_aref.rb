n = 10_000_000 / 5 # / 100 # for interp
ary = Array.new(n) { |i| i % 2 }

Thread.abort_on_exception = true

unless defined?(Truffle)
  module Truffle
    module Debug
      def self.array_storage(ary)
        ary.class.to_s
      end
    end
  end
end

def setup(name)
	eval <<EOR
def bench_#{name}(ary)
	n = ary.size
	i = 0
	sum = 0
	while i < n
		sum += ary.at(i)
		i += 1
	end
	sum
end
EOR
end

def measure(input, name)
	meth = setup(name)
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

setup(:first)
p bench_first(ary)

puts Truffle::Debug.array_storage(ary)
p measure(ary, :local)

Thread.new {}
puts Truffle::Debug.array_storage(ary)
p measure(ary, :fixed)

Truffle::Array.set_strategy(ary, :Synchronized)
puts Truffle::Debug.array_storage(ary)
p measure(ary, :synchd)
# p measure(ary, :synchd)

Truffle::Array.set_strategy(ary, :ReentrantLock)
puts Truffle::Debug.array_storage(ary)
p measure(ary, :reentrant)

Truffle::Array.set_strategy(ary, :CustomLock)
puts Truffle::Debug.array_storage(ary)
p measure(ary, :custom)
p measure(ary, :custom)

Truffle::Array.set_strategy(ary, :StampedLock)
puts Truffle::Debug.array_storage(ary)
p measure(ary, :stamped)

Truffle::Array.set_strategy(ary, :LayoutLock)
# ary << ary.pop
puts Truffle::Debug.array_storage(ary)
p measure(ary, :layout)
p measure(ary, :layout)
p measure(ary, :layout)
p measure(ary, :layout)
