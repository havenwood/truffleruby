require_relative 'common'

N = 1000

ary = []

def bench(ary, t)
  i = 0
  while i < N
    ary << i
    i += 1
  end
  ary
end

Truffle::Array.set_strategy(ary, STRATEGY)
p measure_ops(ary) {
  ary.clear
  ary << 0
  Truffle::Array.set_capacity(ary, 40_000 * N * THROUGHPUT_TIME)
  ary[0] = 0
  ary
}
