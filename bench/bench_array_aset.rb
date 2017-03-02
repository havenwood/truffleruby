require_relative 'common'

n = 10_000_000 / 5 # / 100 # for interp
ary = Array.new(n, 0)

def bench(ary)
  n = ary.size
  i = 0
  while i < n
    ary[i] = i
    i += 1
  end
  ary
end

Truffle::Array.set_strategy(ary, STRATEGY)
measure_single(ary)
