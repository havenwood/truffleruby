require_relative 'common'

n = 10_000_000 / 5 # / 100 # for interp
ary = Array.new(n) { |i| i % 2 }

def bench(ary)
	n = ary.size
	i = 0
	sum = 0
	while i < n
		sum += ary.at(i)
		i += 1
	end
	sum
end

measure_single(ary)
